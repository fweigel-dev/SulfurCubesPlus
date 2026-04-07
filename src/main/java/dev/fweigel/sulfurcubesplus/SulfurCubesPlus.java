package dev.fweigel.sulfurcubesplus;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SulfurCubesPlus implements ModInitializer {

    public static final String MOD_ID = "sulfurcubesplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Sulfur Cubes+ loaded");
    }
}
