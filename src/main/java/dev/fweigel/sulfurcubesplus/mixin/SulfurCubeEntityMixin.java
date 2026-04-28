package dev.fweigel.sulfurcubesplus.mixin;

import dev.fweigel.sulfurcubesplus.IGhastSoulHolder;
import dev.fweigel.sulfurcubesplus.ILightHolder;
import dev.fweigel.sulfurcubesplus.ISulfurCubeAnvilMenu;
import dev.fweigel.sulfurcubesplus.SulfurCubeEntityAccess;
import dev.fweigel.sulfurcubesplus.SulfurCubesPlus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
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
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DriedGhastBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.List;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SulfurCube.class)
public abstract class SulfurCubeEntityMixin implements ILightHolder, IGhastSoulHolder {

    /** Tracks the block position where we last placed a light block, for cleanup on move/remove. */
    @Unique
    private BlockPos sulfurcubesplus$lastLightPos = null;

    /** All positions where we currently have a phantom redstone block placed. */
    @Unique
    private java.util.Set<BlockPos> sulfurcubesplus$lastRedstonePositions = null;

    /** Non-null while a player has a workstation UI open for this cube. */
    @Unique
    private SulfurCubeEntityAccess sulfurcubesplus$activeAccess = null;

    /** How many ticks the cube has been in water during the current hydration stage. */
    @Unique
    private int sulfurcubesplus$hydrationTicks = 0;

    /** Current hydration stage (0–3). Advances while in water; never resets. */
    @Unique
    private int sulfurcubesplus$hydrationStage = 0;

    /** UUID of the happy ghast this cube is wrapping (null when not in ghast-soul mode). */
    @Unique
    private UUID sulfurcubesplus$linkedGhastUuid = null;

    /** True while this cube is locked onto a happy ghast. */
    @Unique
    private boolean sulfurcubesplus$isGhastSoulMode = false;

    /** Permanently true once this cube has gone through a soul-mode cycle; prevents re-use. */
    @Unique
    private boolean sulfurcubesplus$hasBeenSoulMode = false;

