package com.glennfo.portalium;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class Portalium implements ModInitializer {

    public static final String MOD_ID = "portalium";

    @Override
    public void onInitialize() {
        PortaliumConfig.load();

        ServerLifecycleEvents.SERVER_STARTED.register(DimensionManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DimensionManager.onServerStopping());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DimensionManager.onServerStopped());
        ServerTickEvents.END_SERVER_TICK.register(DimensionManager::tick);

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
            DimensionManager.onPlayerChangeDimension(player, origin.dimension(), destination.dimension()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
            DimensionManager.onPlayerRespawn(newPlayer));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            DimensionManager.onPlayerLoggedIn(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            DimensionManager.onPlayerLoggedOut(handler.getPlayer()));
    }
}
