package dev.tlockout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

/** Maps the 16 concrete blocks to their dye colour, so a side's text colour matches its block. */
public final class ConcreteColors {

    private static final Map<String, String> HEX = new HashMap<>();

    /** Ordered high-contrast palette used to auto-assign distinct colours in FFA mode. */
    public static final List<Material> PALETTE = List.of(
            Material.RED_CONCRETE,
            Material.BLUE_CONCRETE,
            Material.LIME_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE,
            Material.ORANGE_CONCRETE,
            Material.MAGENTA_CONCRETE,
            Material.CYAN_CONCRETE,
            Material.PURPLE_CONCRETE,
            Material.PINK_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.WHITE_CONCRETE,
            Material.BROWN_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE,
            Material.GRAY_CONCRETE,
            Material.BLACK_CONCRETE);

    private static final List<String> COLOR_NAMES;

    static {
        HEX.put("WHITE_CONCRETE", "#F9FFFE");
        HEX.put("ORANGE_CONCRETE", "#F9801D");
        HEX.put("MAGENTA_CONCRETE", "#C74EBD");
        HEX.put("LIGHT_BLUE_CONCRETE", "#3AB3DA");
        HEX.put("YELLOW_CONCRETE", "#FED83D");
        HEX.put("LIME_CONCRETE", "#80C71F");
        HEX.put("PINK_CONCRETE", "#F38BAA");
        HEX.put("GRAY_CONCRETE", "#474F52");
        HEX.put("LIGHT_GRAY_CONCRETE", "#9D9D97");
        HEX.put("CYAN_CONCRETE", "#169C9C");
        HEX.put("PURPLE_CONCRETE", "#8932B8");
        HEX.put("BLUE_CONCRETE", "#3C44AA");
        HEX.put("BROWN_CONCRETE", "#835432");
        HEX.put("GREEN_CONCRETE", "#5E7C16");
        HEX.put("RED_CONCRETE", "#B02E26");
        HEX.put("BLACK_CONCRETE", "#1D1D21");

        List<String> names = new ArrayList<>(PALETTE.size());
        for (Material material : PALETTE) {
            names.add(colorKey(material));
        }
        COLOR_NAMES = Collections.unmodifiableList(names);
    }

    private ConcreteColors() {
    }

    public static boolean isConcrete(Material material) {
        return material != null && HEX.containsKey(material.name());
    }

    public static TextColor of(Material material) {
        String hex = HEX.get(material.name());
        if (hex == null) {
            throw new IllegalArgumentException(material.name() + " is not a concrete block");
        }
        return TextColor.fromHexString(hex);
    }

    /** Short lowercase colour key for a concrete material (e.g. {@code LIGHT_BLUE_CONCRETE -> "light_blue"}). */
    public static String colorKey(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace("_concrete", "");
    }

    /** Friendly display name for a concrete material (e.g. {@code "Light Blue"}). */
    public static String colorLabel(Material material) {
        return Names.pretty(colorKey(material));
    }

    /** All 16 short colour names in palette order, used for tab completion. */
    public static List<String> colorNames() {
        return COLOR_NAMES;
    }

    /** Parses a short colour name (e.g. {@code "lime"}, {@code "light-blue"}) or full concrete material name. */
    public static Material parseConcrete(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!normalized.endsWith("_CONCRETE")) {
            normalized = normalized + "_CONCRETE";
        }
        Material material = Material.matchMaterial(normalized);
        return isConcrete(material) ? material : null;
    }
}
