package com.glennfo.portalium;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.IServerWorldInfo;
import net.minecraftforge.common.MinecraftForge;
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

    private static final Map<RegistryKey<World>, Integer> pendingUnload = new HashMap<>();
    private static final Set<RegistryKey<World>> dormantDims = new HashSet<>();
    private static final int STARTUP_CLEANUP_DELAY = 40;
    private static int startupCountdown = -1;
    private static MinecraftServer activeServer = null;

    public static boolean isDormant(RegistryKey<World> key) {
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

        RegistryKey<World> from = event.getFrom();
        RegistryKey<World> to = event.getTo();

        LastDimensionStore.save(server, event.getPlayer().getUUID(), to);
        pendingUnload.remove(to);

        if (dormantDims.contains(to)) {
            activateDim(server, to);
        }

        ServerWorld fromWorld = server.getLevel(from);
        if (fromWorld == null) return;

        try {
            fromWorld.save(null, true, false);
        } catch (Exception e) {
            System.err.println("[Portalium] Save-on-leave failed for " + from.location() + ": " + e.getMessage());
        }

        if (fromWorld.players().isEmpty()) {
            scheduleUnload(from);
        }
    }

    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        RegistryKey<World> destination = event.getDimension();
        if (dormantDims.contains(destination)) {
            System.out.println("[Portalium] Activating dormant dimension " + destination.location() + " for travel");
            activateDim(server, destination);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PlayerEntity player = event.getPlayer();
        if (!(player instanceof ServerPlayerEntity)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        RegistryKey<World> respawnDim = ((ServerPlayerEntity) player).getRespawnDimension();
        if (respawnDim != null && dormantDims.contains(respawnDim)) {
            System.out.println("[Portalium] Activating dormant respawn dimension " + respawnDim.location());
            activateDim(server, respawnDim);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerEntity player = event.getPlayer();
        if (!(player instanceof ServerPlayerEntity)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        RegistryKey<World> lastDim = LastDimensionStore.load(server, player.getUUID());
        if (lastDim != null && dormantDims.contains(lastDim)) {
            System.out.println("[Portalium] Pre-activating " + lastDim.location() + " for " + player.getName().getString());
            activateDim(server, lastDim);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerEntity player = event.getPlayer();
        if (!(player instanceof ServerPlayerEntity)) return;
        MinecraftServer server = activeServer;
        if (server == null) return;

        RegistryKey<World> dim = player.level.dimension();
        ServerWorld world = server.getLevel(dim);
        if (world == null) return;

        if (world.players().size() <= 1) {
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
        RegistryKey<World> destination = null;
        if (player.level.getBlockState(player.blockPosition()).getBlock() == Blocks.NETHER_PORTAL) {
            destination = World.NETHER;
        } else if (player.level.getBlockState(player.blockPosition()).getBlock() == Blocks.END_PORTAL) {
            destination = World.END;
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
            ServerWorld world = server.getLevel(entry.getKey());
            if (world == null) return true;
            if (!world.players().isEmpty()) {
                System.out.println("[Portalium] Cancelled dormant for " + entry.getKey().location() + " — player re-entered");
                return true;
            }
            makeDormant(server, world);
            return true;
        });
    }

    private static void doStartupCleanup(MinecraftServer server) {
        Set<RegistryKey<World>> keepActive = LastDimensionStore.loadAll(server);
        keepActive.add(World.OVERWORLD);
        for (ServerWorld world : server.getAllLevels()) {
            RegistryKey<World> key = world.dimension();
            if (keepActive.contains(key) || !world.players().isEmpty()) continue;
            scheduleUnload(key);
        }
    }

    private static void scheduleUnload(RegistryKey<World> key) {
        int delayTicks = PortaliumConfig.UNLOAD_DELAY_SECONDS.get() * 20;
        if (key.equals(World.OVERWORLD)) {
            delayTicks *= PortaliumConfig.OVERWORLD_DELAY_MULTIPLIER.get();
        }
        pendingUnload.put(key, delayTicks);
    }

    private static void makeDormant(MinecraftServer server, ServerWorld world) {
        RegistryKey<World> key = world.dimension();

        long activeCount = 0;
        for (ServerWorld w : server.getAllLevels()) {
            if (!dormantDims.contains(w.dimension())) activeCount++;
        }
        if (activeCount <= 1) {
            System.err.println("[Portalium] Refused to make " + key.location() + " dormant — only active dimension");
            return;
        }

        try {
            MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(world));
            world.save(null, true, false);
            IServerWorldInfo info = (IServerWorldInfo) world.getLevelData();
            info.setGameTime(0);
            info.setDayTime(0);
            dormantDims.add(key);
            System.out.println("[Portalium] Made dormant: " + key.location());
        } catch (Exception e) {
            System.err.println("[Portalium] Failed to make dormant " + key.location() + ": " + e.getMessage());
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
                    Map<RegistryKey<World>, ServerWorld> worlds = (Map<RegistryKey<World>, ServerWorld>) val;
                    f.set(server, new DormantAwareLevelMap(worlds, dormantDims));
                    System.out.println("[Portalium] Injected DormantAwareLevelMap — dormant dims will not be ticked");
                    return;
                } catch (Exception ignored) {}
            }
        }
        System.err.println("[Portalium] Could not inject DormantAwareLevelMap — dormant dims will still be ticked (less effective but safe)");
    }

    private static void activateDim(MinecraftServer server, RegistryKey<World> key) {
        ServerWorld world = server.getLevel(key);
        if (world == null) return;
        try {
            ServerWorld overworld = server.overworld();
            IServerWorldInfo info = (IServerWorldInfo) world.getLevelData();
            info.setGameTime(overworld.getGameTime());
            info.setDayTime(overworld.getDayTime());
            dormantDims.remove(key);
            pendingUnload.remove(key);
            MinecraftForge.EVENT_BUS.post(new WorldEvent.Load(world));
            System.out.println("[Portalium] Activated: " + key.location());
        } catch (Exception e) {
            System.err.println("[Portalium] Failed to activate " + key.location() + ": " + e.getMessage());
            dormantDims.remove(key);
        }
    }
}
