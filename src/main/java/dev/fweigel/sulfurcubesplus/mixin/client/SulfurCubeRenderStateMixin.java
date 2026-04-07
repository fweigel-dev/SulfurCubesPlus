package dev.fweigel.sulfurcubesplus.mixin.client;

import dev.fweigel.sulfurcubesplus.IFuseRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SulfurCubeRenderState.class)
@Environment(EnvType.CLIENT)
public class SulfurCubeRenderStateMixin implements IFuseRenderState {

    @Unique
    private int sulfurcubesplus$fuseTicks = -1;

    @Override
    public int sulfurcubesplus$getFuseTicks() {
        return sulfurcubesplus$fuseTicks;
    }

    @Override
    public void sulfurcubesplus$setFuseTicks(int ticks) {
        sulfurcubesplus$fuseTicks = ticks;
    }
}
