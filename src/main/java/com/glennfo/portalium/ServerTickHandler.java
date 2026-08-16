package com.glennfo.portalium;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Portalium.MOD_ID)
public class ServerTickHandler {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        DimensionManager.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DimensionManager.onServerStopped();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DimensionManager.tick(event.getServer());
    }
}
