package dev.fweigel.sulfurcubesplus.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fweigel.sulfurcubesplus.IGhastSoulRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.SulfurCubeInnerLayer;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SulfurCubeInnerLayer.class)
@Environment(EnvType.CLIENT)
public class SulfurCubeInnerLayerMixin {

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
}
