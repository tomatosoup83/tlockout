package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * Wear a specific armor piece (or any piece from a list), optionally dyed a specific
 * {@link DyeColor}. Uses best-single-player semantics.
 */
public final class ArmorPieceGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        String single = spec.param("item");
        List<String> list = spec.listParam("items");
        if (single != null && !single.isBlank()) {
            spec.validateMaterialPattern("item");
        } else if (!list.isEmpty()) {
            for (String item : list) {
                GoalSupport.material(item);
            }
        } else {
            throw new IllegalArgumentException("must specify 'item' or 'items'");
        }

        String color = spec.param("color");
        if (color != null && !color.isBlank()) {
            validateColorPattern(spec, color);
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        String single = spec.param("item");
        String color = spec.param("color");
        if (single != null && !single.isBlank()) {
            if (color != null && !color.isBlank()) {
                return "Wear " + Names.pretty(color) + " " + Names.pretty(single);
            }
            return "Wear " + Names.pretty(single);
        }
        return "Wear an Armor Piece";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        String single = spec.param("item");
        if (single != null && !single.isBlank()) {
            return single;
        }
        List<String> list = spec.listParam("items");
        return list.isEmpty() ? Material.CHAINMAIL_CHESTPLATE.name() : list.get(0);
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        String single = goal.param("item");
        String color = goal.prettyParam("color");
        if (single != null && !single.isBlank()) {
            if (color != null && !color.isBlank()) {
                return "Wear a " + color + " " + Names.pretty(single);
            }
            return "Wear a " + Names.pretty(single);
        }
        return "Wear any matching armor piece";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        Set<Material> allowed = allowedMaterials(goal);
        if (allowed.isEmpty()) {
            return 0;
        }
        String colorRaw = goal.param("color");
        DyeColor requiredColor = (colorRaw == null || colorRaw.isBlank())
                ? null
                : parseDyeColorOrNull(colorRaw);
        if (colorRaw != null && !colorRaw.isBlank() && requiredColor == null) {
            return 0;
        }

        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (stack == null || !allowed.contains(stack.getType())) {
                continue;
            }
            if (requiredColor == null) {
                return 1;
            }
            ItemMeta meta = stack.getItemMeta();
            if (meta instanceof LeatherArmorMeta leather
                    && leather.isDyed()
                    && leather.getColor().equals(requiredColor.getColor())) {
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        for (UUID memberId : side.members()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && progress(player, goal, state) >= 1) {
                return 1;
            }
        }
        return 0;
    }

    private static Set<Material> allowedMaterials(ResolvedGoal goal) {
        Set<Material> allowed = EnumSet.noneOf(Material.class);
        Material single = goal.materialParam("item");
        if (single != null) {
            allowed.add(single);
            return allowed;
        }
        for (String raw : goal.spec().listParam("items")) {
            Material material = GoalSupport.materialOrNull(raw);
            if (material != null) {
                allowed.add(material);
            }
        }
        return allowed;
    }

    private static void validateColorPattern(GoalSpec spec, String pattern) {
        String trimmed = pattern.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.length() > 2) {
            String key = trimmed.substring(1, trimmed.length() - 1);
            List<String> values = spec.variants().get(key);
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("field 'color' uses {" + key
                        + "} but no variants are defined for it");
            }
            for (String value : values) {
                parseDyeColor(value);
            }
            return;
        }
        parseDyeColor(trimmed);
    }

    public static DyeColor parseDyeColor(String raw) {
        DyeColor color = parseDyeColorOrNull(raw);
        if (color == null) {
            throw new IllegalArgumentException("unknown dye color '" + raw + "'");
        }
        return color;
    }

    public static DyeColor parseDyeColorOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return DyeColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
