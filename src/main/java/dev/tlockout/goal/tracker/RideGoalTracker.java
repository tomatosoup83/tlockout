package dev.tlockout.goal.tracker;

import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Ride a specific entity; pigs also require a carrot on a stick. */
public final class RideGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        GoalSupport.entity(spec.param("entity"));
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        EntityType type = GoalSupport.entity(spec.param("entity"));
        if (type == EntityType.PIG) {
            return "Ride Pig with Carrot on a Stick";
        }
        return "Ride a " + pretty(type);
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        EntityType type = GoalSupport.entity(spec.param("entity"));
        if (type == EntityType.PIG) {
            return Material.CARROT_ON_A_STICK.name();
        }
        if (type == EntityType.MINECART) {
            return Material.MINECART.name();
        }
        return Material.SADDLE.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        EntityType type = GoalSupport.entity(goal.param("entity"));
        if (type == EntityType.PIG) {
            return "Ride a pig while holding a carrot on a stick";
        }
        return "Ride a " + pretty(type);
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        EntityType wanted = GoalSupport.entity(goal.param("entity"));
        Entity vehicle = player.getVehicle();
        if (vehicle == null || vehicle.getType() != wanted) {
            return 0;
        }
        if (wanted == EntityType.PIG) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            return hand.getType() == Material.CARROT_ON_A_STICK ? 1 : 0;
        }
        return 1;
    }

    private static String pretty(EntityType type) {
        return dev.tlockout.Names.pretty(type.getKey().getKey());
    }
}
