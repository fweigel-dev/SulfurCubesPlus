package dev.fweigel.sulfurcubesplus.mixin.client;

import dev.fweigel.sulfurcubesplus.IFuseHolder;
import dev.fweigel.sulfurcubesplus.IFuseRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.SulfurCubeRenderer;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SulfurCubeRenderer.class)
@Environment(EnvType.CLIENT)
public class SulfurCubeRendererMixin {

    /** Copy the synced fuse ticks from the entity into the render state each frame. */
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/monster/cubemob/SulfurCube;Lnet/minecraft/client/renderer/entity/state/SulfurCubeRenderState;F)V",
            at = @At("TAIL")
    )
    private void sulfurcubesplus$populateFuse(
            SulfurCube cube, SulfurCubeRenderState renderState, float partialTick, CallbackInfo ci) {
        ((IFuseRenderState) renderState).sulfurcubesplus$setFuseTicks(
                ((IFuseHolder) cube).sulfurcubesplus$getFuseTicks()
        );
    }
}
