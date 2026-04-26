package dev.fweigel.sulfurcubesplus.mixin;

import dev.fweigel.sulfurcubesplus.IGhastSoulHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HappyGhast.class)
public class HappyGhastMixin {

    /**
     * When a player shears an adult HappyGhast that carries a SulfurCube in soul mode, free the
     * cube. For ghastlings the cube is slightly larger than the ghast so the cube's own
     * mobInteract handles shearing; for adults the ghast hitbox intercepts the click first.
     */
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void sulfurcubesplus$shearReleasesCube(
            Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (hand != InteractionHand.MAIN_HAND) return;

        ItemStack heldItem = player.getItemInHand(hand);
        if (!heldItem.is(Items.SHEARS)) return;

        HappyGhast self = (HappyGhast)(Object)this;
        for (Entity passenger : self.getPassengers()) {
            if (passenger instanceof SulfurCube && passenger instanceof IGhastSoulHolder holder
                    && holder.sulfurcubesplus$isGhastSoulMode()) {
                cir.setReturnValue(InteractionResult.SUCCESS);
                if (!self.level().isClientSide()) {
                    heldItem.hurtAndBreak(1, player, hand);
                    holder.sulfurcubesplus$releaseGhastSoul();
                }
                return;
            }
        }
    }
}
