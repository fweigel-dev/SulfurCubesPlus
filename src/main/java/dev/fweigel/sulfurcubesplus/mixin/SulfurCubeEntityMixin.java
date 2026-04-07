package dev.fweigel.sulfurcubesplus.mixin;

import dev.fweigel.sulfurcubesplus.IFuseHolder;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SulfurCube.class)
public abstract class SulfurCubeEntityMixin implements IFuseHolder {

    // Registered as part of SulfurCube's static init (mixin static fields merge in).
    // ID is assigned after all vanilla SulfurCube IDs.
    @Unique
    private static final EntityDataAccessor<Integer> SULFURCUBESPLUS$FUSE =
            SynchedEntityData.defineId(SulfurCube.class, EntityDataSerializers.INT);

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
