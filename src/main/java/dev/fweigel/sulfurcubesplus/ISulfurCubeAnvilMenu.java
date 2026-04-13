package dev.fweigel.sulfurcubesplus;

/**
 * Implemented by AnvilMenu (via mixin) to carry the SulfurCubeEntityAccess reference
 * when an anvil UI is opened for an entity-held anvil block.
 * Placed-anvil menus never have this set (it stays null), so vanilla is unaffected.
 */
public interface ISulfurCubeAnvilMenu {
    SulfurCubeEntityAccess sulfurcubesplus$getEntityAccess();
    void sulfurcubesplus$setEntityAccess(SulfurCubeEntityAccess access);
}
