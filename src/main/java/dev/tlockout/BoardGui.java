package dev.tlockout;

import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.tracker.ArmorPieceGoalTracker;
import dev.tlockout.goal.tracker.BrewPotionGoalTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
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
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/** The browsable 5x5 board shown in a chest-style inventory. */
public final class BoardGui implements Listener {

    /** Board cell 0..24 -> inventory slot. Rows 1-5, columns 2-6 of a 6-row GUI. */
    private static final int[] CELL_SLOTS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42,
            47, 48, 49, 50, 51
    };

    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    /** Bottom-left corner of the 6-row GUI: opens the player tracker compass menu. */
    private static final int TRACKER_SLOT = 45;
    /** Bottom-right corner of the 6-row GUI, shown only to opped viewers. */
    private static final int REROLL_SLOT = 53;
    /** Slots for the two team counters in teams mode, left and right of the header. */
    private static final int[] SIDE_SLOTS = {3, 5};
    /** Slots for top ranked players flanking the header clock in FFA mode. */
    private static final int[] FFA_SIDE_SLOTS = {3, 5, 2, 6, 1, 7, 0, 8};

    private final TLockout plugin;
    private final ChallengeManager challenge;

    public BoardGui(TLockout plugin, ChallengeManager challenge) {
        this.plugin = plugin;
        this.challenge = challenge;
    }

    public static final class Holder implements InventoryHolder {
        private final int[] cellBySlot = new int[SIZE];
        private Inventory inventory;

        private Holder() {
            Arrays.fill(cellBySlot, -1);
            for (int cell = 0; cell < CELL_SLOTS.length; cell++) {
                cellBySlot[CELL_SLOTS[cell]] = cell;
            }
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public int cellAt(int slot) {
            return slot >= 0 && slot < cellBySlot.length ? cellBySlot[slot] : -1;
        }
    }

    public void open(Player player) {
        Board board = challenge.board();
        if (board == null) {
            player.sendMessage(plugin.msg("<red>No challenge is running. Start one with <white>/lockout start</white>."));
            return;
        }

        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("Lockout Board"));
        holder.setInventory(inventory);
        render(inventory, board, player, null);
        player.openInventory(inventory);
    }

    /** Redraws the board for every player who currently has it open. */
    public void refreshAll() {
        refreshAll(null);
    }

    private void refreshAll(UUID quittingPlayer) {
        Board board = challenge.board();
        if (board == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(quittingPlayer)) {
                continue;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Holder) {
                render(top, board, player, quittingPlayer);
            }
        }
    }

    /** Closes the board for everyone viewing it, used when a round ends. */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                player.closeInventory();
            }
        }
    }

    private void render(Inventory inventory, Board board, Player viewer, UUID quittingPlayer) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        inventory.setItem(INFO_SLOT, infoItem(board));

        if (challenge.isFfa()) {
            List<Side> ranked = challenge.rankedSides();
            for (int i = 0; i < ranked.size() && i < FFA_SIDE_SLOTS.length; i++) {
                Side side = ranked.get(i);
                inventory.setItem(FFA_SIDE_SLOTS[i],
                        ffaSideItem(side, i + 1, challenge.claimsFor(side.id()), quittingPlayer));
            }
        } else {
            List<Side> sides = challenge.sides().ordered();
            for (int i = 0; i < sides.size() && i < SIDE_SLOTS.length; i++) {
                Side side = sides.get(i);
                inventory.setItem(SIDE_SLOTS[i], sideItem(side, challenge.claimsFor(side.id()), quittingPlayer));
            }
        }

        for (int cell = 0; cell < Board.CELLS; cell++) {
            inventory.setItem(CELL_SLOTS[cell], tile(board, cell, viewer));
        }

        inventory.setItem(TRACKER_SLOT, trackerItem(viewer, quittingPlayer));

        if (viewer.isOp()) {
            inventory.setItem(REROLL_SLOT, rerollItem());
        }
    }

    private ItemStack trackerItem(Player viewer, UUID quittingPlayer) {
        PlayerTrackerService tracker = plugin.playerTracker();
        PlayerTrackerService.TargetView view = tracker == null ? null : tracker.viewFor(viewer, quittingPlayer);
        List<Component> lore = new ArrayList<>();
        if (view != null) {
            lore.add(Component.text("Tracking: ", NamedTextColor.GRAY)
                    .append(Component.text(view.targetName(), view.nameColor()))
                    .append(Component.text(" (" + view.statusSuffix() + ")", NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Not tracking anyone", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click to choose a player to track", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Tip: Shift + Right-click your compass", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        return item(Material.COMPASS,
                Component.text("Track Player", NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false),
                lore,
                view != null);
    }

    private ItemStack rerollItem() {
        List<Component> lore = List.of(
                Component.text("Click to start a fresh board", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Same as /lo reroll", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Op only", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        return item(Material.RECOVERY_COMPASS,
                Component.text("Reroll Board", NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false),
                lore);
    }

    private ItemStack infoItem(Board board) {
        List<Component> lore = new ArrayList<>();
        if (challenge.isFfa()) {
            lore.add(line("Mode: ", "FFA", NamedTextColor.GRAY, NamedTextColor.AQUA));
        }
        lore.add(line("Seed: ", String.valueOf(board.seed()), NamedTextColor.GRAY, NamedTextColor.WHITE));
        lore.add(line("Claimed: ", board.claimedCount() + "/" + Board.CELLS, NamedTextColor.GRAY,
                NamedTextColor.WHITE));
        if (challenge.finished()) {
            List<Side> winners = challenge.winners();
            if (winners.size() > 1) {
                Component line = Component.text("Tie: ", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false);
                for (int i = 0; i < winners.size(); i++) {
                    if (i > 0) {
                        line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
                    }
                    Side w = winners.get(i);
                    line = line.append(Component.text(w.displayName(), w.color()));
                }
                lore.add(line);
            } else {
                Side winner = challenge.winner();
                lore.add(Component.text("Winner: ", NamedTextColor.GRAY)
                        .append(Component.text(winner == null ? "?" : winner.displayName(),
                                winner == null ? NamedTextColor.WHITE : winner.color()))
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("First to " + challenge.winTarget() + " wins", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (challenge.isFfa()) {
            List<Side> ranked = challenge.rankedSides();
            if (!ranked.isEmpty()) {
                lore.add(Component.empty());
                int rank = 1;
                for (Side side : ranked) {
                    lore.add(Component.text("#" + rank + " ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(side.displayName(), side.color()))
                            .append(Component.text(" - " + challenge.claimsFor(side.id()), NamedTextColor.WHITE))
                            .decoration(TextDecoration.ITALIC, false));
                    rank++;
                }
            }
        }
        lore.add(Component.text("Click a tile for details", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        return item(Material.CLOCK,
                Component.text("Lockout", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                lore);
    }

    private ItemStack ffaSideItem(Side side, int rank, int claims, UUID quittingPlayer) {
        boolean online = false;
        for (UUID memberId : side.members()) {
            if (!memberId.equals(quittingPlayer) && Bukkit.getPlayer(memberId) != null) {
                online = true;
                break;
            }
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(claims + " / " + challenge.winTarget() + " goals", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add((online
                ? Component.text("\u25CF Online", NamedTextColor.GREEN)
                : Component.text("\u25CB Offline", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        Component title = Component.text("#" + rank + " ", rank == 1 ? NamedTextColor.GOLD : NamedTextColor.GRAY)
                .append(Component.text(side.displayName(), side.color()))
                .decoration(TextDecoration.ITALIC, false);
        return item(side.concrete(), title, lore);
    }

    private ItemStack sideItem(Side side, int claims, UUID quittingPlayer) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(claims + " / " + challenge.winTarget() + " goals", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        List<String> onlineNames = new ArrayList<>();
        List<String> offlineNames = new ArrayList<>();
        for (UUID memberId : side.members()) {
            Player online = memberId.equals(quittingPlayer) ? null : Bukkit.getPlayer(memberId);
            if (online != null) {
                onlineNames.add(online.getName());
            } else {
                String cached = Bukkit.getOfflinePlayer(memberId).getName();
                offlineNames.add((cached != null && !cached.isBlank()) ? cached : "offline");
            }
        }

        lore.add(Component.text(onlineNames.size() + " / " + side.size() + " online", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        if (side.members().isEmpty()) {
            lore.add(Component.text("(empty)", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            for (String name : onlineNames) {
                lore.add(Component.text("\u25CF ", NamedTextColor.GREEN)
                        .append(Component.text(name, NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false));
            }
            for (String name : offlineNames) {
                lore.add(Component.text("\u25CB ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(name + " (offline)", NamedTextColor.DARK_GRAY))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        return item(side.concrete(),
                Component.text(side.displayName(), side.color()).decoration(TextDecoration.ITALIC, false),
                lore);
    }

    private ItemStack tile(Board board, int cell, Player viewer) {
        ResolvedGoal goal = board.cell(cell);
        Board.Claim claim = board.claim(cell);
        return claim == null ? unclaimedTile(goal, cell, viewer) : claimedTile(goal, claim);
    }

    private ItemStack unclaimedTile(ResolvedGoal goal, int cell, Player viewer) {
        Side side = challenge.sides().sideOf(viewer.getUniqueId());
        boolean tracked = side != null && side.isTargeted(cell);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(goal.requirement(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(Names.pretty(goal.spec().type().name()), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add((tracked
                ? Component.text(challenge.isFfa() ? "\u2605 Tracked" : "\u2605 Tracked by your team",
                        NamedTextColor.GOLD)
                : Component.text("Right-click to track", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Unclaimed", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

        Component name = Component.text(goal.display(), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
        if (tracked) {
            name = Component.text("\u2605 ", NamedTextColor.GOLD).append(name);
        }
        ItemStack stack = item(goal.icon(), name, lore);
        String colorParam = goal.param("color");
        if (colorParam != null && !colorParam.isBlank()
                && stack.getItemMeta() instanceof LeatherArmorMeta leather) {
            DyeColor dye = ArmorPieceGoalTracker.parseDyeColorOrNull(colorParam);
            if (dye != null) {
                leather.setColor(dye.getColor());
                stack.setItemMeta(leather);
            }
        }
        if (goal.spec().type() == GoalType.BREW_POTION
                && stack.getItemMeta() instanceof PotionMeta potionMeta) {
            PotionType iconPotion = BrewPotionGoalTracker.iconPotion(goal);
            if (iconPotion != null) {
                potionMeta.setBasePotionType(iconPotion);
                stack.setItemMeta(potionMeta);
            }
        }
        return stack;
    }

    /** Claimed tiles become the claiming side's enchanted concrete block, struck through. */
    private ItemStack claimedTile(ResolvedGoal goal, Board.Claim claim) {
        Side side = challenge.sides().byId(claim.sideId());
        Material material = side == null ? goal.icon() : side.concrete();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("\u2714 ", NamedTextColor.GREEN)
                .append(Component.text(goal.requirement(), NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Claimed by ", NamedTextColor.DARK_GRAY)
                .append(Component.text(claim.playerName(), claim.color()))
                .decoration(TextDecoration.ITALIC, false));
        if (side != null && !challenge.isFfa()) {
            lore.add(Component.text(side.displayName(), side.color())
                    .decoration(TextDecoration.ITALIC, false));
        }
        return item(material,
                Component.text(goal.display(), claim.color())
                        .decoration(TextDecoration.STRIKETHROUGH, true)
                        .decoration(TextDecoration.ITALIC, false),
                lore, true);
    }

    private void toggleTarget(Player player, int cell) {
        boolean ffa = challenge.isFfa();
        ChallengeManager.TargetResult result = challenge.toggleTarget(player, cell);
        switch (result) {
            case ADDED -> player.sendMessage(plugin.msg(ffa
                    ? "<green>Tracking it.</green>"
                    : "<green>Tracking it.</green> <dark_gray>Shared with your team.</dark_gray>"));
            case REMOVED -> player.sendMessage(plugin.msg("<gray>No longer tracking that goal.</gray>"));
            case FULL -> player.sendMessage(plugin.msg((ffa
                    ? "<red>You are already tracking "
                    : "<red>Your team is already tracking ")
                    + Side.MAX_TARGETS + " goals.</red>"));
            case CLAIMED -> player.sendMessage(plugin.msg("<red>That goal is already claimed.</red>"));
            case UNAVAILABLE -> player.sendMessage(plugin.msg("<red>No round is running.</red>"));
        }
        plugin.refreshViews();
    }

    private static Component line(String label, String value, NamedTextColor labelColor, NamedTextColor valueColor) {
        return Component.text(label, labelColor).append(Component.text(value, valueColor))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        return item(material, name, lore, false);
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
        if (event.getRawSlot() == TRACKER_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && challenge.board() != null && plugin.playerTrackerGui() != null) {
                    plugin.playerTrackerGui().open(player);
                }
            });
            return;
        }
        if (event.getRawSlot() == REROLL_SLOT && player.isOp()) {
            if (plugin.catalog() == null || plugin.catalog().size() < Board.CELLS) {
                player.sendMessage(plugin.msg("<red>Not enough goal templates in goals.yml (need "
                        + Board.CELLS + ")."));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> plugin.rerollChallenge(
                    System.nanoTime(), "lo", () -> {
                        if (player.isOnline() && challenge.board() != null) {
                            open(player);
                        }
                    }));
            return;
        }
        int cell = holder.cellAt(event.getRawSlot());
        if (cell < 0) {
            return;
        }
        Board board = challenge.board();
        if (board == null) {
            return;
        }

        if (event.isRightClick() && !event.isShiftClick()) {
            toggleTarget(player, cell);
            return;
        }

        ResolvedGoal goal = board.cell(cell);
        Board.Claim claim = board.claim(cell);

        player.sendMessage(plugin.prefix()
                .append(Component.text(goal.display(), NamedTextColor.WHITE))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(goal.requirement(), NamedTextColor.GRAY)));
        if (claim == null) {
            player.sendMessage(plugin.prefix().append(Component.text("Still unclaimed.", NamedTextColor.GREEN)));
        } else {
            Side side = challenge.sides().byId(claim.sideId());
            Component message = plugin.prefix()
                    .append(Component.text("Claimed by ", NamedTextColor.GRAY))
                    .append(Component.text(claim.playerName(), claim.color()));
            if (side != null && !challenge.isFfa()) {
                message = message.append(Component.text(" for ", NamedTextColor.GRAY))
                        .append(Component.text(side.displayName(), side.color()));
            }
            player.sendMessage(message);
        }
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
