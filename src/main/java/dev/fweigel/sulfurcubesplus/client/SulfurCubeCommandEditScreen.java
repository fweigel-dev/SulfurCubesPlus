package dev.fweigel.sulfurcubesplus.client;

import dev.fweigel.sulfurcubesplus.SetSulfurCubeCommandPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseCommandBlock;

/**
 * Client-side command-edit screen for a command-block cube. Mirrors vanilla's
 * MinecartCommandBlockEditScreen, but is seeded from a network payload and saves by sending our
 * own {@link SetSulfurCubeCommandPayload} (the vanilla minecart packet only accepts minecarts).
 */
@Environment(EnvType.CLIENT)
public class SulfurCubeCommandEditScreen extends AbstractCommandBlockEditScreen {

    private final int entityId;

    /** Local, display-only command block holding the current command for the edit box. */
    private final BaseCommandBlock display = new BaseCommandBlock() {
        @Override
        public void onUpdated(ServerLevel level) {
        }

        @Override
        public CommandSourceStack createCommandSourceStack(ServerLevel level, CommandSource source) {
            return null; // never invoked client-side
        }

        @Override
        public boolean isValid() {
            return true;
        }
    };

    public SulfurCubeCommandEditScreen(int entityId, String command, boolean trackOutput) {
        this.entityId = entityId;
        this.display.setCommand(command);
        this.display.setTrackOutput(trackOutput);
    }

    @Override
    protected BaseCommandBlock getCommandBlock() {
        return this.display;
    }

    @Override
    protected int getPreviousY() {
        return 150;
    }

    @Override
    protected void init() {
        super.init();
        this.commandEdit.setValue(getCommandBlock().getCommand());
    }

    @Override
    protected void populateAndSendPacket() {
        ClientPlayNetworking.send(new SetSulfurCubeCommandPayload(
                this.entityId, this.commandEdit.getValue(), getCommandBlock().isTrackOutput()));
    }
}
