package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Opponent-triggered goals: evaluated against the opposing side and claimed by the beneficiary
 * side when the opponent satisfies the trigger.
 */
public final class OpponentGoalTracker implements GoalTracker {

    public static final String DEATHS_KEY = "DEATHS";
    public static final String FALL_DAMAGE_KEY = "OPP:FALL_DAMAGE";
    public static final String CATCH_FIRE_KEY = "OPP:CATCH_FIRE";
    public static final String TOUCH_WATER_KEY = "OPP:TOUCH_WATER";
    public static final String EAT_FOOD_KEY = "OPP:EAT_FOOD";
    public static final String HIT_BY_EGG_KEY = "OPP:HIT_BY_EGG";
    public static final String HIT_BY_SNOWBALL_KEY = "OPP:HIT_BY_SNOWBALL";

    public enum Trigger {
        DEATH,
        DAMAGE_TAKEN,
        FALL_DAMAGE,
        CATCH_FIRE,
        TOUCH_WATER,
        OBTAIN_ITEM,
        EAT_FOOD,
        HIT_BY_EGG,
        HIT_BY_SNOWBALL
    }

    @Override
    public GoalScope scope() {
        return GoalScope.TEAM;
    }

    @Override
    public boolean opponentGoal() {
        return true;
    }

    @Override
    public void validate(GoalSpec spec) {
        Trigger trigger = parseTrigger(spec.param("trigger"));
        if (trigger == Trigger.DEATH && spec.intParam("count", 1) <= 0) {
            throw new IllegalArgumentException("'count' must be positive");
        }
        if (trigger == Trigger.DAMAGE_TAKEN && spec.intParam("amount", 100) <= 0) {
            throw new IllegalArgumentException("'amount' must be positive");
        }
        if (trigger == Trigger.OBTAIN_ITEM) {
            GoalSupport.material(spec.param("item"));
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return switch (parseTrigger(spec.param("trigger"))) {
            case DEATH -> {
                int count = Math.max(1, spec.intParam("count", 1));
                yield count == 1 ? "Opponent dies" : "Opponent dies " + count + " times";
            }
            case DAMAGE_TAKEN -> "Opponent Team takes " + Math.max(1, spec.intParam("amount", 100)) + " Damage";
            case FALL_DAMAGE -> "Opponent takes Fall Damage";
            case CATCH_FIRE -> "Opponent catches on Fire";
            case TOUCH_WATER -> "Opponent touches Water";
            case OBTAIN_ITEM -> "Opponent obtains " + Names.pretty(spec.param("item"));
            case EAT_FOOD -> "Opponent eats Food";
            case HIT_BY_EGG -> "Opponent hit by Egg";
            case HIT_BY_SNOWBALL -> "Opponent hit by Snowball";
        };
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return switch (parseTrigger(spec.param("trigger"))) {
            case DEATH -> spec.intParam("count", 1) > 1
                    ? Material.WITHER_SKELETON_SKULL.name()
                    : Material.SKELETON_SKULL.name();
            case DAMAGE_TAKEN -> Material.IRON_SWORD.name();
            case FALL_DAMAGE -> Material.FEATHER.name();
            case CATCH_FIRE -> Material.FLINT_AND_STEEL.name();
            case TOUCH_WATER -> Material.WATER_BUCKET.name();
            case OBTAIN_ITEM -> GoalSupport.material(spec.param("item")).name();
            case EAT_FOOD -> Material.APPLE.name();
            case HIT_BY_EGG -> Material.EGG.name();
            case HIT_BY_SNOWBALL -> Material.SNOWBALL.name();
        };
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return switch (parseTrigger(goal.param("trigger"))) {
            case DEATH -> {
                int count = target(goal);
                yield count == 1
                        ? "An opponent dies (your team claims)"
                        : "Opponents die " + count + " times total (your team claims)";
            }
            case DAMAGE_TAKEN -> "Opponents take " + target(goal) + " damage total (your team claims)";
            case FALL_DAMAGE -> "An opponent takes fall damage (your team claims)";
            case CATCH_FIRE -> "An opponent catches on fire (your team claims)";
            case TOUCH_WATER -> "An opponent touches water (your team claims)";
            case OBTAIN_ITEM -> "An opponent obtains " + Names.pretty(goal.param("item")) + " (your team claims)";
            case EAT_FOOD -> "An opponent eats food (your team claims)";
            case HIT_BY_EGG -> "Hit an opponent with an egg";
            case HIT_BY_SNOWBALL -> "Hit an opponent with a snowball";
        };
    }

    @Override
    public int target(ResolvedGoal goal) {
        return switch (parseTrigger(goal.param("trigger"))) {
            case DEATH -> Math.max(1, goal.intParam("count", 1));
            case DAMAGE_TAKEN -> Math.max(1, goal.intParam("amount", 100));
            case FALL_DAMAGE, CATCH_FIRE, TOUCH_WATER, OBTAIN_ITEM, EAT_FOOD,
                 HIT_BY_EGG, HIT_BY_SNOWBALL -> 1;
        };
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        return switch (parseTrigger(goal.param("trigger"))) {
            case DEATH -> DEATHS_KEY;
            case FALL_DAMAGE -> FALL_DAMAGE_KEY;
            case CATCH_FIRE -> CATCH_FIRE_KEY;
            case TOUCH_WATER -> TOUCH_WATER_KEY;
            case EAT_FOOD -> EAT_FOOD_KEY;
            case HIT_BY_EGG -> HIT_BY_EGG_KEY;
            case HIT_BY_SNOWBALL -> HIT_BY_SNOWBALL_KEY;
            case DAMAGE_TAKEN, OBTAIN_ITEM -> null;
        };
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        Trigger trigger = parseTrigger(goal.param("trigger"));
        if (trigger == Trigger.DAMAGE_TAKEN) {
            return (int) Math.floor(state.playerDouble(player.getUniqueId(), "DAMAGE_TAKEN"));
        }
        if (trigger == Trigger.OBTAIN_ITEM) {
            Material wanted = goal.materialParam("item");
            return wanted != null && hasItem(player, wanted) ? 1 : 0;
        }
        String key = statKey(goal);
        int value = key == null ? 0 : state.playerStat(player.getUniqueId(), key);
        return Math.min(value, target(goal));
    }

    @Override
    public int teamProgress(Side opponentSide, ResolvedGoal goal, TrackingState state) {
        Trigger trigger = parseTrigger(goal.param("trigger"));
        if (trigger == Trigger.DAMAGE_TAKEN) {
            double total = 0d;
            for (UUID memberId : opponentSide.members()) {
                total += state.playerDouble(memberId, "DAMAGE_TAKEN");
            }
            return Math.min((int) Math.floor(total), target(goal));
        }
        if (trigger == Trigger.OBTAIN_ITEM) {
            Material wanted = goal.materialParam("item");
            if (wanted == null) {
                return 0;
            }
            for (UUID memberId : opponentSide.members()) {
                Player online = Bukkit.getPlayer(memberId);
                if (online != null && hasItem(online, wanted)) {
                    return 1;
                }
            }
            return 0;
        }
        String key = statKey(goal);
        if (key == null) {
            return 0;
        }
        int total = 0;
        for (UUID memberId : opponentSide.members()) {
            total += state.playerStat(memberId, key);
        }
        return Math.min(total, target(goal));
    }

    private static boolean hasItem(Player player, Material wanted) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == wanted) {
                return true;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && offhand.getType() == wanted;
    }

    public static Trigger parseTrigger(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'trigger'");
        }
        try {
            return Trigger.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown opponent trigger '" + raw + "'");
        }
    }
}
