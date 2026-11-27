package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

/** Accumulate a vanilla statistic during the round (currently used for sprint distance). */
public final class StatisticGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.TEAM;
    }

    @Override
    public void validate(GoalSpec spec) {
        statistic(spec.param("statistic"));
        if (spec.intParam("divisor", 1) < 1) {
            throw new IllegalArgumentException("'divisor' must be at least 1");
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Reach " + Math.max(1, spec.intParam("amount", 1)) + " "
                + Names.pretty(statistic(spec.param("statistic")).name());
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.GOLDEN_BOOTS.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Reach " + Math.max(1, goal.intParam("amount", 1)) + " "
                + Names.pretty(statistic(goal.param("statistic")).name());
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("amount", 1));
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        return "STAT:" + statistic(goal.param("statistic")).name();
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return state.playerStat(player.getUniqueId(), statKey(goal))
                / Math.max(1, goal.intParam("divisor", 1));
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        String key = statKey(goal);
        int total = 0;
        for (UUID memberId : side.members()) {
            total += state.playerStat(memberId, key);
        }
        return Math.min(total / Math.max(1, goal.intParam("divisor", 1)), target(goal));
    }

    private static Statistic statistic(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'statistic'");
        }
        try {
            return Statistic.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown statistic '" + raw + "'");
        }
    }
}
