package dev.tlockout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The enchanted compass that opens the board (right-click) and tracks players (Shift + right-click).
 * It is tagged with a persistent-data key so a plain compass never triggers the board, and a tagged
 * one is never lost on death or thrown away.
 */
public final class BoardCompass implements Listener {

    /** Default display name shown when not tracking any player. */
    public static final Component DEFAULT_DISPLAY_NAME = Component.text("Board Compass", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false);

    /** Last hotbar slot, so it sits out of the way. */
    private static final int PREFERRED_SLOT = 8;
    private static final NamespacedKey COMPASS_KEY = new NamespacedKey("tlockout", "board_compass");

    private final TLockout plugin;
    private final NamespacedKey key;

    public BoardCompass(TLockout plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "board_compass");
    }

    /** Static helper for trackers that need to recognise the Board Compass without a plugin reference. */
    public static boolean isBoardCompassItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.COMPASS) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(COMPASS_KEY, PersistentDataType.BYTE);
    }

    public ItemStack create() {
        return create(null);
    }

    public ItemStack create(Player owner) {
        ItemStack stack = new ItemStack(Material.COMPASS);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(DEFAULT_DISPLAY_NAME);
            meta.lore(buildLore(null));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            if (owner != null && meta instanceof CompassMeta compassMeta && plugin.playerTracker() != null) {
                plugin.playerTracker().applyToMeta(owner, compassMeta);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<Component> buildLore(UUID quittingPlayer) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-click to open the board", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Shift + Right-click to track a player", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Lost it? Run ", NamedTextColor.DARK_GRAY)
                .append(Component.text("/lo compass", NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));

        ChallengeManager challenge = plugin.challenge();
        if (challenge != null && challenge.sides() != null) {
            lore.add(Component.empty());
            if (challenge.sides().isFfa()) {
                addFfaRosterLines(lore, challenge.sides().ordered(), quittingPlayer);
            } else {
                for (Side side : challenge.sides().ordered()) {
                    lore.add(rosterLine(side, quittingPlayer));
                }
            }
        }
        return lore;
    }

    private void addFfaRosterLines(List<Component> lore, List<Side> sides, UUID quittingPlayer) {
        lore.add(Component.text("FFA Players (" + sides.size() + "):", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        if (sides.isEmpty()) {
            lore.add(Component.text("(none)", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            return;
        }
        final int perLine = 4;
        Component line = Component.empty();
        int countOnLine = 0;
        for (Side side : sides) {
            if (countOnLine > 0) {
                line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            boolean online = false;
            for (UUID memberId : side.members()) {
                if (!memberId.equals(quittingPlayer) && Bukkit.getPlayer(memberId) != null) {
                    online = true;
                    break;
                }
            }
            if (online) {
                line = line.append(Component.text(side.displayName(), side.color())
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                line = line.append(Component.text(side.displayName() + " (offline)", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            countOnLine++;
            if (countOnLine >= perLine) {
                lore.add(line);
                line = Component.empty();
                countOnLine = 0;
            }
        }
        if (countOnLine > 0) {
            lore.add(line);
        }
    }

    private Component rosterLine(Side side, UUID quittingPlayer) {
        Component line = Component.text(side.displayName() + ": ", side.color())
                .decoration(TextDecoration.ITALIC, false);
        if (side.members().isEmpty()) {
            return line.append(Component.text("(empty)", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        boolean first = true;
        for (UUID memberId : side.members()) {
            if (!first) {
                line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            Player online = memberId.equals(quittingPlayer) ? null : Bukkit.getPlayer(memberId);
            if (online != null) {
                line = line.append(Component.text(online.getName(), NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                String cached = Bukkit.getOfflinePlayer(memberId).getName();
                String label = (cached != null && !cached.isBlank()) ? cached + " (offline)" : "offline";
                line = line.append(Component.text(label, NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            first = false;
        }
        return line;
    }

    public boolean isBoardCompass(ItemStack stack) {
        if (stack == null || stack.getType() != Material.COMPASS) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public boolean has(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isBoardCompass(stack)) {
                return true;
            }
        }
        return false;
    }

    public void give(Player player) {
        if (has(player)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack compass = create(player);
        if (inventory.getItem(PREFERRED_SLOT) == null) {
            inventory.setItem(PREFERRED_SLOT, compass);
        } else if (!inventory.addItem(compass).isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), compass);
        }
        if (plugin.playerTracker() != null) {
            plugin.playerTracker().invalidate(player.getUniqueId());
        }
    }

    public void remove(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isBoardCompass(contents[slot])) {
                inventory.setItem(slot, null);
            }
        }
    }

    public void giveToAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            give(player);
        }
        refreshAll();
    }

    public void removeFromAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            remove(player);
        }
    }

    /** Updates the lore (including live team rosters) on every online player's Board Compass. */
    public void refreshAll() {
        refreshAll(null);
    }

    private void refreshAll(UUID quittingPlayer) {
        List<Component> lore = buildLore(quittingPlayer);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(quittingPlayer)) {
                continue;
            }
            refresh(player, lore);
        }
    }

    private void refresh(Player player, List<Component> lore) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (isBoardCompass(stack)) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.lore(lore);
                    stack.setItemMeta(meta);
                    inventory.setItem(slot, stack);
                }
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (isBoardCompass(cursor)) {
            ItemMeta meta = cursor.getItemMeta();
            if (meta != null) {
                meta.lore(lore);
                cursor.setItemMeta(meta);
                player.setItemOnCursor(cursor);
            }
        }
    }

    /**
     * Updates the display name and lodestone target on every Board Compass held by {@code player}
     * (inventory slots and cursor). Passing {@code null} for {@code lodestoneTarget} clears any
     * lodestone override so the compass returns to normal behavior.
     *
     * @return true if at least one Board Compass stack was found and updated
     */
    public boolean applyTracking(Player player, Component displayName, Location lodestoneTarget) {
        boolean found = false;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (isBoardCompass(stack) && applyMeta(stack, displayName, lodestoneTarget)) {
                inventory.setItem(slot, stack);
                found = true;
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (isBoardCompass(cursor) && applyMeta(cursor, displayName, lodestoneTarget)) {
            player.setItemOnCursor(cursor);
            found = true;
        }
        return found;
    }

    private static boolean applyMeta(ItemStack stack, Component displayName, Location lodestoneTarget) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof CompassMeta compassMeta)) {
            return false;
        }
        compassMeta.displayName(displayName);
        if (lodestoneTarget != null) {
            compassMeta.setLodestone(lodestoneTarget);
            compassMeta.setLodestoneTracked(false);
        } else {
            compassMeta.clearLodestone();
        }
        stack.setItemMeta(compassMeta);
        return true;
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isBoardCompass(event.getItem())) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND
                && isBoardCompass(event.getPlayer().getInventory().getItemInMainHand())) {
            return; // the main-hand event already opened it
        }
        event.setCancelled(true);
        if (event.getPlayer().isSneaking() && plugin.playerTrackerGui() != null) {
            plugin.playerTrackerGui().open(event.getPlayer());
        } else {
            plugin.boardGui().open(event.getPlayer());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isBoardCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isBoardCompass);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.challenge().board() != null) {
            give(event.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.challenge().board() != null) {
            give(event.getPlayer());
            refreshAll();
        } else {
            remove(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.challenge().board() != null) {
            refreshAll(event.getPlayer().getUniqueId());
        }
    }
}
