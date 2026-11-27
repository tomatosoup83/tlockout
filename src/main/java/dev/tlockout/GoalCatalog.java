package dev.tlockout;

import dev.tlockout.goal.GoalGroups;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.GoalTrackers;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.WorldFeasibilityScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The pool of goal templates loaded from goals.yml, and the weighted draw used to populate a board.
 * Each template is validated by its {@link GoalTracker}; text and icon defaults are generated there,
 * so a new goal family only needs a tracker and YAML entries.
 */
public final class GoalCatalog {

    /** goals.yml format this build understands. Bumped whenever the YAML shape changes. */
    public static final int GOALS_VERSION = 9;

    private static final int MAX_DRAW_ATTEMPTS = 12;

    private static final Set<String> RESERVED_KEYS = Set.of(
            "id", "type", "display", "requirement", "icon", "weight", "group", "variants",
            "pool", "team-size", "requires");

    private final List<GoalSpec> templates;

    private GoalCatalog(List<GoalSpec> templates) {
        this.templates = List.copyOf(templates);
    }

    public int size() {
        return templates.size();
    }

    public List<GoalSpec> templates() {
        return templates;
    }

    /** Convenience draw with default 2-team count and unrestricted world facts. */
    public List<ResolvedGoal> draw(int amount, Random random) {
        return draw(amount, random, 2, WorldFeasibilityScanner.WorldFacts.UNRESTRICTED, null);
    }

    /**
     * Draws {@code amount} distinct templates weighted by {@link GoalSpec#weight()}, respecting
     * pool filters ({@code pool: random}), team-size gating, world structure/biome feasibility,
     * and {@link GoalGroups} anti-clustering caps and predecessor rules.
     * <p>
     * Same seed + same world facts always produces the same board.
     */
    public List<ResolvedGoal> draw(int amount, Random random, int teamCount,
                                   WorldFeasibilityScanner.WorldFacts worldFacts, Logger logger) {
        if (templates.size() < amount) {
            throw new IllegalStateException("goals.yml defines " + templates.size()
                    + " templates, but a board needs " + amount + " - add more templates");
        }

        List<GoalSpec> randomPool = new ArrayList<>();
        List<GoalSpec> feasible = new ArrayList<>();
        for (GoalSpec spec : templates) {
            if (!spec.inRandomPool() || !spec.teamSize().accepts(teamCount)) {
                continue;
            }
            randomPool.add(spec);
            if (worldFacts == null
                    || worldFacts.satisfies(spec.requiredBiomes(), spec.requiredStructures())) {
                feasible.add(spec);
            }
        }

        List<GoalSpec> eligible;
        if (feasible.size() >= amount) {
            eligible = feasible;
        } else if (randomPool.size() >= amount) {
            if (logger != null) {
                logger.warning("World gating left only " + feasible.size() + " feasible goals (need "
                        + amount + "); relaxing structure/biome gating for this board.");
            }
            eligible = randomPool;
        } else {
            if (logger != null) {
                logger.warning("Random pool has only " + randomPool.size() + " goals (need "
                        + amount + "); falling back to all templates.");
            }
            eligible = templates;
        }

        List<GoalSpec> chosen = null;
        for (int attempt = 0; attempt < MAX_DRAW_ATTEMPTS; attempt++) {
            List<GoalSpec> candidate = drawWithCaps(eligible, amount, random, true);
            if (candidate.size() == amount) {
                chosen = candidate;
                break;
            }
        }
        if (chosen == null) {
            if (logger != null) {
                logger.warning("Group caps prevented filling " + amount + " cells after "
                        + MAX_DRAW_ATTEMPTS + " attempts; relaxing group caps for this board.");
            }
            chosen = drawWithCaps(eligible, amount, random, false);
        }

        List<ResolvedGoal> picked = new ArrayList<>(amount);
        for (GoalSpec spec : chosen) {
            picked.add(spec.resolve(pickVariants(spec, random)));
        }
        // Shuffle after picking so predecessor-gated goals are not biased toward the bottom rows.
        Collections.shuffle(picked, random);
        return picked;
    }

    private static List<GoalSpec> drawWithCaps(List<GoalSpec> eligible, int amount, Random random,
                                               boolean enforceGroups) {
        List<GoalSpec> remaining = new ArrayList<>(eligible);
        List<GoalSpec> chosen = new ArrayList<>(amount);
        while (chosen.size() < amount && !remaining.isEmpty()) {
            int total = 0;
            List<GoalSpec> allowed = enforceGroups ? new ArrayList<>(remaining.size()) : remaining;
            if (enforceGroups) {
                for (GoalSpec spec : remaining) {
                    if (GoalGroups.canAdd(spec, chosen)) {
                        allowed.add(spec);
                        total += weightOf(spec);
                    }
                }
            } else {
                for (GoalSpec spec : remaining) {
                    total += weightOf(spec);
                }
            }
            if (allowed.isEmpty() || total <= 0) {
                break;
            }
            int roll = random.nextInt(total);
            int cursor = 0;
            for (GoalSpec spec : allowed) {
                cursor += weightOf(spec);
                if (roll < cursor) {
                    chosen.add(spec);
                    remaining.remove(spec);
                    break;
                }
            }
        }
        return chosen;
    }

