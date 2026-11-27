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

/** Reach a vertical position special case: height limit, bedrock or the Nether roof. */
public final class PositionGoalTracker implements GoalTracker {

    private enum Mode {
        HEIGHT_LIMIT,
        BEDROCK,
        NETHER_ROOF
    }

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        mode(spec.param("mode"));
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return switch (mode(spec.param("mode"))) {
            case HEIGHT_LIMIT -> "Reach height limit in Overworld";
            case BEDROCK -> "Reach Bedrock";
            case NETHER_ROOF -> "Get on Nether Roof";
        };
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return switch (mode(spec.param("mode"))) {
            case HEIGHT_LIMIT -> Material.ELYTRA.name();
            case BEDROCK -> Material.BEDROCK.name();
            case NETHER_ROOF -> Material.NETHERRACK.name();
        };
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return switch (mode(goal.param("mode"))) {
            case HEIGHT_LIMIT -> "Be at Y=320 or above in the Overworld";
            case BEDROCK -> "Be below Y=10 with bedrock underneath";
            case NETHER_ROOF -> "Be at Y=128 or above in the Nether";
        };
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return switch (mode(goal.param("mode"))) {
            case HEIGHT_LIMIT -> player.getWorld().getEnvironment() == World.Environment.NORMAL
                    && player.getLocation().getY() >= 320 ? 1 : 0;
            case BEDROCK -> player.getLocation().getY() < 10
                    && player.getLocation().getBlock().getRelative(0, -1, 0).getType() == Material.BEDROCK
                    ? 1 : 0;
            case NETHER_ROOF -> player.getWorld().getEnvironment() == World.Environment.NETHER
                    && player.getLocation().getY() >= 128 ? 1 : 0;
        };
    }

    private static Mode mode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'mode'");
        }
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown position mode '" + raw
                    + "' (use HEIGHT_LIMIT, BEDROCK or NETHER_ROOF)");
        }
    }
}
