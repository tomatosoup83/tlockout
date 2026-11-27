package dev.tlockout.goal.tracker;

import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Hold either all items in a configured set or at least {@code count} unique items from the set.
 * Progress is the union of matching materials held across all online teammates (offline players'
 * inventories are never scanned).
 */
public final class ItemSetGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.TEAM;
    }

    @Override
    public void validate(GoalSpec spec) {
        List<String> items = spec.listParam("items");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("missing 'items' list");
        }
        Set<Material> unique = EnumSet.noneOf(Material.class);
        for (String raw : items) {
            unique.add(GoalSupport.material(raw));
        }
        String countRaw = spec.param("count");
        if (countRaw != null && !countRaw.isBlank()) {
            int count = spec.intParam("count", -1);
            if (count < 1 || count > unique.size()) {
                throw new IllegalArgumentException("'count' must be between 1 and " + unique.size());
            }
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        int needed = requiredCount(spec);
        int total = spec.listParam("items").size();
        if (needed >= total) {
            return "Obtain all " + total + " items";
        }
        return "Obtain " + needed + " unique items";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        List<String> items = spec.listParam("items");
        return items.isEmpty() ? Material.CHEST.name() : items.get(0);
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        int needed = target(goal);
        int total = allowedMaterials(goal).size();
        if (needed >= total) {
            return "Hold all " + needed + " items in your team's inventories";
        }
        return "Hold " + needed + " unique items from the set";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return requiredCount(goal.spec());
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        Set<Material> allowed = allowedMaterials(goal);
        Set<Material> held = EnumSet.noneOf(Material.class);
        collectHeld(player, allowed, held);
        return Math.min(held.size(), target(goal));
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        Set<Material> allowed = allowedMaterials(goal);
        Set<Material> union = EnumSet.noneOf(Material.class);
        for (UUID memberId : side.members()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null) {
                collectHeld(player, allowed, union);
            }
        }
        return Math.min(union.size(), target(goal));
    }

    private static void collectHeld(Player player, Set<Material> allowed, Set<Material> dest) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && allowed.contains(stack.getType())) {
                dest.add(stack.getType());
            }
        }
    }

    private static Set<Material> allowedMaterials(ResolvedGoal goal) {
        Set<Material> allowed = EnumSet.noneOf(Material.class);
        for (String raw : goal.spec().listParam("items")) {
            Material material = GoalSupport.materialOrNull(raw);
            if (material != null) {
                allowed.add(material);
            }
        }
        return allowed;
    }

    private static int requiredCount(GoalSpec spec) {
        int total = Math.max(1, spec.listParam("items").size());
        int count = spec.intParam("count", total);
        return Math.max(1, Math.min(count, total));
    }
}