    private static Map<String, String> pickVariants(GoalSpec spec, Random random) {
        if (spec.variants().isEmpty()) {
            return Map.of();
        }
        Map<String, String> data = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : spec.variants().entrySet()) {
            List<String> values = entry.getValue();
            data.put(entry.getKey(), values.get(random.nextInt(values.size())));
        }
        return data;
    }

    private static int weightOf(GoalSpec template) {
        return Math.max(1, template.weight());
    }

    // ------------------------------------------------------------------ load

    public static GoalCatalog load(TLockout plugin) {
        return load(new File(plugin.getDataFolder(), "goals.yml"), plugin.getLogger());
    }

    public static GoalCatalog load(File file, Logger logger) {
        logger.info("Reading goal templates from " + file.getAbsolutePath());
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception ex) {
            throw new IllegalStateException("could not read goals.yml: " + ex.getMessage(), ex);
        }

        int version = yaml.getInt("goals-version", 0);
        if (version < GOALS_VERSION) {
            throw new IllegalStateException("goals.yml is version "
                    + (version == 0 ? "(missing)" : version) + ", but this build needs version "
                    + GOALS_VERSION + ". Delete " + file.getAbsolutePath()
                    + " and restart to restore the bundled goals.");
        }

        List<Map<?, ?>> raw = yaml.getMapList("templates");
        if (raw.isEmpty()) {
            throw new IllegalStateException("goals.yml has no entries under 'templates'");
        }

        List<GoalSpec> loaded = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int index = 0;
        for (Map<?, ?> entry : raw) {
            index++;
            String id = optional(entry, "id");
            if (id == null) {
                id = "goal_" + index;
            }
            if (!ids.add(id)) {
                throw new IllegalStateException("goals.yml entry #" + index + ": duplicate goal id '" + id + "'");
            }
            try {
                loaded.add(parse(id, entry));
            } catch (RuntimeException ex) {
                throw new IllegalStateException("goals.yml entry #" + index + " (id '" + id + "'): "
                        + ex.getMessage(), ex);
            }
        }
        return new GoalCatalog(loaded);
    }

    private static GoalSpec parse(String id, Map<?, ?> entry) {
        GoalType type = GoalType.parse(require(entry, "type"));
        GoalTracker tracker = GoalTrackers.of(type);

        Map<String, String> params = new LinkedHashMap<>();
        Map<String, List<String>> variants = new LinkedHashMap<>();
        for (Map.Entry<?, ?> raw : entry.entrySet()) {
            String key = String.valueOf(raw.getKey());
            if (key.equals("variants")) {
                parseVariants(raw.getValue(), variants);
                continue;
            }
            if (RESERVED_KEYS.contains(key)) {
                continue;
            }
            if (raw.getValue() instanceof List<?> list) {
                List<String> values = new ArrayList<>();
                for (Object item : list) {
                    String value = stringValue(item);
                    if (value != null) {
                        values.add(value);
                    }
                }
                if (!values.isEmpty()) {
                    params.put(key, String.join(",", values));
                }
                continue;
            }
            String value = stringValue(raw.getValue());
            if (value != null) {
                params.put(key, value);
            }
        }

        GoalSpec.Pool pool = GoalSpec.Pool.parse(optional(entry, "pool"));
        GoalSpec.TeamSize teamSize = GoalSpec.TeamSize.parse(optional(entry, "team-size"));
        List<String> requiredBiomes = new ArrayList<>();
        List<String> requiredStructures = new ArrayList<>();
        Object requiresRaw = entry.get("requires");
        if (requiresRaw != null) {
            parseRequires(requiresRaw, requiredBiomes, requiredStructures);
        }

        GoalSpec spec = new GoalSpec(id, type, tracker,
                optional(entry, "display"),
                optional(entry, "icon"),
                optional(entry, "requirement"),
                integer(entry.get("weight"), 10),
                optional(entry, "group"),
                pool,
                teamSize,
                requiredBiomes,
                requiredStructures,
                params,
                variants);
        tracker.validate(spec);
        spec.finishDefaults();
        return spec;
    }

    private static void parseRequires(Object value, List<String> biomes, List<String> structures) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("'requires' must be a map with 'biomes' and/or 'structures'");
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey()).trim().toLowerCase(java.util.Locale.ROOT);
            List<String> target = switch (key) {
                case "biomes" -> biomes;
                case "structures" -> structures;
                default -> throw new IllegalArgumentException("unknown 'requires' key '" + key
                        + "' (expected 'biomes' or 'structures')");
            };
            if (!(entry.getValue() instanceof List<?> list)) {
                throw new IllegalArgumentException("'requires." + key + "' must be a list");
            }
            for (Object item : list) {
                String text = stringValue(item);
                if (text != null) {
                    target.add(WorldFeasibilityScanner.normaliseKey(text));
                }
            }
        }
    }

    private static void parseVariants(Object value, Map<String, List<String>> variants) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("'variants' must be a map of name to value list");
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof List<?> list) || list.isEmpty()) {
                throw new IllegalArgumentException("variant '" + key + "' must be a non-empty list");
            }
            List<String> values = new ArrayList<>(list.size());
            for (Object item : list) {
                String text = stringValue(item);
                if (text == null) {
                    throw new IllegalArgumentException("variant '" + key + "' has a blank value");
                }
                values.add(text);
            }
            variants.put(key, values);
        }
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("'variants' must define at least one variant");
        }
    }

    /** A required field; throws with the field's name when missing or blank. */
    private static String require(Map<?, ?> entry, String field) {
        String value = optional(entry, field);
        if (value == null) {
            throw new IllegalArgumentException("missing field '" + field + "'");
        }
        return value;
    }

    /** An optional field; null when missing or blank. */
    private static String optional(Map<?, ?> entry, String field) {
        return stringValue(entry.get(field));
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static int integer(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
