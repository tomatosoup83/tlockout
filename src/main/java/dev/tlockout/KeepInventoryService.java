package dev.tlockout;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Keeps participating players' inventories and XP levels on death during an active round when
 * {@code match.keep-inventory} is enabled, without modifying world gamerules or affecting
 * unassigned spectators.
 */
public final class KeepInventoryService implements Listener {

    private final TLockout plugin;
    private boolean enabled;

    public KeepInventoryService(TLockout plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!enabled) {
            return;
        }
        ChallengeManager challenge = plugin.challenge();
        if (challenge == null || !challenge.running()) {
            return;
        }
        Player player = event.getEntity();
        if (challenge.sideOf(player) == null) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }
}
