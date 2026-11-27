package dev.tlockout.goal;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Counters and sets accumulated by event-driven goals; cleared when a round starts. */
public final class TrackingState {

    private final Map<UUID, Map<String, Integer>> playerStats = new HashMap<>();
    private final Map<UUID, Map<String, Double>> playerDoubles = new HashMap<>();
    private final Map<UUID, Map<String, Set<String>>> playerSets = new HashMap<>();
    private final Map<String, Map<String, Set<String>>> sideSets = new HashMap<>();

    public void clear() {
        playerStats.clear();
        playerDoubles.clear();
        playerSets.clear();
        sideSets.clear();
    }

    /** Clears one player's personal counters and sets (used when joining or switching mid-round). */
    public void clearPlayer(UUID playerId) {
        playerStats.remove(playerId);
        playerDoubles.remove(playerId);
        playerSets.remove(playerId);
    }

    // ------------------------------------------------------------- counters

    public void add(UUID playerId, String key, int amount) {
        if (key == null || key.isEmpty()) {
            return;
        }
        playerStats.computeIfAbsent(playerId, id -> new HashMap<>())
                .merge(key, amount, Integer::sum);
    }

    public int playerStat(UUID playerId, String key) {
        Map<String, Integer> values = playerStats.get(playerId);
        return values == null ? 0 : values.getOrDefault(key, 0);
    }

    public void setPlayerStat(UUID playerId, String key, int value) {
        playerStats.computeIfAbsent(playerId, id -> new HashMap<>()).put(key, value);
    }

    public void addDouble(UUID playerId, String key, double amount) {
        if (key == null || key.isEmpty() || amount == 0) {
            return;
        }
        playerDoubles.computeIfAbsent(playerId, id -> new HashMap<>())
                .merge(key, amount, Double::sum);
    }

    public double playerDouble(UUID playerId, String key) {
        Map<String, Double> values = playerDoubles.get(playerId);
        return values == null ? 0d : values.getOrDefault(key, 0d);
    }

    // ----------------------------------------------------------------- sets

    public void addPlayerSet(UUID playerId, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        playerSets.computeIfAbsent(playerId, id -> new HashMap<>())
                .computeIfAbsent(key, k -> new LinkedHashSet<>())
                .add(value);
    }

    public void addSideSet(String sideId, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        sideSets.computeIfAbsent(sideId, id -> new HashMap<>())
                .computeIfAbsent(key, k -> new LinkedHashSet<>())
                .add(value);
    }

    public int playerSetSize(UUID playerId, String key) {
        Map<String, Set<String>> sets = playerSets.get(playerId);
        if (sets == null) {
            return 0;
        }
        Set<String> values = sets.get(key);
        return values == null ? 0 : values.size();
    }

    public int sideSetSize(String sideId, String key) {
        Map<String, Set<String>> sets = sideSets.get(sideId);
        if (sets == null) {
            return 0;
        }
        Set<String> values = sets.get(key);
        return values == null ? 0 : values.size();
    }

    public boolean playerSetContains(UUID playerId, String key, String value) {
        Map<String, Set<String>> sets = playerSets.get(playerId);
        if (sets == null) {
            return false;
        }
        Set<String> values = sets.get(key);
        return values != null && values.contains(value);
    }

    public boolean sideSetContains(String sideId, String key, String value) {
        Map<String, Set<String>> sets = sideSets.get(sideId);
        if (sets == null) {
            return false;
        }
        Set<String> values = sets.get(key);
        return values != null && values.contains(value);
    }

    public Set<String> playerSet(UUID playerId, String key) {
        Map<String, Set<String>> sets = playerSets.get(playerId);
        if (sets == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(sets.getOrDefault(key, Set.of()));
    }

    public Set<String> sideSet(String sideId, String key) {
        Map<String, Set<String>> sets = sideSets.get(sideId);
        if (sets == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(sets.getOrDefault(key, Set.of()));
    }

    /** Shared counter-key helpers, so event handlers and trackers cannot drift apart. */
    public static String mobKey(org.bukkit.entity.EntityType type) {
        return "MOB_KILL:" + keyOf(type);
    }

    public static String tameKey(org.bukkit.entity.EntityType type) {
        return "TAME:" + keyOf(type);
    }

    public static String breedKey(org.bukkit.entity.EntityType type) {
        return "BREED:" + keyOf(type);
    }

    private static String keyOf(org.bukkit.entity.EntityType type) {
        return type.getKey().toString().toLowerCase(Locale.ROOT);
    }
}
