package dev.tlockout;

import dev.tlockout.goal.WorldFeasibilityScanner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class TLockout extends JavaPlugin {

    private GoalCatalog catalog;
    private LockoutMode mode = LockoutMode.TEAMS;
    private SideManager sides;
    private ChallengeManager challenge;
    private TrackingService tracking;
    private BoardGui boardGui;
    private BoardCompass boardCompass;
    private PlayerTrackerService playerTracker;
    private PlayerTrackerGui playerTrackerGui;
    private HudService hud;
    private PlayerResetService playerReset;
    private KeepInventoryService keepInventory;
    private WorldFeasibilityScanner worldScanner;
    private SoundService sounds;
    private FireworkService fireworks;
    private CountdownService countdown;
    private int teamsWinTarget = 13;
    private int ffaWinTarget = 5;
    private org.bukkit.scheduler.BukkitTask pendingScanTask;
    private BukkitRunnable checkTask;
    private int checkIntervalTicks = 10;
    private Component prefix = Component.empty();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        prepareGoalsFile();
        applyPrefix();

        try {
            mode = LockoutMode.parse(getConfig().getString("mode", "teams"));
        } catch (RuntimeException ex) {
            getLogger().severe("Invalid config.yml mode: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            // Validate teams section on startup even when starting in FFA so /lo mode teams is safe.
            SideManager loadedTeams = SideManager.load(getConfig().getConfigurationSection("teams"));
            sides = mode == LockoutMode.FFA ? SideManager.ffa() : loadedTeams;
        } catch (RuntimeException ex) {
            getLogger().severe("Could not load teams from config.yml: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            catalog = GoalCatalog.load(this);
        } catch (RuntimeException ex) {
            getLogger().severe("Could not load goals.yml: " + ex.getMessage());
            getLogger().severe("Fix that file, or delete "
                    + new java.io.File(getDataFolder(), "goals.yml").getAbsolutePath()
                    + " and restart to restore the bundled defaults.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Loaded " + catalog.size() + " goal templates.");
        if (catalog.size() < Board.CELLS) {
            getLogger().warning("Only " + catalog.size() + " templates defined, but a board needs "
                    + Board.CELLS + ".");
        }

        teamsWinTarget = loadConfigWinTarget(LockoutMode.TEAMS);
        ffaWinTarget = loadConfigWinTarget(LockoutMode.FFA);
        challenge = new ChallengeManager(this, catalog, sides, winTargetFor(mode));
        tracking = new TrackingService(this, challenge);
        boardGui = new BoardGui(this, challenge);
        boardCompass = new BoardCompass(this);
        playerTracker = new PlayerTrackerService(this);
        playerTrackerGui = new PlayerTrackerGui(this, challenge);
        hud = new HudService(this);
        playerReset = new PlayerResetService(this, loadAutoReset());
        keepInventory = new KeepInventoryService(this, loadKeepInventory());
        worldScanner = new WorldFeasibilityScanner(getLogger(), loadWorldScanEnabled(), loadWorldScanRadius());
        sounds = new SoundService(this, getConfig().getConfigurationSection("sounds"));
        fireworks = new FireworkService(this, loadFireworksOnClaim());
        countdown = new CountdownService(this, loadCountdownSeconds(), loadFreezeDuringCountdown());

        getServer().getPluginManager().registerEvents(playerReset, this);
        getServer().getPluginManager().registerEvents(keepInventory, this);
        getServer().getPluginManager().registerEvents(countdown, this);
        getServer().getPluginManager().registerEvents(tracking, this);
        getServer().getPluginManager().registerEvents(boardGui, this);
        getServer().getPluginManager().registerEvents(boardCompass, this);
        getServer().getPluginManager().registerEvents(playerTracker, this);
        getServer().getPluginManager().registerEvents(playerTrackerGui, this);
        getServer().getPluginManager().registerEvents(hud, this);
        getServer().getPluginManager().registerEvents(fireworks, this);
        getServer().getPluginManager().registerEvents(new TeamCombatListener(this), this);

        PluginCommand command = getCommand("lockout");
        if (command != null) {
            LockoutCommand executor = new LockoutCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Command 'lockout' is missing from plugin.yml");
        }

        scheduleChecker();
        playerTracker.startTask();
        if (mode == LockoutMode.FFA) {
            getLogger().info("TLockout enabled - mode: FFA, first to " + challenge.winTarget() + " goals wins.");
        } else {
            getLogger().info("TLockout enabled - mode: Teams "
                    + sides.ordered().stream().map(Side::displayName).toList()
                    + ", first to " + challenge.winTarget() + " goals wins.");
        }
    }

    @Override
    public void onDisable() {
        cancelPendingStart();
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        if (playerTracker != null) {
            playerTracker.stopTask();
            playerTracker.clearAll();
        }
        if (challenge != null) {
            challenge.stop();
        }
    }

    /**
     * Upgrades an older config.yml in place. The previous file is kept as config.yml.old so
     * server owners can copy customisations across.
     */
    private void migrateConfig() {
        final int currentVersion = 8;
        if (getConfig().getInt("config-version", 0) >= currentVersion) {
            return;
        }
        java.io.File file = new java.io.File(getDataFolder(), "config.yml");
        java.io.File backup = new java.io.File(getDataFolder(), "config.yml.old");
        try {
            java.nio.file.Files.copy(file.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException ex) {
            getLogger().warning("Could not back up config.yml: " + ex.getMessage());
        }
        saveResource("config.yml", true);
        reloadConfig();
        getLogger().info("Upgraded config.yml to version " + currentVersion
                + " (previous version saved as config.yml.old).");
    }

    /**
     * Writes the bundled goals.yml on first run. An older file is copied to
     * goals.yml.v&lt;old version&gt;.bak and replaced, so an owner who customised it can merge
     * their entries back in.
     */
    private void prepareGoalsFile() {
        java.io.File file = new java.io.File(getDataFolder(), "goals.yml");
        if (!file.exists()) {
            saveResource("goals.yml", false);
            return;
        }
        int version;
        try {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    new org.bukkit.configuration.file.YamlConfiguration();
            yaml.load(file);
            version = yaml.getInt("goals-version", 0);
        } catch (Exception ex) {
            // Leave a malformed file alone; GoalCatalog will report the parse error with its path.
            getLogger().warning("Could not read goals.yml while checking its version: " + ex.getMessage());
            return;
        }
        if (version >= GoalCatalog.GOALS_VERSION) {
            return;
        }
        String backupName = "goals.yml.v" + (version <= 0 ? 1 : version) + ".bak";
        java.io.File backup = new java.io.File(getDataFolder(), backupName);
        try {
            java.nio.file.Files.copy(file.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException ex) {
            getLogger().warning("Could not back up goals.yml: " + ex.getMessage());
        }
        saveResource("goals.yml", true);
        getLogger().info("Upgraded goals.yml to version " + GoalCatalog.GOALS_VERSION
                + " (previous version saved as " + backupName + ").");
    }

    private int loadConfigWinTarget(LockoutMode targetMode) {
        if (targetMode == LockoutMode.FFA) {
            return Math.max(1, Math.min(Board.CELLS, getConfig().getInt("win.ffa-goals-to-win", 5)));
        }
        return Math.max(1, Math.min(Board.CELLS, getConfig().getInt("win.goals-to-win", 13)));
    }

    private int winTargetFor(LockoutMode targetMode) {
        return targetMode == LockoutMode.FFA ? ffaWinTarget : teamsWinTarget;
    }

    private boolean loadFireworksOnClaim() {
        return getConfig().getBoolean("match.fireworks-on-claim", true);
    }

    private boolean loadAutoReset() {
        return getConfig().getBoolean("match.auto-reset", true);
    }

    private boolean loadKeepInventory() {
        return getConfig().getBoolean("match.keep-inventory", true);
    }

    private int loadCountdownSeconds() {
        return Math.max(0, getConfig().getInt("match.countdown-seconds", 5));
    }

    private boolean loadFreezeDuringCountdown() {
        return getConfig().getBoolean("match.freeze-during-countdown", true);
    }

    private boolean loadWorldScanEnabled() {
        return getConfig().getBoolean("world-scan.enabled", true);
    }

    private int loadWorldScanRadius() {
        return Math.max(64, getConfig().getInt("world-scan.radius", 750));
    }

    /** Returns cached overworld structure/biome feasibility facts for board generation. */
    public WorldFeasibilityScanner.WorldFacts worldFactsFor(GoalCatalog goalCatalog) {
        if (worldScanner == null || goalCatalog == null) {
            return WorldFeasibilityScanner.WorldFacts.UNRESTRICTED;
        }
        return worldScanner.factsFor(primaryOverworld(), goalCatalog.templates());
    }

    private World primaryOverworld() {
        for (World world : getServer().getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }
        java.util.List<World> worlds = getServer().getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    private void scheduleChecker() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        int interval = Math.max(1, getConfig().getInt("check-interval-ticks", 10));
        checkIntervalTicks = interval;
        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (tracking != null) {
                    tracking.checkGoals();
                }
            }
        };
        checkTask.runTaskTimer(this, 20L, interval);
    }

    private void applyPrefix() {
        String raw = getConfig().getString("message-prefix", "");
        prefix = (raw == null || raw.isBlank())
                ? Component.empty()
                : MiniMessage.miniMessage().deserialize(raw);
    }

    /** Reloads config.yml and goals.yml. Stops any running challenge; preserves same-mode membership. */
    public void reloadAll() {
        reloadConfig();
        applyPrefix();
        stopChallenge();
        LockoutMode reloadedMode = LockoutMode.parse(getConfig().getString("mode", "teams"));
        GoalCatalog reloadedCatalog = GoalCatalog.load(this);
        SideManager reloadedTeams = SideManager.load(getConfig().getConfigurationSection("teams"));
        SideManager reloadedSides = reloadedMode == LockoutMode.FFA ? SideManager.ffa() : reloadedTeams;
        reloadedSides.transferMembershipFrom(sides);

        catalog = reloadedCatalog;
        challenge.setCatalog(reloadedCatalog);
        mode = reloadedMode;
        sides = reloadedSides;
        teamsWinTarget = loadConfigWinTarget(LockoutMode.TEAMS);
        ffaWinTarget = loadConfigWinTarget(LockoutMode.FFA);
        challenge.setSides(reloadedSides);
        challenge.setWinTarget(winTargetFor(reloadedMode));
        if (playerReset != null) {
            playerReset.setEnabled(loadAutoReset());
        }
        if (keepInventory != null) {
            keepInventory.setEnabled(loadKeepInventory());
        }
        if (worldScanner != null) {
            worldScanner.configure(loadWorldScanEnabled(), loadWorldScanRadius());
        }
        if (sounds != null) {
            sounds.reload(getConfig().getConfigurationSection("sounds"));
        }
        if (fireworks != null) {
            fireworks.setEnabled(loadFireworksOnClaim());
        }
        if (countdown != null) {
            countdown.configure(loadCountdownSeconds(), loadFreezeDuringCountdown());
        }

        scheduleChecker();
    }

    /**
     * Sets the number of goals needed to win for the active mode (1..25). If a round is currently
     * running, immediately refreshes views and ends the round if a side already meets the target.
     */
    public void setWinTargetForCurrentMode(int target) {
        int clamped = Math.max(1, Math.min(Board.CELLS, target));
        if (mode == LockoutMode.FFA) {
            ffaWinTarget = clamped;
        } else {
            teamsWinTarget = clamped;
        }
        challenge.setWinTarget(clamped);
        if (!challenge.checkWinCondition()) {
            refreshViews();
        }
    }

    /**
     * Switches the active mode in memory between {@link LockoutMode#TEAMS} and {@link LockoutMode#FFA}.
     * Refuses while a round or countdown is active; clears any finished board first.
     *
     * @return true when the switch succeeded
     */
    public boolean setMode(LockoutMode nextMode) {
        if (nextMode == null || challenge.running() || challenge.countdown()) {
            return false;
        }
        if (challenge.state() != ChallengeManager.State.IDLE) {
            stopChallenge();
        }
        if (nextMode == mode) {
            return true;
        }
        SideManager nextSides = nextMode == LockoutMode.FFA
                ? SideManager.ffa()
                : SideManager.load(getConfig().getConfigurationSection("teams"));
        this.mode = nextMode;
        this.sides = nextSides;
        challenge.setSides(nextSides);
        challenge.setWinTarget(winTargetFor(nextMode));
        return true;
    }

    public void startChallenge(long seed) {
        if (playerReset != null) {
            playerReset.resetParticipants(sides);
        }
        if (playerTracker != null) {
            playerTracker.clearAll();
        }
        tracking.resetStats();
        challenge.start(seed);
        if (boardCompass != null) {
            boardCompass.giveToAll();
        }
        if (playerTracker != null) {
            playerTracker.syncAllNow();
        }
        if (sounds != null) {
            sounds.playGameStart();
        }
        refreshViews();
    }

    public void stopChallenge() {
        cancelPendingStart();
        if (playerReset != null) {
            playerReset.clearPending();
        }
        if (playerTracker != null) {
            playerTracker.clearAll();
        }
        challenge.stop();
        if (boardCompass != null) {
            boardCompass.removeFromAll();
        }
        if (hud != null) {
            hud.clearAll();
        }
    }

    private void cancelPendingStart() {
        if (pendingScanTask != null) {
            pendingScanTask.cancel();
            pendingScanTask = null;
        }
        if (countdown != null) {
            countdown.cancel();
        }
    }

    /**
     * Runs a fresh overworld feasibility scan (printing start and completion notices to chat)
     * when the world is not yet cached, then executes {@code afterScan}.
     */
    private void prepareWorldAndRun(Runnable afterScan) {
        World overworld = primaryOverworld();
        if (worldScanner == null || catalog == null || !worldScanner.needsScan(overworld)) {
            afterScan.run();
            return;
        }
        challenge.beginCountdown();
        getServer().sendMessage(msg("<gray>Scanning world <white>" + overworld.getName()
                + "</white> for nearby structures & biomes <dark_gray>(radius "
                + worldScanner.radiusBlocks() + " blocks)</dark_gray>...</gray>"));
        // Run on the next tick so the "Scanning world..." chat message flushes to clients first.
        pendingScanTask = getServer().getScheduler().runTask(this, () -> {
            pendingScanTask = null;
            if (!challenge.countdown()) {
                return;
            }
            WorldFeasibilityScanner.WorldFacts facts = worldScanner.factsFor(overworld, catalog.templates());
            getServer().sendMessage(msg("<gray>World scan complete: <white>"
                    + facts.foundStructures() + "/" + facts.structures().size()
                    + "</white> structures, <white>"
                    + facts.foundBiomes() + "/" + facts.biomes().size()
                    + "</white> biomes found <dark_gray>(" + facts.scanDurationMs() + "ms)</dark_gray>.</gray>"));
            afterScan.run();
        });
    }

    /**
     * Stops any active/finished round and immediately starts a fresh testing round (allowing empty
     * sides, respecting {@code autoreset}, skipping countdown), broadcasting the same notices as
     * {@code /lo reroll}.
     */
    public void rerollChallenge(long seed, String commandLabel) {
        rerollChallenge(seed, commandLabel, null);
    }

    public void rerollChallenge(long seed, String commandLabel, Runnable onStarted) {
        if (challenge.state() != ChallengeManager.State.IDLE) {
            stopChallenge();
        }
        if (sides.isFfa()) {
            sides.enrollOnlineForFfa();
        }
        java.util.List<Side> allSides = challenge.sides().ordered();
        java.util.List<String> emptySideNames = new java.util.ArrayList<>();
        if (!sides.isFfa()) {
            for (Side side : allSides) {
                if (side.size() == 0) {
                    emptySideNames.add(side.displayName());
                }
            }
        }
        prepareWorldAndRun(() -> {
            finishLaunch(commandLabel, seed, allSides, emptySideNames, "Lockout board rerolled!", "reroll");
            if (onStarted != null) {
                onStarted.run();
            }
        });
    }

    /**
     * Runs any needed world scan (with chat notices), runs the configured pre-round countdown,
     * and then starts the round with the standard announcements.
     */
    public void launchRound(String commandLabel, long seed, java.util.List<Side> allSides,
                            java.util.List<String> emptySideNames, String headline, String emptyReason) {
        prepareWorldAndRun(() -> {
            if (countdown != null && countdown.countdownSeconds() > 0) {
                countdown.start(() -> finishLaunch(commandLabel, seed, allSides, emptySideNames, headline, emptyReason));
            } else {
                finishLaunch(commandLabel, seed, allSides, emptySideNames, headline, emptyReason);
            }
        });
    }

    private void finishLaunch(String commandLabel, long seed, java.util.List<Side> allSides,
                              java.util.List<String> emptySideNames, String headline, String emptyReason) {
        startChallenge(seed);
        String resetNote = playerReset.enabled()
                ? " <dark_gray>(players reset)</dark_gray>"
                : " <dark_gray>(auto-reset off)</dark_gray>";
        String modeTag = sides.isFfa() ? " <aqua>[FFA]</aqua>" : "";
        getServer().sendMessage(msg("<gold><bold>" + headline + "</bold></gold>" + modeTag
                + " <gray>Seed: <white>" + seed + "</white>, first to <white>"
                + challenge.winTarget() + "</white> goals wins. <white>/" + commandLabel + " board</white>"
                + resetNote));

        if (sides.isFfa()) {
            if (allSides.size() < 2) {
                getServer().sendMessage(msg("<yellow>Testing round: starting FFA with <white>"
                        + allSides.size() + "</white> player" + (allSides.size() == 1 ? "" : "s") + ".</yellow>"));
                getLogger().warning("Starting a testing FFA round with " + allSides.size()
                        + " player(s) (" + emptyReason + ").");
            }
        } else if (!emptySideNames.isEmpty()) {
            getServer().sendMessage(msg("<yellow>Testing round: starting with empty "
                    + "team" + (emptySideNames.size() == 1 ? "" : "s") + " <white>"
                    + String.join(", ", emptySideNames)
                    + "</white>. Empty sides cannot claim goals.</yellow>"));
            getLogger().warning("Starting a testing round with empty teams: "
                    + String.join(", ", emptySideNames) + " (" + emptyReason + ").");
        } else if (allSides.size() >= 2) {
            int a = allSides.get(0).size();
            int b = allSides.get(1).size();
            if (Math.abs(a - b) > 1) {
                getServer().sendMessage(msg("<yellow>Heads up: the teams are uneven ("
                        + a + " vs " + b + ").</yellow>"));
            }
        }
    }

    /**
     * Handles a player joining or being moved onto a team while a round is actively running.
     * Newly joining unassigned players get a clean-match reset when auto-reset is enabled; every
     * mid-game joiner gets fresh tracking state/baseline, a Board Compass, and refreshed views.
     */
    public void onMidGameTeamJoin(Player player, boolean wasUnassigned) {
        if (!challenge.running()) {
            return;
        }
        if (wasUnassigned && playerReset != null && playerReset.enabled()) {
            playerReset.resetPlayer(player);
        }
        if (tracking != null) {
            tracking.resetPlayerTracking(player);
        }
        if (boardCompass != null) {
            boardCompass.give(player);
            boardCompass.refreshAll();
        }
        refreshViews();
    }

    /** Redraws every open board, player-tracker menu, and refreshes all sidebars and boss bars. */
    public void refreshViews() {
        if (boardGui != null) {
            boardGui.refreshAll();
        }
        if (playerTrackerGui != null) {
            playerTrackerGui.refreshAll();
        }
        if (hud != null) {
            hud.updateAll();
        }
    }

    // -------------------------------------------------------------- accessors

    public GoalCatalog catalog() {
        return catalog;
    }

    public LockoutMode mode() {
        return mode;
    }

    public ChallengeManager challenge() {
        return challenge;
    }

    public TrackingService tracking() {
        return tracking;
    }

    public BoardGui boardGui() {
        return boardGui;
    }

    public BoardCompass boardCompass() {
        return boardCompass;
    }

    public PlayerTrackerService playerTracker() {
        return playerTracker;
    }

    public PlayerTrackerGui playerTrackerGui() {
        return playerTrackerGui;
    }

    public HudService hud() {
        return hud;
    }

    public PlayerResetService playerReset() {
        return playerReset;
    }

    public KeepInventoryService keepInventory() {
        return keepInventory;
    }

    public SoundService sounds() {
        return sounds;
    }

    public FireworkService fireworks() {
        return fireworks;
    }

    public int checkIntervalTicks() {
        return checkIntervalTicks;
    }

    /** The configured chat prefix, already parsed. */
    public Component prefix() {
        return prefix;
    }

    /** Parses MiniMessage and prepends the configured prefix. */
    public Component msg(String miniMessage) {
        return prefix.append(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    /** Parses MiniMessage without a prefix. */
    public Component bare(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }
}
