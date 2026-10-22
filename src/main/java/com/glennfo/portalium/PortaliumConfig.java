package com.glennfo.portalium;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class PortaliumConfig {

    private static final String FILE_NAME = "portalium.properties";
    private static final int DEFAULT_UNLOAD_DELAY = 8;

    private static int unloadDelaySeconds = DEFAULT_UNLOAD_DELAY;

    public static int unloadDelaySeconds() {
        return unloadDelaySeconds;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                props.load(r);
            } catch (IOException e) {
                System.err.println("[Portalium] Failed to read " + FILE_NAME + ", using defaults: " + e.getMessage());
            }
        }
        unloadDelaySeconds = parse(props.getProperty("unloadDelaySeconds"), DEFAULT_UNLOAD_DELAY, 1, 300);
        props.setProperty("unloadDelaySeconds", Integer.toString(unloadDelaySeconds));
        try (Writer w = Files.newBufferedWriter(file)) {
            props.store(w, "Portalium - unloadDelaySeconds: seconds after all players leave a dimension before it goes dormant (1-300, default 8). The overworld never goes dormant.");
        } catch (IOException e) {
            System.err.println("[Portalium] Failed to write " + FILE_NAME + ": " + e.getMessage());
        }
    }

    private static int parse(String value, int fallback, int min, int max) {
        if (value == null) return fallback;
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
