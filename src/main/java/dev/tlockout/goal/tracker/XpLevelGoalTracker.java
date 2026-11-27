package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Reach an XP level. */
public final class XpLevelGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        // Only 'level' is meaningful.
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Reach XP level " + Math.max(1, spec.intParam("level", 15));
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.EXPERIENCE_BOTTLE.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Reach XP level " + Math.max(1, goal.intParam("level", 15));
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return player.getLevel() >= Math.max(1, goal.intParam("level", 15)) ? 1 : 0;
    }
}
