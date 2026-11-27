package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Use one of the eight Fabric workstation goals (anvil, cauldron, composter, etc.). */
public final class WorkstationGoalTracker implements GoalTracker {

    public enum Station {
        ANVIL("Use Anvil", "Use an anvil to repair, combine or rename an item", Material.ANVIL),
        CAULDRON("Use Cauldron to wash something", "Wash dyed armor, a banner or a shulker box in a water cauldron", Material.CAULDRON),
        COMPOSTER("Fill Composter and get Bone Meal", "Harvest bone meal from a full composter", Material.COMPOSTER),
        ENCHANTING_TABLE("Use Enchanting Table", "Enchant an item at an enchanting table", Material.ENCHANTING_TABLE),
        GRINDSTONE("Use Grindstone", "Use a grindstone to repair or disenchant an item", Material.GRINDSTONE),
        LOOM("Use Loom to design a Banner", "Design a banner using a loom", Material.LOOM),
        SMITHING_TABLE("Use Smithing Table", "Upgrade or trim gear at a smithing table", Material.SMITHING_TABLE),
        STONECUTTER("Use Stonecutter", "Craft a block at a stonecutter", Material.STONECUTTER);

        private final String display;
        private final String requirement;
        private final Material icon;

        Station(String display, String requirement, Material icon) {
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

    public static String statKeyFor(Station station) {
        return "WORKSTATION:" + station.name();
    }

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        parseStation(spec.param("station"));
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return parseStation(spec.param("station")).display();
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return parseStation(spec.param("station")).icon().name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return parseStation(goal.param("station")).requirement();
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        return statKeyFor(parseStation(goal.param("station")));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return Math.min(1, state.playerStat(player.getUniqueId(), statKey(goal)));
    }

    public static Station parseStation(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'station'");
        }
        try {
            return Station.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown workstation '" + raw + "'");
        }
    }
}
