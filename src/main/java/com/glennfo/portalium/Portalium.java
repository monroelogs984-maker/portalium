package com.glennfo.portalium;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(Portalium.MOD_ID)
public class Portalium {

    public static final String MOD_ID = "portalium";

    public Portalium() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PortaliumConfig.SPEC, "portalium-server.toml");
        MinecraftForge.EVENT_BUS.register(DimensionManager.class);
        MinecraftForge.EVENT_BUS.register(ServerTickHandler.class);
    }
}
