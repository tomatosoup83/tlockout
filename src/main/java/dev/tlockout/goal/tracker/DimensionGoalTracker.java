package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Be inside a specific dimension: the Nether, the End or the overworld. */
public final class DimensionGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        environment(spec.param("dimension"));
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Enter " + displayName(spec.param("dimension"));
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return switch (environment(spec.param("dimension"))) {
            case NETHER -> Material.OBSIDIAN.name();
            case THE_END -> Material.ENDER_EYE.name();
            default -> Material.GRASS_BLOCK.name();
        };
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Be in " + requirementName(goal.param("dimension"));
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        World.Environment wanted = environmentOrNull(goal.param("dimension"));
        return wanted != null && player.getWorld().getEnvironment() == wanted ? 1 : 0;
    }

    private static World.Environment environment(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'dimension'");
        }
        World.Environment environment = environmentOrNull(raw);
        if (environment == null) {
            throw new IllegalArgumentException("unknown dimension '" + raw
                    + "' (use NETHER, END or OVERWORLD)");
        }
        return environment;
    }

    private static World.Environment environmentOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "NETHER" -> World.Environment.NETHER;
            case "END", "THE_END" -> World.Environment.THE_END;
            case "OVERWORLD", "NORMAL" -> World.Environment.NORMAL;
            default -> null;
        };
    }

    private static String displayName(String raw) {
        World.Environment environment = environment(raw);
        return switch (environment) {
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> "Overworld";
        };
    }

    private static String requirementName(String raw) {
        World.Environment environment = environment(raw);
        return switch (environment) {
            case NETHER -> "the Nether";
            case THE_END -> "the End";
            default -> "the Overworld";
        };
    }
}
