package dev.tlockout.goal.tracker;

import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Kill a total number of mobs belonging to a configured group (e.g. 20 Arthropods or 30 Undead
 * Mobs). Sums the existing per-entity kill counters across the team, including offline members.
 */
public final class MobKillGroupGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.TEAM;
    }

    @Override
    public void validate(GoalSpec spec) {
        List<String> entities = spec.listParam("entities");
        if (entities.isEmpty()) {
            throw new IllegalArgumentException("missing 'entities' list");
        }
        for (String raw : entities) {
            GoalSupport.entity(raw);
        }
        if (spec.intParam("count", 0) <= 0) {
            throw new IllegalArgumentException("'count' must be positive");
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Kill " + spec.count() + " Mobs";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        List<String> entities = spec.listParam("entities");
        if (!entities.isEmpty()) {
            return GoalSupport.spawnEgg(GoalSupport.entity(entities.get(0))).name();
        }
        return Material.IRON_SWORD.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Kill " + target(goal) + " matching mobs as a team";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return Math.min(memberKills(player.getUniqueId(), goal, state), target(goal));
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        int total = 0;
        for (UUID memberId : side.members()) {
            total += memberKills(memberId, goal, state);
        }
        return Math.min(total, target(goal));
    }

    private static int memberKills(UUID playerId, ResolvedGoal goal, TrackingState state) {
        Set<EntityType> unique = new LinkedHashSet<>();
        for (String raw : goal.spec().listParam("entities")) {
            EntityType type = GoalSupport.entityOrNull(raw);
            if (type != null) {
                unique.add(type);
            }
        }
        int total = 0;
        for (EntityType type : unique) {
            total += state.playerStat(playerId, TrackingState.mobKey(type));
        }
        return total;
    }
}
