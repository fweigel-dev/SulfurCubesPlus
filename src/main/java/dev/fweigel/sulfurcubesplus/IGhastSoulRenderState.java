package dev.fweigel.sulfurcubesplus;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface IGhastSoulRenderState {
    boolean sulfurcubesplus$isGhastSoulMode();
    void sulfurcubesplus$setGhastSoulMode(boolean mode);
}
