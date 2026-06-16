package dev.fweigel.sulfurcubesplus.mixin.client;

import dev.fweigel.sulfurcubesplus.IEndRodRenderState;
import dev.fweigel.sulfurcubesplus.IGhastSoulHolder;
import dev.fweigel.sulfurcubesplus.IGhastSoulRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.SulfurCubeRenderer;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SulfurCubeRenderer.class)
@Environment(EnvType.CLIENT)
public class SulfurCubeRendererMixin {

    @Unique
    private static final Identifier SULFURCUBESPLUS$ENDROD_FACE = Identifier.fromNamespaceAndPath(
            "sulfurcubesplus", "textures/entity/sulfur_cube/sulfur_cube_outer_endrod.png");

    @Unique
    private static final Identifier SULFURCUBESPLUS$ENDROD_FACE_SMALL = Identifier.fromNamespaceAndPath(
            "sulfurcubesplus", "textures/entity/sulfur_cube/sulfur_cube_outer_small_endrod.png");

    /** Swap the cube's outer texture for the blush "end-rod face" when it carries an end rod. */
    @Inject(
            method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/SulfurCubeRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"),
            cancellable = true)
    private void sulfurcubesplus$endRodFace(
            SulfurCubeRenderState renderState, CallbackInfoReturnable<Identifier> cir) {
        if (renderState instanceof IEndRodRenderState endRodState && endRodState.sulfurcubesplus$isEndRod()) {
            cir.setReturnValue(renderState.isBaby
                    ? SULFURCUBESPLUS$ENDROD_FACE_SMALL : SULFURCUBESPLUS$ENDROD_FACE);
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/monster/cubemob/SulfurCube;Lnet/minecraft/client/renderer/entity/state/SulfurCubeRenderState;F)V",
            at = @At("TAIL")
    )
    private void sulfurcubesplus$populateFlags(
            SulfurCube cube, SulfurCubeRenderState renderState, float partialTick, CallbackInfo ci) {
        ((IGhastSoulRenderState) renderState).sulfurcubesplus$setGhastSoulMode(
                ((IGhastSoulHolder) cube).sulfurcubesplus$isGhastSoulMode());
        ((IEndRodRenderState) renderState).sulfurcubesplus$setEndRod(
                cube.getItemBySlot(EquipmentSlot.BODY).is(Items.END_ROD));

        Entity vehicle = cube.getVehicle();
        if (vehicle instanceof HappyGhast ghast) {
            float ghastYaw = Mth.rotLerp(partialTick, ghast.yRotO, ghast.getYRot());
            renderState.yRot = ghastYaw;
            renderState.bodyRot = ghastYaw;
        }
    }
}
