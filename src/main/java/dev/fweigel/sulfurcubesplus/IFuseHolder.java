package dev.fweigel.sulfurcubesplus;

/** Implemented by SulfurCube (via mixin) so renderers can read fuse state. */
public interface IFuseHolder {
    int sulfurcubesplus$getFuseTicks();
}
