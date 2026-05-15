package net.raderth.scalable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ScalableConfig {
    private static Path configPath;
    public static long budgetMs = 40L;        // -1 = auto
    public static int maxDeferred = 2048;

    public static void init(Path gameDir) {
        configPath = gameDir.resolve("config/scalable.json");
        load();
    }

    public static void load() {
        if (!Files.exists(configPath)) return;
        try {
            JsonObject obj = JsonParser.parseReader(
                    Files.newBufferedReader(configPath)).getAsJsonObject();
            budgetMs    = obj.get("budget_ms").getAsLong();
            maxDeferred = obj.get("max_deferred").getAsInt();
        } catch (Exception e) {
            ScalableMod.LOGGER.warn("[Scalable] Failed to load config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("budget_ms",    budgetMs);
            obj.addProperty("max_deferred", maxDeferred);
            Files.writeString(configPath, new GsonBuilder()
                    .setPrettyPrinting().create().toJson(obj));
        } catch (Exception e) {
            ScalableMod.LOGGER.warn("[Scalable] Failed to save config", e);
        }
    }
}