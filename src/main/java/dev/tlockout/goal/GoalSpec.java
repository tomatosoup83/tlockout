package dev.tlockout.goal;

import dev.tlockout.GoalType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * A parsed template from goals.yml: the goal id and type, the text/icon patterns, weight and any
 * type-specific params. Variant placeholders ({@code {color}}) are resolved per board cell by
 * {@link #resolve(Map)}.
 */
public final class GoalSpec {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_-]+)}");

    public enum Pool {
        RANDOM,
        CUSTOM_ONLY;

        public static Pool parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return RANDOM;
            }
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "random" -> RANDOM;
                case "custom-only", "custom_only", "custom" -> CUSTOM_ONLY;
                default -> throw new IllegalArgumentException("unknown pool '" + raw
                        + "' (expected random or custom-only)");
            };
        }
    }

    public enum TeamSize {
        ANY,
        EXACTLY_TWO,
        AT_LEAST_TWO;

        public boolean accepts(int teamCount) {
            return switch (this) {
                case ANY -> true;
                case EXACTLY_TWO -> teamCount == 2;
                case AT_LEAST_TWO -> teamCount >= 2;
            };
        }

        public static TeamSize parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return ANY;
            }
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "any" -> ANY;
                case "2", "exactly_two", "to2" -> EXACTLY_TWO;
                case "2+", "at_least_two", "teams" -> AT_LEAST_TWO;
                default -> throw new IllegalArgumentException("unknown team-size '" + raw
                        + "' (expected any, 2, or 2+)");
            };
        }
    }

    private final String id;
    private final GoalType type;
    private final GoalTracker tracker;
    private final Map<String, String> params;
    private final Map<String, List<String>> variants;
    private final int weight;
    private final String group;
    private final Pool pool;
    private final TeamSize teamSize;
    private final List<String> requiredBiomes;
    private final List<String> requiredStructures;
    private final String requirementPattern;
    private String displayPattern;
    private String iconPattern;

    public GoalSpec(String id, GoalType type, GoalTracker tracker,
                    String displayPattern, String iconPattern, String requirementPattern,
                    int weight, String group, Map<String, String> params,
                    Map<String, List<String>> variants) {
        this(id, type, tracker, displayPattern, iconPattern, requirementPattern,
                weight, group, Pool.RANDOM, TeamSize.ANY, List.of(), List.of(), params, variants);
    }

    public GoalSpec(String id, GoalType type, GoalTracker tracker,
                    String displayPattern, String iconPattern, String requirementPattern,
                    int weight, String group, Pool pool, TeamSize teamSize,
                    List<String> requiredBiomes, List<String> requiredStructures,
                    Map<String, String> params, Map<String, List<String>> variants) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.displayPattern = displayPattern;
        this.iconPattern = iconPattern;
        this.requirementPattern = requirementPattern;
        this.weight = weight;
        this.group = group;
        this.pool = pool == null ? Pool.RANDOM : pool;
        this.teamSize = teamSize == null ? TeamSize.ANY : teamSize;
        this.requiredBiomes = requiredBiomes == null ? List.of() : List.copyOf(requiredBiomes);
        this.requiredStructures = requiredStructures == null ? List.of() : List.copyOf(requiredStructures);
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        this.variants = copyVariants(variants);
    }

    /** Fills the generated display/icon and validates placeholder references. */
    public void finishDefaults() {
        if (displayPattern == null || displayPattern.isBlank()) {
            displayPattern = tracker.defaultDisplay(this);
        }
        if (iconPattern == null || iconPattern.isBlank()) {
            iconPattern = tracker.defaultIcon(this);
        }
        validatePlaceholders();
        validateMaterialPatternValue(iconPattern, "icon");
    }

    public String id() {
        return id;
    }

    public GoalType type() {
        return type;
    }

    public GoalTracker tracker() {
        return tracker;
    }

    public GoalScope scope() {
        return tracker.scope();
    }

    public int weight() {
        return weight;
    }

    public String group() {
        return group;
    }

    public Pool pool() {
        return pool;
    }

    public boolean inRandomPool() {
        return pool == Pool.RANDOM;
    }

    public TeamSize teamSize() {
        return teamSize;
    }

    public List<String> requiredBiomes() {
        return requiredBiomes;
    }

    public List<String> requiredStructures() {
        return requiredStructures;
    }

    public String displayPattern() {
        return displayPattern;
    }

    public String iconPattern() {
        return iconPattern;
    }

    public String requirementPattern() {
        return requirementPattern;
    }

    public Map<String, String> params() {
        return params;
    }

    public Map<String, List<String>> variants() {
        return variants;
    }

    public String param(String key) {
        return params.get(key);
    }

    /** A comma-separated list param, e.g. the advancement keys of an any-of goal. */
    public List<String> listParam(String key) {
        String raw = params.get(key);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    public int intParam(String key, int fallback) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public int count() {
        return Math.max(1, intParam("count", 1));
    }

    public int remainingPercent() {
        return Math.max(1, Math.min(100, intParam("remaining-percent", 50)));
    }

    public String itemPattern() {
        return param("item");
    }

    public String entityPattern() {
        return param("entity");
    }

    public String biomePattern() {
        return param("biome");
    }

    /**
     * Validates a param that names a material or a {@code {variant}} material pattern, e.g.
     * {@code "{color}_WOOL"}. Unknown items and unknown placeholders are reported with the field
     * name so goals.yml errors point at the offending key.
     */
    public void validateMaterialPattern(String key) {
        String raw = param(key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing '" + key + "'");
        }
        validateMaterialPatternValue(raw, key);
    }

    private void validateMaterialPatternValue(String pattern, String field) {
        List<String> keys = placeholderKeys(pattern);
        if (keys.isEmpty()) {
            GoalSupport.material(pattern);
            return;
        }
        for (String key : keys) {
            List<String> values = variants.get(key);
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("field '" + field + "' uses {" + key
                        + "} but no variants are defined for it");
            }
        }
        for (Map<String, String> combination : combinations(keys)) {
            String resolved = substitute(pattern, combination).toUpperCase(java.util.Locale.ROOT);
            if (GoalSupport.materialOrNull(resolved) == null) {
                throw new IllegalArgumentException("field '" + field + "' resolves to unknown item '"
                        + resolved + "'");
            }
        }
    }

    private void validatePlaceholders() {
        for (String key : placeholderKeys(displayPattern)) {
            requireVariant(key, "display");
        }
        for (String key : placeholderKeys(requirementPattern)) {
            requireVariant(key, "requirement");
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            for (String key : placeholderKeys(entry.getValue())) {
                requireVariant(key, entry.getKey());
            }
        }
    }

    private void requireVariant(String key, String field) {
        List<String> values = variants.get(key);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("field '" + field + "' uses {" + key
                    + "} but no variants are defined for it");
        }
    }

    private List<Map<String, String>> combinations(List<String> keys) {
        List<Map<String, String>> result = new ArrayList<>();
        result.add(new LinkedHashMap<>());
        for (String key : keys) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> partial : result) {
                for (String value : variants.get(key)) {
                    Map<String, String> copy = new LinkedHashMap<>(partial);
                    copy.put(key, value);
                    next.add(copy);
                }
            }
            result = next;
        }
        return result;
    }

    private static List<String> placeholderKeys(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(pattern);
        while (matcher.find()) {
            if (!keys.contains(matcher.group(1))) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    public static String substitute(String pattern, Map<String, String> data) {
        if (pattern == null || data.isEmpty()) {
            return pattern;
        }
        String out = pattern;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }

    /** Resolves this template into a board cell with concrete variant values. */
    public ResolvedGoal resolve(Map<String, String> data) {
        return new ResolvedGoal(this, data);
    }

    private static Map<String, List<String>> copyVariants(Map<String, List<String>> variants) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : variants.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
