package dev.fweigel.sulfurcubesplus;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface IEndRodRenderState {
    boolean sulfurcubesplus$isEndRod();
    void sulfurcubesplus$setEndRod(boolean endRod);
}
