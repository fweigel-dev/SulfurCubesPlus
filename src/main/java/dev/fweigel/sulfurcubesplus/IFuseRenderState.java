package dev.fweigel.sulfurcubesplus;

/** Implemented by SulfurCubeRenderState (via mixin) to carry fuse ticks to layers. */
public interface IFuseRenderState extends IFuseHolder {
    void sulfurcubesplus$setFuseTicks(int ticks);
}
