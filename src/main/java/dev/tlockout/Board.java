package dev.tlockout;

import dev.tlockout.goal.ResolvedGoal;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.format.TextColor;

/** A generated 5x5 board and the claims made against it. */
public final class Board {

    public static final int SIZE = 5;
    public static final int CELLS = SIZE * SIZE;

    public record Claim(UUID playerId, String playerName, String sideId, TextColor color, long at) {
    }

    private final long seed;
    private final ResolvedGoal[] cells;
    private final Claim[] claims;
    private final long startedAt;

    public Board(long seed, List<ResolvedGoal> drawn) {
        if (drawn.size() != CELLS) {
            throw new IllegalArgumentException("a board needs " + CELLS + " goals, got " + drawn.size());
        }
        this.seed = seed;
        this.cells = drawn.toArray(new ResolvedGoal[0]);
        this.claims = new Claim[CELLS];
        this.startedAt = System.currentTimeMillis();
    }

    public long seed() {
        return seed;
    }

    public long startedAt() {
        return startedAt;
    }

    public ResolvedGoal cell(int index) {
        return cells[index];
    }

    public Claim claim(int index) {
        return claims[index];
    }

    public boolean claimed(int index) {
        return claims[index] != null;
    }

    public void setClaim(int index, Claim claim) {
        claims[index] = claim;
    }

    public int claimedCount() {
        int count = 0;
        for (Claim claim : claims) {
            if (claim != null) {
                count++;
            }
        }
        return count;
    }
}
