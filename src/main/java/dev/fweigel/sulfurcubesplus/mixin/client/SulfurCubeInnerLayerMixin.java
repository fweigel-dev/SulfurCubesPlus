package dev.fweigel.sulfurcubesplus.mixin.client;

import dev.fweigel.sulfurcubesplus.IFuseRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.SulfurCubeInnerLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SulfurCubeInnerLayer.class)
@Environment(EnvType.CLIENT)
public class SulfurCubeInnerLayerMixin {

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
