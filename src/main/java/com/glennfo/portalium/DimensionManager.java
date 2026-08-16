package com.glennfo.portalium;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Portalium.MOD_ID)
public class DimensionManager {

    private static final Map<DimensionType, Integer> pendingUnload = new HashMap<>();
    private static final Set<DimensionType> dormantDims = new HashSet<>();
    private static final int STARTUP_CLEANUP_DELAY = 40;
    private static int startupCountdown = -1;
    private static MinecraftServer activeServer = null;

    public static boolean isDormant(DimensionType dim) {
        return dormantDims.contains(dim);
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

        DimensionType from = event.getFrom();
        DimensionType to = event.getTo();

        LastDimensionStore.save(server, event.getPlayer().getUniqueID(), to);
        pendingUnload.remove(to);

        if (dormantDims.contains(to)) {
            activateDim(server, to);
        }

        ServerWorld fromWorld = server.getWorld(from);
        if (fromWorld == null) return;

        try {
            fromWorld.save(null, true, false);
        } catch (Exception e) {
            System.err.println("[Portalium] Save-on-leave failed for " + from + ": " + e.getMessage());
        }

        if (fromWorld.getPlayers().isEmpty()) {
            scheduleUnload(from);
        }
    }

    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        DimensionType destination = event.getDimension();
        if (dormantDims.contains(destination)) {
            System.out.println("[Portalium] Activating dormant dimension " + destination + " for travel");
            activateDim(server, destination);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PlayerEntity player = event.getPlayer();
        if (!(player instanceof ServerPlayerEntity)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        DimensionType respawnDim = player.getSpawnDimension();
        if (respawnDim != null && dormantDims.contains(respawnDim)) {
            System.out.println("[Portalium] Activating dormant respawn dimension " + respawnDim);
            activateDim(server, respawnDim);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerEntity player = event.getPlayer();
        if (!(player instanceof ServerPlayerEntity)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        DimensionType lastDim = LastDimensionStore.load(server, player.getUniqueID());
        if (lastDim != null && dormantDims.contains(lastDim)) {
            System.out.println("[Portalium] Pre-activating " + lastDim + " for " + player.getName().getString());
            activateDim(server, lastDim);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerEntity player = event.getPlayer();
        if (!(player instanceof ServerPlayerEntity)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        DimensionType dim = player.dimension;
        ServerWorld world = server.getWorld(dim);
        if (world == null) return;

        if (world.getPlayers().size() <= 1) {
            scheduleUnload(dim);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayerEntity)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        PlayerEntity player = event.player;
        DimensionType destination = null;
        if (player.world.getBlockState(player.getPosition()).getBlock() == Blocks.NETHER_PORTAL) {
            destination = DimensionType.THE_NETHER;
        } else if (player.world.getBlockState(player.getPosition()).getBlock() == Blocks.END_PORTAL) {
            destination = DimensionType.THE_END;
        }

        if (destination != null && dormantDims.contains(destination)) {
            System.out.println("[Portalium] Portal contact — activating " + destination);
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
            ServerWorld world = server.getWorld(entry.getKey());
            if (world == null) return true;
            if (!world.getPlayers().isEmpty()) {
                System.out.println("[Portalium] Cancelled dormant for " + entry.getKey() + " — player re-entered");
                return true;
            }
            makeDormant(server, world);
            return true;
        });
    }

    private static void doStartupCleanup(MinecraftServer server) {
        Set<DimensionType> keepActive = LastDimensionStore.loadAll(server);
        keepActive.add(DimensionType.OVERWORLD);
        for (ServerWorld world : server.getWorlds()) {
            DimensionType key = world.getDimension().getType();
            if (keepActive.contains(key) || !world.getPlayers().isEmpty()) continue;
            scheduleUnload(key);
        }
    }

    private static void scheduleUnload(DimensionType key) {
        int delayTicks = PortaliumConfig.UNLOAD_DELAY_SECONDS.get() * 20;
        if (key == DimensionType.OVERWORLD) {
            delayTicks *= PortaliumConfig.OVERWORLD_DELAY_MULTIPLIER.get();
        }
        pendingUnload.put(key, delayTicks);
    }

    private static void makeDormant(MinecraftServer server, ServerWorld world) {
        DimensionType key = world.getDimension().getType();

        long activeCount = 0;
        for (ServerWorld w : server.getWorlds()) {
            if (!dormantDims.contains(w.getDimension().getType())) activeCount++;
        }
        if (activeCount <= 1) {
            System.err.println("[Portalium] Refused to make " + key + " dormant — only active dimension");
            return;
        }

        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(world));
            world.save(null, true, false);
            WorldInfo info = (WorldInfo) world.getWorldInfo();
            info.setGameTime(0);
            info.setDayTime(0);
            dormantDims.add(key);
            System.out.println("[Portalium] Made dormant: " + key);
        } catch (Exception e) {
            System.err.println("[Portalium] Failed to make dormant " + key + ": " + e.getMessage());
        }
    }

    private static void injectLevelMap(MinecraftServer server) {
        for (Class<?> cls = server.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(server);
                    if (!(val instanceof Map)) continue;
                    Map<?, ?> map = (Map<?, ?>) val;
                    if (map instanceof DormantAwareLevelMap) return;
                    if (map.isEmpty()) continue;
                    Object firstValue = map.values().iterator().next();
                    if (!(firstValue instanceof ServerWorld)) continue;
                    @SuppressWarnings("unchecked")
                    Map<DimensionType, ServerWorld> worlds = (Map<DimensionType, ServerWorld>) val;
                    f.set(server, new DormantAwareLevelMap(worlds, dormantDims));
                    System.out.println("[Portalium] Injected DormantAwareLevelMap — dormant dims will not be ticked");
                    return;
                } catch (Exception ignored) {}
            }
        }
        System.err.println("[Portalium] Could not inject DormantAwareLevelMap — dormant dims will still be ticked (less effective but safe)");
    }

    private static void activateDim(MinecraftServer server, DimensionType key) {
        ServerWorld world = server.getWorld(key);
        if (world == null) return;
        try {
            ServerWorld overworld = server.getWorld(DimensionType.OVERWORLD);
            WorldInfo info = (WorldInfo) world.getWorldInfo();
            info.setGameTime(overworld.getGameTime());
            info.setDayTime(overworld.getDayTime());
            dormantDims.remove(key);
            pendingUnload.remove(key);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new WorldEvent.Load(world));
            System.out.println("[Portalium] Activated: " + key);
        } catch (Exception e) {
            System.err.println("[Portalium] Failed to activate " + key + ": " + e.getMessage());
            dormantDims.remove(key);
        }
    }
}
