package dev.tlockout;

import dev.tlockout.goal.ResolvedGoal;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Per-player sidebar and boss bar showing the side's tracked goals, ranked by progress.
 * Targets are shared per side, so every member sees the same list.
 */
public final class HudService implements Listener {

    /** Sidebar capacity: score, targets (max 5), footer. */
    private static final int LINES = 10;

    private final TLockout plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Scoreboard> sidebars = new HashMap<>();

    public HudService(TLockout plugin) {
        this.plugin = plugin;
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void update(Player player) {
        updateSidebar(player);
        updateBossBar(player);
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
    }

    public void clear(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        if (sidebars.remove(player.getUniqueId()) != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bars.remove(event.getPlayer().getUniqueId());
        sidebars.remove(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------- sidebar

    private void updateSidebar(Player player) {
        ChallengeManager challenge = plugin.challenge();
        Board board = challenge.board();
        if (board == null) {
            clear(player);
            return;
        }
        Side side = challenge.sides().sideOf(player.getUniqueId());
        Scoreboard scoreboard = sidebars.computeIfAbsent(player.getUniqueId(),
                id -> createSidebar(player));

        List<Component> lines = buildLines(challenge, side, board);
        for (int index = 0; index < LINES; index++) {
            Team team = scoreboard.getTeam("tl" + index);
            if (team == null) {
                continue;
            }
            Component text = index < lines.size() ? lines.get(index) : Component.text(" ");
            team.prefix(text);
        }
    }

    private Scoreboard createSidebar(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("tlockout", Criteria.DUMMY,
                Component.text("Lockout", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        for (int index = 0; index < LINES; index++) {
            // Colour codes render as nothing, so the entry itself is invisible and the
            // team prefix carries the visible text.
            String entry = "\u00A7" + Integer.toHexString(index) + "\u00A7r";
            objective.getScore(entry).setScore(LINES - index);
            Team team = scoreboard.registerNewTeam("tl" + index);
            team.addEntry(entry);
            team.prefix(Component.text(" "));
        }
        player.setScoreboard(scoreboard);
        return scoreboard;
    }

    private List<Component> buildLines(ChallengeManager challenge, Side side, Board board) {
        List<Component> lines = new ArrayList<>();

        if (challenge.finished() && challenge.winner() != null) {
            lines.add(winnerLine(challenge));
            lines.add(Component.text(" "));
        }

        if (challenge.isFfa()) {
            addFfaScoreLines(lines, challenge, side);
        } else {
            Component score = Component.empty();
            boolean first = true;
            for (Side other : challenge.sides().ordered()) {
                if (!first) {
                    score = score.append(Component.text(" - ", NamedTextColor.DARK_GRAY));
                }
                score = score.append(Component.text(
                        other.displayName() + " " + challenge.claimsFor(other.id()), other.color()));
                first = false;
            }
            lines.add(score);
            lines.add(Component.text(" "));
        }

        if (side == null) {
            if (challenge.isFfa()) {
                lines.add(Component.text("Spectating (FFA)", NamedTextColor.GRAY));
                lines.add(Component.text("Run /lo join to play", NamedTextColor.DARK_GRAY));
            } else {
                lines.add(Component.text("Join a team to track goals", NamedTextColor.GRAY));
            }
        } else {
            List<Target> targets = ranked(side, board);
            if (targets.isEmpty()) {
                lines.add(Component.text("Right-click a goal", NamedTextColor.GRAY));
                lines.add(Component.text("on the board to track", NamedTextColor.GRAY));
            } else {
                int rank = 1;
                for (Target target : targets) {
                    lines.add(targetLine(rank++, target));
                }
            }
        }

        lines.add(Component.text(" "));
        lines.add(Component.text("Claimed " + board.claimedCount() + "/" + Board.CELLS,
                NamedTextColor.DARK_GRAY));
        return lines.size() > LINES ? lines.subList(0, LINES) : lines;
    }

    private static Component winnerLine(ChallengeManager challenge) {
        List<Side> winners = challenge.winners();
        if (winners.size() > 1) {
            Component line = Component.text("Tie! ", NamedTextColor.GOLD);
            int maxNames = Math.min(2, winners.size());
            for (int i = 0; i < maxNames; i++) {
                if (i > 0) {
                    line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
                }
                Side w = winners.get(i);
                line = line.append(Component.text(w.displayName(), w.color()));
            }
            if (winners.size() > maxNames) {
                line = line.append(Component.text(" +" + (winners.size() - maxNames), NamedTextColor.GRAY));
            }
            return line.append(Component.text(" win!", NamedTextColor.WHITE));
        }
        Side winner = challenge.winner();
        return Component.text(winner.displayName() + " wins!", winner.color());
    }

    private static void addFfaScoreLines(List<Component> lines, ChallengeManager challenge, Side viewerSide) {
        List<Side> ranked = challenge.rankedSides();
        if (ranked.isEmpty()) {
            lines.add(Component.text("FFA: no players", NamedTextColor.GRAY));
            lines.add(Component.text(" "));
            return;
        }
        int target = challenge.winTarget();
        int topScore = challenge.claimsFor(ranked.get(0).id());
        List<Side> leaders = new ArrayList<>();
        for (Side candidate : ranked) {
            if (challenge.claimsFor(candidate.id()) == topScore) {
                leaders.add(candidate);
            } else {
                break;
            }
        }
        if (leaders.size() == 1) {
            Side leader = leaders.get(0);
            lines.add(Component.text("1st: ", NamedTextColor.GOLD)
                    .append(Component.text(leader.displayName(), leader.color()))
                    .append(Component.text(" " + topScore + "/" + target, NamedTextColor.WHITE)));
        } else if (leaders.size() == 2) {
            lines.add(Component.text("Tied: ", NamedTextColor.GOLD)
                    .append(Component.text(leaders.get(0).displayName(), leaders.get(0).color()))
                    .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(leaders.get(1).displayName(), leaders.get(1).color()))
                    .append(Component.text(" (" + topScore + ")", NamedTextColor.WHITE)));
        } else {
            lines.add(Component.text("Tied 1st: ", NamedTextColor.GOLD)
                    .append(Component.text(leaders.size() + " players (" + topScore + "/" + target + ")",
                            NamedTextColor.WHITE)));
        }

        if (viewerSide != null) {
            int viewerScore = challenge.claimsFor(viewerSide.id());
            int viewerRank = 1;
            for (Side candidate : ranked) {
                if (challenge.claimsFor(candidate.id()) > viewerScore) {
                    viewerRank++;
                }
            }
            lines.add(Component.text("You: ", NamedTextColor.GRAY)
                    .append(Component.text(viewerScore + "/" + target, viewerSide.color()))
                    .append(Component.text(" (#" + viewerRank + ")", NamedTextColor.DARK_GRAY)));
        } else if (ranked.size() >= 2) {
            Side second = ranked.get(1);
            lines.add(Component.text("2nd: ", NamedTextColor.GRAY)
                    .append(Component.text(second.displayName(), second.color()))
                    .append(Component.text(" " + challenge.claimsFor(second.id()) + "/" + target,
                            NamedTextColor.WHITE)));
        }
        lines.add(Component.text(" "));
    }

    private Component targetLine(int rank, Target target) {
        NamedTextColor progressColor = target.fraction() >= 0.66d ? NamedTextColor.GREEN
                : target.fraction() >= 0.33d ? NamedTextColor.YELLOW
                : NamedTextColor.GRAY;
        return Component.text(rank + ". ", NamedTextColor.DARK_GRAY)
                .append(Component.text(target.current() + "/" + target.target() + " ", progressColor))
                .append(Component.text(target.goal().display(), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------- boss bar

    private void updateBossBar(Player player) {
        ChallengeManager challenge = plugin.challenge();
        Board board = challenge.board();
        Side side = challenge.sides().sideOf(player.getUniqueId());
        if (board == null || side == null) {
            hideBar(player);
            return;
        }

        Component name;
        float progress;
        if (challenge.finished() && challenge.winner() != null) {
            name = winnerLine(challenge);
            progress = 1f;
        } else {
            List<Target> targets = ranked(side, board);
            if (targets.isEmpty()) {
                name = Component.text("Right-click a goal on the board to track it",
                        NamedTextColor.GRAY);
                progress = 0f;
            } else {
                Target top = targets.get(0);
                name = Component.text(top.current() + "/" + top.target() + " ", NamedTextColor.WHITE)
                        .append(Component.text(top.goal().display(), side.color()));
                progress = (float) top.fraction();
            }
        }

        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(name, progress, barColor(side), BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(name);
            bar.progress(progress);
            bar.color(barColor(side));
        }
    }

    private void hideBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private static BossBar.Color barColor(Side side) {
        String concrete = side.concrete().name();
        if (concrete.startsWith("LIGHT_BLUE")) {
            return BossBar.Color.BLUE;
        }
        if (concrete.startsWith("RED") || concrete.startsWith("BROWN")) {
            return BossBar.Color.RED;
        }
        if (concrete.startsWith("BLUE") || concrete.startsWith("CYAN")) {
            return BossBar.Color.BLUE;
        }
        if (concrete.startsWith("GREEN") || concrete.startsWith("LIME")) {
            return BossBar.Color.GREEN;
        }
        if (concrete.startsWith("YELLOW") || concrete.startsWith("ORANGE")) {
            return BossBar.Color.YELLOW;
        }
        if (concrete.startsWith("PINK") || concrete.startsWith("MAGENTA")) {
            return BossBar.Color.PINK;
        }
        if (concrete.startsWith("PURPLE")) {
            return BossBar.Color.PURPLE;
        }
        return BossBar.Color.WHITE;
    }

    // -------------------------------------------------------------- targets

    private record Target(int cell, ResolvedGoal goal, int current, int target) {
        double fraction() {
            return target <= 0 ? 0d : Math.min(1d, (double) current / target);
        }
    }

    private List<Target> ranked(Side side, Board board) {
        List<Target> targets = new ArrayList<>();
        for (int cell : side.targets()) {
            if (cell < 0 || cell >= Board.CELLS || board.claimed(cell)) {
                continue;
            }
            ResolvedGoal goal = board.cell(cell);
            int target = goal.target();
            int current = plugin.tracking().teamProgress(side, goal);
            targets.add(new Target(cell, goal, current, target));
        }
        targets.sort(Comparator.comparingDouble(Target::fraction).reversed()
                .thenComparing(Comparator.comparingInt(Target::current).reversed()));
        return targets;
    }
}
