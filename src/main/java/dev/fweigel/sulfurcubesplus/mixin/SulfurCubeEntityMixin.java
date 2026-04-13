package dev.fweigel.sulfurcubesplus.mixin;

import dev.fweigel.sulfurcubesplus.IFuseHolder;
import dev.fweigel.sulfurcubesplus.ILightHolder;
import dev.fweigel.sulfurcubesplus.ISulfurCubeAnvilMenu;
import dev.fweigel.sulfurcubesplus.SulfurCubeEntityAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SulfurCube.class)
public abstract class SulfurCubeEntityMixin implements IFuseHolder, ILightHolder {

    // Registered as part of SulfurCube's static init (mixin static fields merge in).
    // ID is assigned after all vanilla SulfurCube IDs.
    @Unique
    private static final EntityDataAccessor<Integer> SULFURCUBESPLUS$FUSE =
            SynchedEntityData.defineId(SulfurCube.class, EntityDataSerializers.INT);

    /** Tracks the block position where we last placed a light block, for cleanup on move/remove. */
    @Unique
    private BlockPos sulfurcubesplus$lastLightPos = null;

    /** Non-null while a player has a workstation UI open for this cube. */
    @Unique
    private SulfurCubeEntityAccess sulfurcubesplus$activeAccess = null;

