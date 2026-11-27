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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/** Earn one of a set of advancements. The earned set is shared by the side. */
public final class AdvancementGoalTracker implements GoalTracker {

    public static final String ADVANCEMENT_SET = "ADVANCEMENTS";

    @Override
    public GoalScope scope() {
        return GoalScope.TEAM;
    }

    @Override
    public void validate(GoalSpec spec) {
        List<String> keys = spec.listParam("advancements");
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("missing 'advancements'");
        }
        for (String key : keys) {
            if (parseKey(key) == null) {
                throw new IllegalArgumentException("invalid advancement key '" + key + "'");
            }
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Earn an Advancement";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.BOOK.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        List<String> names = new ArrayList<>();
        for (NamespacedKey key : keys(goal)) {
            names.add(pretty(key));
        }
        return "Earn " + String.join(" or ", names);
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        for (NamespacedKey key : keys(goal)) {
            if (state.playerSetContains(player.getUniqueId(), ADVANCEMENT_SET, key.toString())) {
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        for (NamespacedKey key : keys(goal)) {
            if (state.sideSetContains(side.id(), ADVANCEMENT_SET, key.toString())) {
                return 1;
            }
        }
        return 0;
    }

    private static List<NamespacedKey> keys(ResolvedGoal goal) {
        List<NamespacedKey> keys = new ArrayList<>();
        for (String raw : goal.spec().listParam("advancements")) {
            NamespacedKey key = parseKey(raw);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static NamespacedKey parseKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (!text.contains(":")) {
            text = "minecraft:" + text;
        }
        try {
            return NamespacedKey.fromString(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String pretty(NamespacedKey key) {
        String path = key.getKey();
        int slash = path.lastIndexOf('/');
        return Names.pretty(slash >= 0 ? path.substring(slash + 1) : path);
    }
}
