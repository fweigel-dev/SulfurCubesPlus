package dev.fweigel.sulfurcubesplus.mixin.client;

import dev.fweigel.sulfurcubesplus.IFuseRenderState;
import dev.fweigel.sulfurcubesplus.IGhastSoulRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SulfurCubeRenderState.class)
@Environment(EnvType.CLIENT)
public class SulfurCubeRenderStateMixin implements IFuseRenderState, IGhastSoulRenderState {

    @Unique
    private int sulfurcubesplus$fuseTicks = -1;

    @Unique
    private boolean sulfurcubesplus$ghastSoulMode = false;

    @Override
    public int sulfurcubesplus$getFuseTicks() {
        return sulfurcubesplus$fuseTicks;
    }

    @Override
    public void sulfurcubesplus$setFuseTicks(int ticks) {
        sulfurcubesplus$fuseTicks = ticks;
    }

    @Override
    public boolean sulfurcubesplus$isGhastSoulMode() {
        return sulfurcubesplus$ghastSoulMode;
    }

    @Override
    public void sulfurcubesplus$setGhastSoulMode(boolean mode) {
        sulfurcubesplus$ghastSoulMode = mode;
    }
}
