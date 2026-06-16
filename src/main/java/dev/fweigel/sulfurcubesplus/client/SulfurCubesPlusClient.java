package dev.fweigel.sulfurcubesplus.client;

import dev.fweigel.sulfurcubesplus.OpenSulfurCubeCommandPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class SulfurCubesPlusClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Server asked us to open the command-edit screen for a command cube.
        ClientPlayNetworking.registerGlobalReceiver(
                OpenSulfurCubeCommandPayload.TYPE, (payload, context) ->
                        context.client().execute(() ->
                                context.client().setScreenAndShow(new SulfurCubeCommandEditScreen(
                                        payload.entityId(), payload.command(), payload.trackOutput()))));
    }
}
