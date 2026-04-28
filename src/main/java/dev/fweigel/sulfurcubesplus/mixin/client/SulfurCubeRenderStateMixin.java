package dev.fweigel.sulfurcubesplus.mixin.client;

import dev.fweigel.sulfurcubesplus.IGhastSoulRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SulfurCubeRenderState.class)
@Environment(EnvType.CLIENT)
public class SulfurCubeRenderStateMixin implements IGhastSoulRenderState {

    @Unique
    private boolean sulfurcubesplus$ghastSoulMode = false;

    @Override
    public boolean sulfurcubesplus$isGhastSoulMode() {
        return sulfurcubesplus$ghastSoulMode;
    }

    @Override
    public void sulfurcubesplus$setGhastSoulMode(boolean mode) {
        sulfurcubesplus$ghastSoulMode = mode;
    }
}
