package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Put wolf armor onto a tamed wolf. */
public final class WolfArmorGoalTracker implements GoalTracker {

    public static final String STAT_KEY = "WOLF_ARMOR";

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        // No required params.
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Put Wolf Armor on Wolf";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.WOLF_ARMOR.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Equip wolf armor on a tamed wolf";
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
        return Math.min(1, state.playerStat(player.getUniqueId(), STAT_KEY));
    }
}
