package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Kill another player a number of times. */
public final class PlayerKillGoalTracker implements GoalTracker {

    public static final String STAT_KEY = "PLAYER_KILL";

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        // Only 'count' is meaningful; the loader clamps it to at least 1.
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return spec.count() == 1 ? "Kill a player" : "Kill " + spec.count() + " players";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.IRON_SWORD.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        int count = Math.max(1, goal.intParam("count", 1));
        return count == 1 ? "1 player" : count + " players";
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
