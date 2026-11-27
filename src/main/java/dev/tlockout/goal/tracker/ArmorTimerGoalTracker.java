package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Wear a specific item in an armor slot for a cumulative number of seconds across the round (e.g.
 * a Carved Pumpkin for 5 minutes). Ticks are accumulated per check cycle in {@link TrackingState}
 * and progress is reported in whole seconds for the best single player on the team.
 */
public final class ArmorTimerGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        spec.validateMaterialPattern("item");
        if (spec.intParam("seconds", 300) <= 0) {
            throw new IllegalArgumentException("'seconds' must be positive");
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        int seconds = Math.max(1, spec.intParam("seconds", 300));
        return "Wear " + Names.pretty(spec.itemPattern()) + " for " + formatDuration(seconds);
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return spec.itemPattern();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        int seconds = target(goal);
        return "Wear " + Names.pretty(goal.param("item")) + " for " + formatDuration(seconds) + " total";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("seconds", 300));
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        Material material = goal.materialParam("item");
        return material == null ? null : statKeyFor(material);
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        String key = statKey(goal);
        return key == null ? 0 : state.playerStat(player.getUniqueId(), key) / 20;
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        String key = statKey(goal);
        if (key == null) {
            return 0;
        }
        int best = 0;
        for (UUID memberId : side.members()) {
            best = Math.max(best, state.playerStat(memberId, key) / 20);
        }
        return Math.min(best, target(goal));
    }

    public static String statKeyFor(Material material) {
        return "ARMOR_TICKS:" + material.name();
    }

    public static boolean isWearing(Player player, Material material) {
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (stack != null && stack.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private static String formatDuration(int seconds) {
        if (seconds % 60 == 0) {
            int minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + " seconds";
    }
}
