package dev.tlockout;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Plays configurable vanilla sound cues for goal claims (different for your team, opposing teams,
 * and spectators), round start, and match finish (win / lose / spectator).
 */
public final class SoundService {

    public record Cue(Key soundKey, float volume, float pitch, Float secondPitch, long delayTicks) {
        public boolean playable() {
            return soundKey != null && volume > 0f;
        }
    }

    private static final Cue DEFAULT_GOAL_TEAM = new Cue(
            Key.key("minecraft", "block.note_block.pling"), 1.0f, 1.0f, 1.5f, 3L);
    private static final Cue DEFAULT_GOAL_OPPONENT = new Cue(
            Key.key("minecraft", "block.note_block.bass"), 1.0f, 0.9f, 0.6f, 3L);
    private static final Cue DEFAULT_GOAL_SPECTATOR = new Cue(
            Key.key("minecraft", "block.note_block.chime"), 0.9f, 1.0f, null, 0L);
    private static final Cue DEFAULT_GAME_START = new Cue(
            Key.key("minecraft", "item.goat_horn.sound.0"), 0.9f, 1.0f, null, 0L);
    private static final Cue DEFAULT_COUNTDOWN_TICK = new Cue(
            Key.key("minecraft", "block.note_block.bit"), 0.9f, 1.0f, null, 0L);
    private static final Cue DEFAULT_GAME_WIN = new Cue(
            Key.key("minecraft", "ui.toast.challenge_complete"), 0.9f, 1.0f, null, 0L);
    private static final Cue DEFAULT_GAME_LOSE = new Cue(
            Key.key("minecraft", "entity.warden.death"), 0.8f, 1.0f, null, 0L);
    private static final Cue DEFAULT_GAME_FINISH_SPECTATOR = new Cue(
            Key.key("minecraft", "ui.toast.challenge_complete"), 0.9f, 1.0f, null, 0L);

    private final TLockout plugin;
    private boolean enabled = true;
    private Cue goalTeam = DEFAULT_GOAL_TEAM;
    private Cue goalOpponent = DEFAULT_GOAL_OPPONENT;
    private Cue goalSpectator = DEFAULT_GOAL_SPECTATOR;
    private Cue gameStart = DEFAULT_GAME_START;
    private Cue countdownTick = DEFAULT_COUNTDOWN_TICK;
    private Cue gameWin = DEFAULT_GAME_WIN;
    private Cue gameLose = DEFAULT_GAME_LOSE;
    private Cue gameFinishSpectator = DEFAULT_GAME_FINISH_SPECTATOR;

    public SoundService(TLockout plugin, ConfigurationSection section) {
        this.plugin = plugin;
        reload(section);
    }

    public void reload(ConfigurationSection section) {
        if (section == null) {
            enabled = true;
            goalTeam = DEFAULT_GOAL_TEAM;
            goalOpponent = DEFAULT_GOAL_OPPONENT;
            goalSpectator = DEFAULT_GOAL_SPECTATOR;
            gameStart = DEFAULT_GAME_START;
            countdownTick = DEFAULT_COUNTDOWN_TICK;
            gameWin = DEFAULT_GAME_WIN;
            gameLose = DEFAULT_GAME_LOSE;
            gameFinishSpectator = DEFAULT_GAME_FINISH_SPECTATOR;
            return;
        }
        enabled = section.getBoolean("enabled", true);
        goalTeam = parseCue(section.getConfigurationSection("goal-team"), DEFAULT_GOAL_TEAM, "goal-team");
        goalOpponent = parseCue(section.getConfigurationSection("goal-opponent"), DEFAULT_GOAL_OPPONENT, "goal-opponent");
        goalSpectator = parseCue(section.getConfigurationSection("goal-spectator"), DEFAULT_GOAL_SPECTATOR, "goal-spectator");
        gameStart = parseCue(section.getConfigurationSection("game-start"), DEFAULT_GAME_START, "game-start");
        countdownTick = parseCue(section.getConfigurationSection("countdown-tick"), DEFAULT_COUNTDOWN_TICK, "countdown-tick");
        gameWin = parseCue(section.getConfigurationSection("game-win"), DEFAULT_GAME_WIN, "game-win");
        gameLose = parseCue(section.getConfigurationSection("game-lose"), DEFAULT_GAME_LOSE, "game-lose");
        gameFinishSpectator = parseCue(section.getConfigurationSection("game-finish-spectator"),
                DEFAULT_GAME_FINISH_SPECTATOR, "game-finish-spectator");
    }