    @Unique
    private static final TagKey<DamageType> SULFURCUBESPLUS$CACTUS_PUSH = TagKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath("sulfurcubesplus", "cactus_push"));

    @Unique
    private static final TagKey<Item> SULFURCUBESPLUS$SWALLOWABLE = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("minecraft", "sulfur_cube_swallowable"));

    // ── IGhastSoulHolder ──────────────────────────────────────────────────────

    @Override
    public boolean sulfurcubesplus$isGhastSoulMode() {
        // sulfurcubesplus$isGhastSoulMode is only set server-side. On the client,
        // derive soul mode from the passenger relationship (which IS synced via packets).
        return sulfurcubesplus$isGhastSoulMode
                || ((Entity)(Object)this).getVehicle() instanceof HappyGhast;
    }

    @Override
    public void sulfurcubesplus$releaseGhastSoul() {
        sulfurcubesplus$freeFromGhast((SulfurCube)(Object)this);
    }

    /**
     * Vanilla SulfurCube.setUpSplitCube() calls child.setBaby(true), making split children
     * unable to hold items (vanilla's canHoldItem rejects babies). For size > 1 children we
     * want adults that can hold blocks.
     *
     * Calling setBaby(false) triggers ageBoundaryReached() → setSize(2, true), collapsing every
     * split child to size 2. We re-apply the intended split size immediately afterwards to
     * preserve the correct intermediate step (e.g. size-8 → size-4 → size-2 → size-1).
     */
    /**
     * Vanilla ageBoundaryReached() always calls setSize(2, true) when a baby grows up. For
     * size-1 (tiny) cubes that should stay tiny, cancel the whole method before it runs.
     */
    @Inject(method = "ageBoundaryReached", at = @At("HEAD"), cancellable = true)
    private void sulfurcubesplus$tinyStaysTiny(CallbackInfo ci) {
        if (((SulfurCube)(Object)this).getSize() == 1) {
            ci.cancel();
        }
    }

    @Inject(method = "setUpSplitCube", at = @At("TAIL"))
    private void sulfurcubesplus$splitCubeAdultCorrectSize(AbstractCubeMob child, int size, float xOffset, float zOffset, CallbackInfo ci) {
        if (size > 1) {
            child.setBaby(false);       // makes adult (canHoldItem works); triggers ageBoundaryReached → setSize(2)
            child.setSize(size, false); // restore the intended split size
        }
    }

    /** Any damage on a TNT-carrying cube starts the fuse; vanilla handles knockback. */
    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void sulfurcubesplus$startFuseOnDamage(
            ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        SulfurCube self = (SulfurCube) (Object) this;

        ItemStack bodyItem = self.getItemBySlot(EquipmentSlot.BODY);
        if (bodyItem.isEmpty() || !bodyItem.is(Items.TNT)) {
            return;
        }

        if (!self.isPrimed()) {
            self.primeTime(source.is(DamageTypeTags.IS_EXPLOSION));
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

        if (self.isPrimed()) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        handItem.hurtAndBreak(1, player, hand);
        self.primeTime(false);
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

        if (sulfurcubesplus$lastRedstonePositions != null) {
            for (BlockPos pos : sulfurcubesplus$lastRedstonePositions) {
                sulfurcubesplus$removeRedstoneAt(level, pos);
            }
            sulfurcubesplus$lastRedstonePositions = null;
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
        if (!level.isLoaded(pos)) return;
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * Each server tick: maintain phantom redstone blocks at every block position overlapped by
     * the cube's bounding box. Covering the full bounding box means a size-2+ cube acts as a
     * signal-15 source across all its physical blocks — so redstone dust that the cube body
     * touches (even diagonally) gets powered, exactly matching how a piston-pushed Redstone
     * Block would behave. New positions are placed before old ones are removed to keep the
     * signal alive during movement. Observers fire on both old and new positions.
     */
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void sulfurcubesplus$tickRedstone(ServerLevel level, CallbackInfo ci) {
        SulfurCube self = (SulfurCube) (Object) this;
        ItemStack bodyItem = self.getItemBySlot(EquipmentSlot.BODY);
        boolean carriesRedstone = !bodyItem.isEmpty() && bodyItem.is(Items.REDSTONE_BLOCK);

        if (!carriesRedstone) {
            if (sulfurcubesplus$lastRedstonePositions != null) {
                for (BlockPos pos : sulfurcubesplus$lastRedstonePositions) {
                    sulfurcubesplus$removeRedstoneAt(level, pos);
                }
                sulfurcubesplus$lastRedstonePositions = null;
            }
            return;
        }

        // Collect block positions the cube meaningfully occupies: only those whose CENTER
        // falls strictly inside the bounding box. This requires the cube to cover more than
        // half of that block, preventing ghost-powering from a 0.01-block edge clip.
        AABB box = self.getBoundingBox();
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        java.util.Set<BlockPos> wanted = new java.util.LinkedHashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
            if (cx > box.minX && cx < box.maxX
                    && cy > box.minY && cy < box.maxY
                    && cz > box.minZ && cz < box.maxZ) {
                BlockState existing = level.getBlockState(pos);
                if (existing.isAir() || existing.is(SulfurCubesPlus.PHANTOM_REDSTONE_BLOCK)) {
                    wanted.add(pos.immutable());
                }
            }
        }

        if (wanted.equals(sulfurcubesplus$lastRedstonePositions)) return;

        // Place new positions first (no signal gap), then remove positions no longer needed.
        for (BlockPos pos : wanted) {
            if (!level.getBlockState(pos).is(SulfurCubesPlus.PHANTOM_REDSTONE_BLOCK)) {
                level.setBlock(pos, SulfurCubesPlus.PHANTOM_REDSTONE_BLOCK.defaultBlockState(), 3);
            }
        }
        if (sulfurcubesplus$lastRedstonePositions != null) {
            for (BlockPos old : sulfurcubesplus$lastRedstonePositions) {
                if (!wanted.contains(old)) {
                    sulfurcubesplus$removeRedstoneAt(level, old);
                }
            }
        }
        sulfurcubesplus$lastRedstonePositions = wanted.isEmpty() ? null : wanted;
    }

    @Unique
    private static void sulfurcubesplus$removeRedstoneAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        if (level.getBlockState(pos).is(SulfurCubesPlus.PHANTOM_REDSTONE_BLOCK)) {
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

        // Ghast-soul mode: shears always free the ghast; size-1 blocks all other interactions;
        // larger sizes fall through to normal workstation/block interaction logic below.
        if (sulfurcubesplus$isGhastSoulMode) {
            ItemStack heldItem = player.getItemInHand(hand);
            if (heldItem.is(Items.SHEARS)) {
                cir.setReturnValue(InteractionResult.SUCCESS);
                if (!self.level().isClientSide()) {
                    heldItem.hurtAndBreak(1, player, hand);
                    sulfurcubesplus$freeFromGhast(self);
                }
                return;
            }
            if (self.getSize() <= 1) {
                cir.setReturnValue(InteractionResult.PASS);
                return;
            }
            // Size > 1: fall through to normal interaction handling below.
        }

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

    /**
     * Each server tick: when carrying a dried ghast and submerged/in rain, count toward the
     * next hydration stage using the same delay as the vanilla DriedGhastBlock. Also keeps the
     * body-item's BLOCK_STATE component in sync so the correct hydration-level texture shows
     * through the cube. Emits green HAPPY_VILLAGER particles matching vanilla.
     */
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void sulfurcubesplus$tickHydration(ServerLevel level, CallbackInfo ci) {
        if (sulfurcubesplus$isGhastSoulMode) return;

        SulfurCube self = (SulfurCube)(Object)this;
        ItemStack bodyItem = self.getItemBySlot(EquipmentSlot.BODY);

        if (bodyItem.isEmpty() || !bodyItem.is(Items.DRIED_GHAST)) {
            sulfurcubesplus$hydrationStage = 0;
            sulfurcubesplus$hydrationTicks = 0;
            return;
        }

        // Cubes that have already gone through the soul cycle cannot re-hydrate.
        // Show angry (red) particles while in water to signal this to the player.
        if (sulfurcubesplus$hasBeenSoulMode) {
            if (self.isInWaterOrRain() && self.getRandom().nextInt(20) == 0) {
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        self.getX(), self.getY() + self.getBbHeight() * 0.5, self.getZ(),
                        1, 0.3, 0.3, 0.3, 0.0);
            }
            return;
        }

        // Keep body-item visual in sync with the current hydration stage (e.g. after reload).
        if (sulfurcubesplus$hydrationStage > 0) {
            BlockItemStateProperties current = bodyItem.getOrDefault(
                    DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
            String stored = current.properties().getOrDefault("hydration", "0");
            if (!stored.equals(String.valueOf(sulfurcubesplus$hydrationStage))) {
                ItemStack updated = new ItemStack(Items.DRIED_GHAST);
                updated.set(DataComponents.BLOCK_STATE,
                        BlockItemStateProperties.EMPTY.with(
                                DriedGhastBlock.HYDRATION_LEVEL, sulfurcubesplus$hydrationStage));
                self.setItemSlot(EquipmentSlot.BODY, updated);
            }
        }

        if (!self.isInWaterOrRain()) return;

        // 1-in-6 chance per tick, 1 particle — matches vanilla DriedGhastBlock.animateTick.
        if (self.getRandom().nextInt(6) == 0) {
            double rx = (self.getRandom().nextFloat() * 2 - 1) / 3.0;
            double rz = (self.getRandom().nextFloat() * 2 - 1) / 3.0;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    self.getX() + rx, self.getY() + self.getBbHeight() * 0.5 + 0.4, self.getZ() + rz,
                    1, 0.0, self.getRandom().nextFloat(), 0.0, 0.0);
        }

        sulfurcubesplus$hydrationTicks++;
        if (sulfurcubesplus$hydrationTicks < DriedGhastBlock.HYDRATION_TICK_DELAY) return;

        sulfurcubesplus$hydrationTicks = 0;
        sulfurcubesplus$hydrationStage++;

        // Update body item to show the new hydration level.
        ItemStack updated = new ItemStack(Items.DRIED_GHAST);
        updated.set(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY.with(
                        DriedGhastBlock.HYDRATION_LEVEL, sulfurcubesplus$hydrationStage));
        self.setItemSlot(EquipmentSlot.BODY, updated);

        // Stage-advance burst matching vanilla.
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                self.getX(), self.getY() + self.getBbHeight() / 2.0, self.getZ(),
                10, 0.4, 0.4, 0.4, 0.08);

        if (sulfurcubesplus$hydrationStage >= DriedGhastBlock.MAX_HYDRATION_LEVEL) {
            sulfurcubesplus$transformToGhastSoul(self, level);
        }
    }

    /**
     * Each server tick while in ghast-soul mode: keep the cube riding the tracked happy ghast
     * so the passenger system positions it (via getVehicleAttachmentPoint below), resize to
     * match the growing ghast, and free the cube if the ghast disappears.
     */
    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void sulfurcubesplus$tickGhastSoul(ServerLevel level, CallbackInfo ci) {
        if (!sulfurcubesplus$isGhastSoulMode) return;

        SulfurCube self = (SulfurCube)(Object)this;

        if (sulfurcubesplus$linkedGhastUuid == null) {
            sulfurcubesplus$freeFromGhast(self);
            return;
        }

        Entity ghast = level.getEntity(sulfurcubesplus$linkedGhastUuid);
        if (ghast == null || ghast.isRemoved()) {
            sulfurcubesplus$freeFromGhast(self);
            return;
        }

        // Resize cube to match the ghast as it grows from ghastling to adult.
        int targetSize = sulfurcubesplus$cubeSizeForGhast(ghast);
        if (self.getSize() != targetSize) {
            self.setSize(targetSize, false);
        }

        // Re-attach if the passenger link was lost (e.g. chunk reload).
        if (self.getVehicle() != ghast) {
            self.startRiding(ghast, true, false);
        }

        // Force cube rotation to always match the ghast.
        self.setYRot(ghast.getYRot());
        self.setYHeadRot(ghast.getYHeadRot());
    }

    /**
     * When the cube is riding the happy ghast in soul mode, cancel the ghast's own passenger
     * attachment offset (which sits the rider on the ghast's back, offset in Z) and replace it
     * with an offset that perfectly centres the cube on the ghast instead.
     *
     * positionRider formula: passenger.feet = ridingPos - getVehicleAttachmentPoint()
     * We compute vehicleAttach = ridingPos - desiredFeetPos so the subtraction yields exactly
     * the centred position, without needing to know the ghast's attachment geometry up-front.
     */
    // Plain method — no @Inject, no @Override. Mixin merges this directly into SulfurCube,
    // overriding the inherited Entity.getVehicleAttachmentPoint for SulfurCube instances.
    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        // Check vehicle type, not the server-only boolean — this runs on the client too.
        if (vehicle instanceof HappyGhast) {
            SulfurCube cube = (SulfurCube)(Object)this;
            Vec3 ridingPos = vehicle.getPassengerRidingPosition(cube);
            double desiredFeetY = vehicle.getY() + (vehicle.getBbHeight() - cube.getBbHeight()) / 2.0;
            return new Vec3(
                    ridingPos.x - vehicle.getX(),
                    ridingPos.y - desiredFeetY,
                    ridingPos.z - vehicle.getZ()
            );
        }
        return Vec3.ZERO;
    }

    /** Consumes a dried ghast, spawns a baby happy ghast, and enters soul mode. */
    @Unique
    private void sulfurcubesplus$transformToGhastSoul(SulfurCube self, ServerLevel level) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                self.getX(), self.getY() + self.getBbHeight() / 2.0, self.getZ(),
                50, 0.5, 0.5, 0.5, 0.15);
        level.playSound(null, self.getX(), self.getY(), self.getZ(),
                SoundEvents.GHASTLING_SPAWN, SoundSource.NEUTRAL, 1.0f, 1.0f);

        HappyGhast ghastling = EntityTypes.HAPPY_GHAST.create(level, EntitySpawnReason.NATURAL);
        if (ghastling == null) return;

        ghastling.setBaby(true);
        ghastling.snapTo(self.getX(), self.getY(), self.getZ(), self.getYRot(), 0.0f);
        level.addFreshEntity(ghastling);

        self.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
        sulfurcubesplus$hydrationStage = 0;
        sulfurcubesplus$hydrationTicks = 0;
        sulfurcubesplus$isGhastSoulMode = true;
        sulfurcubesplus$hasBeenSoulMode = true;
        sulfurcubesplus$linkedGhastUuid = ghastling.getUUID();
        self.setNoGravity(true);
        self.setSize(sulfurcubesplus$cubeSizeForGhast(ghastling), false);
        // Make the cube a passenger of the ghast — the ghast drives, the cube follows.
        // getVehicleAttachmentPoint() centres the cube on the ghast each tick.
        self.startRiding(ghastling, true, false);
    }

    /** Unlinks the cube from its ghast and restores normal physics. Cube keeps its current size. */
    @Unique
    private void sulfurcubesplus$freeFromGhast(SulfurCube self) {
        sulfurcubesplus$isGhastSoulMode = false;
        sulfurcubesplus$linkedGhastUuid = null;
        self.stopRiding();
        self.setDeltaMovement(Vec3.ZERO);
        self.setNoGravity(false);
    }

    /** Returns the SulfurCube size that best matches the given entity's bounding-box width. */
    @Unique
    private static int sulfurcubesplus$cubeSizeForGhast(Entity ghast) {
        float ghastWidth = (float) ghast.getBoundingBox().getXsize();
        float cubeBaseWidth = EntityTypes.SULFUR_CUBE.getDimensions().width();
        return Math.max(1, Math.round(ghastWidth / cubeBaseWidth));
    }

    /** Persist hydration progress and ghast-soul state so chunk reload doesn't break anything. */
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void sulfurcubesplus$saveGhastData(ValueOutput out, CallbackInfo ci) {
        if (sulfurcubesplus$hydrationStage > 0)
            out.putInt("sulfurcubesplus_hydration_stage", sulfurcubesplus$hydrationStage);
        if (sulfurcubesplus$hydrationTicks > 0)
            out.putInt("sulfurcubesplus_hydration_ticks", sulfurcubesplus$hydrationTicks);
        if (sulfurcubesplus$hasBeenSoulMode)
            out.putBoolean("sulfurcubesplus_been_soul", true);
        if (sulfurcubesplus$isGhastSoulMode) {
            out.putBoolean("sulfurcubesplus_ghast_soul", true);
            if (sulfurcubesplus$linkedGhastUuid != null) {
                out.putLong("sulfurcubesplus_ghast_uuid_most",
                        sulfurcubesplus$linkedGhastUuid.getMostSignificantBits());
                out.putLong("sulfurcubesplus_ghast_uuid_least",
                        sulfurcubesplus$linkedGhastUuid.getLeastSignificantBits());
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void sulfurcubesplus$loadGhastData(ValueInput in, CallbackInfo ci) {
        sulfurcubesplus$hydrationStage = in.getIntOr("sulfurcubesplus_hydration_stage", 0);
        sulfurcubesplus$hydrationTicks = in.getIntOr("sulfurcubesplus_hydration_ticks", 0);
        sulfurcubesplus$hasBeenSoulMode = in.getBooleanOr("sulfurcubesplus_been_soul", false);
        if (in.getBooleanOr("sulfurcubesplus_ghast_soul", false)) {
            sulfurcubesplus$isGhastSoulMode = true;
            long most = in.getLongOr("sulfurcubesplus_ghast_uuid_most", 0L);
            long least = in.getLongOr("sulfurcubesplus_ghast_uuid_least", 0L);
            if (most != 0L || least != 0L) {
                sulfurcubesplus$linkedGhastUuid = new UUID(most, least);
            }
            ((SulfurCube)(Object)this).setNoGravity(true);
        }
    }

    @ModifyArg(
            method = "tickFuse()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"),
            index = 4
    )
    private float sulfurcubesplus$sizeScaledExplosion(float power) {
        int size = ((SulfurCube)(Object)this).getSize();
        if (size <= 2) return power;
        return power * (float)Math.pow(size / 2.0, 1.5);
    }

}
