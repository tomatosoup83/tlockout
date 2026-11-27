package dev.tlockout;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tracks per-player compass targets (teammates or enemies) during an active lockout board,
 * maintains last-known locations per dimension for cross-dimension portal pointing, and keeps
 * each viewer's Board Compass needle and display name up to date.
 */
public final class PlayerTrackerService implements Listener {

    /** Update cadence for compass needles and distance labels (10 ticks = 0.5s). */
    private static final long UPDATE_INTERVAL_TICKS = 10L;

    /** Computed display & needle target for a viewer tracking another player. */
    public record TargetView(
            UUID targetId,
            String targetName,
            TextColor nameColor,
            String statusSuffix,
            Location lodestoneTarget,
            boolean online,
            boolean sameWorld,
            Integer distanceMeters,
            String dimensionName) {}

    private record AppliedState(
            UUID targetId,
            String labelKey,
            UUID lodestoneWorldId,
            int blockX,
            int blockY,
            int blockZ,
            boolean hasLodestone) {}

    private final TLockout plugin;
    private final Map<UUID, UUID> trackedByViewer = new HashMap<>();
    private final Map<UUID, Map<UUID, Location>> lastKnownByWorld = new HashMap<>();
    private final Map<UUID, String> knownNames = new HashMap<>();
    private final Map<UUID, AppliedState> lastApplied = new HashMap<>();
    private BukkitRunnable updateTask;

