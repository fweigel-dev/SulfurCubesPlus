package dev.fweigel.sulfurcubesplus;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: tells the client to open the command-edit screen for the given cube, seeded
 * with its current command. Sent only to ops in creative when they interact with a command cube.
 */
public record OpenSulfurCubeCommandPayload(int entityId, String command, boolean trackOutput)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenSulfurCubeCommandPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(SulfurCubesPlus.MOD_ID, "open_command_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenSulfurCubeCommandPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenSulfurCubeCommandPayload::entityId,
                    ByteBufCodecs.STRING_UTF8, OpenSulfurCubeCommandPayload::command,
                    ByteBufCodecs.BOOL, OpenSulfurCubeCommandPayload::trackOutput,
                    OpenSulfurCubeCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
