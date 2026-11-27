package dev.tlockout.goal.tracker;

import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Breed N distinct animal species across the team (shared side-wide set). */
public final class BreedUniqueGoalTracker implements GoalTracker {

    public static final String BRED_SET = "ANIMALS_BRED";

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
        return "Breed " + spec.count() + " Unique Animals";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.WHEAT.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Breed " + target(goal) + " different animal species as a team";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 4));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return Math.min(state.playerSetSize(player.getUniqueId(), BRED_SET), target(goal));
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        return Math.min(state.sideSetSize(side.id(), BRED_SET), target(goal));
    }
}
