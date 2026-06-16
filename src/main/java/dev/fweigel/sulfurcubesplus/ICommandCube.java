package dev.fweigel.sulfurcubesplus;

import net.minecraft.world.level.BaseCommandBlock;

/** Implemented by SulfurCube (via mixin) to expose its command-block state to other classes. */
public interface ICommandCube {

    /** The cube's lazily-created command block (never null). */
    BaseCommandBlock sulfurcubesplus$getCommandBlock();

    /** True while the cube currently carries a (default) command block. */
    boolean sulfurcubesplus$isCommandCube();
}
