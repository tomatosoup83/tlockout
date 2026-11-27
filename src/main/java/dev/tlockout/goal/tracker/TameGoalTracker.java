package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** Tame a specific animal. */
public final class TameGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        GoalSupport.entity(spec.entityPattern());
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Tame a " + Names.pretty(spec.entityPattern());
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return GoalSupport.spawnEgg(GoalSupport.entity(spec.entityPattern())).name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return Names.pretty(goal.param("entity"));
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        EntityType entity = goal.entityParam("entity");
        return entity == null ? null : TrackingState.tameKey(entity);
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        String key = statKey(goal);
        return key == null ? 0 : state.playerStat(player.getUniqueId(), key);
    }

    /** Counter key used by the tame event handler. */
    public static String statKeyFor(EntityType entity) {
        return TrackingState.tameKey(entity);
    }
}