    @Unique
    private static final TagKey<DamageType> SULFURCUBESPLUS$CACTUS_PUSH = TagKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath("sulfurcubesplus", "cactus_push"));

    @Unique
    private static final TagKey<Item> SULFURCUBESPLUS$SWALLOWABLE = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("minecraft", "sulfur_cube_swallowable"));

    private static final int FUSE_TICKS = 40;   // 2 seconds, same as primed TNT
    private static final float EXPLOSION_POWER = 8.0f;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void sulfurcubesplus$defineFuseData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(SULFURCUBESPLUS$FUSE, -1);
    }

    // IFuseHolder — lets the client renderer read the synced fuse value
    @Override
    public int sulfurcubesplus$getFuseTicks() {
        return ((Entity)(Object)this).entityData.get(SULFURCUBESPLUS$FUSE);
    }

    /** Any damage on a TNT-carrying cube starts the fuse; ignores further hits while lit. */
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void sulfurcubesplus$startFuseOnDamage(
            ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        SulfurCube self = (SulfurCube) (Object) this;

        if (self.isRemoved()) {
            cir.setReturnValue(false);
            return;
        }

        ItemStack bodyItem = self.getItemBySlot(EquipmentSlot.BODY);
        if (bodyItem.isEmpty() || !bodyItem.is(Items.TNT)) {
            return;
        }

        if (((Entity)(Object)this).entityData.get(SULFURCUBESPLUS$FUSE) >= 0) {
            cir.setReturnValue(false);
            return;
        }

        ((Entity)(Object)this).entityData.set(SULFURCUBESPLUS$FUSE, FUSE_TICKS);
        cir.setReturnValue(true);
    }

    /** Counts the fuse down each server tick and detonates at zero. */
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void sulfurcubesplus$tickFuse(ServerLevel level, CallbackInfo ci) {
        int ticks = ((Entity)(Object)this).entityData.get(SULFURCUBESPLUS$FUSE);
        if (ticks < 0) {
            return;
        }

        int next = ticks - 1;
        ((Entity)(Object)this).entityData.set(SULFURCUBESPLUS$FUSE, next);

        if (next <= 0) {
            triggerExplosion((SulfurCube) (Object) this, level);
        }
    }

    /** Flint and steel lights the fuse. */
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void sulfurcubesplus$igniteWithFlintAndSteel(
            Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        SulfurCube self = (SulfurCube) (Object) this;

        if (self.level().isClientSide()) {
            return;
        }

        ItemStack handItem = player.getItemInHand(hand);
        if (!handItem.is(Items.FLINT_AND_STEEL)) {
            return;
        }

        ItemStack bodyItem = self.getItemBySlot(EquipmentSlot.BODY);
        if (bodyItem.isEmpty() || !bodyItem.is(Items.TNT)) {
            return;
        }

        if (((Entity)(Object)this).entityData.get(SULFURCUBESPLUS$FUSE) >= 0) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        handItem.hurtAndBreak(1, player, hand);
        ((Entity)(Object)this).entityData.set(SULFURCUBESPLUS$FUSE, FUSE_TICKS);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    /**
     * Each server tick: place a light block at the entity's block position when it carries a
     * light-emitting block (e.g. glowstone, frog light). Removes the old light block whenever
     * the entity moves to a new block position, and removes it entirely when the item is gone.
     * This is real server-side light — it prevents mob spawning and affects crops.
     */
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void sulfurcubesplus$tickLight(ServerLevel level, CallbackInfo ci) {
        SulfurCube self = (SulfurCube) (Object) this;
        ItemStack bodyItem = self.getItemBySlot(EquipmentSlot.BODY);

        int lightLevel = 0;
        if (!bodyItem.isEmpty() && bodyItem.getItem() instanceof BlockItem blockItem) {
            lightLevel = blockItem.getBlock().defaultBlockState().getLightEmission();
        }

        BlockPos currentPos = self.blockPosition();

        if (lightLevel > 0) {
            if (!currentPos.equals(sulfurcubesplus$lastLightPos)) {
                // Place the new light block first so there is never a dark gap between positions.
                BlockState existing = level.getBlockState(currentPos);
                if (existing.isAir() || existing.is(Blocks.LIGHT)) {
                    level.setBlock(currentPos,
                            Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lightLevel), 3);
                    // Only then remove the old one.
                    if (sulfurcubesplus$lastLightPos != null) {
                        sulfurcubesplus$removeLightAt(level, sulfurcubesplus$lastLightPos);
                    }
                    sulfurcubesplus$lastLightPos = currentPos;
                }
                // If the new position is not free, keep the old light block in place.
            } else {
                // Same position — update light level in case the carried item changed.
                BlockState existing = level.getBlockState(currentPos);
                if (existing.is(Blocks.LIGHT) && existing.getValue(LightBlock.LEVEL) != lightLevel) {
                    level.setBlock(currentPos,
                            Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lightLevel), 3);
                }
            }
        } else if (sulfurcubesplus$lastLightPos != null) {
            sulfurcubesplus$removeLightAt(level, sulfurcubesplus$lastLightPos);
            sulfurcubesplus$lastLightPos = null;
        }
    }

    /** Called via ServerEntityEvents.ENTITY_UNLOAD to clean up our light block on entity removal. */
    @Override
    public void sulfurcubesplus$cleanupLight(ServerLevel level) {
        if (sulfurcubesplus$lastLightPos != null) {
            sulfurcubesplus$removeLightAt(level, sulfurcubesplus$lastLightPos);
            sulfurcubesplus$lastLightPos = null;
        }

        // Also close any open workstation UI so items are returned before the entity disappears.
        if (sulfurcubesplus$activeAccess != null) {
            ServerPlayer player = sulfurcubesplus$activeAccess.player;
            sulfurcubesplus$activeAccess = null;
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }
        }
    }

    @Unique
    private static void sulfurcubesplus$removeLightAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * Each server tick: deal cactus damage (1 HP) to any living entity whose bounding box
     * overlaps the SulfurCube when it carries a cactus. The entity's invincibility frames
     * prevent this from firing more than once per ~0.5 s.
     */
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void sulfurcubesplus$tickCactus(ServerLevel level, CallbackInfo ci) {
        SulfurCube self = (SulfurCube) (Object) this;
        ItemStack bodyItem = self.getItemBySlot(EquipmentSlot.BODY);
        if (bodyItem.isEmpty() || !bodyItem.is(Items.CACTUS)) {
            return;
        }

        AABB box = self.getBoundingBox();
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != self);
        for (LivingEntity entity : nearby) {
            entity.hurt(level.damageSources().cactus(), 1.0f);
            // SulfurCubes are immune to cactus damage when carrying a block, and even
            // non-immune cubes get no knockback since cactus has no attacker entity.
            // Push any neighbouring SulfurCube away explicitly.
            if (entity instanceof SulfurCube) {
                Vec3 pushDir = entity.position().subtract(self.position());
                if (pushDir.lengthSqr() > 1e-6) {
                    double strength = Math.max(0.0, 1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) * 0.6;
                    entity.addDeltaMovement(pushDir.normalize().scale(strength));
                }
            }
        }
    }

    /**
     * When a cactus-carrying SulfurCube is hit, the attacker takes cactus damage (1 HP) —
     * as if they had run into a placed cactus block.
     */
    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void sulfurcubesplus$cactusRetaliationOnHurt(
            ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        SulfurCube self = (SulfurCube) (Object) this;
        ItemStack bodyItem = self.getItemBySlot(EquipmentSlot.BODY);
        if (bodyItem.isEmpty() || !bodyItem.is(Items.CACTUS)) {
            return;
        }

        Entity attacker = source.getDirectEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            livingAttacker.hurt(level.damageSources().cactus(), 1.0f);
        }
    }

    /**
     * When a cactus-carrying cube touches a placed cactus block it receives cactus damage but
     * vanilla skips the push (only Player hits trigger playerHit). This injection replicates the
     * same applyKnockback logic — push away from the cactus block that caused the damage.
     */
    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void sulfurcubesplus$cactusKnockback(
            ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        SulfurCube self = (SulfurCube) (Object) this;
        if (!self.hasBodyItem()) return;
        if (!source.is(SULFURCUBESPLUS$CACTUS_PUSH)) return;

        // Scan the cube's block position and all 6 neighbours for a cactus block and
        // accumulate a push direction away from each one found.
        BlockPos cubePos = self.blockPosition();
        Vec3 push = Vec3.ZERO;
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(cubePos.relative(dir)).is(Blocks.CACTUS)) {
                push = push.add(dir.getOpposite().getUnitVec3());
            }
        }
        // Also check the cube's own position in case blockPosition() sits inside the cactus.
        if (level.getBlockState(cubePos).is(Blocks.CACTUS)) {
            Vec3 movement = self.getDeltaMovement();
            if (movement.horizontalDistanceSqr() > 1e-6) {
                push = push.add(new Vec3(-movement.x, 0.0, -movement.z).normalize());
            }
        }

        if (push.lengthSqr() < 1e-6) return;

        double strength = Math.sqrt(amount)
                * Math.max(0.0, 1.0 - self.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE))
                * 0.6;
        self.addDeltaMovement(push.normalize().scale(strength));
    }

    /**
     * When a player right-clicks a SulfurCube carrying a crafting-table-style block, open the
     * corresponding UI. Items placed inside are returned to the player on close — the cube never
     * stores data.
     *
     * Pass-through conditions (vanilla keeps full control):
     *   - Sneak + right-click       → vanilla bucket capture (Bucketable.bucketMobPickup)
     *   - Shears                    → vanilla shear (drops the body block)
     *   - Baby cube                 → vanilla feeding
     *   - Swallowable DIFFERENT item → vanilla equipItem (swaps body block)
     *
     * Same swallowable item as what is already equipped: vanilla equipItem returns false and
     * PASS — falls through here to open the UI instead of doing nothing.
     *
     * IMPORTANT: the cancel must happen on BOTH sides. SulfurCube implements Bucketable and
     * calls Bucketable.bucketMobPickup inside mobInteract. If we only cancel on the server,
     * the client still runs the method and bucketMobPickup removes the entity client-side,
     * causing the cube to visually disappear even though the server kept it alive.
     */
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void sulfurcubesplus$openFunctionalBlockUI(
            Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        // Only fire once — skip the off-hand call.
        if (hand != InteractionHand.MAIN_HAND) return;

        SulfurCube self = (SulfurCube) (Object) this;

        // Sneak + right-click: pass through to vanilla (bucket capture etc.)
        if (player.isShiftKeyDown()) return;

        // Baby cube: pass through to vanilla (feeding)
        if (self.isBaby()) return;

        ItemStack bodyItem = self.getItemBySlot(EquipmentSlot.BODY);
        if (bodyItem.isEmpty()) return;

        ItemStack heldItem = player.getItemInHand(hand);

        // Shears: pass through to vanilla (shearing drops the body item)
        if (heldItem.is(Items.SHEARS)) return;

        // Swallowable item that is DIFFERENT from what's already carried: pass through to
        // vanilla so equipItem can swap the body block. Same item: fall through to open UI
        // (vanilla equipItem returns false + PASS in that case — we do better).
        if (!heldItem.isEmpty() && heldItem.is(SULFURCUBESPLUS$SWALLOWABLE)
                && !heldItem.is(bodyItem.getItem())) {
            return;
        }

        // Body item must map to a workstation menu; otherwise nothing to open.
        if (!sulfurcubesplus$isWorkstationItem(bodyItem)) return;

        // Cancel on BOTH client and server to prevent client-side Bucketable capture.
        cir.setReturnValue(InteractionResult.SUCCESS);

        // Only actually open the menu on the server.
        if (self.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        SulfurCubeEntityAccess access = new SulfurCubeEntityAccess(self, serverPlayer, bodyItem.getItem());

        MenuProvider menu = null;

        if (bodyItem.is(Items.CRAFTING_TABLE)) {
            menu = new SimpleMenuProvider(
                    (id, inv, p) -> new CraftingMenu(id, inv, access),
                    Component.translatable("container.crafting"));
        } else if (bodyItem.is(Items.STONECUTTER)) {
            menu = new SimpleMenuProvider(
                    (id, inv, p) -> new StonecutterMenu(id, inv, access),
                    Component.translatable("container.stonecutter"));
        } else if (bodyItem.is(Items.GRINDSTONE)) {
            menu = new SimpleMenuProvider(
                    (id, inv, p) -> new GrindstoneMenu(id, inv, access),
                    Component.translatable("container.grindstone_title"));
        } else if (bodyItem.is(Items.SMITHING_TABLE)) {
            menu = new SimpleMenuProvider(
                    (id, inv, p) -> new SmithingMenu(id, inv, access),
                    Component.translatable("container.upgrade"));
        } else if (bodyItem.is(Items.LOOM)) {
            menu = new SimpleMenuProvider(
                    (id, inv, p) -> new LoomMenu(id, inv, access),
                    Component.translatable("container.loom"));
        } else if (bodyItem.is(Items.CARTOGRAPHY_TABLE)) {
            menu = new SimpleMenuProvider(
                    (id, inv, p) -> new CartographyTableMenu(id, inv, access),
                    Component.translatable("container.cartography_table"));
        } else if (bodyItem.is(Items.ANVIL) || bodyItem.is(Items.CHIPPED_ANVIL) || bodyItem.is(Items.DAMAGED_ANVIL)) {
            menu = new SimpleMenuProvider(
                    (id, inv, p) -> new AnvilMenu(id, inv, access),
                    Component.translatable("container.repair"));
        }

        if (menu != null) {
            serverPlayer.openMenu(menu);
            sulfurcubesplus$activeAccess = access;
            // For anvil menus: store the entity access directly on the menu instance so
            // AnvilMenuMixin can reach it without touching the access field (which would
            // interfere with placed-anvil menus that use a real ContainerLevelAccess).
            if (serverPlayer.containerMenu instanceof ISulfurCubeAnvilMenu anvilMenu) {
                anvilMenu.sulfurcubesplus$setEntityAccess(access);
            }
        }
    }

    @Unique
    private static boolean sulfurcubesplus$isWorkstationItem(ItemStack stack) {
        return stack.is(Items.CRAFTING_TABLE) || stack.is(Items.STONECUTTER)
                || stack.is(Items.GRINDSTONE) || stack.is(Items.SMITHING_TABLE)
                || stack.is(Items.LOOM) || stack.is(Items.CARTOGRAPHY_TABLE)
                || stack.is(Items.ANVIL) || stack.is(Items.CHIPPED_ANVIL)
                || stack.is(Items.DAMAGED_ANVIL);
    }

    /**
     * Each server tick: check whether the player who has a workstation UI open is still close
     * enough to the cube and whether the cube still carries the expected body item. Closes the
     * menu (returning any items inside) when either condition is violated. Also clears the
     * tracking reference when the player closed the menu themselves.
     */
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void sulfurcubesplus$tickMenuValidity(ServerLevel level, CallbackInfo ci) {
        if (sulfurcubesplus$activeAccess == null) return;

        SulfurCube self = (SulfurCube) (Object) this;
        ServerPlayer player = sulfurcubesplus$activeAccess.player;

        if (player.containerMenu == player.inventoryMenu) {
            // Player already closed the menu on their own.
            sulfurcubesplus$activeAccess = null;
            return;
        }

        ItemStack currentBody = self.getItemBySlot(EquipmentSlot.BODY);
        // Anvil variants (normal→chipped→damaged) count as the same workstation; only close
        // when the anvil breaks completely (body slot becomes empty or changes to something else).
        Item originalType = sulfurcubesplus$activeAccess.bodyItemType;
        boolean originalWasAnvil = originalType == Items.ANVIL
                || originalType == Items.CHIPPED_ANVIL
                || originalType == Items.DAMAGED_ANVIL;
        boolean bodyChanged = originalWasAnvil
                ? !(currentBody.is(Items.ANVIL) || currentBody.is(Items.CHIPPED_ANVIL) || currentBody.is(Items.DAMAGED_ANVIL))
                : !currentBody.is(originalType);
        boolean tooFar = self.distanceToSqr(player) > 64.0; // 8-block range, same as vanilla

        if (bodyChanged || tooFar) {
            player.closeContainer(); // triggers menu.removed() → clearContainer → items returned
            sulfurcubesplus$activeAccess = null;
        }
    }

    /**
     * Anvil-carrying cubes fall faster than normal (extra -0.08 m/t² while airborne and
     * descending), mimicking the way placed anvil blocks crush entities below them.
     */
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void sulfurcubesplus$anvilFallsFaster(ServerLevel level, CallbackInfo ci) {
        SulfurCube self = (SulfurCube) (Object) this;
        ItemStack bodyItem = self.getItemBySlot(EquipmentSlot.BODY);
        if (bodyItem.isEmpty()) return;
        if (!bodyItem.is(Items.ANVIL) && !bodyItem.is(Items.CHIPPED_ANVIL) && !bodyItem.is(Items.DAMAGED_ANVIL)) return;
        if (self.onGround()) return;
        if (self.getDeltaMovement().y >= 0) return;
        self.addDeltaMovement(new Vec3(0, -0.08, 0));
    }

    private static void triggerExplosion(SulfurCube cube, ServerLevel level) {
        cube.discard();
        level.explode(
                cube,
                cube.getX(),
                cube.getY(),
                cube.getZ(),
                EXPLOSION_POWER,
                false,
                Level.ExplosionInteraction.TNT
        );
    }
}
