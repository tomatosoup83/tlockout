package dev.tlockout;

import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import dev.tlockout.goal.tracker.AdvancementGoalTracker;
import dev.tlockout.goal.tracker.ArmorTimerGoalTracker;
import dev.tlockout.goal.tracker.BlockActionGoalTracker;
import dev.tlockout.goal.tracker.BreedGoalTracker;
import dev.tlockout.goal.tracker.BreedUniqueGoalTracker;
import dev.tlockout.goal.tracker.BrewPotionGoalTracker;
import dev.tlockout.goal.tracker.ColoredSheepKillGoalTracker;
import dev.tlockout.goal.tracker.ConditionalKillGoalTracker;
import dev.tlockout.goal.tracker.DeathGoalTracker;
import dev.tlockout.goal.tracker.MilkEffectGoalTracker;
import dev.tlockout.goal.tracker.MineBlockGoalTracker;
import dev.tlockout.goal.tracker.MobKillTotalGoalTracker;
import dev.tlockout.goal.tracker.MobKillUniqueGoalTracker;
import dev.tlockout.goal.tracker.OpponentGoalTracker;
import dev.tlockout.goal.tracker.PlayerKillGoalTracker;
import dev.tlockout.goal.tracker.TameGoalTracker;
import dev.tlockout.goal.tracker.UniqueFoodsGoalTracker;
import dev.tlockout.goal.tracker.WolfArmorGoalTracker;
import dev.tlockout.goal.tracker.WorkstationGoalTracker;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.ChiseledBookshelf;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Wolf;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

/**
 * Detects goal completion and exposes partial progress.
 * <p>
 * Each {@link GoalTracker} owns the progress calculation for its family. Event-driven trackers
 * accumulate counters in {@link TrackingState}, which is reset when a board starts. State-driven
 * trackers read the player's inventory, biome or other live state each check cycle.
 */
public final class TrackingService implements Listener {

    private static final long BRUSH_TRACK_TIMEOUT_MS = 12_000L;

    private record BrushTarget(UUID worldId, int x, int y, int z, Material original, long startedAt) {
        boolean matches(Block block) {
            return block.getWorld().getUID().equals(worldId)
                    && block.getX() == x
                    && block.getY() == y
                    && block.getZ() == z;
        }
    }

    private final TLockout plugin;
    private final ChallengeManager challenge;
    private final TrackingState state = new TrackingState();
    private final Map<UUID, Integer> sprintBaselines = new HashMap<>();
    private final Map<UUID, BrushTarget> activeBrushes = new HashMap<>();

    public TrackingService(TLockout plugin, ChallengeManager challenge) {
        this.plugin = plugin;
        this.challenge = challenge;
    }

    public void resetStats() {
        state.clear();
        sprintBaselines.clear();
        activeBrushes.clear();
        // Vanilla statistics persist across rounds; record the current value so only distance
        // travelled after the round starts counts.
        for (Player player : Bukkit.getOnlinePlayers()) {
            sprintBaselines.put(player.getUniqueId(),
                    player.getStatistic(Statistic.SPRINT_ONE_CM));
        }
    }

    /**
     * Clears one player's round counters/sets and snapshots their current sprint statistic as a
     * fresh baseline so pre-join or prior-team progress does not carry over.
     */
    public void resetPlayerTracking(Player player) {
        UUID playerId = player.getUniqueId();
        state.clearPlayer(playerId);
        activeBrushes.remove(playerId);
        sprintBaselines.put(playerId, player.getStatistic(Statistic.SPRINT_ONE_CM));
    }

