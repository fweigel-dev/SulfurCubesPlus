package dev.fweigel.sulfurcubesplus.mixin.client;

import dev.fweigel.sulfurcubesplus.IFuseRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
@Environment(EnvType.CLIENT)
public class LivingEntityRendererMixin {

    /**
     * Returns full white overlay (1.0) during the white phase of the fuse flash cycle.
     * Targets LivingEntityRenderer directly since SulfurCubeRenderer doesn't override it.
     * The IFuseRenderState check ensures only TNT-carrying sulfur cubes are affected.
     */
    @Inject(method = "getWhiteOverlayProgress", at = @At("HEAD"), cancellable = true)
    private void sulfurcubesplus$fuseWhiteFlash(
            LivingEntityRenderState renderState, CallbackInfoReturnable<Float> cir) {
        if (!(renderState instanceof IFuseRenderState fuseState)) {
            return;
        }
        int ticks = fuseState.sulfurcubesplus$getFuseTicks();
        if (ticks >= 0 && (ticks / 5) % 2 == 0) {
            cir.setReturnValue(1.0f);
        }
    }
}
