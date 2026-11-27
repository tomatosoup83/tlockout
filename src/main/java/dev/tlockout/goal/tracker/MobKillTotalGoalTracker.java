package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Kill any N mobs across the team during the round (e.g. Kill 100 mobs). */
public final class MobKillTotalGoalTracker implements GoalTracker {

    public static final String STAT_KEY = "MOB_KILL_ANY";

    @Override
    public GoalScope scope() {
        return GoalScope.TEAM;
    }

    @Override
    public void validate(GoalSpec spec) {
        if (spec.intParam("count", 0) <= 0) {
            throw new IllegalArgumentException("'count' must be positive");
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Kill " + spec.count() + " mobs";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.IRON_SWORD.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Kill " + target(goal) + " mobs as a team";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1));
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        return STAT_KEY;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return state.playerStat(player.getUniqueId(), STAT_KEY);
    }
}
