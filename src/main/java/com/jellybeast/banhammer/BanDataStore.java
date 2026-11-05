package com.jellybeast.banhammer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BanDataStore {
    public record BanRecord(String playerName, String reason, long expiresAt) {}
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Map<UUID, BanRecord> bans = new ConcurrentHashMap<>();

    public BanDataStore(Path file) {
        this.file = file;
        load();
    }

    private synchronized void load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.writeString(file, "{}");
            Type type = new TypeToken<Map<String, BanRecord>>(){}.getType();
            Map<String, BanRecord> loaded = GSON.fromJson(Files.readString(file), type);
            if (loaded != null) loaded.forEach((id, ban) -> {
                try { bans.put(UUID.fromString(id), ban); } catch (IllegalArgumentException ignored) {}
            });
        } catch (Exception e) {
            throw new RuntimeException("Could not load banhammer-bans.json", e);
        }
    }

    public BanRecord get(UUID id) { return bans.get(id); }

    public synchronized void ban(UUID id, String name, String reason, long expiresAt) {
        bans.put(id, new BanRecord(name, reason, expiresAt));
        save();
    }

    public synchronized void unban(UUID id) {
        bans.remove(id);
        save();
    }

    private synchronized void save() {
        try {
            Map<String, BanRecord> output = new ConcurrentHashMap<>();
            bans.forEach((id, record) -> output.put(id.toString(), record));
            Files.writeString(file, GSON.toJson(output));
        } catch (Exception e) {
            throw new RuntimeException("Could not save banhammer-bans.json", e);
        }
    }
}
