package dev.fweigel.sulfurcubesplus.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fweigel.sulfurcubesplus.IFuseRenderState;
import dev.fweigel.sulfurcubesplus.IGhastSoulRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.SulfurCubeInnerLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SulfurCubeInnerLayer.class)
@Environment(EnvType.CLIENT)
public class SulfurCubeInnerLayerMixin {

    /**
     * Skip all inner-layer rendering in ghast-soul mode so the sulfur block inside the cube
     * is not drawn — the happy ghast entity, riding centered inside the translucent outer shell,
     * provides the visual instead.
     */
    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/SulfurCubeRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sulfurcubesplus$skipInSoulMode(
            PoseStack poseStack, SubmitNodeCollector collector, int light,
            SulfurCubeRenderState renderState, float partialTick, float p,
            CallbackInfo ci) {
        if (renderState instanceof IGhastSoulRenderState soulState
                && soulState.sulfurcubesplus$isGhastSoulMode()) {
            ci.cancel();
        }
    }

    /**
     * Redirects the getOverlayCoords() call inside the inner-layer submit so the
     * contained block (TNT) also flashes white in sync with the outer model.
     */
    @Redirect(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/SulfurCubeRenderState;FF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getOverlayCoords(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)I")
    )
    private int sulfurcubesplus$whiteOverlayForBlock(LivingEntityRenderState renderState, float whiteAlpha) {
        if (renderState instanceof IFuseRenderState fuseState) {
            int ticks = fuseState.sulfurcubesplus$getFuseTicks();
            if (ticks >= 0 && (ticks / 5) % 2 == 0) {
                return OverlayTexture.pack(OverlayTexture.u(1.0f), OverlayTexture.WHITE_OVERLAY_V);
            }
        }
        return LivingEntityRenderer.getOverlayCoords(renderState, whiteAlpha);
    }
}
