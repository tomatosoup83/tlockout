package dev.tlockout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Chest menu letting a player pick any participating player (teammate or enemy) for their
 * Board Compass to track. Opened via Shift + Right-click on the Board Compass or from the Board UI.
 */
public final class PlayerTrackerGui implements Listener {

    private static final int COLS = 9;
    private static final int MAX_ROWS = 6;
    private static final int HEADS_PER_ROW = 8; // col 0 is the team icon/gutter, cols 1..8 are player heads
    private static final int BACK_SLOT = 0;
    private static final int INFO_SLOT = 4;
    private static final int STOP_SLOT = 8;

    private final TLockout plugin;
    private final ChallengeManager challenge;

    public PlayerTrackerGui(TLockout plugin, ChallengeManager challenge) {
        this.plugin = plugin;
        this.challenge = challenge;
    }

    public static final class Holder implements InventoryHolder {
        private final Map<Integer, UUID> playerBySlot = new HashMap<>();
        private Inventory inventory;

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void clearTargets() {
            playerBySlot.clear();
        }

        private void putTarget(int slot, UUID playerId) {
            playerBySlot.put(slot, playerId);
        }

        public UUID targetAt(int slot) {
            return playerBySlot.get(slot);
        }
    }

    public void open(Player player) {
        if (challenge.board() == null) {
            player.sendMessage(plugin.msg("<red>No challenge is running. Start one with <white>/lockout start</white>."));
            return;
        }
        int size = requiredSize(player);
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text("Track Player"));
        holder.setInventory(inventory);
        render(inventory, holder, player, null);
        player.openInventory(inventory);
    }

    /** Redraws open player-tracker menus in place (or re-opens if row count grew). */
    public void refreshAll() {
        refreshAll(null);
    }

    private void refreshAll(UUID quittingPlayer) {
        if (challenge.board() == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(quittingPlayer)) {
                continue;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Holder holder) {
                int needed = requiredSize(player);
                if (top.getSize() != needed) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()
                                && challenge.board() != null
                                && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                            open(player);
                        }
                    });
                } else {
                    render(top, holder, player, quittingPlayer);
                }
            }
        }
    }

    /** Closes the player-tracker menu for everyone viewing it, used when a round stops. */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                player.closeInventory();
            }
        }
    }

    private List<Side> orderedSidesFor(Player viewer) {
        List<Side> base = challenge.sides().ordered();
        Side viewerSide = challenge.sideOf(viewer);
        if (viewerSide == null) {
            return base;
        }
        List<Side> ordered = new ArrayList<>(base.size());
        ordered.add(viewerSide);
        for (Side side : base) {
            if (!side.id().equals(viewerSide.id())) {
                ordered.add(side);
            }
        }
        return ordered;
    }

    private List<UUID> trackableMembers(Side side, Player viewer) {
        List<UUID> result = new ArrayList<>();
        UUID viewerId = viewer.getUniqueId();
        for (UUID memberId : side.members()) {
            if (!memberId.equals(viewerId)) {
                result.add(memberId);
            }
        }
        return result;
    }

    private int requiredSize(Player viewer) {
        if (challenge.isFfa()) {
            int count = ffaTargets(viewer).size();
            int rows = 1 + Math.max(1, (count + COLS - 1) / COLS);
            return Math.min(MAX_ROWS, Math.max(2, rows)) * COLS;
        }
        int rows = 1; // header row
        for (Side side : orderedSidesFor(viewer)) {
            int count = trackableMembers(side, viewer).size();
            rows += Math.max(1, (count + HEADS_PER_ROW - 1) / HEADS_PER_ROW);
        }
        return Math.min(MAX_ROWS, Math.max(3, rows)) * COLS;
    }

    private record FfaEntry(Side side, UUID memberId) {}

    private List<FfaEntry> ffaTargets(Player viewer) {
        List<FfaEntry> entries = new ArrayList<>();
        UUID viewerId = viewer.getUniqueId();
        for (Side side : challenge.rankedSides()) {
            for (UUID memberId : side.members()) {
                if (!memberId.equals(viewerId)) {
                    entries.add(new FfaEntry(side, memberId));
                }
            }
        }
        return entries;
    }

    private void render(Inventory inventory, Holder holder, Player viewer, UUID quittingPlayer) {
        holder.clearTargets();
        int size = inventory.getSize();
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        PlayerTrackerService tracker = plugin.playerTracker();
        UUID currentTarget = tracker.targetOf(viewer.getUniqueId());
        PlayerTrackerService.TargetView activeView = tracker.viewFor(viewer, quittingPlayer);

        inventory.setItem(BACK_SLOT, backItem());
        inventory.setItem(INFO_SLOT, infoItem(activeView));
        inventory.setItem(STOP_SLOT, stopItem(activeView != null));

        if (challenge.isFfa()) {
            renderFfa(inventory, holder, viewer, currentTarget, quittingPlayer);
            return;
        }

        Side viewerSide = challenge.sideOf(viewer);
        int row = 1;
        int maxRows = size / COLS;

        for (Side side : orderedSidesFor(viewer)) {
            if (row >= maxRows) {
                break;
            }
            List<UUID> members = trackableMembers(side, viewer);
            boolean isTeammateSide = viewerSide != null && viewerSide.id().equals(side.id());
            boolean isEnemySide = viewerSide != null && !isTeammateSide;

            inventory.setItem(row * COLS, sideHeaderItem(side, members, isTeammateSide, isEnemySide, quittingPlayer));

            if (members.isEmpty()) {
                String emptyLabel = isTeammateSide ? "No other teammates" : "No players on this team";
                inventory.setItem(row * COLS + 1, item(
                        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        Component.text(emptyLabel, NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        List.of(),
                        false));
                row++;
                continue;
            }

            // Online players first, then offline, preserving roster order within each group.
            List<UUID> sorted = new ArrayList<>(members.size());
            for (UUID id : members) {
                if (!id.equals(quittingPlayer) && Bukkit.getPlayer(id) != null) {
                    sorted.add(id);
                }
            }
            for (UUID id : members) {
                if (id.equals(quittingPlayer) || Bukkit.getPlayer(id) == null) {
                    sorted.add(id);
                }
            }

            int col = 1;
            for (UUID memberId : sorted) {
                if (col >= COLS) {
                    row++;
                    col = 1;
                }
                if (row >= maxRows) {
                    break;
                }
                int slot = row * COLS + col;
                boolean selected = memberId.equals(currentTarget);
                inventory.setItem(slot, playerHeadItem(
                        viewer, side, memberId, isTeammateSide, isEnemySide, selected, quittingPlayer));
                holder.putTarget(slot, memberId);
                col++;
            }
            row++;
        }
    }

    private void renderFfa(Inventory inventory, Holder holder, Player viewer,
                           UUID currentTarget, UUID quittingPlayer) {
        List<FfaEntry> all = ffaTargets(viewer);
        if (all.isEmpty()) {
            inventory.setItem(COLS + 4, item(
                    Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    Component.text("No other players to track", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(),
                    false));
            return;
        }
        // Online first, preserving score-ranked order within online and offline groups.
        List<FfaEntry> sorted = new ArrayList<>(all.size());
        for (FfaEntry entry : all) {
            if (!entry.memberId().equals(quittingPlayer) && Bukkit.getPlayer(entry.memberId()) != null) {
                sorted.add(entry);
            }
        }
        for (FfaEntry entry : all) {
            if (entry.memberId().equals(quittingPlayer) || Bukkit.getPlayer(entry.memberId()) == null) {
                sorted.add(entry);
            }
        }
        int slot = COLS;
        int size = inventory.getSize();
        for (FfaEntry entry : sorted) {
            if (slot >= size) {
                break;
            }
            boolean selected = entry.memberId().equals(currentTarget);
            inventory.setItem(slot, playerHeadItem(
                    viewer, entry.side(), entry.memberId(), false, false, selected, quittingPlayer));
            holder.putTarget(slot, entry.memberId());
            slot++;
        }
    }

    private ItemStack backItem() {
        return item(
                Material.ARROW,
                Component.text("Back to Board", NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Click to return to the Lockout board", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)),
                false);
    }

    private ItemStack infoItem(PlayerTrackerService.TargetView activeView) {
        List<Component> lore = new ArrayList<>();
        if (activeView != null) {
            lore.add(Component.text("Currently: ", NamedTextColor.GRAY)
                    .append(Component.text(activeView.targetName(), activeView.nameColor()))
                    .append(Component.text(" (" + activeView.statusSuffix() + ")", NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Currently: ", NamedTextColor.GRAY)
                    .append(Component.text("None", NamedTextColor.DARK_GRAY))
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click any player to point your", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Board Compass toward them.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        return item(
                Material.COMPASS,
                Component.text("Player Tracker", NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false),
                lore,
                activeView != null);
    }

    private ItemStack stopItem(boolean tracking) {
        if (!tracking) {
            return item(
                    Material.GRAY_DYE,
                    Component.text("Stop Tracking", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(Component.text("Not currently tracking anyone", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)),
                    false);
        }
        return item(
                Material.BARRIER,
                Component.text("Stop Tracking", NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Click to clear your compass target", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)),
                false);
    }

    private ItemStack sideHeaderItem(Side side, List<UUID> trackable, boolean isTeammateSide,
                                     boolean isEnemySide, UUID quittingPlayer) {
        Component title = Component.text(side.displayName(), side.color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
        if (isTeammateSide) {
            title = title.append(Component.text(" (Your Team)", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (isEnemySide) {
            title = title.append(Component.text(" (Enemy)", NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.ITALIC, false));
        }

        int onlineCount = 0;
        for (UUID memberId : trackable) {
            if (!memberId.equals(quittingPlayer) && Bukkit.getPlayer(memberId) != null) {
                onlineCount++;
            }
        }
        List<Component> lore = List.of(
                Component.text(onlineCount + " / " + trackable.size() + " trackable online", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        return item(side.concrete(), title, lore, false);
    }

    private ItemStack playerHeadItem(Player viewer, Side side, UUID memberId,
                                     boolean isTeammate, boolean isEnemy,
                                     boolean selected, UUID quittingPlayer) {
        PlayerTrackerService tracker = plugin.playerTracker();
        PlayerTrackerService.TargetView view = tracker.viewForTarget(viewer, memberId, quittingPlayer);
        Player online = memberId.equals(quittingPlayer) ? null : Bukkit.getPlayer(memberId);

        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = stack.getItemMeta();
        if (rawMeta instanceof SkullMeta skull) {
            if (online != null) {
                skull.setPlayerProfile(online.getPlayerProfile());
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
                skull.setOwningPlayer(offline);
            }

            Component displayName = Component.text(view.targetName(), side.color())
                    .decoration(TextDecoration.ITALIC, false);
            if (selected) {
                displayName = Component.text("\u2605 ", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(displayName);
            }
            skull.displayName(displayName);

            List<Component> lore = new ArrayList<>();
            if (challenge.isFfa()) {
                int claims = challenge.claimsFor(side.id());
                lore.add(Component.text("FFA", NamedTextColor.AQUA)
                        .append(Component.text(" \u00B7 ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(claims + "/" + challenge.winTarget() + " goals", NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false));
            } else if (isTeammate) {
                lore.add(Component.text("Teammate", NamedTextColor.GREEN)
                        .append(Component.text(" \u00B7 ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(side.displayName(), side.color()))
                        .decoration(TextDecoration.ITALIC, false));
            } else if (isEnemy) {
                lore.add(Component.text("Enemy", NamedTextColor.RED)
                        .append(Component.text(" \u00B7 ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(side.displayName(), side.color()))
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(side.displayName(), side.color())
                        .decoration(TextDecoration.ITALIC, false));
            }

            if (view.online()) {
                if (view.sameWorld() && view.distanceMeters() != null) {
                    lore.add(Component.text("\u25CF ", NamedTextColor.GREEN)
                            .append(Component.text(view.distanceMeters() + "m away", NamedTextColor.WHITE))
                            .decoration(TextDecoration.ITALIC, false));
                } else if (view.distanceMeters() != null) {
                    lore.add(Component.text("\u25CF ", NamedTextColor.YELLOW)
                            .append(Component.text("In " + view.dimensionName(), NamedTextColor.YELLOW))
                            .append(Component.text(" (last seen " + view.distanceMeters() + "m)", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("\u25CF ", NamedTextColor.YELLOW)
                            .append(Component.text("In " + view.dimensionName(), NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false));
                }
            } else {
                lore.add(Component.text("\u25CB ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("Offline", NamedTextColor.DARK_GRAY))
                        .decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.empty());
            if (selected) {
                lore.add(Component.text("\u2605 Currently tracking", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Click to stop tracking", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                skull.setEnchantmentGlintOverride(true);
            } else {
                lore.add(Component.text("Click to track", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }

            skull.lore(lore);
            stack.setItemMeta(skull);
        }
        return stack;
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (rawSlot == BACK_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    plugin.boardGui().open(player);
                }
            });
            return;
        }
        if (rawSlot == STOP_SLOT) {
            PlayerTrackerService tracker = plugin.playerTracker();
            if (tracker.targetOf(player.getUniqueId()) != null) {
                tracker.clearTarget(player);
                player.sendMessage(plugin.msg("<gray>Compass tracking cleared.</gray>"));
                plugin.refreshViews();
            }
            return;
        }

        UUID targetId = holder.targetAt(rawSlot);
        if (targetId == null) {
            return;
        }

        PlayerTrackerService tracker = plugin.playerTracker();
        UUID current = tracker.targetOf(player.getUniqueId());
        if (targetId.equals(current)) {
            tracker.clearTarget(player);
            player.sendMessage(plugin.msg("<gray>Stopped tracking that player.</gray>"));
            plugin.refreshViews();
            return;
        }

        tracker.setTarget(player, targetId);
        PlayerTrackerService.TargetView view = tracker.viewFor(player);
        Side targetSide = challenge.sides().sideOf(targetId);
        String name = view != null ? view.targetName() : tracker.resolveName(targetId, Bukkit.getPlayer(targetId));
        Component message = plugin.prefix()
                .append(Component.text("Compass now tracking ", NamedTextColor.GRAY))
                .append(Component.text(name, targetSide != null ? targetSide.color() : NamedTextColor.WHITE));
        if (targetSide != null && !challenge.isFfa()) {
            message = message.append(Component.text(" (" + targetSide.displayName() + ")", targetSide.color()));
        }
        player.sendMessage(message);
        plugin.refreshViews();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()
                    && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                player.closeInventory();
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshAll();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        refreshAll(event.getPlayer().getUniqueId());
    }
}
