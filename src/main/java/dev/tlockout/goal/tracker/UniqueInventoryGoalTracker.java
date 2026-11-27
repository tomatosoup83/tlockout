package dev.tlockout.goal.tracker;

import dev.tlockout.BoardCompass;
import dev.tlockout.Side;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Fill all 36 main/hotbar inventory slots with unique item types (best single player, not a sum).
 * The undroppable Board Compass is recognised as its own unique slot so holding a normal Compass
 * alongside it does not deadlock the goal at 35/36.
 */
public final class UniqueInventoryGoalTracker implements GoalTracker {

    private static final int STORAGE_SLOTS = 36;
    private static final String BOARD_COMPASS_TOKEN = "__BOARD_COMPASS__";

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        int count = spec.intParam("count", STORAGE_SLOTS);
        if (count < 1 || count > STORAGE_SLOTS) {
            throw new IllegalArgumentException("'count' must be between 1 and " + STORAGE_SLOTS);
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Fill Inventory with unique items";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.CHEST.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Fill all " + target(goal) + " inventory slots with unique items";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, Math.min(STORAGE_SLOTS, goal.intParam("count", STORAGE_SLOTS)));
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        Set<String> seen = new HashSet<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (BoardCompass.isBoardCompassItem(stack)) {
                seen.add(BOARD_COMPASS_TOKEN);
            } else {
                seen.add(stack.getType().name());
            }
        }
        return Math.min(seen.size(), target(goal));
    }

    @Override
    public int teamProgress(Side side, ResolvedGoal goal, TrackingState state) {
        int best = 0;
        for (UUID memberId : side.members()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null) {
                best = Math.max(best, progress(player, goal, state));
            }
        }
        return Math.min(best, target(goal));
    }
}
