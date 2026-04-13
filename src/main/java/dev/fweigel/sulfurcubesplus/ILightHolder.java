package dev.fweigel.sulfurcubesplus;

import net.minecraft.server.level.ServerLevel;

/** Implemented by SulfurCube (via mixin) to allow external cleanup of placed light blocks. */
public interface ILightHolder {
    void sulfurcubesplus$cleanupLight(ServerLevel level);
}
