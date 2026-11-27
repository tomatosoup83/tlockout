package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Empty the hunger bar. */
public final class EmptyHungerGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        // No params.
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Empty Hunger Bar";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.ROTTEN_FLESH.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Get your hunger bar to zero";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return player.getFoodLevel() == 0 ? 1 : 0;
    }
}
