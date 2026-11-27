package dev.tlockout.goal.tracker;

import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Eat a number of distinct food types; the set is shared by the whole side. */
public final class UniqueFoodsGoalTracker implements GoalTracker {

    public static final String FOOD_SET = "FOODS";

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
        return "Eat " + spec.count() + " Unique Food";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.APPLE.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1)) + " unique foods";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return state.playerSetSize(player.getUniqueId(), FOOD_SET);
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        return Math.min(state.sideSetSize(side.id(), FOOD_SET), target(goal));
    }
}
