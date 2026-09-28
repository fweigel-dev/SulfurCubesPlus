package dev.fweigel.sulfurcubesplus;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;

/**
 * A {@link net.minecraft.world.level.BaseCommandBlock} bound to a {@link SulfurCube}, mirroring
 * the command block that vanilla's command-block minecart carries. Holds the command, runs it,
 * and produces a command source positioned at the cube with gamemaster permissions.
 */
public class SulfurCubeCommandBlock extends net.minecraft.world.level.BaseCommandBlock {

    private final SulfurCube cube;

    public SulfurCubeCommandBlock(SulfurCube cube) {
        this.cube = cube;
    }

    @Override
    public void onUpdated(ServerLevel level) {
        // No synced output to refresh — the screen is populated on demand via networking.
    }

    @Override
    public CommandSourceStack createCommandSourceStack(ServerLevel level, CommandSource source) {
        return new CommandSourceStack(
                source,
                cube.position(),
                cube.getRotationVector(),
                level,
                LevelBasedPermissionSet.GAMEMASTER,
                level.getServer(),
                cube);
    }

    @Override
    public boolean isValid() {
        return !cube.isRemoved();
    }
}
