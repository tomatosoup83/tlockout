package dev.tlockout.goal;

import dev.tlockout.Names;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/** One concrete board cell: a template plus the variant values chosen for it. */
public record ResolvedGoal(GoalSpec spec, Map<String, String> data) {

    public ResolvedGoal {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public String display() {
        return GoalSpec.substitute(spec.displayPattern(), prettifiedData());
    }

    public String requirement() {
        String pattern = spec.requirementPattern();
        if (pattern != null && !pattern.isBlank()) {
            return GoalSpec.substitute(pattern, prettifiedData());
        }
        return spec.tracker().requirement(this);
    }

    public Material icon() {
        String pattern = GoalSpec.substitute(spec.iconPattern(), data);
        Material material = GoalSupport.materialOrNull(pattern.toUpperCase(Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }

    public int target() {
        return spec.tracker().target(this);
    }

    /** A type-specific param with variant placeholders resolved. */
    public String param(String key) {
        return GoalSpec.substitute(spec.param(key), data);
    }

    /** A type-specific param with variant placeholders resolved and prettified for display. */
    public String prettyParam(String key) {
        String raw = spec.param(key);
        if (raw == null) {
            return null;
        }
        return GoalSpec.substitute(raw, prettifiedData());
    }

    public int intParam(String key, int fallback) {
        String value = param(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public Material materialParam(String key) {
        String value = param(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return GoalSupport.materialOrNull(value.toUpperCase(Locale.ROOT));
    }

    public EntityType entityParam(String key) {
        String value = param(key);
        return value == null ? null : GoalSupport.entityOrNull(value);
    }

    private Map<String, String> prettifiedData() {
        Map<String, String> pretty = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            pretty.put(entry.getKey(), Names.pretty(entry.getValue()));
        }
        return pretty;
    }
}
