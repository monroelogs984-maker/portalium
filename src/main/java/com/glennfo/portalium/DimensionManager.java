package com.glennfo.portalium;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = Portalium.MOD_ID)
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

    public static void onServerStopped() {
        pendingUnload.clear();
        dormantDims.clear();
        activeServer = null;
        startupCountdown = -1;
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        MinecraftServer server = activeServer;
        if (server == null) return;

        ResourceKey<Level> from = event.getFrom();
        ResourceKey<Level> to = event.getTo();

        LastDimensionStore.save(server, event.getEntity().getUUID(), to);
        pendingUnload.remove(to);

        if (dormantDims.contains(to)) {
            activateDim(server, to);
        }

        ServerLevel fromLevel = server.getLevel(from);
        if (fromLevel == null) return;

        try {
            fromLevel.save(null, true, false);
        } catch (Exception e) {
            System.err.println("[Portalium] Save-on-leave failed for " + from.location() + ": " + e.getMessage());
        }

        if (fromLevel.players().isEmpty()) {
            scheduleUnload(from);
        }
    }

    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        ResourceKey<Level> destination = event.getDimension();
        if (dormantDims.contains(destination)) {
            System.out.println("[Portalium] Activating dormant dimension " + destination.location() + " for travel");
            activateDim(server, destination);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        ResourceKey<Level> respawnDim = sp.getRespawnDimension();
        if (dormantDims.contains(respawnDim)) {
            System.out.println("[Portalium] Activating dormant respawn dimension " + respawnDim.location());
            activateDim(server, respawnDim);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        ResourceKey<Level> lastDim = LastDimensionStore.load(server, sp.getUUID());
        if (lastDim != null && dormantDims.contains(lastDim)) {
            System.out.println("[Portalium] Pre-activating " + lastDim.location() + " for " + sp.getName().getString());
            activateDim(server, lastDim);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
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

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

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
        int delayTicks = PortaliumConfig.UNLOAD_DELAY_SECONDS.get() * 20;
        if (key.equals(Level.OVERWORLD)) {
            delayTicks *= PortaliumConfig.OVERWORLD_DELAY_MULTIPLIER.get();
        }
        pendingUnload.put(key, delayTicks);
    }

    private static void makeDormant(MinecraftServer server, ServerLevel level) {
        ResourceKey<Level> key = level.dimension();

        long activeCount = 0;
        for (ServerLevel l : server.getAllLevels()) {
            if (!dormantDims.contains(l.dimension())) activeCount++;
        }
        if (activeCount <= 1) {
            System.err.println("[Portalium] Refused to make " + key.location() + " dormant — only active dimension");
            return;
        }

        try {
            NeoForge.EVENT_BUS.post(new LevelEvent.Unload((LevelAccessor) level));
            level.save(null, true, false);
            // Freeze time so the dormant level doesn't drift
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(0);
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setDayTime(0);
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
        ServerLevel level = server.getLevel(key);
        if (level == null) return;
        try {
            ServerLevel overworld = server.overworld();
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(overworld.getGameTime());
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setDayTime(overworld.getDayTime());
            dormantDims.remove(key);
            pendingUnload.remove(key);
            NeoForge.EVENT_BUS.post(new LevelEvent.Load((LevelAccessor) level));
            System.out.println("[Portalium] Activated: " + key.location());
        } catch (Exception e) {
            System.err.println("[Portalium] Failed to activate " + key.location() + ": " + e.getMessage());
            dormantDims.remove(key);
        }
    }
}
