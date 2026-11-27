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

/** Have a number of a specific item in the inventory at once. */
public final class ItemGoalTracker implements GoalTracker {

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
        return "Collect " + spec.count() + "x " + Names.pretty(spec.itemPattern());
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return spec.itemPattern();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1)) + "x " + Names.pretty(goal.param("item"));
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        Material material = goal.materialParam("item");
        if (material == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }
}
