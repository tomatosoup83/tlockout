package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ShieldMeta;

/** Obtain a shield decorated with a banner (checked across storage slots and offhand). */
public final class BannerShieldGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        // No required params.
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Decorate Shield with Banner";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.SHIELD.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        return "Hold a shield decorated with any banner";
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getStorageContents()) {
            if (isDecoratedShield(item)) {
                return 1;
            }
        }
        return isDecoratedShield(inventory.getItemInOffHand()) ? 1 : 0;
    }

    private static boolean isDecoratedShield(ItemStack item) {
        if (item == null || item.getType() != Material.SHIELD) {
            return false;
        }
        if (item.hasData(DataComponentTypes.BASE_COLOR)) {
            return true;
        }
        return item.getItemMeta() instanceof ShieldMeta shieldMeta
                && shieldMeta.getBaseColor() != null;
    }
}
