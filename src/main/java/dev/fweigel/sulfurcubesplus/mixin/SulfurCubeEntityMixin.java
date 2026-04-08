package dev.fweigel.sulfurcubesplus.mixin;

import dev.fweigel.sulfurcubesplus.IFuseHolder;
import dev.fweigel.sulfurcubesplus.ILightHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
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
        if (sulfurcubesplus$lastLightPos == null) return;
        sulfurcubesplus$removeLightAt(level, sulfurcubesplus$lastLightPos);
        sulfurcubesplus$lastLightPos = null;
    }

    @Unique
    private static void sulfurcubesplus$removeLightAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.removeBlock(pos, false);
        }
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
