package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** An item worn down to (or below) a percentage of its maximum durability. */
public final class DurabilityGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        spec.validateMaterialPattern("item");
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return Names.pretty(spec.itemPattern()) + " worn to " + spec.remainingPercent() + "%";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return spec.itemPattern();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        int remaining = Math.max(1, Math.min(100, goal.intParam("remaining-percent", 50)));
        return Names.pretty(goal.param("item")) + " at or below "
                + remaining + "% durability";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        Material material = goal.materialParam("item");
        if (material == null) {
            return 0;
        }
        int threshold = Math.max(1, Math.min(100, goal.intParam("remaining-percent", 50)));
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != material) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof Damageable damageable)) {
                continue;
            }
            int max = stack.getType().getMaxDurability();
            if (max <= 0) {
                continue;
            }
            int remaining = max - damageable.getDamage();
            int percent = (int) Math.round(remaining * 100.0 / max);
            if (percent <= threshold) {
                return 1;
            }
        }
        return 0;
    }
}
