package dev.tlockout;

import java.util.Collection;
import java.util.UUID;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Spawns harmless celebratory fireworks when goals are claimed or when a match is won.
 * Every spawned firework is tagged in its PersistentDataContainer and any damage it would deal is
 * cancelled at {@link EventPriority#LOWEST} so it never hurts players/mobs or counts toward
 * damage/death goals.
 */
public final class FireworkService implements Listener {

    private final TLockout plugin;
    private final NamespacedKey celebrationKey;
    private boolean enabled;

    public FireworkService(TLockout plugin, boolean enabled) {
        this.plugin = plugin;
        this.celebrationKey = new NamespacedKey(plugin, "celebration_firework");
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Launches a coloured celebration firework at {@code claimant} (or at every online member of
     * {@code claimingSide} when there is no single claimant, such as an opponent-triggered claim).
     */
    public void launchGoalClaimed(Player claimant, Side claimingSide) {
        if (!enabled || claimingSide == null) {
            return;
        }
        Color primary = bukkitColor(claimingSide.color());
        if (claimant != null && claimant.isOnline()) {
            spawn(claimant, primary, Color.WHITE, FireworkEffect.Type.BALL, 1);
            return;
        }
        for (UUID memberId : claimingSide.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                spawn(member, primary, Color.WHITE, FireworkEffect.Type.BALL, 1);
            }
        }
    }

    /** Launches a short multi-rocket victory burst for every online member of the winning side(s). */
    public void launchVictory(Collection<Side> winningSides) {
        if (!enabled || winningSides == null || winningSides.isEmpty()) {
            return;
        }
        long[] delays = {0L, 8L, 16L};
        FireworkEffect.Type[] shapes = {
                FireworkEffect.Type.BALL_LARGE,
                FireworkEffect.Type.STAR,
                FireworkEffect.Type.BURST
        };
        for (Side side : winningSides) {
            if (side == null) {
                continue;
            }
            Color primary = bukkitColor(side.color());
            for (UUID memberId : side.members()) {
                for (int i = 0; i < delays.length; i++) {
                    final FireworkEffect.Type shape = shapes[i];
                    final Color fade = i == 1 ? Color.YELLOW : Color.WHITE;
                    long delay = delays[i];
                    if (delay == 0L) {
                        Player online = Bukkit.getPlayer(memberId);
                        if (online != null && online.isOnline()) {
                            spawn(online, primary, fade, shape, 1);
                        }
                    } else {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            Player online = Bukkit.getPlayer(memberId);
                            if (online != null && online.isOnline()) {
                                spawn(online, primary, fade, shape, 1);
                            }
                        }, delay);
                    }
                }
            }
        }
    }

    private void spawn(Player player, Color primary, Color fade, FireworkEffect.Type type, int power) {
        Location loc = player.getLocation().add(0.0, 1.0, 0.0);
        if (loc.getWorld() == null) {
            return;
        }
        loc.getWorld().spawn(loc, Firework.class, firework -> {
            firework.getPersistentDataContainer().set(celebrationKey, PersistentDataType.BYTE, (byte) 1);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(type)
                    .withColor(primary)
                    .withFade(fade)
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(power);
            firework.setFireworkMeta(meta);
        });
    }

    private static Color bukkitColor(TextColor color) {
        if (color == null) {
            return Color.AQUA;
        }
        return Color.fromRGB(color.red(), color.green(), color.blue());
    }

    /** Cancel all damage from tagged celebration fireworks before damage trackers see it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(celebrationKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
