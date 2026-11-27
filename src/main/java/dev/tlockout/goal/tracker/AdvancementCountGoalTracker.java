package dev.tlockout.goal.tracker;

import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Earn a number of distinct advancements; recipe unlocks are not counted. */
public final class AdvancementCountGoalTracker implements GoalTracker {

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
        return "Get " + spec.count() + " Advancements";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.BOOK.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1)) + " advancements (recipes excluded)";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return state.playerSetSize(player.getUniqueId(), AdvancementGoalTracker.ADVANCEMENT_SET);
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        return Math.min(state.sideSetSize(side.id(), AdvancementGoalTracker.ADVANCEMENT_SET),
                target(goal));
    }
}
