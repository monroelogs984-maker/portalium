package com.glennfo.portalium;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LastDimensionStore {

    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "portalium_last_dim.json";

    public static void save(MinecraftServer server, UUID playerUuid, ResourceKey<Level> dimension) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
        JsonObject root = readRaw(file);
        JsonObject players = root.has("players") ? root.getAsJsonObject("players") : new JsonObject();
        players.addProperty(playerUuid.toString(), dimension.location().toString());
        root.add("players", players);
        try (FileWriter w = new FileWriter(file.toFile())) {
            GSON.toJson(root, w);
        } catch (IOException e) {
            System.err.println("[Portalium] Failed to save last dimension: " + e.getMessage());
        }
    }

    public static ResourceKey<Level> load(MinecraftServer server, UUID playerUuid) {
        JsonObject root = readRaw(server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME));
        if (!root.has("players")) return null;
        JsonObject players = root.getAsJsonObject("players");
        String uuidKey = playerUuid.toString();
        if (!players.has(uuidKey)) return null;
        return parseKey(players.get(uuidKey).getAsString());
    }

    public static Set<ResourceKey<Level>> loadAll(MinecraftServer server) {
        Set<ResourceKey<Level>> result = new HashSet<>();
        JsonObject root = readRaw(server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME));
        if (!root.has("players")) return result;
        JsonObject players = root.getAsJsonObject("players");
        for (String uuid : players.keySet()) {
            ResourceKey<Level> key = parseKey(players.get(uuid).getAsString());
            if (key != null) result.add(key);
        }
        return result;
    }

    private static ResourceKey<Level> parseKey(String str) {
        try {
            ResourceLocation loc = ResourceLocation.tryParse(str);
            if (loc == null) return null;
            return ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, loc);
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject readRaw(Path file) {
        if (!file.toFile().exists()) return new JsonObject();
        try (FileReader r = new FileReader(file.toFile())) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            return obj != null ? obj : new JsonObject();
        } catch (Exception e) {
            System.err.println("[Portalium] Failed to read last dimension store: " + e.getMessage());
            return new JsonObject();
        }
    }
}
