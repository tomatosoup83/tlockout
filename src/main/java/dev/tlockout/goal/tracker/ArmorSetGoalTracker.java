package dev.tlockout.goal.tracker;

import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * Wear a full armor set at once (best single player on the team, not a sum across teammates).
 * Supports three modes:
 * <ul>
 *   <li>{@code ITEMS} (default): wear every listed armor material.</li>
 *   <li>{@code UNIQUE_DYED_LEATHER}: wear all four leather armor pieces dyed in four different
 *       non-default colours.</li>
 *   <li>{@code ENCHANTED}: wear an enchanted item in all four armor slots.</li>
 * </ul>
 */
public final class ArmorSetGoalTracker implements GoalTracker {

    public enum Mode {
        ITEMS,
        UNIQUE_DYED_LEATHER,
        ENCHANTED
    }

    private static final Set<Material> LEATHER_SET = EnumSet.of(
            Material.LEATHER_HELMET,
            Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.LEATHER_BOOTS);

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        Mode mode = parseMode(spec.param("mode"));
        if (mode == Mode.ITEMS) {
            List<String> items = spec.listParam("items");
            if (items.isEmpty()) {
                throw new IllegalArgumentException("missing 'items' list");
            }
            for (String item : items) {
                GoalSupport.material(item);
            }
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return switch (parseMode(spec.param("mode"))) {
            case UNIQUE_DYED_LEATHER -> "Wear Full Leather Armor, in different colors";
            case ENCHANTED -> "Wear Full Enchanted Armor";
            case ITEMS -> "Wear Full Armor";
        };
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return switch (parseMode(spec.param("mode"))) {
            case UNIQUE_DYED_LEATHER -> Material.LEATHER_CHESTPLATE.name();
            case ENCHANTED -> Material.DIAMOND_CHESTPLATE.name();
            case ITEMS -> {
                List<String> items = spec.listParam("items");
                yield items.isEmpty() ? Material.IRON_CHESTPLATE.name() : items.get(0);
            }
        };
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return switch (parseMode(goal.param("mode"))) {
            case UNIQUE_DYED_LEATHER -> "Wear full leather armor dyed in 4 different colors";
            case ENCHANTED -> "Wear enchanted armor in all 4 slots";
            case ITEMS -> "Wear all " + target(goal) + " required armor pieces at once";
        };
    }

    @Override
    public int target(ResolvedGoal goal) {
        Mode mode = parseMode(goal.param("mode"));
        if (mode == Mode.ITEMS) {
            return Math.max(1, goal.spec().listParam("items").size());
        }
        return 4;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        return switch (parseMode(goal.param("mode"))) {
            case ITEMS -> countMatchingItems(armor, goal.spec().listParam("items"));
            case UNIQUE_DYED_LEATHER -> countUniqueDyedLeather(armor);
            case ENCHANTED -> countEnchantedSlots(armor);
        };
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        int best = 0;
        for (UUID memberId : side.members()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null) {
                best = Math.max(best, progress(player, goal, state));
            }
        }
        return Math.min(best, target(goal));
    }

    private static int countMatchingItems(ItemStack[] armor, List<String> rawItems) {
        Set<Material> required = EnumSet.noneOf(Material.class);
        for (String raw : rawItems) {
            Material material = GoalSupport.materialOrNull(raw);
            if (material != null) {
                required.add(material);
            }
        }
        Set<Material> worn = EnumSet.noneOf(Material.class);
        for (ItemStack stack : armor) {
            if (stack != null && required.contains(stack.getType())) {
                worn.add(stack.getType());
            }
        }
        return worn.size();
    }

    private static int countUniqueDyedLeather(ItemStack[] armor) {
        Color defaultColor = Bukkit.getItemFactory().getDefaultLeatherColor();
        Set<Material> seenPieces = EnumSet.noneOf(Material.class);
        Set<Integer> colors = new HashSet<>();
        for (ItemStack stack : armor) {
            if (stack == null || !LEATHER_SET.contains(stack.getType())) {
                continue;
            }
            if (!seenPieces.add(stack.getType())) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            if (meta instanceof LeatherArmorMeta leather && leather.isDyed()) {
                Color color = leather.getColor();
                if (!color.equals(defaultColor)) {
                    colors.add(color.asRGB());
                }
            }
        }
        return colors.size();
    }

    private static int countEnchantedSlots(ItemStack[] armor) {
        int count = 0;
        for (ItemStack stack : armor) {
            if (stack != null && !stack.getType().isAir()) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null && meta.hasEnchants()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.ITEMS;
        }
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown armor_set mode '" + raw + "'");
        }
    }
}
