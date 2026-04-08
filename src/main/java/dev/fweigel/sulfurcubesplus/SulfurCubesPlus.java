package dev.fweigel.sulfurcubesplus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SulfurCubesPlus implements ModInitializer {

    public static final String MOD_ID = "sulfurcubesplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Sulfur Cubes+ loaded");

        // Clean up any placed light block when a SulfurCube leaves the world (death, discard, unload).
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof SulfurCube && level instanceof ServerLevel serverLevel) {
                ((ILightHolder) entity).sulfurcubesplus$cleanupLight(serverLevel);
            }
        });
    }
}
