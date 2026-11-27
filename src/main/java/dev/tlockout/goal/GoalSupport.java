package dev.tlockout.goal;

import dev.tlockout.Names;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;

/** Shared parsing helpers for goal params and generated icons. */
public final class GoalSupport {

    private GoalSupport() {
    }

    /** Parses a material; throws with the raw value when it is unknown or not an item. */
    public static Material material(String raw) {
        Material material = materialOrNull(raw);
        if (material == null) {
            throw new IllegalArgumentException("unknown item '" + raw + "'");
        }
        return material;
    }

    public static Material materialOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Material material;
        try {
            material = Material.matchMaterial(raw.trim());
        } catch (RuntimeException ex) {
            material = null;
        }
        return material != null && material.isItem() ? material : null;
    }

    public static EntityType entity(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'entity'");
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(key));
        if (type == null) {
            throw new IllegalArgumentException("unknown entity '" + raw + "'");
        }
        return type;
    }

    public static EntityType entityOrNull(String raw) {
        try {
            return entity(raw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Strips a namespace and normalises case, matching how biome keys are compared. */
    public static String biomeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'biome'");
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    public static Material spawnEgg(EntityType type) {
        Material egg = Material.matchMaterial(type.getKey().getKey().toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        return egg == null ? Material.NAME_TAG : egg;
    }

    public static Material biomeIcon(String biome) {
        return switch (biomeKey(biome)) {
            case "desert" -> Material.SAND;
            case "ocean", "deep_ocean" -> Material.WATER_BUCKET;
            case "swamp" -> Material.LILY_PAD;
            case "jungle" -> Material.VINE;
            case "taiga" -> Material.SPRUCE_SAPLING;
            case "badlands" -> Material.TERRACOTTA;
            case "forest" -> Material.OAK_SAPLING;
            case "savanna" -> Material.ACACIA_SAPLING;
            default -> Material.GRASS_BLOCK;
        };
    }

    public static String prettyEntity(EntityType type) {
        return Names.pretty(type.getKey().getKey());
    }
}
