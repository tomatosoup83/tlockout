package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Event-driven world interactions and block actions (jukebox, campfire, candle, brush, etc.). */
public final class BlockActionGoalTracker implements GoalTracker {

    public enum Action {
        USE_JUKEBOX(
                "Use Jukebox to play a Music Disc",
                "Insert a music disc into a jukebox",
                Material.JUKEBOX),
        GLOW_INK_CRIMSON_SIGN(
                "Use Glow Ink on Crimson Sign",
                "Apply a glow ink sac to a crimson sign",
                Material.GLOW_INK_SAC),
        POT_FLOWER(
                "Pot any plant in Flower Pot",
                "Place any plant into a flower pot",
                Material.FLOWER_POT),
        FILL_ARMOR_STAND(
                "Fill an Armor Stand",
                "Equip all 4 armor slots on an armor stand",
                Material.ARMOR_STAND),
        ITEM_FRAME_IN_ITEM_FRAME(
                "Put Item Frame in an Item Frame",
                "Place a regular item frame inside another regular item frame",
                Material.ITEM_FRAME),
        FILL_CAMPFIRE(
                "Fill Campfire with 4 Food Items",
                "Place 4 cookable food items onto one campfire",
                Material.CAMPFIRE),
        FILL_CHISELED_BOOKSHELF(
                "Fill Chiseled Bookshelf",
                "Fill all 6 slots of a chiseled bookshelf with books",
                Material.CHISELED_BOOKSHELF),
        PLACE_END_CRYSTAL(
                "Place End Crystal",
                "Place an end crystal on obsidian or bedrock",
                Material.END_CRYSTAL),
        LIGHT_CANDLE(
                "Light a Candle",
                "Light any unlit candle with flint & steel, a fire charge or fire",
                Material.CANDLE),
        SHOOT_FIREWORK_CROSSBOW(
                "Shoot Firework from Crossbow",
                "Fire a firework rocket from a crossbow",
                Material.CROSSBOW),
        TOOT_GOAT_HORN(
                "Toot a Goat Horn",
                "Blow a goat horn",
                Material.GOAT_HORN),
        BRUSH_SUSPICIOUS_BLOCK(
                "Use Brush on Suspicious Sand/Gravel",
                "Finish brushing a suspicious sand or suspicious gravel block",
                Material.BRUSH),
        ANGER_ZOMBIFIED_PIGLIN(
                "Enrage Zombified Piglin",
                "Make a zombified piglin hostile toward you",
                Material.GOLDEN_SWORD),
        SHIELD_DISABLED(
                "Have your Shield disabled",
                "Have your raised shield disabled by an axe or mob attack",
                Material.SHIELD);

        private final String display;
        private final String requirement;
        private final Material icon;

        Action(String display, String requirement, Material icon) {
            this.display = display;
            this.requirement = requirement;
            this.icon = icon;
        }

        public String display() {
            return display;
        }

        public String requirement() {
            return requirement;
        }

        public Material icon() {
            return icon;
        }
    }

    public static String statKeyFor(Action action) {
        return "ACTION:" + action.name();
    }

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        parseAction(spec.param("action"));
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return parseAction(spec.param("action")).display();
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return parseAction(spec.param("action")).icon().name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return parseAction(goal.param("action")).requirement();
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        return statKeyFor(parseAction(goal.param("action")));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return Math.min(1, state.playerStat(player.getUniqueId(), statKey(goal)));
    }

    public static Action parseAction(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'action'");
        }
        try {
            return Action.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown block_action '" + raw + "'");
        }
    }
}
