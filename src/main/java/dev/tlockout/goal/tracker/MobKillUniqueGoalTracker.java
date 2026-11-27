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
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Kill unique mob types across the team. Supports two forms:
 * <ul>
 *   <li>Without {@code entities}: kill {@code count} unique hostile ({@link org.bukkit.entity.Enemy})
 *       mob types (shared side-wide set).</li>
 *   <li>With {@code entities}: kill all (or {@code count}) distinct mob types from the configured
 *       list, such as all 6 raid mobs (shared side-wide set).</li>
 * </ul>
 */
public final class MobKillUniqueGoalTracker implements GoalTracker {

    public static final String HOSTILE_SET = "HOSTILE_MOBS_KILLED";
    public static final String KILLED_MOB_SET = "MOBS_KILLED";

    @Override
    public GoalScope scope() {
        return GoalScope.TEAM;
    }

    @Override
    public void validate(GoalSpec spec) {
        List<String> entities = spec.listParam("entities");
        if (entities.isEmpty()) {
            if (spec.intParam("count", 0) <= 0) {
                throw new IllegalArgumentException("'count' must be positive");
            }
            return;
        }
        Set<EntityType> unique = new LinkedHashSet<>();
        for (String raw : entities) {
            unique.add(GoalSupport.entity(raw));
        }
        String countRaw = spec.param("count");
        if (countRaw != null && !countRaw.isBlank()) {
            int count = spec.intParam("count", -1);
            if (count < 1 || count > unique.size()) {
                throw new IllegalArgumentException("'count' must be between 1 and " + unique.size());
            }
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        List<String> entities = spec.listParam("entities");
        if (entities.isEmpty()) {
            return "Kill " + spec.count() + " Unique Hostile Mobs";
        }
        return "Kill all " + requiredCount(spec) + " Mobs";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        List<String> entities = spec.listParam("entities");
        if (!entities.isEmpty()) {
            return GoalSupport.spawnEgg(GoalSupport.entity(entities.get(0))).name();
        }
        return Material.DIAMOND_SWORD.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        List<String> entities = goal.spec().listParam("entities");
        if (entities.isEmpty()) {
            return "Kill " + target(goal) + " different hostile mob types";
        }
        return "Kill " + target(goal) + " required mob types as a team";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return requiredCount(goal.spec());
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        List<String> entities = goal.spec().listParam("entities");
        if (entities.isEmpty()) {
            return Math.min(state.playerSetSize(player.getUniqueId(), HOSTILE_SET), target(goal));
        }
        Set<String> killed = state.playerSet(player.getUniqueId(), KILLED_MOB_SET);
        return Math.min(countMatching(killed, entities), target(goal));
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        List<String> entities = goal.spec().listParam("entities");
        if (entities.isEmpty()) {
            return Math.min(state.sideSetSize(side.id(), HOSTILE_SET), target(goal));
        }
        Set<String> killed = state.sideSet(side.id(), KILLED_MOB_SET);
        return Math.min(countMatching(killed, entities), target(goal));
    }

    private static int countMatching(Set<String> killed, List<String> entities) {
        Set<String> matched = new LinkedHashSet<>();
        for (String raw : entities) {
            EntityType type = GoalSupport.entityOrNull(raw);
            if (type == null) {
                continue;
            }
            String key = type.getKey().toString().toLowerCase(Locale.ROOT);
            if (killed.contains(key)) {
                matched.add(key);
            }
        }
        return matched.size();
    }

    private static int requiredCount(GoalSpec spec) {
        List<String> entities = spec.listParam("entities");
        if (entities.isEmpty()) {
            return Math.max(1, spec.intParam("count", 1));
        }
        int total = entities.size();
        int count = spec.intParam("count", total);
        return Math.max(1, Math.min(count, total));
    }
}
