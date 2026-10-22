package com.glennfo.portalium;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DimensionManager {

    private static final Map<ResourceKey<Level>, Integer> pendingUnload = new HashMap<>();
    private static final Set<ResourceKey<Level>> dormantDims = new HashSet<>();
    private static final int STARTUP_CLEANUP_DELAY = 40;
    private static int startupCountdown = -1;
    private static MinecraftServer activeServer = null;

    public static boolean isDormant(ResourceKey<Level> key) {
        return dormantDims.contains(key);
    }

    public static void onServerStarted(MinecraftServer server) {
        activeServer = server;
        pendingUnload.clear();
        dormantDims.clear();
        startupCountdown = STARTUP_CLEANUP_DELAY;
        injectLevelMap(server);
    }

    /**
     * Wake every dormant dimension before vanilla shuts down, so the shutdown save and
     * level close (which iterate getAllLevels()) include them again.
     */
    public static void onServerStopping() {
        if (!dormantDims.isEmpty()) {
            System.out.println("[Portalium] Waking " + dormantDims.size() + " dormant dimension(s) for shutdown save");
        }
        dormantDims.clear();
        pendingUnload.clear();
    }

    public static void onServerStopped() {
        pendingUnload.clear();
        dormantDims.clear();
        activeServer = null;
        startupCountdown = -1;
    }

    public static void onPlayerChangeDimension(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to) {
        MinecraftServer server = activeServer;
        if (server == null) return;

        LastDimensionStore.save(server, player.getUUID(), to);
        pendingUnload.remove(to);

        if (dormantDims.contains(to)) {
            activateDim(server, to);
        }

        // No save here: a flushed save on every dimension change caused 500 ms+ ticks.
        // The level is saved once, without blocking, when it actually goes dormant.
        ServerLevel fromLevel = server.getLevel(from);
        if (fromLevel == null) return;

        if (fromLevel.players().isEmpty()) {
            scheduleUnload(from);
        }
    }

    public static void onPlayerRespawn(ServerPlayer sp) {
        MinecraftServer server = activeServer;
        if (server == null) return;

        ResourceKey<Level> respawnDim = sp.getRespawnDimension();
        if (dormantDims.contains(respawnDim)) {
            System.out.println("[Portalium] Activating dormant respawn dimension " + respawnDim.location());
            activateDim(server, respawnDim);
        }
    }

    public static void onPlayerLoggedIn(ServerPlayer sp) {
        MinecraftServer server = activeServer;
        if (server == null) return;

        ResourceKey<Level> lastDim = LastDimensionStore.load(server, sp.getUUID());
        if (lastDim != null && dormantDims.contains(lastDim)) {
            System.out.println("[Portalium] Pre-activating " + lastDim.location() + " for " + sp.getName().getString());
            activateDim(server, lastDim);
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer sp) {
        MinecraftServer server = activeServer;
        if (server == null) return;

        @SuppressWarnings("unchecked")
        ResourceKey<Level> dim = (ResourceKey<Level>) sp.level().dimension();
        ServerLevel level = server.getLevel(dim);
        if (level == null) return;

        if (level.players().size() <= 1) {
            scheduleUnload(dim);
        }
    }

    // Wakes the destination as soon as a player touches a portal, before the teleport happens.
    private static void checkPortalContact(MinecraftServer server, ServerPlayer sp) {
        var state = sp.level().getBlockState(sp.blockPosition());
        ResourceKey<Level> destination = null;
        if (state.is(Blocks.NETHER_PORTAL)) {
            destination = Level.NETHER;
        } else if (state.is(Blocks.END_PORTAL)) {
            destination = Level.END;
        }

        if (destination != null && dormantDims.contains(destination)) {
            System.out.println("[Portalium] Portal contact — activating " + destination.location());
            activateDim(server, destination);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server != activeServer) return;
        if (!dormantDims.isEmpty()) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                checkPortalContact(server, sp);
            }
        }
        if (startupCountdown > 0) {
            if (--startupCountdown == 0) {
                doStartupCleanup(server);
            }
            return;
        }
        if (pendingUnload.isEmpty()) return;

        pendingUnload.replaceAll((key, ticks) -> ticks - 1);
        pendingUnload.entrySet().removeIf(entry -> {
            if (entry.getValue() > 0) return false;
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) return true;
            if (!level.players().isEmpty()) {
                System.out.println("[Portalium] Cancelled dormant for " + entry.getKey().location() + " — player re-entered");
                return true;
            }
            makeDormant(server, level);
            return true;
        });
    }

    private static void doStartupCleanup(MinecraftServer server) {
        Set<ResourceKey<Level>> keepActive = LastDimensionStore.loadAll(server);
        keepActive.add(Level.OVERWORLD);
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            if (keepActive.contains(key) || !level.players().isEmpty()) continue;
            scheduleUnload(key);
        }
    }

    private static void scheduleUnload(ResourceKey<Level> key) {
        // The overworld never goes dormant: it drives world time and weather and holds the
        // world's shared data (maps, raids, most mods' saved data).
        if (key.equals(Level.OVERWORLD)) return;
        pendingUnload.put(key, PortaliumConfig.unloadDelaySeconds() * 20);
    }

    private static void makeDormant(MinecraftServer server, ServerLevel level) {
        ResourceKey<Level> key = level.dimension();
        if (key.equals(Level.OVERWORLD)) return;

        if (!level.getForcedChunks().isEmpty()) {
            System.out.println("[Portalium] Keeping " + key.location() + " active — it has force-loaded chunks");
            return;
        }

        try {
            // Non-flushing save: chunk writes go to the IO worker instead of blocking the tick.
            // The level stays loaded, so no LevelEvent.Unload is posted and its time is untouched.
            level.save(null, false, false);
            dormantDims.add(key);
            System.out.println("[Portalium] Made dormant: " + key.location());
        } catch (Exception e) {
            System.err.println("[Portalium] Failed to make dormant " + key.location() + ": " + e.getMessage());
        }
    }

    // Scans MinecraftServer's fields for the levels Map and wraps it with DormantAwareLevelMap
    // so that server.getAllLevels() skips dormant dimensions without them being ticked.
    private static void injectLevelMap(MinecraftServer server) {
        for (Class<?> cls = MinecraftServer.class; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(server);
                    if (!(val instanceof Map<?, ?> map)) continue;
                    if (map instanceof DormantAwareLevelMap) return;
                    if (map.isEmpty() || !(map.values().iterator().next() instanceof ServerLevel)) continue;
                    @SuppressWarnings("unchecked")
                    Map<ResourceKey<Level>, ServerLevel> levels = (Map<ResourceKey<Level>, ServerLevel>) val;
                    f.set(server, new DormantAwareLevelMap(levels, dormantDims));
                    System.out.println("[Portalium] Injected DormantAwareLevelMap — dormant dims will not be ticked");
                    return;
                } catch (Exception ignored) {}
            }
        }
        System.err.println("[Portalium] Could not inject DormantAwareLevelMap — dormant dims will still be ticked (less effective but safe)");
    }

    private static void activateDim(MinecraftServer server, ResourceKey<Level> key) {
        if (server.getLevel(key) == null) return;
        dormantDims.remove(key);
        pendingUnload.remove(key);
        System.out.println("[Portalium] Activated: " + key.location());
    }
}
