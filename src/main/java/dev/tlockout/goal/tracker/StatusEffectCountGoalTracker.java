package dev.tlockout.goal.tracker;

import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Have a number of status effects at the same time. The side's progress is the best single
 * player's simultaneous count, not a sum across members.
 */
public final class StatusEffectCountGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.TEAM;
    }

    @Override
    public void validate(GoalSpec spec) {
        // Only 'count' is meaningful.
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Get " + spec.count() + " Status Effects at once";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.POTION.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Have " + Math.max(1, goal.intParam("count", 1))
                + " status effects at the same time";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return player.getActivePotionEffects().size();
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
}
