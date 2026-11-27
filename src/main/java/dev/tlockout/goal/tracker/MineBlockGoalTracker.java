package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Mine one or more of any matching block type (e.g. Diamond Ore or Deepslate Diamond Ore). */
public final class MineBlockGoalTracker implements GoalTracker {

    public static String statKeyFor(Material block) {
        return "MINE:" + block.name();
    }

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        List<String> raw = spec.listParam("blocks");
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("missing 'blocks' list");
        }
        for (String entry : raw) {
            Material material = GoalSupport.material(entry);
            if (!material.isBlock()) {
                throw new IllegalArgumentException("material '" + entry + "' is not a block");
            }
        }
        if (spec.intParam("count", 1) <= 0) {
            throw new IllegalArgumentException("'count' must be positive");
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        List<Material> blocks = blocksOf(spec);
        int count = Math.max(1, spec.intParam("count", 1));
        String name = Names.pretty(blocks.get(0).name());
        return count == 1 ? "Mine " + name : "Mine " + count + "x " + name;
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return blocksOf(spec).get(0).name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        List<Material> blocks = blocksOf(goal.spec());
        int count = target(goal);
        String name = Names.pretty(blocks.get(0).name());
        return count == 1 ? "Break a " + name + " block" : "Break " + count + " " + name + " blocks";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return Math.min(progressFor(player.getUniqueId(), goal, state), target(goal));
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        int total = 0;
        for (UUID memberId : side.members()) {
            total += progressFor(memberId, goal, state);
        }
        return Math.min(total, target(goal));
    }

    private static int progressFor(UUID playerId, ResolvedGoal goal, TrackingState state) {
        int total = 0;
        for (Material block : blocksOf(goal.spec())) {
            total += state.playerStat(playerId, statKeyFor(block));
        }
        return total;
    }

    private static List<Material> blocksOf(GoalSpec spec) {
        List<String> raw = spec.listParam("blocks");
        List<Material> result = new ArrayList<>(raw.size());
        for (String entry : raw) {
            result.add(GoalSupport.material(entry));
        }
        return result;
    }
}
