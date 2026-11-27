package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionType;

/**
 * Brew a matching potion type (or any lingering potion) and take it from a brewing stand.
 */
public final class BrewPotionGoalTracker implements GoalTracker {

    public static final String LINGERING_KEY = "BREW:LINGERING";

    public static String potionKey(PotionType type) {
        return "BREW:TYPE:" + type.name();
    }

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        if (isLingering(spec)) {
            return;
        }
        List<String> raw = spec.listParam("potions");
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("missing 'potions' list or 'mode: LINGERING'");
        }
        for (String entry : raw) {
            parsePotion(entry);
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        if (isLingering(spec)) {
            return "Brew a Lingering Potion";
        }
        List<PotionType> types = parsePotions(spec);
        return "Brew a Potion of " + Names.pretty(types.get(0).getKey().getKey());
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return isLingering(spec) ? Material.LINGERING_POTION.name() : Material.POTION.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        if (isLingering(goal.spec())) {
            return "Take a brewed Lingering Potion from a brewing stand";
        }
        List<PotionType> types = parsePotions(goal.spec());
        return "Brew and collect a Potion of " + Names.pretty(types.get(0).getKey().getKey());
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        return isLingering(goal.spec()) ? LINGERING_KEY : null;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return progressFor(player.getUniqueId(), goal, state);
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        for (UUID memberId : side.members()) {
            if (progressFor(memberId, goal, state) > 0) {
                return 1;
            }
        }
        return 0;
    }

    /** First configured potion type for tinting the board GUI icon, or null for lingering. */
    public static PotionType iconPotion(ResolvedGoal goal) {
        if (isLingering(goal.spec())) {
            return null;
        }
        List<PotionType> types = parsePotions(goal.spec());
        return types.isEmpty() ? null : types.get(0);
    }

    private static int progressFor(UUID playerId, ResolvedGoal goal, TrackingState state) {
        if (isLingering(goal.spec())) {
            return state.playerStat(playerId, LINGERING_KEY) > 0 ? 1 : 0;
        }
        for (PotionType type : parsePotions(goal.spec())) {
            if (state.playerStat(playerId, potionKey(type)) > 0) {
                return 1;
            }
        }
        return 0;
    }

    private static boolean isLingering(GoalSpec spec) {
        String mode = spec.param("mode");
        if (mode == null || mode.isBlank()) {
            return false;
        }
        if ("LINGERING".equalsIgnoreCase(mode.trim())) {
            return true;
        }
        throw new IllegalArgumentException("unknown brew_potion mode '" + mode + "' (only LINGERING is supported)");
    }

    private static List<PotionType> parsePotions(GoalSpec spec) {
        List<String> raw = spec.listParam("potions");
        List<PotionType> result = new ArrayList<>(raw.size());
        for (String entry : raw) {
            result.add(parsePotion(entry));
        }
        return result;
    }

    private static PotionType parsePotion(String raw) {
        try {
            return PotionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown potion type '" + raw + "'");
        }
    }
}
