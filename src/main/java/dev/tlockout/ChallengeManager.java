package dev.tlockout;

import dev.tlockout.goal.ResolvedGoal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Owns the current board, the round state machine and claim handling. */
public final class ChallengeManager {

    public enum State {
        /** No board. */
        IDLE,
        /** Pre-round world scan / countdown in progress; claims are not accepted yet. */
        COUNTDOWN,
        /** Board active, claims accepted. */
        RUNNING,
        /** A side reached the target; board stays viewable but claims are refused. */
        FINISHED
    }

    /** Outcome of a player trying to (un)track a board goal. */
    public enum TargetResult {
        ADDED,
        REMOVED,
        FULL,
        CLAIMED,
        UNAVAILABLE
    }

    private final TLockout plugin;
    private GoalCatalog catalog;
    private SideManager sides;
    private int winTarget;

    private Board board;
    private State state = State.IDLE;
    private Side winner;
    private List<Side> winners = List.of();

    public ChallengeManager(TLockout plugin, GoalCatalog catalog, SideManager sides, int winTarget) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.sides = sides;
        this.winTarget = winTarget;
    }

    public State state() {
        return state;
    }

    public boolean countdown() {
        return state == State.COUNTDOWN;
    }

    public boolean running() {
        return state == State.RUNNING;
    }

    public boolean finished() {
        return state == State.FINISHED;
    }

    /** Enters pre-round countdown state and locks team switching until stop or finish. */
    public void beginCountdown() {
        this.state = State.COUNTDOWN;
        this.winner = null;
        this.winners = List.of();
        sides.setLocked(true);
    }

    public Board board() {
        return board;
    }

    public Side winner() {
        return winner;
    }

    public List<Side> winners() {
        return winners;
    }

    public boolean isTie() {
        return winners.size() > 1;
    }

    public boolean isFfa() {
        return sides != null && sides.isFfa();
    }

    public SideManager sides() {
        return sides;
    }

    /** Returns sides sorted by claim count descending (preserving roster order on ties). */
    public List<Side> rankedSides() {
        List<Side> ranked = new ArrayList<>(sides.ordered());
        ranked.sort(Comparator.comparingInt((Side s) -> claimsFor(s.id())).reversed());
        return ranked;
    }

    public GoalCatalog catalog() {
        return catalog;
    }

    public int winTarget() {
        return winTarget;
    }

    public Side sideOf(Player player) {
        return sides.sideOf(player.getUniqueId());
    }

    public void start(long seed) {
        this.board = new Board(seed, catalog.draw(
                Board.CELLS,
                new Random(seed),
                sides.ordered().size(),
                plugin.worldFactsFor(catalog),
                plugin.getLogger()));
        this.state = State.RUNNING;
        this.winner = null;
        this.winners = List.of();
        sides.setLocked(true);
        for (Side side : sides.all()) {
            side.clearTargets();
        }
    }

    public void stop() {
        if (plugin.boardGui() != null) {
            plugin.boardGui().closeAll();
        }
        if (plugin.playerTrackerGui() != null) {
            plugin.playerTrackerGui().closeAll();
        }
        this.board = null;
        this.state = State.IDLE;
        this.winner = null;
        this.winners = List.of();
        sides.setLocked(false);
    }

    public void setCatalog(GoalCatalog catalog) {
        this.catalog = catalog;
    }

    public void setSides(SideManager sides) {
        this.sides = sides;
    }

    public void setWinTarget(int winTarget) {
        this.winTarget = winTarget;
    }

    public int claimsFor(String sideId) {
        Board current = board;
        if (current == null) {
            return 0;
        }
        int count = 0;
        for (int cell = 0; cell < Board.CELLS; cell++) {
            Board.Claim claim = current.claim(cell);
            if (claim != null && claim.sideId().equals(sideId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Claims a cell for the player's side if the round is running and the cell is free.
     *
     * @return true when the claim succeeded
     */
    public boolean tryClaim(Player player, int cell) {
        Side side = sideOf(player);
        return side != null && tryClaimFor(side, player, cell);
    }

    /**
     * Claims a cell for a side. Player-scope goals pass the player who completed it; team-scope
     * goals pass the member being credited (the last contributor, or the first online member).
     *
     * @return true when the claim succeeded
     */
    public boolean tryClaimFor(Side side, Player player, int cell) {
        Board current = board;
        if (state != State.RUNNING || current == null || side == null) {
            return false;
        }
        if (cell < 0 || cell >= Board.CELLS || current.claimed(cell)) {
            return false;
        }
        if (!side.has(player.getUniqueId())) {
            return false;
        }

        ResolvedGoal goal = current.cell(cell);
        current.setClaim(cell, new Board.Claim(player.getUniqueId(), player.getName(), side.id(),
                side.color(), System.currentTimeMillis()));

        // A claimed goal is done for everyone: drop it from every side's tracking list so it
        // frees a slot and leaves the HUD.
        for (Side tracked : sides.all()) {
            tracked.removeTarget(cell);
        }

        Component claimMsg = plugin.prefix()
                .append(Component.text("\u2714 ", NamedTextColor.GREEN))
                .append(Component.text(player.getName(), side.color()))
                .append(Component.text(" claimed ", NamedTextColor.GRAY))
                .append(Component.text(goal.display(), NamedTextColor.WHITE));
        if (!sides.isFfa()) {
            claimMsg = claimMsg
                    .append(Component.text(" for ", NamedTextColor.GRAY))
                    .append(Component.text(side.displayName(), side.color()));
        }
        claimMsg = claimMsg.append(Component.text(
                " (" + current.claimedCount() + "/" + Board.CELLS + ")", NamedTextColor.DARK_GRAY));
        plugin.getServer().sendMessage(claimMsg);

        if (claimsFor(side.id()) >= winTarget) {
            finish(List.of(side), null);
        } else if (current.claimedCount() >= Board.CELLS) {
            finishByHighestScore();
        } else {
            if (plugin.sounds() != null) {
                plugin.sounds().playGoalClaimed(side, sides);
            }
            if (plugin.fireworks() != null) {
                plugin.fireworks().launchGoalClaimed(player, side);
            }
        }
        plugin.refreshViews();
        return true;
    }

    /**
     * Claims an opponent-triggered goal for {@code beneficiary} when {@code opponent} (optionally
     * a specific {@code triggeredBy} player on that side) satisfied the trigger condition.
     * Empty beneficiary sides cannot claim.
     *
     * @return true when the claim succeeded
     */
    public boolean tryClaimOpponent(Side beneficiary, Player triggeredBy, Side opponent, int cell) {
        Board current = board;
        if (state != State.RUNNING || current == null || beneficiary == null || beneficiary.size() == 0) {
            return false;
        }
        if (cell < 0 || cell >= Board.CELLS || current.claimed(cell)) {
            return false;
        }

        ResolvedGoal goal = current.cell(cell);
        String triggerLabel = triggeredBy != null
                ? triggeredBy.getName()
                : (opponent != null ? opponent.displayName() : "opponent");
        String attribution = beneficiary.displayName() + " (opponent: " + triggerLabel + ")";
        current.setClaim(cell, new Board.Claim(null, attribution, beneficiary.id(),
                beneficiary.color(), System.currentTimeMillis()));

        for (Side tracked : sides.all()) {
            tracked.removeTarget(cell);
        }

        Component broadcast = plugin.prefix()
                .append(Component.text("\u2714 ", NamedTextColor.GREEN))
                .append(Component.text(beneficiary.displayName(), beneficiary.color()))
                .append(Component.text(" claimed ", NamedTextColor.GRAY))
                .append(Component.text(goal.display(), NamedTextColor.WHITE))
                .append(Component.text(" (triggered by ", NamedTextColor.GRAY))
                .append(Component.text(triggerLabel, opponent != null ? opponent.color() : NamedTextColor.WHITE))
                .append(Component.text(") (" + current.claimedCount() + "/" + Board.CELLS + ")",
                        NamedTextColor.DARK_GRAY));
        plugin.getServer().sendMessage(broadcast);

        if (claimsFor(beneficiary.id()) >= winTarget) {
            finish(List.of(beneficiary), null);
        } else if (current.claimedCount() >= Board.CELLS) {
            finishByHighestScore();
        } else {
            if (plugin.sounds() != null) {
                plugin.sounds().playGoalClaimed(beneficiary, sides);
            }
            if (plugin.fireworks() != null) {
                plugin.fireworks().launchGoalClaimed(null, beneficiary);
            }
        }
        plugin.refreshViews();
        return true;
    }

    /**
     * Adds or removes a board cell from the player's side target list.
     * Targets are shared, so any member of a side can change them for the whole side.
     */
    public TargetResult toggleTarget(Player player, int cell) {
        Board current = board;
        if (state != State.RUNNING || current == null || cell < 0 || cell >= Board.CELLS) {
            return TargetResult.UNAVAILABLE;
        }
        Side side = sideOf(player);
        if (side == null) {
            return TargetResult.UNAVAILABLE;
        }
        if (current.claimed(cell)) {
            return TargetResult.CLAIMED;
        }
        if (side.isTargeted(cell)) {
            side.removeTarget(cell);
            return TargetResult.REMOVED;
        }
        if (side.targets().size() >= Side.MAX_TARGETS) {
            return TargetResult.FULL;
        }
        side.addTarget(cell);
        return TargetResult.ADDED;
    }

    /**
     * Re-checks the win condition during an active round (e.g. after lowering {@code goalstowin}).
     *
     * @return true if the round finished immediately
     */
    public boolean checkWinCondition() {
        if (state != State.RUNNING || board == null) {
            return false;
        }
        List<Side> ranked = rankedSides();
        if (ranked.isEmpty()) {
            return false;
        }
        int best = claimsFor(ranked.get(0).id());
        if (best < winTarget) {
            return false;
        }
        List<Side> leaders = new ArrayList<>();
        for (Side candidate : ranked) {
            if (claimsFor(candidate.id()) == best) {
                leaders.add(candidate);
            } else {
                break;
            }
        }
        finish(leaders, null);
        plugin.refreshViews();
        return true;
    }

    /**
     * Force-ends a running round and awards the win to {@code winningSide}.
     *
     * @return true when the round was running and ended cleanly
     */
    public boolean forceWin(Side winningSide, String adminName) {
        if (state != State.RUNNING || board == null || winningSide == null) {
            return false;
        }
        finish(List.of(winningSide), adminName);
        plugin.refreshViews();
        return true;
    }

    private void finishByHighestScore() {
        List<Side> ranked = rankedSides();
        if (ranked.isEmpty()) {
            return;
        }
        int best = claimsFor(ranked.get(0).id());
        List<Side> leaders = new ArrayList<>();
        for (Side candidate : ranked) {
            if (claimsFor(candidate.id()) == best) {
                leaders.add(candidate);
            } else {
                break;
            }
        }
        finish(leaders, null);
    }

    private void finish(List<Side> winningSides, String forcedByAdmin) {
        if (winningSides == null || winningSides.isEmpty()) {
            return;
        }
        this.state = State.FINISHED;
        this.winners = List.copyOf(winningSides);
        this.winner = this.winners.get(0);
        this.sides.setLocked(false);

        List<Side> displaySides = sides.isFfa() ? rankedSides() : sides.ordered();
        int maxListed = sides.isFfa() ? 6 : displaySides.size();
        Component score = Component.empty();
        int listed = 0;
        for (Side side : displaySides) {
            if (listed >= maxListed) {
                break;
            }
            if (listed > 0) {
                score = score.append(Component.text(sides.isFfa() ? ", " : " - ", NamedTextColor.DARK_GRAY));
            }
            score = score.append(Component.text(
                    side.displayName() + " " + claimsFor(side.id()), side.color()));
            listed++;
        }
        if (displaySides.size() > listed) {
            score = score.append(Component.text(" (+" + (displaySides.size() - listed) + " more)",
                    NamedTextColor.DARK_GRAY));
        }

        Component headline = plugin.prefix();
        if (forcedByAdmin != null && !forcedByAdmin.isBlank()) {
            headline = headline.append(Component.text("[Force-ended by " + forcedByAdmin + "] ", NamedTextColor.YELLOW));
        }
        if (this.winners.size() == 1) {
            Side sole = this.winners.get(0);
            headline = headline
                    .append(Component.text(sole.displayName(), sole.color()))
                    .append(Component.text(" wins the lockout! ", NamedTextColor.WHITE));
        } else {
            headline = headline.append(Component.text("Tie! ", NamedTextColor.GOLD));
            for (int i = 0; i < this.winners.size(); i++) {
                if (i > 0) {
                    headline = headline.append(Component.text(", ", NamedTextColor.DARK_GRAY));
                }
                Side tied = this.winners.get(i);
                headline = headline.append(Component.text(tied.displayName(), tied.color()));
            }
            headline = headline.append(Component.text(" win the lockout! ", NamedTextColor.WHITE));
        }

        plugin.getServer().sendMessage(headline.append(score));
        if (plugin.sounds() != null) {
            plugin.sounds().playGameFinish(this.winners, sides);
        }
        if (plugin.fireworks() != null) {
            plugin.fireworks().launchVictory(this.winners);
        }
    }
}
