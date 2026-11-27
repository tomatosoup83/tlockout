package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;

/** Be inside a specific biome (or any biome from a configured list such as Badlands variants). */
public final class BiomeGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        if (biomesOf(spec).isEmpty()) {
            throw new IllegalArgumentException("missing 'biome' or 'biomes'");
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Visit the " + Names.pretty(biomesOf(spec).get(0)) + " biome";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return GoalSupport.biomeIcon(biomesOf(spec).get(0)).name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return Names.pretty(biomesOf(goal.spec()).get(0)) + " biome";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        var key = player.getLocation().getBlock().getBiome().getKey();
        if (key == null) {
            return 0;
        }
        String actual = key.getKey();
        for (String wanted : biomesOf(goal.spec())) {
            if (actual.equalsIgnoreCase(wanted)) {
                return 1;
            }
        }
        return 0;
    }

    private static List<String> biomesOf(GoalSpec spec) {
        List<String> list = spec.listParam("biomes");
        if (!list.isEmpty()) {
            List<String> normalized = new ArrayList<>(list.size());
            for (String raw : list) {
                normalized.add(GoalSupport.biomeKey(raw));
            }
            return normalized;
        }
        String single = spec.biomePattern();
        if (single == null || single.isBlank()) {
            return List.of();
        }
        return List.of(GoalSupport.biomeKey(single));
    }
}
