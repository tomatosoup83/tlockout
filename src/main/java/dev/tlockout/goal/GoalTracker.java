package dev.tlockout.goal;

import dev.tlockout.Side;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * One detection family. Implementations own the config validation, generated text and progress
 * calculation for a {@link GoalType}; new goal types are added by adding a tracker and registering
 * it in {@link GoalTrackers}, not by extending a switch.
 */
public interface GoalTracker {

    GoalScope scope();

    /** Validates the template's params and throws with a field-level message when they are wrong. */
    void validate(GoalSpec spec);

    /** Display name used when the template omits {@code display}. May contain variant placeholders. */
    String defaultDisplay(GoalSpec spec);

    /** Material name (possibly a pattern) used when the template omits {@code icon}. */
    String defaultIcon(GoalSpec spec);

    /** Requirement line shown in the tile lore. */
    String requirement(ResolvedGoal goal);

    /** Progress needed to claim the goal. */
    int target(ResolvedGoal goal);

    /**
     * Counter key for event-driven goals, or null for state-driven ones.
     * Offline members contribute event counters but not inventory/biome state.
     */
    default String statKey(ResolvedGoal goal) {
        return null;
    }

    /**
     * True when this goal is triggered by the opposing side's actions rather than the claiming
     * side's own (e.g. "Opponent dies"). Requires exactly two sides; progress is evaluated against
     * the opposing side and the claim is awarded to the beneficiary side.
     */
    default boolean opponentGoal() {
        return false;
    }

    /** This player's progress toward the goal. */
    int progress(Player player, ResolvedGoal goal, TrackingState state);

    /**
     * The side's progress. The default sums each online member's progress plus offline members'
     * event counters. Trackers backed by team-wide sets or "at once" state override this.
     */
    default int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        String statKey = statKey(goal);
        int total = 0;
        for (UUID memberId : side.members()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) {
                total += progress(online, goal, state);
            } else if (statKey != null) {
                total += state.playerStat(memberId, statKey);
            }
        }
        return Math.min(total, target(goal));
    }
}
