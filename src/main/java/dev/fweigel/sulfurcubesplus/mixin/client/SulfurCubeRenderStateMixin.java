package dev.fweigel.sulfurcubesplus.mixin.client;

import dev.fweigel.sulfurcubesplus.IEndRodRenderState;
import dev.fweigel.sulfurcubesplus.IGhastSoulRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SulfurCubeRenderState.class)
@Environment(EnvType.CLIENT)
public class SulfurCubeRenderStateMixin
        implements IGhastSoulRenderState, IEndRodRenderState {

    @Unique
    private boolean sulfurcubesplus$ghastSoulMode = false;

    @Unique
    private boolean sulfurcubesplus$endRod = false;

    @Override
    public boolean sulfurcubesplus$isGhastSoulMode() {
        return sulfurcubesplus$ghastSoulMode;
    }

    @Override
    public void sulfurcubesplus$setGhastSoulMode(boolean mode) {
        sulfurcubesplus$ghastSoulMode = mode;
    }

    @Override
    public boolean sulfurcubesplus$isEndRod() {
        return sulfurcubesplus$endRod;
    }

    @Override
    public void sulfurcubesplus$setEndRod(boolean endRod) {
        sulfurcubesplus$endRod = endRod;
    }
}
