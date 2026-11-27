package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionType;

/** Consume or drink a specific item. Water bottles are distinguished by potion type. */
public final class ConsumeItemGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        Material item = GoalSupport.material(spec.param("item"));
        if (spec.param("potion") != null) {
            if (item != Material.POTION) {
                throw new IllegalArgumentException("'potion' is only valid when 'item' is POTION");
            }
            potionType(spec.param("potion"));
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        PotionType potion = potionTypeOrNull(spec.param("potion"));
        if (potion != null) {
            return "Drink " + Names.pretty(potion.getKey().getKey());
        }
        return "Consume " + Names.pretty(spec.param("item"));
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return spec.param("item");
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        PotionType potion = potionTypeOrNull(goal.param("potion"));
        if (potion != null) {
            return "Drink a " + Names.pretty(potion.getKey().getKey()) + " potion";
        }
        return "Consume " + Names.pretty(goal.param("item"));
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        Material item = goal.materialParam("item");
        if (item == null) {
            return null;
        }
        PotionType potion = potionTypeOrNull(goal.param("potion"));
        if (potion != null) {
            return "CONSUME:POTION:" + potion.name();
        }
        return "CONSUME:" + item.name();
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        String key = statKey(goal);
        return key == null ? 0 : state.playerStat(player.getUniqueId(), key);
    }

    private static PotionType potionType(String raw) {
        try {
            return PotionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown potion '" + raw + "'");
        }
    }

    private static PotionType potionTypeOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : potionType(raw);
    }
}
