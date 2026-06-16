package dev.fweigel.sulfurcubesplus;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the command typed in the edit screen. The server re-checks gamemaster
 * permission before applying it, so a forged packet from a non-op is ignored.
 */
public record SetSulfurCubeCommandPayload(int entityId, String command, boolean trackOutput)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetSulfurCubeCommandPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(SulfurCubesPlus.MOD_ID, "set_command"));

    public static final StreamCodec<FriendlyByteBuf, SetSulfurCubeCommandPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetSulfurCubeCommandPayload::entityId,
                    ByteBufCodecs.STRING_UTF8, SetSulfurCubeCommandPayload::command,
                    ByteBufCodecs.BOOL, SetSulfurCubeCommandPayload::trackOutput,
                    SetSulfurCubeCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
