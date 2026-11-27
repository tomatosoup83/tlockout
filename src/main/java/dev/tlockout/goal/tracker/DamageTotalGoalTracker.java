package dev.tlockout.goal.tracker;

import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Accumulate damage taken or dealt, as a team-wide total. */
public final class DamageTotalGoalTracker implements GoalTracker {

    private enum Mode {
        TAKEN,
        DEALT
    }

    @Override
    public GoalScope scope() {
        return GoalScope.TEAM;
    }

    @Override
    public void validate(GoalSpec spec) {
        mode(spec.param("mode"));
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        int amount = Math.max(1, spec.intParam("amount", 200));
        return (mode(spec.param("mode")) == Mode.TAKEN ? "Take " : "Deal ") + amount + " damage";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return mode(spec.param("mode")) == Mode.TAKEN
                ? Material.SHIELD.name() : Material.IRON_SWORD.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        int amount = Math.max(1, goal.intParam("amount", 200));
        return (mode(goal.param("mode")) == Mode.TAKEN ? "Take " : "Deal ")
                + amount + " damage";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("amount", 200));
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        return mode(goal.param("mode")) == Mode.TAKEN ? "DAMAGE_TAKEN" : "DAMAGE_DEALT";
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return (int) Math.floor(state.playerDouble(player.getUniqueId(), statKey(goal)));
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        String key = statKey(goal);
        double total = 0d;
        for (UUID memberId : side.members()) {
            total += state.playerDouble(memberId, key);
        }
        return Math.min((int) Math.floor(total), target(goal));
    }

    private static Mode mode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'mode'");
        }
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown damage mode '" + raw
                    + "' (use TAKEN or DEALT)");
        }
    }
}
