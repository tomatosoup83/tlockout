package dev.tlockout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Resets participating players' state (inventory, armor, offhand, effects, XP, hunger, health,
 * advancements and untyped statistics) at round start when auto-reset is enabled.
 * <p>
 * Team members who are offline when a round starts are queued and reset when they join while the
 * round is still running.
 */
public final class PlayerResetService implements Listener {

    private final TLockout plugin;
    private final Set<UUID> pendingResets = new HashSet<>();
    private boolean enabled;

    public PlayerResetService(TLockout plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void clearPending() {
        pendingResets.clear();
    }

    /**
     * Resets every online member of every configured team and queues offline members for reset on
     * join. Does nothing when auto-reset is disabled.
     *
     * @return number of online players reset immediately
     */
    public int resetParticipants(SideManager sides) {
        pendingResets.clear();
        if (!enabled) {
            return 0;
        }
        int resetCount = 0;
        for (Side side : sides.all()) {
            for (UUID memberId : side.members()) {
                Player online = Bukkit.getPlayer(memberId);
                if (online != null) {
                    resetPlayer(online);
                    resetCount++;
                } else {
                    pendingResets.add(memberId);
                }
            }
        }
        return resetCount;
    }

    public void resetPlayer(Player player) {
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);

        player.clearActivePotionEffects();

        player.setLevel(0);
        player.setExp(0f);
        player.setTotalExperience(0);

        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setExhaustion(0f);

        if (!player.isDead()) {
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            double health = maxHealth != null ? maxHealth.getValue() : 20.0;
            player.setHealth(health);
        }
        player.setAbsorptionAmount(0);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setRemainingAir(player.getMaximumAir());
        player.setFallDistance(0f);

        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }

        Iterator<Advancement> advancements = Bukkit.advancementIterator();
        while (advancements.hasNext()) {
            Advancement advancement = advancements.next();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criteria : new ArrayList<>(progress.getAwardedCriteria())) {
                progress.revokeCriteria(criteria);
            }
        }

        for (Statistic statistic : Statistic.values()) {
            if (statistic.getType() == Statistic.Type.UNTYPED) {
                try {
                    player.setStatistic(statistic, 0);
                } catch (IllegalArgumentException ignored) {
                    // Skip any statistic Paper refuses to reset directly.
                }
            }
        }
    }

    /**
     * Runs at LOW priority so any pending reset happens before {@link BoardCompass#onJoin} hands
     * out the Board Compass.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!pendingResets.remove(player.getUniqueId())) {
            return;
        }
        if (plugin.challenge().running()) {
            resetPlayer(player);
        }
    }
}
