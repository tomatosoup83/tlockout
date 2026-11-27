package dev.tlockout;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runs the configurable pre-round countdown (titles, chat ticks, SFX) and optionally freezes
 * participating players in place while protecting them from damage and block edits until GO.
 */
public final class CountdownService implements Listener {

    private static final Title.Times TICK_TIMES = Title.Times.times(
            Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(150));
    private static final Title.Times GO_TIMES = Title.Times.times(
            Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(400));

    private final TLockout plugin;
    private int countdownSeconds;
    private boolean freezeDuringCountdown;
    private BukkitRunnable activeTask;

    public CountdownService(TLockout plugin, int countdownSeconds, boolean freezeDuringCountdown) {
        this.plugin = plugin;
        this.countdownSeconds = Math.max(0, countdownSeconds);
        this.freezeDuringCountdown = freezeDuringCountdown;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public boolean freezeDuringCountdown() {
        return freezeDuringCountdown;
    }

    public void configure(int countdownSeconds, boolean freezeDuringCountdown) {
        this.countdownSeconds = Math.max(0, countdownSeconds);
        this.freezeDuringCountdown = freezeDuringCountdown;
    }

    /**
     * Starts a countdown of {@code countdownSeconds} seconds and invokes {@code onComplete} on GO.
     * If {@code countdownSeconds <= 0}, invokes {@code onComplete} immediately.
     */
    public void start(Runnable onComplete) {
        cancel();
        if (countdownSeconds <= 0) {
            onComplete.run();
            return;
        }
        plugin.challenge().beginCountdown();
        final int total = countdownSeconds;
        activeTask = new BukkitRunnable() {
            private int remaining = total;

            @Override
            public void run() {
                if (!plugin.challenge().countdown()) {
                    cancel();
                    activeTask = null;
                    return;
                }
                if (remaining <= 0) {
                    cancel();
                    activeTask = null;
                    showGoTitle();
                    onComplete.run();
                    return;
                }
                broadcastTick(remaining);
                remaining--;
            }
        };
        activeTask.runTaskTimer(plugin, 0L, 20L);
    }

    /** Cancels any active countdown and clears countdown titles for online players. */
    public void cancel() {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.clearTitle();
        }
    }

    private void broadcastTick(int seconds) {
        NamedTextColor color = seconds <= 3 ? NamedTextColor.RED : NamedTextColor.GOLD;
        Title title = Title.title(
                Component.text(String.valueOf(seconds), color).decoration(TextDecoration.BOLD, true),
                Component.text("Lockout starting soon...", NamedTextColor.GRAY),
                TICK_TIMES);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
        plugin.getServer().sendMessage(plugin.prefix()
                .append(Component.text("Starting in ", NamedTextColor.GRAY))
                .append(Component.text(seconds + "...", color).decoration(TextDecoration.BOLD, true)));
        if (plugin.sounds() != null) {
            plugin.sounds().playCountdownTick(seconds);
        }
    }

    private void showGoTitle() {
        Title title = Title.title(
                Component.text("GO!", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("First to " + plugin.challenge().winTarget() + " goals wins!", NamedTextColor.GOLD),
                GO_TIMES);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }

    // --------------------------------------------------------- freeze guards

    private boolean frozenParticipant(Player player) {
        return freezeDuringCountdown
                && plugin.challenge().countdown()
                && plugin.challenge().sideOf(player) != null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!frozenParticipant(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location pinned = from.clone();
            pinned.setYaw(to.getYaw());
            pinned.setPitch(to.getPitch());
            event.setTo(pinned);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (frozenParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (frozenParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && frozenParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && frozenParticipant(attacker)) {
            event.setCancelled(true);
        }
    }
}
