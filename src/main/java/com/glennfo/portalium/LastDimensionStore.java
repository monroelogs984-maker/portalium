package com.glennfo.portalium;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.util.ResourceLocation;

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

    public static void save(MinecraftServer server, UUID playerUuid, DimensionType dimension) {
        File file = getSaveFile(server);
        if (file == null) return;
        String dimKey = dimToString(dimension);
        if (dimKey == null) return;
        JsonObject root = readRaw(file);
        JsonObject players = root.has("players") ? root.getAsJsonObject("players") : new JsonObject();
        players.addProperty(playerUuid.toString(), dimKey);
        root.add("players", players);
        try (FileWriter w = new FileWriter(file)) {
            GSON.toJson(root, w);
        } catch (IOException e) {
            System.err.println("[Portalium] Failed to save last dimension: " + e.getMessage());
        }
    }

    public static DimensionType load(MinecraftServer server, UUID playerUuid) {
        File file = getSaveFile(server);
        if (file == null) return null;
        JsonObject root = readRaw(file);
        if (!root.has("players")) return null;
        JsonObject players = root.getAsJsonObject("players");
        String uuidKey = playerUuid.toString();
        if (!players.has(uuidKey)) return null;
        return stringToDim(players.get(uuidKey).getAsString());
    }

    public static Set<DimensionType> loadAll(MinecraftServer server) {
        Set<DimensionType> result = new HashSet<>();
        File file = getSaveFile(server);
        if (file == null) return result;
        JsonObject root = readRaw(file);
        if (!root.has("players")) return result;
        JsonObject players = root.getAsJsonObject("players");
        for (Map.Entry<String, com.google.gson.JsonElement> entry : players.entrySet()) {
            DimensionType dim = stringToDim(entry.getValue().getAsString());
            if (dim != null) result.add(dim);
        }
        return result;
    }

    private static File getSaveFile(MinecraftServer server) {
        try {
            File worldDir = server.getWorld(DimensionType.OVERWORLD).getSaveHandler().getWorldDirectory();
            return new File(worldDir, FILE_NAME);
        } catch (Exception e) {
            System.err.println("[Portalium] Could not resolve world directory: " + e.getMessage());
            return null;
        }
    }

    private static String dimToString(DimensionType dim) {
        try {
            ResourceLocation key = DimensionType.getKey(dim);
            return key != null ? key.toString() : null;
        } catch (Exception e) {
            System.err.println("[Portalium] Could not serialize DimensionType: " + e.getMessage());
            return null;
        }
    }

    private static DimensionType stringToDim(String str) {
        try {
            return DimensionType.byName(new ResourceLocation(str));
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