    // --------------------------------------------------------------- events

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null && event.getDamageSource() != null
                && event.getDamageSource().getCausingEntity() instanceof Player causing) {
            killer = causing;
        }
        if (killer == null) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            state.add(killer.getUniqueId(), PlayerKillGoalTracker.STAT_KEY, 1);
        } else {
            UUID killerId = killer.getUniqueId();
            state.add(killerId, TrackingState.mobKey(event.getEntityType()), 1);
            if (event.getEntity() instanceof Mob) {
                Side killerSide = challenge.sideOf(killer);
                state.add(killerId, MobKillTotalGoalTracker.STAT_KEY, 1);
                String typeKey = event.getEntityType().getKey().toString().toLowerCase(Locale.ROOT);
                state.addPlayerSet(killerId, MobKillUniqueGoalTracker.KILLED_MOB_SET, typeKey);
                if (killerSide != null) {
                    state.addSideSet(killerSide.id(), MobKillUniqueGoalTracker.KILLED_MOB_SET, typeKey);
                }
                if (event.getEntity() instanceof Enemy) {
                    state.addPlayerSet(killerId, MobKillUniqueGoalTracker.HOSTILE_SET, typeKey);
                    if (killerSide != null) {
                        state.addSideSet(killerSide.id(), MobKillUniqueGoalTracker.HOSTILE_SET, typeKey);
                    }
                }
                if (event.getEntity() instanceof Sheep sheep) {
                    DyeColor color = sheep.getColor();
                    if (color != null) {
                        state.add(killerId, ColoredSheepKillGoalTracker.statKeyFor(color), 1);
                    }
                }
            }
            Board board = challenge.board();
            if (board != null) {
                for (int cell = 0; cell < Board.CELLS; cell++) {
                    if (board.claimed(cell)) {
                        continue;
                    }
                    ResolvedGoal goal = board.cell(cell);
                    if (goal.spec().tracker() instanceof ConditionalKillGoalTracker conditional
                            && conditional.matches(event, goal)) {
                        state.add(killerId, conditional.statKey(goal), 1);
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!challenge.running()) {
            return;
        }
        if (event.getOwner() instanceof Player player) {
            state.add(player.getUniqueId(), TameGoalTracker.statKeyFor(event.getEntity().getType()), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!challenge.running()) {
            return;
        }
        if (event.getBreeder() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            state.add(playerId, BreedGoalTracker.statKeyFor(event.getEntity().getType()), 1);
            String typeKey = event.getEntity().getType().getKey().toString().toLowerCase(Locale.ROOT);
            state.addPlayerSet(playerId, BreedUniqueGoalTracker.BRED_SET, typeKey);
            Side side = challenge.sideOf(player);
            if (side != null) {
                state.addSideSet(side.id(), BreedUniqueGoalTracker.BRED_SET, typeKey);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player player = event.getPlayer();
        Side side = challenge.sideOf(player);
        if (side == null) {
            return;
        }
        ItemStack item = event.getItem();
        Material material = item.getType();
        state.add(player.getUniqueId(), "CONSUME:" + material.name(), 1);

        if (material == Material.POTION) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof PotionMeta potionMeta) {
                PotionType base = potionMeta.getBasePotionType();
                if (base != null) {
                    state.add(player.getUniqueId(), "CONSUME:POTION:" + base.name(), 1);
                }
            }
        }

        if (material.isEdible()) {
            state.add(player.getUniqueId(), OpponentGoalTracker.EAT_FOOD_KEY, 1);
            state.addPlayerSet(player.getUniqueId(), UniqueFoodsGoalTracker.FOOD_SET, material.name());
            state.addSideSet(side.id(), UniqueFoodsGoalTracker.FOOD_SET, material.name());
        }
    }

    /** Cake, cauldron wash, jukebox and flower-pot statistics. */
    @EventHandler
    public void onStatistic(PlayerStatisticIncrementEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player player = event.getPlayer();
        if (challenge.sideOf(player) == null) {
            return;
        }
        int delta = Math.max(0, event.getNewValue() - event.getPreviousValue());
        if (delta == 0) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Statistic stat = event.getStatistic();
        if (stat == Statistic.CAKE_SLICES_EATEN) {
            state.add(playerId, "CONSUME:CAKE", delta);
            state.add(playerId, OpponentGoalTracker.EAT_FOOD_KEY, delta);
        } else if (stat == Statistic.ARMOR_CLEANED
                || stat == Statistic.BANNER_CLEANED
                || stat == Statistic.CLEAN_SHULKER_BOX) {
            state.add(playerId, WorkstationGoalTracker.statKeyFor(WorkstationGoalTracker.Station.CAULDRON), delta);
        } else if (stat == Statistic.RECORD_PLAYED) {
            state.add(playerId, BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.USE_JUKEBOX), delta);
        } else if (stat == Statistic.FLOWER_POTTED) {
            state.add(playerId, BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.POT_FLOWER), delta);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player player = event.getEntity();
        if (challenge.sideOf(player) == null) {
            return;
        }
        state.add(player.getUniqueId(), OpponentGoalTracker.DEATHS_KEY, 1);
        Board board = challenge.board();
        if (board == null) {
            return;
        }
        for (int cell = 0; cell < Board.CELLS; cell++) {
            if (board.claimed(cell)) {
                continue;
            }
            ResolvedGoal goal = board.cell(cell);
            if (goal.spec().tracker() instanceof DeathGoalTracker death
                    && death.matches(event, goal)) {
                state.add(player.getUniqueId(), death.statKey(goal), 1);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!challenge.running() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (challenge.sideOf(player) == null) {
            return;
        }
        double damage = event.getFinalDamage();
        if (damage > 0) {
            UUID playerId = player.getUniqueId();
            state.addDouble(playerId, "DAMAGE_TAKEN", damage);
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.FALL) {
                state.add(playerId, OpponentGoalTracker.FALL_DAMAGE_KEY, 1);
            } else if (cause == EntityDamageEvent.DamageCause.FIRE
                    || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                    || cause == EntityDamageEvent.DamageCause.LAVA
                    || cause == EntityDamageEvent.DamageCause.CAMPFIRE) {
                state.add(playerId, OpponentGoalTracker.CATCH_FIRE_KEY, 1);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player attacker = playerDamager(event.getDamager());
        if (attacker == null || challenge.sideOf(attacker) == null
                || event.getEntity().equals(attacker)) {
            return;
        }
        double damage = event.getFinalDamage();
        if (damage > 0) {
            state.addDouble(attacker.getUniqueId(), "DAMAGE_DEALT", damage);
        }
    }

    private static Player playerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player player = event.getPlayer();
        Side side = challenge.sideOf(player);
        if (side == null) {
            return;
        }
        Advancement advancement = event.getAdvancement();
        if (advancement == null) {
            return;
        }
        NamespacedKey key = advancement.getKey();
        if (key.getKey().startsWith("recipes/") || advancement.getDisplay() == null) {
            return;
        }
        state.addPlayerSet(player.getUniqueId(), AdvancementGoalTracker.ADVANCEMENT_SET, key.toString());
        state.addSideSet(side.id(), AdvancementGoalTracker.ADVANCEMENT_SET, key.toString());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!challenge.running() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (challenge.sideOf(player) == null) {
            return;
        }
        if (event.getCause() == EntityPotionEffectEvent.Cause.MILK) {
            state.add(player.getUniqueId(), MilkEffectGoalTracker.STAT_KEY, 1);
            return;
        }
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }
        PotionEffect effect = event.getNewEffect();
        if (effect == null) {
            return;
        }
        state.add(player.getUniqueId(), "EFFECT:" + effect.getType().getKey(), 1);
    }

    /** Primary detection for equipping wolf armor on a tamed wolf via Paper's equipment-change event. */
    @EventHandler
    public void onEntityEquipmentChanged(EntityEquipmentChangedEvent event) {
        if (!challenge.running() || !(event.getEntity() instanceof Wolf wolf)) {
            return;
        }
        EntityEquipmentChangedEvent.EquipmentChange change =
                event.getEquipmentChanges().get(EquipmentSlot.BODY);
        if (change == null) {
            return;
        }
        if (change.newItem().getType() != Material.WOLF_ARMOR
                || change.oldItem().getType() == Material.WOLF_ARMOR) {
            return;
        }
        if (!wolf.isTamed()) {
            return;
        }
        UUID ownerId = wolf.getOwnerUniqueId();
        if (ownerId != null && challenge.sides().sideOf(ownerId) != null) {
            state.add(ownerId, WolfArmorGoalTracker.STAT_KEY, 1);
        }
    }

    /** Fallback 1-tick confirmation if a player right-clicks their tamed wolf with wolf armor. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractWolf(PlayerInteractEntityEvent event) {
        if (!challenge.running() || !(event.getRightClicked() instanceof Wolf wolf)) {
            return;
        }
        Player player = event.getPlayer();
        if (challenge.sideOf(player) == null || !wolf.isTamed()) {
            return;
        }
        if (!player.getUniqueId().equals(wolf.getOwnerUniqueId())) {
            return;
        }
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (held == null || held.getType() != Material.WOLF_ARMOR) {
            return;
        }
        EntityEquipment equipment = wolf.getEquipment();
        if (equipment != null && equipment.getItem(EquipmentSlot.BODY).getType() == Material.WOLF_ARMOR) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!challenge.running() || !wolf.isValid()) {
                return;
            }
            EntityEquipment updated = wolf.getEquipment();
            if (updated != null && updated.getItem(EquipmentSlot.BODY).getType() == Material.WOLF_ARMOR) {
                state.add(playerId, WolfArmorGoalTracker.STAT_KEY, 1);
            }
        });
    }

    /** Catches even brief water contact (e.g. a quick water-bucket MLG) between poll ticks. */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player player = event.getPlayer();
        if (challenge.sideOf(player) == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (state.playerStat(playerId, OpponentGoalTracker.TOUCH_WATER_KEY) == 0 && player.isInWater()) {
            state.add(playerId, OpponentGoalTracker.TOUCH_WATER_KEY, 1);
        }
    }

    // ------------------------------------------------- Slice 12 events

    /** Tracks workstation result-slot takes and brewed potion collection from brewing stands. */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!challenge.running() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (challenge.sideOf(player) == null) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked != event.getView().getTopInventory()) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }

        if (clicked instanceof BrewerInventory && event.getRawSlot() >= 0 && event.getRawSlot() <= 2) {
            Material type = current.getType();
            if ((type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION)
                    && isTakingFromBrewingSlot(event, player, current)) {
                UUID playerId = player.getUniqueId();
                if (type == Material.LINGERING_POTION) {
                    state.add(playerId, BrewPotionGoalTracker.LINGERING_KEY, 1);
                }
                if (current.getItemMeta() instanceof PotionMeta potionMeta) {
                    PotionType base = potionMeta.getBasePotionType();
                    if (base != null) {
                        state.add(playerId, BrewPotionGoalTracker.potionKey(base), 1);
                    }
                }
            }
            return;
        }

        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        WorkstationGoalTracker.Station station = workstationFor(clicked.getType());
        if (station == null || !isTakingFromResultSlot(event, player, current)) {
            return;
        }
        if (station == WorkstationGoalTracker.Station.ANVIL
                && event.getView() instanceof AnvilView anvilView
                && player.getGameMode() != GameMode.CREATIVE) {
            int cost = anvilView.getRepairCost();
            if (cost <= 0 || cost > player.getLevel()) {
                return;
            }
        }
        state.add(player.getUniqueId(), WorkstationGoalTracker.statKeyFor(station), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player player = event.getEnchanter();
        if (challenge.sideOf(player) != null) {
            state.add(player.getUniqueId(),
                    WorkstationGoalTracker.statKeyFor(WorkstationGoalTracker.Station.ENCHANTING_TABLE), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        if (!challenge.running() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (challenge.sideOf(player) == null) {
            return;
        }
        CauldronLevelChangeEvent.ChangeReason reason = event.getReason();
        if (reason == CauldronLevelChangeEvent.ChangeReason.ARMOR_WASH
                || reason == CauldronLevelChangeEvent.ChangeReason.BANNER_WASH
                || reason == CauldronLevelChangeEvent.ChangeReason.SHULKER_WASH) {
            state.add(player.getUniqueId(),
                    WorkstationGoalTracker.statKeyFor(WorkstationGoalTracker.Station.CAULDRON), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!challenge.running()) {
            return;
        }
        Block block = event.getBlock();
        // Breaking a suspicious block is not brushing it; clear any active brush tracker on it.
        activeBrushes.values().removeIf(target -> target.matches(block));
        Player player = event.getPlayer();
        if (challenge.sideOf(player) != null) {
            state.add(player.getUniqueId(), MineBlockGoalTracker.statKeyFor(block.getType()), 1);
        }
    }

    /**
     * Handles block and right-click-air interactions (composter harvest, jukebox, glow ink on
     * crimson sign, campfire, chiseled bookshelf, candle lighting, goat horn, suspicious-block
     * brushing). Does not use ignoreCancelled=true because RIGHT_CLICK_AIR sets block-use DENY.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player player = event.getPlayer();
        if (challenge.sideOf(player) == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        UUID playerId = player.getUniqueId();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.GOAT_HORN
                && event.useItemInHand() != Event.Result.DENY
                && !player.hasCooldown(Material.GOAT_HORN)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (challenge.running() && player.isOnline() && player.hasCooldown(Material.GOAT_HORN)) {
                    state.add(playerId,
                            BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.TOOT_GOAT_HORN), 1);
                }
            });
        }

        if (action != Action.RIGHT_CLICK_BLOCK || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Material blockType = block.getType();

        // Full composter -> harvest bone meal
        if (blockType == Material.COMPOSTER
                && block.getBlockData() instanceof Levelled levelled
                && levelled.getLevel() == levelled.getMaximumLevel()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (challenge.running()
                        && block.getType() == Material.COMPOSTER
                        && block.getBlockData() instanceof Levelled after
                        && after.getLevel() == 0) {
                    state.add(playerId,
                            WorkstationGoalTracker.statKeyFor(WorkstationGoalTracker.Station.COMPOSTER), 1);
                }
            });
            return;
        }

        // Jukebox -> insert music disc
        if (blockType == Material.JUKEBOX
                && item != null && item.getType().isRecord()
                && block.getState() instanceof Jukebox jukebox
                && !jukebox.hasRecord()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (challenge.running()
                        && block.getState() instanceof Jukebox after
                        && after.hasRecord()) {
                    state.add(playerId,
                            BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.USE_JUKEBOX), 1);
                }
            });
            return;
        }

        // Glow ink sac on Crimson Sign (normal, wall, hanging, wall hanging)
        if (isCrimsonSign(blockType)
                && item != null && item.getType() == Material.GLOW_INK_SAC
                && block.getState() instanceof Sign sign
                && !sign.isWaxed()) {
            org.bukkit.block.sign.Side targetSide = sign.getInteractableSideFor(player);
            if (!sign.getSide(targetSide).isGlowingText()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (challenge.running()
                            && block.getState() instanceof Sign after
                            && after.getSide(targetSide).isGlowingText()) {
                        state.add(playerId,
                                BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.GLOW_INK_CRIMSON_SIGN),
                                1);
                    }
                });
            }
            return;
        }

        // Campfire -> 4 food items cooking simultaneously
        if ((blockType == Material.CAMPFIRE || blockType == Material.SOUL_CAMPFIRE)
                && item != null && !item.getType().isAir()
                && block.getState() instanceof Campfire) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (challenge.running()
                        && block.getState() instanceof Campfire after
                        && isCampfireFull(after)) {
                    state.add(playerId,
                            BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.FILL_CAMPFIRE), 1);
                }
            });
            return;
        }

        // Chiseled Bookshelf -> all 6 slots occupied
        if (blockType == Material.CHISELED_BOOKSHELF
                && block.getBlockData() instanceof ChiseledBookshelf shelf
                && shelf.getOccupiedSlots().size() < shelf.getMaximumOccupiedSlots()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (challenge.running()
                        && block.getBlockData() instanceof ChiseledBookshelf after
                        && after.getOccupiedSlots().size() >= after.getMaximumOccupiedSlots()) {
                    state.add(playerId,
                            BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.FILL_CHISELED_BOOKSHELF),
                            1);
                }
            });
            return;
        }

        // Light any candle or candle cake with flint & steel or fire charge
        if (isCandleBlock(blockType)
                && block.getBlockData() instanceof Lightable lightable
                && !lightable.isLit()
                && item != null
                && (item.getType() == Material.FLINT_AND_STEEL || item.getType() == Material.FIRE_CHARGE)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (challenge.running()
                        && block.getBlockData() instanceof Lightable after
                        && after.isLit()) {
                    state.add(playerId,
                            BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.LIGHT_CANDLE), 1);
                }
            });
            return;
        }

        // Track suspicious sand/gravel brushing to detect completion when it converts to sand/gravel
        if ((blockType == Material.SUSPICIOUS_SAND || blockType == Material.SUSPICIOUS_GRAVEL)
                && item != null && item.getType() == Material.BRUSH) {
            activeBrushes.put(playerId, new BrushTarget(
                    block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                    blockType, System.currentTimeMillis()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || challenge.sideOf(player) == null) {
            return;
        }
        if (isCandleBlock(event.getBlock().getType())) {
            state.add(player.getUniqueId(),
                    BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.LIGHT_CANDLE), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlowerPotManipulate(PlayerFlowerPotManipulateEvent event) {
        if (!challenge.running() || !event.isPlacing()) {
            return;
        }
        Player player = event.getPlayer();
        if (challenge.sideOf(player) != null) {
            state.add(player.getUniqueId(),
                    BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.POT_FLOWER), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!challenge.running()) {
            return;
        }
        Player player = event.getPlayer();
        if (challenge.sideOf(player) == null) {
            return;
        }
        ArmorStand stand = event.getRightClicked();
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (challenge.running() && stand.isValid() && isArmorStandFull(stand)) {
                state.add(playerId,
                        BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.FILL_ARMOR_STAND), 1);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        if (!challenge.running()
                || event.getAction() != PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE) {
            return;
        }
        if (event.getItemFrame() instanceof GlowItemFrame) {
            return;
        }
        if (event.getItemStack().getType() != Material.ITEM_FRAME) {
            return;
        }
        Player player = event.getPlayer();
        if (challenge.sideOf(player) != null) {
            state.add(player.getUniqueId(),
                    BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.ITEM_FRAME_IN_ITEM_FRAME), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (!challenge.running() || event.getEntityType() != EntityType.END_CRYSTAL) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && challenge.sideOf(player) != null) {
            state.add(player.getUniqueId(),
                    BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.PLACE_END_CRYSTAL), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!challenge.running() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (challenge.sideOf(player) == null) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.CROSSBOW) {
            return;
        }
        ItemStack consumable = event.getConsumable();
        boolean firedRocket = event.getProjectile() instanceof Firework
                || (consumable != null && consumable.getType() == Material.FIREWORK_ROCKET);
        if (firedRocket) {
            state.add(player.getUniqueId(),
                    BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.SHOOT_FIREWORK_CROSSBOW), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemCooldown(PlayerItemCooldownEvent event) {
        if (!challenge.running() || event.getType() != Material.GOAT_HORN || event.getCooldown() <= 0) {
            return;
        }
        Player player = event.getPlayer();
        if (challenge.sideOf(player) != null) {
            state.add(player.getUniqueId(),
                    BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.TOOT_GOAT_HORN), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!challenge.running() || event.getItem().getType() != Material.BRUSH) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        if (activeBrushes.containsKey(playerId)) {
            Bukkit.getScheduler().runTask(plugin, () -> checkBrushTarget(playerId));
        }
    }

    @EventHandler
    public void onStopUsingItem(PlayerStopUsingItemEvent event) {
        if (!challenge.running() || event.getItem().getType() != Material.BRUSH) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        if (activeBrushes.containsKey(playerId)) {
            Bukkit.getScheduler().runTask(plugin, () -> checkBrushTarget(playerId));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!challenge.running()
                || !(event.getEntity() instanceof PigZombie)
                || !(event.getTarget() instanceof Player player)) {
            return;
        }
        if (challenge.sideOf(player) != null) {
            state.add(player.getUniqueId(),
                    BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.ANGER_ZOMBIFIED_PIGLIN), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShieldDisable(PlayerShieldDisableEvent event) {
        if (!challenge.running() || event.getCooldown() <= 0) {
            return;
        }
        Player player = event.getPlayer();
        if (challenge.sideOf(player) != null) {
            state.add(player.getUniqueId(),
                    BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.SHIELD_DISABLED), 1);
        }
    }

    /** Detects eggs and snowballs thrown by one team hitting an opposing player (0-damage hits). */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!challenge.running() || !(event.getHitEntity() instanceof Player victim)) {
            return;
        }
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Egg) && !(projectile instanceof Snowball)) {
            return;
        }
        if (!(projectile.getShooter() instanceof Player shooter) || shooter.equals(victim)) {
            return;
        }
        Side shooterSide = challenge.sideOf(shooter);
        Side victimSide = challenge.sideOf(victim);
        if (shooterSide == null || victimSide == null || shooterSide.id().equals(victimSide.id())) {
            return;
        }
        if (projectile instanceof Egg) {
            state.add(victim.getUniqueId(), OpponentGoalTracker.HIT_BY_EGG_KEY, 1);
        } else {
            state.add(victim.getUniqueId(), OpponentGoalTracker.HIT_BY_SNOWBALL_KEY, 1);
        }
    }

    // ------------------------------------------------- Slice 12 helpers

    private static WorkstationGoalTracker.Station workstationFor(InventoryType type) {
        return switch (type) {
            case ANVIL -> WorkstationGoalTracker.Station.ANVIL;
            case GRINDSTONE -> WorkstationGoalTracker.Station.GRINDSTONE;
            case LOOM -> WorkstationGoalTracker.Station.LOOM;
            case STONECUTTER -> WorkstationGoalTracker.Station.STONECUTTER;
            case SMITHING, SMITHING_NEW -> WorkstationGoalTracker.Station.SMITHING_TABLE;
            default -> null;
        };
    }

    private static boolean isTakingFromResultSlot(InventoryClickEvent event, Player player, ItemStack current) {
        return switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 COLLECT_TO_CURSOR, DROP_ONE_SLOT, DROP_ALL_SLOT -> true;
            case MOVE_TO_OTHER_INVENTORY -> hasStorageRoom(player, current);
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                if (event.getClick() == ClickType.SWAP_OFFHAND) {
                    ItemStack offhand = player.getInventory().getItemInOffHand();
                    yield offhand == null || offhand.getType().isAir();
                }
                int hotbar = event.getHotbarButton();
                if (hotbar < 0 || hotbar > 8) {
                    yield false;
                }
                ItemStack slotItem = player.getInventory().getItem(hotbar);
                yield slotItem == null || slotItem.getType().isAir();
            }
            default -> false;
        };
    }

    private static boolean isTakingFromBrewingSlot(InventoryClickEvent event, Player player, ItemStack current) {
        return switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 SWAP_WITH_CURSOR, COLLECT_TO_CURSOR, DROP_ONE_SLOT, DROP_ALL_SLOT,
                 HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> true;
            case MOVE_TO_OTHER_INVENTORY -> hasStorageRoom(player, current);
            default -> false;
        };
    }

    private static boolean hasStorageRoom(Player player, ItemStack item) {
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                return true;
            }
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCrimsonSign(Material type) {
        return type == Material.CRIMSON_SIGN
                || type == Material.CRIMSON_WALL_SIGN
                || type == Material.CRIMSON_HANGING_SIGN
                || type == Material.CRIMSON_WALL_HANGING_SIGN;
    }

    private static boolean isCandleBlock(Material type) {
        return Tag.CANDLES.isTagged(type) || Tag.CANDLE_CAKES.isTagged(type);
    }

    private static boolean isCampfireFull(Campfire campfire) {
        int size = campfire.getSize();
        if (size <= 0) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            ItemStack food = campfire.getItem(i);
            if (food == null || food.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isArmorStandFull(ArmorStand stand) {
        EntityEquipment eq = stand.getEquipment();
        if (eq == null) {
            return false;
        }
        return !eq.getItem(EquipmentSlot.HEAD).getType().isAir()
                && !eq.getItem(EquipmentSlot.CHEST).getType().isAir()
                && !eq.getItem(EquipmentSlot.LEGS).getType().isAir()
                && !eq.getItem(EquipmentSlot.FEET).getType().isAir();
    }

    private void checkBrushTarget(UUID playerId) {
        if (!challenge.running()) {
            return;
        }
        BrushTarget target = activeBrushes.get(playerId);
        if (target == null) {
            return;
        }
        World world = Bukkit.getWorld(target.worldId());
        if (world == null) {
            activeBrushes.remove(playerId);
            return;
        }
        Material current = world.getBlockAt(target.x(), target.y(), target.z()).getType();
        Material expectedAfter = target.original() == Material.SUSPICIOUS_SAND
                ? Material.SAND
                : Material.GRAVEL;
        if (current == expectedAfter) {
            activeBrushes.remove(playerId);
            if (challenge.sides().sideOf(playerId) != null) {
                state.add(playerId,
                        BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.BRUSH_SUSPICIOUS_BLOCK), 1);
            }
        } else if (current != target.original()) {
            activeBrushes.remove(playerId);
        }
    }

    private void checkActiveBrushes() {
        if (activeBrushes.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, BrushTarget>> it = activeBrushes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BrushTarget> entry = it.next();
            UUID playerId = entry.getKey();
            BrushTarget target = entry.getValue();
            World world = Bukkit.getWorld(target.worldId());
            if (world == null) {
                it.remove();
                continue;
            }
            Material current = world.getBlockAt(target.x(), target.y(), target.z()).getType();
            Material expectedAfter = target.original() == Material.SUSPICIOUS_SAND
                    ? Material.SAND
                    : Material.GRAVEL;
            if (current == expectedAfter) {
                it.remove();
                if (challenge.sides().sideOf(playerId) != null) {
                    state.add(playerId,
                            BlockActionGoalTracker.statKeyFor(BlockActionGoalTracker.Action.BRUSH_SUSPICIOUS_BLOCK), 1);
                }
            } else if (current != target.original() || now - target.startedAt() > BRUSH_TRACK_TIMEOUT_MS) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------ checking

    /** Runs every check-interval-ticks; claims anything newly completed and refreshes the HUD. */
    public void checkGoals() {
        Board board = challenge.board();
        if (board == null) {
            return;
        }
        updateSprintProgress();
        updateArmorTimers(board);
        updateOpponentStates();
        checkActiveBrushes();
        for (Side side : challenge.sides().all()) {
            if (side.size() == 0) {
                continue; // empty sides cannot claim goals
            }
            List<Player> online = onlineMembers(side);
            for (int cell = 0; cell < Board.CELLS; cell++) {
                if (board.claimed(cell)) {
                    continue;
                }
                ResolvedGoal goal = board.cell(cell);
                if (goal.spec().tracker().opponentGoal()) {
                    Side opponent = challenge.sides().otherOf(side);
                    if (opponent == null || opponent.size() == 0) {
                        continue;
                    }
                    if (teamProgress(side, goal) < goal.target()) {
                        continue;
                    }
                    List<Player> onlineOpponents = onlineMembers(opponent);
                    Player trigger = onlineOpponents.isEmpty() ? null : claimant(onlineOpponents, goal);
                    challenge.tryClaimOpponent(side, trigger, opponent, cell);
                    continue;
                }
                if (online.isEmpty()) {
                    continue; // offline-only sides cannot complete state-driven goals
                }
                if (teamProgress(side, goal) < goal.target()) {
                    continue;
                }
                challenge.tryClaimFor(side, claimant(online, goal), cell);
            }
        }
        if (plugin.hud() != null) {
            plugin.hud().updateAll();
        }
    }

    /**
     * The member credited with a team-completed goal: the one with the largest personal
     * contribution, or the first online member when the total came from event counters only.
     */
    private Player claimant(List<Player> online, ResolvedGoal goal) {
        Player best = online.get(0);
        int bestProgress = progress(best, goal);
        for (int i = 1; i < online.size(); i++) {
            Player candidate = online.get(i);
            int candidateProgress = progress(candidate, goal);
            if (candidateProgress > bestProgress) {
                best = candidate;
                bestProgress = candidateProgress;
            }
        }
        return best;
    }

    /**
     * Vanilla movement statistics are updated internally and do not reliably fire
     * {@link PlayerStatisticIncrementEvent}, so poll the sprint statistic against the round-start
     * baseline instead.
     */
    private void updateSprintProgress() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (challenge.sideOf(player) == null) {
                continue;
            }
            UUID playerId = player.getUniqueId();
            int current = player.getStatistic(Statistic.SPRINT_ONE_CM);
            Integer baseline = sprintBaselines.get(playerId);
            if (baseline == null || current < baseline) {
                // First observation in this round (mid-round join) or a statistic reset.
                sprintBaselines.put(playerId, current);
                state.setPlayerStat(playerId, "STAT:SPRINT_ONE_CM", 0);
                continue;
            }
            state.setPlayerStat(playerId, "STAT:SPRINT_ONE_CM", current - baseline);
        }
    }

    private void updateArmorTimers(Board board) {
        if (!challenge.running()) {
            return;
        }
        Set<Material> trackedItems = EnumSet.noneOf(Material.class);
        for (int cell = 0; cell < Board.CELLS; cell++) {
            if (board.claimed(cell)) {
                continue;
            }
            ResolvedGoal goal = board.cell(cell);
            if (goal.spec().type() == GoalType.ARMOR_TIMER) {
                Material material = goal.materialParam("item");
                if (material != null) {
                    trackedItems.add(material);
                }
            }
        }
        if (trackedItems.isEmpty()) {
            return;
        }
        int ticks = plugin.checkIntervalTicks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (challenge.sideOf(player) == null) {
                continue;
            }
            for (Material material : trackedItems) {
                if (ArmorTimerGoalTracker.isWearing(player, material)) {
                    state.add(player.getUniqueId(), ArmorTimerGoalTracker.statKeyFor(material), ticks);
                }
            }
        }
    }

    private void updateOpponentStates() {
        if (!challenge.running()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (challenge.sideOf(player) == null) {
                continue;
            }
            UUID playerId = player.getUniqueId();
            if (state.playerStat(playerId, OpponentGoalTracker.CATCH_FIRE_KEY) == 0
                    && player.getFireTicks() > 0) {
                state.add(playerId, OpponentGoalTracker.CATCH_FIRE_KEY, 1);
            }
            if (state.playerStat(playerId, OpponentGoalTracker.TOUCH_WATER_KEY) == 0
                    && player.isInWater()) {
                state.add(playerId, OpponentGoalTracker.TOUCH_WATER_KEY, 1);
            }
        }
    }

    // ------------------------------------------------------------- progress

    /** This player's current progress toward a goal (0 when they have not started it). */
    public int progress(Player player, ResolvedGoal goal) {
        return goal.spec().tracker().progress(player, goal, state);
    }

    /**
     * The side's combined progress. Opponent goals evaluate the opposing side; trackers with
     * team-wide sets or best-single-player semantics override this; the rest sum members and
     * include offline event counters.
     */
    public int teamProgress(Side side, ResolvedGoal goal) {
        if (goal.spec().tracker().opponentGoal()) {
            Side opponent = challenge.sides().otherOf(side);
            if (opponent == null || opponent.size() == 0) {
                return 0;
            }
            return goal.spec().tracker().teamProgress(opponent, goal, state);
        }
        return goal.spec().tracker().teamProgress(side, goal, state);
    }

    private static List<Player> onlineMembers(Side side) {
        List<Player> online = new ArrayList<>();
        for (UUID memberId : side.members()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }
}
