package dev.fweigel.sulfurcubesplus.mixin.client;

import dev.fweigel.sulfurcubesplus.IFuseHolder;
import dev.fweigel.sulfurcubesplus.IFuseRenderState;
import dev.fweigel.sulfurcubesplus.IGhastSoulHolder;
import dev.fweigel.sulfurcubesplus.IGhastSoulRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.SulfurCubeRenderer;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SulfurCubeRenderer.class)
@Environment(EnvType.CLIENT)
public class SulfurCubeRendererMixin {

    /** Copy per-entity flags into the render state each frame. */
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/monster/cubemob/SulfurCube;Lnet/minecraft/client/renderer/entity/state/SulfurCubeRenderState;F)V",
            at = @At("TAIL")
    )
    private void sulfurcubesplus$populateFlags(
            SulfurCube cube, SulfurCubeRenderState renderState, float partialTick, CallbackInfo ci) {
        ((IFuseRenderState) renderState).sulfurcubesplus$setFuseTicks(
                ((IFuseHolder) cube).sulfurcubesplus$getFuseTicks());
        ((IGhastSoulRenderState) renderState).sulfurcubesplus$setGhastSoulMode(
                ((IGhastSoulHolder) cube).sulfurcubesplus$isGhastSoulMode());

        // Override the cube's rendered yaw to exactly match the ghast's per-frame interpolated
        // yaw. Accessing ghast.yRotO as a plain field on an external reference needs no refmap,
        // unlike @Shadow which requires refmap resolution for inherited fields.
        Entity vehicle = cube.getVehicle();
        if (vehicle instanceof HappyGhast ghast) {
            float ghastYaw = Mth.rotLerp(partialTick, ghast.yRotO, ghast.getYRot());
            renderState.yRot = ghastYaw;
            renderState.bodyRot = ghastYaw;
        }
    }
}
