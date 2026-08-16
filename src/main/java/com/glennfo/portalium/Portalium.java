package com.glennfo.portalium;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Portalium.MOD_ID)
public class Portalium {

    public static final String MOD_ID = "portalium";

    public Portalium(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, PortaliumConfig.SPEC, "portalium-server.toml");
        NeoForge.EVENT_BUS.register(DimensionManager.class);
    }
}
