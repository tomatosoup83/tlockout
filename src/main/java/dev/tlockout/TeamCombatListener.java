package dev.tlockout;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Cancels damage between players on the same side while a round is running when
 * {@code teams.friendly-fire} is false. In FFA mode every player has their own side so PvP
 * between opponents is never blocked.
 */
public final class TeamCombatListener implements Listener {

    private final TLockout plugin;

    public TeamCombatListener(TLockout plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        ChallengeManager challenge = plugin.challenge();
        if (challenge == null || !challenge.running() || challenge.sides().friendlyFire()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        Side attackerSide = challenge.sideOf(attacker);
        if (attackerSide == null) {
            return;
        }
        if (attackerSide == challenge.sideOf(victim)) {
            event.setCancelled(true);
        }
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
