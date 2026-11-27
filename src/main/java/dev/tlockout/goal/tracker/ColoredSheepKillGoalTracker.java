package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Kill a sheep whose wool matches a resolved {@link DyeColor}. */
public final class ColoredSheepKillGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        String raw = spec.param("color");
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'color'");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.length() > 2) {
            String key = trimmed.substring(1, trimmed.length() - 1);
            List<String> values = spec.variants().get(key);
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("field 'color' uses {" + key
                        + "} but no variants are defined for it");
            }
            for (String value : values) {
                ArmorPieceGoalTracker.parseDyeColor(value);
            }
            return;
        }
        ArmorPieceGoalTracker.parseDyeColor(trimmed);
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Kill " + Names.pretty(spec.param("color")) + " Sheep";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.SHEEP_SPAWN_EGG.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Kill a " + goal.prettyParam("color") + " Sheep";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        DyeColor color = ArmorPieceGoalTracker.parseDyeColorOrNull(goal.param("color"));
        return color == null ? null : statKeyFor(color);
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        String key = statKey(goal);
        return key == null ? 0 : Math.min(1, state.playerStat(player.getUniqueId(), key));
    }

    public static String statKeyFor(DyeColor color) {
        return "SHEEP_KILL:" + color.name();
    }
}
