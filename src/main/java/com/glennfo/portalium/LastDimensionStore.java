package com.glennfo.portalium;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.storage.FolderName;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LastDimensionStore {

    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "portalium_last_dim.json";

    public static void save(MinecraftServer server, UUID playerUuid, RegistryKey<World> dimension) {
        File file = getSaveFile(server);
        JsonObject root = readRaw(file);
        JsonObject players = root.has("players") ? root.getAsJsonObject("players") : new JsonObject();
        players.addProperty(playerUuid.toString(), dimension.location().toString());
        root.add("players", players);
        try (FileWriter w = new FileWriter(file)) {
            GSON.toJson(root, w);
        } catch (IOException e) {
            System.err.println("[Portalium] Failed to save last dimension: " + e.getMessage());
        }
    }

    public static RegistryKey<World> load(MinecraftServer server, UUID playerUuid) {
        JsonObject root = readRaw(getSaveFile(server));
        if (!root.has("players")) return null;
        JsonObject players = root.getAsJsonObject("players");
        String uuidKey = playerUuid.toString();
        if (!players.has(uuidKey)) return null;
        return parseKey(players.get(uuidKey).getAsString());
    }

    public static Set<RegistryKey<World>> loadAll(MinecraftServer server) {
        Set<RegistryKey<World>> result = new HashSet<>();
        JsonObject root = readRaw(getSaveFile(server));
        if (!root.has("players")) return result;
        JsonObject players = root.getAsJsonObject("players");
        for (Map.Entry<String, com.google.gson.JsonElement> entry : players.entrySet()) {
            RegistryKey<World> key = parseKey(entry.getValue().getAsString());
            if (key != null) result.add(key);
        }
        return result;
    }

    private static File getSaveFile(MinecraftServer server) {
        return server.getWorldPath(FolderName.ROOT).resolve(FILE_NAME).toFile();
    }

    // RegistryKey<Registry<World>> derived from an existing World key — avoids hardcoding the registry name
    private static final RegistryKey<net.minecraft.util.registry.Registry<World>> WORLD_REGISTRY =
        RegistryKey.createRegistryKey(World.OVERWORLD.getRegistryName());

    private static RegistryKey<World> parseKey(String str) {
        try {
            ResourceLocation loc = ResourceLocation.tryParse(str);
            if (loc == null) return null;
            return RegistryKey.create(WORLD_REGISTRY, loc);
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject readRaw(File file) {
        if (!file.exists()) return new JsonObject();
        try (FileReader r = new FileReader(file)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            return obj != null ? obj : new JsonObject();
        } catch (Exception e) {
            System.err.println("[Portalium] Failed to read last dimension store: " + e.getMessage());
            return new JsonObject();
        }
    }
}
