package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Drink milk while at least one status effect is active. */
public final class MilkEffectGoalTracker implements GoalTracker {

    public static final String STAT_KEY = "MILK_EFFECT";

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
        return "Remove Effect using Milk Bucket";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.MILK_BUCKET.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Drink milk while you have a status effect";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
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
