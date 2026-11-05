package com.jellybeast.banhammer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record PendingBan(String reason, long expiresAt, String displayDuration, boolean permanent) {
    private static final Map<UUID, PendingBan> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, String> REASONS = new ConcurrentHashMap<>();

    public static void setReason(UUID id, String reason) {
        REASONS.put(id, reason);
        PendingBan old = PENDING.get(id);
        if (old != null) PENDING.put(id, new PendingBan(reason, old.expiresAt, old.displayDuration, old.permanent));
    }

    public static void setDuration(UUID id, String unit, int amount) {
        long multiplier = switch (unit) {
            case "second", "seconds" -> 1_000L;
            case "minute", "minutes" -> 60_000L;
            case "hour", "hours" -> 3_600_000L;
            case "day", "days" -> 86_400_000L;
            case "week", "weeks" -> 604_800_000L;
            case "year", "years" -> 31_536_000_000L;
            default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
        };
        String reason = REASONS.getOrDefault(id, "No reason specified");
        long expiry = System.currentTimeMillis() + multiplier * amount;
        PENDING.put(id, new PendingBan(reason, expiry, amount + " " + unit, false));
    }

    public static void setPermanent(UUID id) {
        String reason = REASONS.getOrDefault(id, "No reason specified");
        PENDING.put(id, new PendingBan(reason, 0L, "Permanent", true));
    }

    public static PendingBan get(UUID id) {
        PendingBan ban = PENDING.get(id);
        if (ban != null && REASONS.containsKey(id) && !ban.reason.equals(REASONS.get(id))) {
            ban = new PendingBan(REASONS.get(id), ban.expiresAt, ban.displayDuration, ban.permanent);
            PENDING.put(id, ban);
        }
        return ban;
    }

    public static void clear(UUID id) {
        PENDING.remove(id);
        REASONS.remove(id);
    }
}
