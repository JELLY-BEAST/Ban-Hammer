package com.jellybeast.banhammer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BanHammerWhitelist {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<UUID> ADMINS = ConcurrentHashMap.newKeySet();
    private BanHammerWhitelist() {}

    public static void load(Path file) {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                Files.writeString(file, "{\n  \"admins\": [\n    \"00000000-0000-0000-0000-000000000000\"\n  ]\n}\n");
            }
            Config config = GSON.fromJson(Files.readString(file), Config.class);
            ADMINS.clear();
            if (config != null && config.admins != null) {
                for (String id : config.admins) {
                    try { ADMINS.add(UUID.fromString(id)); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not load config/banhammer-whitelist.json", e);
        }
    }

    public static boolean isAllowed(UUID uuid) {
        return ADMINS.contains(uuid);
    }

    private static final class Config {
        Set<String> admins;
    }
}