    public PlayerTrackerService(TLockout plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        stopTask();
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        updateTask.runTaskTimer(plugin, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }

    public void stopTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /** Clears all tracked selections, cached locations, and applied compass states. */
    public void clearAll() {
        trackedByViewer.clear();
        lastKnownByWorld.clear();
        lastApplied.clear();
    }

    /** Immediately synchronizes every online player's Board Compass with their current tracking state. */
    public void syncAllNow() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            recordLocation(player, player.getLocation());
            syncCompass(player, null, true);
        }
    }

    public UUID targetOf(UUID viewerId) {
        return trackedByViewer.get(viewerId);
    }

    /**
     * Sets the player {@code viewer} is tracking and immediately updates their Board Compass.
     * Passing {@code null} or the viewer's own UUID clears tracking.
     */
    public void setTarget(Player viewer, UUID targetId) {
        if (viewer == null) {
            return;
        }
        if (targetId == null || targetId.equals(viewer.getUniqueId())) {
            clearTarget(viewer);
            return;
        }
        trackedByViewer.put(viewer.getUniqueId(), targetId);
        Player onlineTarget = Bukkit.getPlayer(targetId);
        if (onlineTarget != null) {
            recordLocation(onlineTarget, onlineTarget.getLocation());
        }
        lastApplied.remove(viewer.getUniqueId());
        syncCompass(viewer, null, true);
    }

    public void clearTarget(Player viewer) {
        if (viewer == null) {
            return;
        }
        trackedByViewer.remove(viewer.getUniqueId());
        lastApplied.remove(viewer.getUniqueId());
        syncCompass(viewer, null, true);
    }

    /** Invalidates the cached compass state for a viewer so the next sync forces a write. */
    public void invalidate(UUID viewerId) {
        lastApplied.remove(viewerId);
    }

    /** Computes the current tracking view for {@code viewer}, or null if they are not tracking anyone. */
    public TargetView viewFor(Player viewer) {
        return viewFor(viewer, null);
    }

    public TargetView viewFor(Player viewer, UUID quittingPlayer) {
        if (viewer == null) {
            return null;
        }
        UUID targetId = trackedByViewer.get(viewer.getUniqueId());
        if (targetId == null) {
            return null;
        }
        return viewForTarget(viewer, targetId, quittingPlayer);
    }

    /** Computes display and lodestone details for {@code viewer} pointing at {@code targetId}. */
    public TargetView viewForTarget(Player viewer, UUID targetId, UUID quittingPlayer) {
        if (viewer == null || targetId == null) {
            return null;
        }
        ChallengeManager challenge = plugin.challenge();
        Side targetSide = (challenge != null && challenge.sides() != null)
                ? challenge.sides().sideOf(targetId)
                : null;
        TextColor nameColor = targetSide != null ? targetSide.color() : NamedTextColor.WHITE;

        Player onlineTarget = targetId.equals(quittingPlayer) ? null : Bukkit.getPlayer(targetId);
        String targetName = resolveName(targetId, onlineTarget);
        World viewerWorld = viewer.getWorld();

        if (onlineTarget != null) {
            Location targetLoc = onlineTarget.getLocation();
            World targetWorld = targetLoc.getWorld();
            String dimName = dimensionName(targetWorld);
            if (targetWorld != null && targetWorld.equals(viewerWorld)) {
                int dist = (int) Math.round(viewer.getLocation().distance(targetLoc));
                return new TargetView(
                        targetId,
                        targetName,
                        nameColor,
                        dist + "m",
                        targetLoc.clone(),
                        true,
                        true,
                        dist,
                        dimName);
            }
            Location lastInViewerWorld = lastKnownInWorld(targetId, viewerWorld);
            if (lastInViewerWorld != null) {
                int dist = (int) Math.round(viewer.getLocation().distance(lastInViewerWorld));
                return new TargetView(
                        targetId,
                        targetName,
                        nameColor,
                        dimName + " \u00B7 " + dist + "m",
                        lastInViewerWorld.clone(),
                        true,
                        false,
                        dist,
                        dimName);
            }
            // No known location in the viewer's world: point lodestone at the target's other-world
            // location so vanilla Minecraft spins the compass needle rather than pointing to spawn.
            return new TargetView(
                    targetId,
                    targetName,
                    nameColor,
                    dimName,
                    targetLoc.clone(),
                    true,
                    false,
                    null,
                    dimName);
        }

        // Target is offline: keep last known location in the viewer's world if we have one.
        Location lastInViewerWorld = lastKnownInWorld(targetId, viewerWorld);
        if (lastInViewerWorld != null) {
            int dist = (int) Math.round(viewer.getLocation().distance(lastInViewerWorld));
            return new TargetView(
                    targetId,
                    targetName,
                    nameColor,
                    "offline \u00B7 " + dist + "m",
                    lastInViewerWorld.clone(),
                    false,
                    false,
                    dist,
                    null);
        }
        return new TargetView(
                targetId,
                targetName,
                nameColor,
                "offline",
                null,
                false,
                false,
                null,
                null);
    }

    /** Formats the Board Compass display name for the given tracking view (or default when null). */
    public Component compassDisplayName(TargetView view) {
        if (view == null) {
            return BoardCompass.DEFAULT_DISPLAY_NAME;
        }
        return Component.text("Tracking: ", NamedTextColor.AQUA)
                .append(Component.text(view.targetName(), view.nameColor()))
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(view.statusSuffix(),
                        view.online() ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY))
                .append(Component.text(")", NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Applies the viewer's current tracking display name and lodestone directly onto a CompassMeta. */
    public void applyToMeta(Player viewer, CompassMeta meta) {
        if (meta == null) {
            return;
        }
        TargetView view = viewFor(viewer);
        meta.displayName(compassDisplayName(view));
        if (view != null && view.lodestoneTarget() != null) {
            meta.setLodestone(view.lodestoneTarget());
            meta.setLodestoneTracked(false);
        } else {
            meta.clearLodestone();
        }
    }

    // -------------------------------------------------------------- periodic tick

    private void tick() {
        ChallengeManager challenge = plugin.challenge();
        if (challenge == null || challenge.board() == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            recordLocation(player, player.getLocation());
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            syncCompass(viewer, null, false);
        }
        if (plugin.playerTrackerGui() != null) {
            plugin.playerTrackerGui().refreshAll();
        }
    }

    private void syncCompass(Player viewer, UUID quittingPlayer, boolean force) {
        BoardCompass compass = plugin.boardCompass();
        if (compass == null) {
            return;
        }
        TargetView view = viewFor(viewer, quittingPlayer);
        AppliedState next = toAppliedState(view);
        AppliedState prev = lastApplied.get(viewer.getUniqueId());
        if (!force && Objects.equals(prev, next)) {
            return;
        }
        // Only write to inventory if the player actually holds a Board Compass; if not, don't
        // record as applied so a later /lo compass or respawn syncs cleanly.
        if (compass.applyTracking(viewer, compassDisplayName(view), view == null ? null : view.lodestoneTarget())) {
            lastApplied.put(viewer.getUniqueId(), next);
        } else {
            lastApplied.remove(viewer.getUniqueId());
        }
    }

    private static AppliedState toAppliedState(TargetView view) {
        if (view == null) {
            return new AppliedState(null, "", null, 0, 0, 0, false);
        }
        Location loc = view.lodestoneTarget();
        boolean hasLoc = loc != null && loc.getWorld() != null;
        return new AppliedState(
                view.targetId(),
                view.targetName() + "|" + view.nameColor().asHexString() + "|" + view.statusSuffix(),
                hasLoc ? loc.getWorld().getUID() : null,
                hasLoc ? loc.getBlockX() : 0,
                hasLoc ? loc.getBlockY() : 0,
                hasLoc ? loc.getBlockZ() : 0,
                hasLoc);
    }

    private void recordLocation(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        knownNames.put(player.getUniqueId(), player.getName());
        lastKnownByWorld
                .computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
                .put(location.getWorld().getUID(), location.clone());
    }

    private Location lastKnownInWorld(UUID playerId, World world) {
        if (playerId == null || world == null) {
            return null;
        }
        Map<UUID, Location> byWorld = lastKnownByWorld.get(playerId);
        if (byWorld == null) {
            return null;
        }
        Location loc = byWorld.get(world.getUID());
        if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(world)) {
            return null;
        }
        return loc;
    }

    public String resolveName(UUID playerId, Player online) {
        if (online != null) {
            knownNames.put(playerId, online.getName());
            return online.getName();
        }
        String remembered = knownNames.get(playerId);
        if (remembered != null && !remembered.isBlank()) {
            return remembered;
        }
        String cached = Bukkit.getOfflinePlayer(playerId).getName();
        return (cached != null && !cached.isBlank()) ? cached : "Unknown";
    }

    public static String dimensionName(World world) {
        if (world == null) {
            return "Unknown";
        }
        return switch (world.getEnvironment()) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> world.getName();
        };
    }

    /** Refreshes compasses for everyone currently tracking {@code targetId}. */
    private void syncTrackersOf(UUID targetId, UUID quittingPlayer) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(quittingPlayer)) {
                continue;
            }
            if (targetId.equals(trackedByViewer.get(viewer.getUniqueId()))) {
                lastApplied.remove(viewer.getUniqueId());
                syncCompass(viewer, quittingPlayer, true);
            }
        }
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.challenge() == null || plugin.challenge().board() == null) {
            return;
        }
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from != null && from.getWorld() != null) {
            recordLocation(player, from);
        }
        if (to != null && to.getWorld() != null) {
            recordLocation(player, to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (plugin.challenge() == null || plugin.challenge().board() == null) {
            return;
        }
        Player player = event.getPlayer();
        recordLocation(player, player.getLocation());
        lastApplied.remove(player.getUniqueId());
        syncCompass(player, null, true);
        syncTrackersOf(player.getUniqueId(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.challenge() == null || plugin.challenge().board() == null) {
            return;
        }
        Player player = event.getEntity();
        recordLocation(player, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.challenge() == null || plugin.challenge().board() == null) {
            return;
        }
        Player player = event.getPlayer();
        lastApplied.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.challenge().board() != null) {
                recordLocation(player, player.getLocation());
                syncCompass(player, null, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        knownNames.put(player.getUniqueId(), player.getName());
        if (plugin.challenge() == null || plugin.challenge().board() == null) {
            return;
        }
        recordLocation(player, player.getLocation());
        lastApplied.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.challenge().board() != null) {
                syncCompass(player, null, true);
                syncTrackersOf(player.getUniqueId(), null);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID quittingId = player.getUniqueId();
        lastApplied.remove(quittingId);
        if (plugin.challenge() == null || plugin.challenge().board() == null) {
            return;
        }
        recordLocation(player, player.getLocation());
        syncTrackersOf(quittingId, quittingId);
    }
}
