package com.anjas.godvillagers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Alpha.85 overlay entrypoint. The reconstructed alpha.82 jar already owns the
 * main God Villagers entrypoint; this second entrypoint only activates optional
 * TACZ integration after Fabric has initialized the normal mod entrypoints.
 */
public final class TaczBridgeBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("tacz")) return;
        TaczDirectEventRuntime.registerEvents();
    }
}
