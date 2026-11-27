package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/** Receive a specific status effect. */
public final class StatusEffectGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        effectKey(spec.param("effect"));
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Get " + Names.pretty(effectPath(spec.param("effect")));
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.POTION.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Get " + Names.pretty(effectPath(goal.param("effect")));
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        return "EFFECT:" + effectKey(goal.param("effect"));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        String key = effectKey(goal.param("effect"));
        for (PotionEffect active : player.getActivePotionEffects()) {
            if (active.getType().getKey().toString().equals(key)) {
                return 1;
            }
        }
        return state.playerStat(player.getUniqueId(), "EFFECT:" + key);
    }

    private static String effectKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'effect'");
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (!text.contains(":")) {
            text = "minecraft:" + text;
        }
        NamespacedKey key;
        try {
            key = NamespacedKey.fromString(text);
        } catch (RuntimeException ex) {
            key = null;
        }
        if (key == null) {
            throw new IllegalArgumentException("invalid status effect '" + raw + "'");
        }
        return key.toString();
    }

    private static String effectPath(String raw) {
        String key = effectKey(raw);
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }
}
