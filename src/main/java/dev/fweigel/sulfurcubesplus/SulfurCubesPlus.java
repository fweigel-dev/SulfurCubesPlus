package dev.fweigel.sulfurcubesplus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SulfurCubesPlus implements ModInitializer {

    public static final String MOD_ID = "sulfurcubesplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Internal invisible block placed at a Redstone-Block-carrying cube's position so vanilla
     * redstone components (dust, pistons, observers, comparators) see a live signal-15 source.
     * No item form; no loot table; pistons destroy it (cube re-places it next tick).
     */
    public static Block PHANTOM_REDSTONE_BLOCK;

    /**
     * Internal invisible block placed at a lit copper-bulb cube's position: emits the bulb's light
     * and provides a comparator-readable signal. No item form; no loot table.
     */
    public static Block PHANTOM_COPPER_BLOCK;

    @Override
    public void onInitialize() {
        LOGGER.info("Sulfur Cubes+ loaded");

        ResourceKey<Block> phantomKey = ResourceKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(MOD_ID, "phantom_redstone")
        );

        PHANTOM_REDSTONE_BLOCK = Registry.register(
                BuiltInRegistries.BLOCK,
                phantomKey,
                new PhantomRedstoneBlock(BlockBehaviour.Properties.of()
                        .noCollision()
                        .noOcclusion()
                        .replaceable()
                        .instabreak()
                        .pushReaction(PushReaction.DESTROY)
                        .setId(phantomKey)
                )
        );

        ResourceKey<Block> copperKey = ResourceKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(MOD_ID, "phantom_copper")
        );

        PHANTOM_COPPER_BLOCK = Registry.register(
                BuiltInRegistries.BLOCK,
                copperKey,
                new PhantomCopperBlock(BlockBehaviour.Properties.of()
                        .noCollision()
                        .noOcclusion()
                        .replaceable()
                        .instabreak()
                        .pushReaction(PushReaction.DESTROY)
                        .lightLevel(state -> state.getValue(PhantomCopperBlock.LEVEL))
                        .setId(copperKey)
                )
        );

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof SulfurCube && level instanceof ServerLevel serverLevel) {
                ((ILightHolder) entity).sulfurcubesplus$cleanupLight(serverLevel);
            }
        });

        registerCommandCubeNetworking();
    }

    /** Registers the command-cube edit packets and the server-side handler that applies them. */
    private static void registerCommandCubeNetworking() {
        PayloadTypeRegistry.clientboundPlay().register(
                OpenSulfurCubeCommandPayload.TYPE, OpenSulfurCubeCommandPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SetSulfurCubeCommandPayload.TYPE, SetSulfurCubeCommandPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                SetSulfurCubeCommandPayload.TYPE, (payload, context) -> {
                    ServerPlayer player = context.player();
                    // Re-validate permission server-side; never trust the client.
                    if (!player.canUseGameMasterBlocks()) {
                        return;
                    }
                    Entity entity = player.level().getEntity(payload.entityId());
                    if (entity instanceof SulfurCube && entity instanceof ICommandCube commandCube
                            && commandCube.sulfurcubesplus$isCommandCube()) {
                        BaseCommandBlock commandBlock = commandCube.sulfurcubesplus$getCommandBlock();
                        commandBlock.setCommand(payload.command());
                        commandBlock.setTrackOutput(payload.trackOutput());
                        if (!payload.trackOutput()) {
                            commandBlock.setLastOutput(null);
                        }
                    }
                });
    }
}
