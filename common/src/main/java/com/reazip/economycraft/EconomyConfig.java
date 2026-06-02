package com.reazip.economycraft;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

public class EconomyConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_RESOURCE_PATH = "/assets/economycraft/config.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public long startingBalance;
    public long dailyAmount;
    public long dailySellLimit;
    public double taxRate;
    @SerializedName("pvp_balance_loss_percentage")
    public double pvpBalanceLossPercentage;
    @SerializedName("standalone_commands")
    public boolean standaloneCommands;
    @SerializedName("standalone_admin_commands")
    public boolean standaloneAdminCommands;
    @SerializedName("scoreboard_enabled")
    public boolean scoreboardEnabled;
    @SerializedName("server_shop_enabled")
    public boolean serverShopEnabled = true;
    @SerializedName("default_shop_limit")
    public int defaultShopLimit = 15;

    private static EconomyConfig INSTANCE = new EconomyConfig();
    private static Path file;

    public static EconomyConfig get() {
        return INSTANCE;
    }

    public static void load(MinecraftServer server) {
        Path dir = server != null ? server.getFile("config/economycraft") : Path.of("config/economycraft");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        file = dir.resolve("config.json");

        if (Files.notExists(file)) {
            copyDefaultFromJarOrThrow();
        } else {
            mergeNewDefaultsFromBundledDefault();
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            EconomyConfig parsed = GSON.fromJson(json, EconomyConfig.class);
            if (parsed == null) {
                throw new IllegalStateException("config.json преобразован в null");
            }
            INSTANCE = parsed;
        } catch (Exception e) {
            throw new IllegalStateException("[EconomyCraft] Не удалось прочитать/разобрать config.json по пути " + file, e);
        }
    }

    public static void save() {
        if (file == null) {
            throw new IllegalStateException("[EconomyCraft] EconomyConfig не инициализирован. Сначала вызовите load().");
        }
        try {
            Files.writeString(
                    file,
                    GSON.toJson(INSTANCE),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new IllegalStateException("[EconomyCraft] Не удалось сохранить config.json по пути " + file, e);
        }
    }

    private static void copyDefaultFromJarOrThrow() {
        try (InputStream in = EconomyConfig.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "[EconomyCraft] Отсутствует встроенный файл по умолчанию " + DEFAULT_RESOURCE_PATH +
                                " (возможно, вы забыли включить его в ресурсы?)"
                );
            }
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[EconomyCraft] Создан {} из встроенного файла по умолчанию {}", file, DEFAULT_RESOURCE_PATH);
        } catch (IOException e) {
            throw new IllegalStateException("[EconomyCraft] Не удалось создать config.json по пути " + file, e);
        }
    }

    private static void mergeNewDefaultsFromBundledDefault() {
        JsonObject defaults = readBundledDefaultJson();
        if (defaults == null) {
            LOGGER.warn("[EconomyCraft] Встроенные настройки по умолчанию не найдены; пропуск слияния конфигов.");
            return;
        }

        JsonObject userRoot;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                LOGGER.warn("[EconomyCraft] Корень config.json не является объектом, пропуск слияния.");
                return;
            }
            userRoot = parsed.getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException("[EconomyCraft] Не удалось прочитать/разобрать пользовательский config.json для слияния по пути " + file, ex);
        }

        int[] added = new int[]{0};
        addMissingRecursive(userRoot, defaults, added);

        if (added[0] > 0) {
            try {
                Files.writeString(file, GSON.toJson(userRoot), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("[EconomyCraft] Не удалось записать объединённый config.json по пути " + file, ex);
            }
        }
    }

    private static JsonObject readBundledDefaultJson() {
        try (InputStream in = EconomyConfig.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) return null;
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return null;
            return parsed.getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException("[EconomyCraft] Не удалось прочитать встроенный config.json по умолчанию из " + DEFAULT_RESOURCE_PATH, ex);
        }
    }

    private static void addMissingRecursive(JsonObject target, JsonObject defaults, int[] added) {
        for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
            String key = e.getKey();
            JsonElement defVal = e.getValue();

            if (!target.has(key)) {
                target.add(key, defVal == null ? JsonNull.INSTANCE : defVal.deepCopy());
                added[0]++;
                continue;
            }

            JsonElement curVal = target.get(key);
            if (curVal != null && curVal.isJsonObject()
                    && defVal != null && defVal.isJsonObject()) {
                addMissingRecursive(curVal.getAsJsonObject(), defVal.getAsJsonObject(), added);
            }
        }
    }
}