    public boolean enabled() {
        return enabled;
    }

    /** Plays the round-start cue to everyone online. */
    public void playGameStart() {
        if (!enabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            play(player, gameStart);
        }
    }

    /** Plays a countdown tick cue to everyone online (slightly higher pitch on the final 3 seconds). */
    public void playCountdownTick(int remainingSeconds) {
        if (!enabled || countdownTick == null || !countdownTick.playable()) {
            return;
        }
        float pitch = remainingSeconds <= 3
                ? clamp(countdownTick.pitch() + (4 - remainingSeconds) * 0.1f, 0.5f, 2.0f)
                : countdownTick.pitch();
        Sound sound = Sound.sound(countdownTick.soundKey(), Sound.Source.MASTER, countdownTick.volume(), pitch);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(sound, Sound.Emitter.self());
        }
    }

    /**
     * Plays the goal-claim cue: friendly chime to the claiming side, opposing drop to every other
     * side, and a neutral chime to spectators / unassigned players.
     */
    public void playGoalClaimed(Side claimingSide, SideManager sides) {
        if (!enabled || claimingSide == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Side playerSide = sides.sideOf(player.getUniqueId());
            if (playerSide == null) {
                play(player, goalSpectator);
            } else if (playerSide.id().equals(claimingSide.id())) {
                play(player, goalTeam);
            } else {
                play(player, goalOpponent);
            }
        }
    }

    /**
     * Plays the match-finish cue: victory fanfare to the winning side, defeat cue to opposing
     * side(s), and spectator finish cue to unassigned players.
     */
    public void playGameFinish(Side winningSide, SideManager sides) {
        if (winningSide == null) {
            return;
        }
        playGameFinish(List.of(winningSide), sides);
    }

    /**
     * Plays the match-finish cue for one or more winning sides (e.g. a tie when all 25 goals are
     * claimed in FFA).
     */
    public void playGameFinish(Collection<Side> winningSides, SideManager sides) {
        if (!enabled || winningSides == null || winningSides.isEmpty()) {
            return;
        }
        Set<String> winnerIds = new HashSet<>();
        for (Side side : winningSides) {
            if (side != null) {
                winnerIds.add(side.id());
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Side playerSide = sides.sideOf(player.getUniqueId());
            if (playerSide == null) {
                play(player, gameFinishSpectator);
            } else if (winnerIds.contains(playerSide.id())) {
                play(player, gameWin);
            } else {
                play(player, gameLose);
            }
        }
    }

    private void play(Player player, Cue cue) {
        if (cue == null || !cue.playable()) {
            return;
        }
        player.playSound(Sound.sound(cue.soundKey(), Sound.Source.MASTER, cue.volume(), cue.pitch()),
                Sound.Emitter.self());
        if (cue.secondPitch() != null && cue.delayTicks() > 0L) {
            Sound followUp = Sound.sound(cue.soundKey(), Sound.Source.MASTER, cue.volume(), cue.secondPitch());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(followUp, Sound.Emitter.self());
                }
            }, cue.delayTicks());
        }
    }

    private Cue parseCue(ConfigurationSection section, Cue fallback, String name) {
        if (section == null) {
            return fallback;
        }
        String rawSound = section.getString("sound");
        Key key;
        if (rawSound == null) {
            key = fallback.soundKey();
        } else if (rawSound.isBlank()) {
            return new Cue(null, 0f, 1f, null, 0L);
        } else {
            key = parseKey(rawSound, name);
            if (key == null) {
                return fallback;
            }
        }
        float volume = clamp((float) section.getDouble("volume", fallback.volume()), 0f, 4f);
        float pitch = clamp((float) section.getDouble("pitch", fallback.pitch()), 0.5f, 2.0f);
        Float secondPitch = null;
        if (section.contains("second-pitch")) {
            secondPitch = clamp((float) section.getDouble("second-pitch", pitch), 0.5f, 2.0f);
        } else if (rawSound == null) {
            secondPitch = fallback.secondPitch();
        }
        long delayTicks = section.contains("delay-ticks")
                ? Math.max(0L, section.getLong("delay-ticks", 3L))
                : (secondPitch != null ? Math.max(1L, fallback.delayTicks()) : 0L);
        return new Cue(key, volume, pitch, secondPitch, delayTicks);
    }

    private Key parseKey(String raw, String cueName) {
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (!text.contains(":")) {
            text = "minecraft:" + text;
        }
        try {
            return Key.key(text);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Invalid sound key '" + raw + "' for sounds." + cueName
                    + " in config.yml; using default.");
            return null;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
