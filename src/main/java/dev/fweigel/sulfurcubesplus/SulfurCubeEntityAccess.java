package dev.fweigel.sulfurcubesplus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * ContainerLevelAccess for entity-held workstation blocks.
 *
 * Stores the carrying SulfurCube, the player who opened the menu, and the body
 * item type that was active at open time. SulfurCubeEntityMixin uses these to
 * close the menu when the player drifts too far or the body item is swapped.
 *
 * - evaluate() returns Optional.empty() → stillValid() stays true (no block to validate).
 * - execute() runs the lambda with the cube's current level/position. Most removed()
 *   lambdas only use captured variables and ignore level/pos, so this is safe.
 */
public class SulfurCubeEntityAccess implements ContainerLevelAccess {

    public final SulfurCube cube;
    public final ServerPlayer player;
    /** Item type that was in the BODY slot when this access was created. */
    public final Item bodyItemType;

    public SulfurCubeEntityAccess(SulfurCube cube, ServerPlayer player, Item bodyItemType) {
        this.cube = cube;
        this.player = player;
        this.bodyItemType = bodyItemType;
    }

    @Override
    public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> fn) {
        return Optional.empty();
    }

    /**
     * stillValid() calls evaluate(fn, false) to check if the workstation block is present.
     * Entity workstations have no block in the world, so we skip that check and always report
     * valid. SulfurCubeEntityMixin.tickMenuValidity() enforces the actual proximity/item rules.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(BiFunction<Level, BlockPos, T> fn, T defaultValue) {
        if (defaultValue instanceof Boolean) {
            return (T) Boolean.TRUE;
        }
        return defaultValue;
    }

    @Override
    public void execute(BiConsumer<Level, BlockPos> fn) {
        fn.accept(cube.level(), cube.blockPosition());
    }
}
