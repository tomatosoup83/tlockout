package dev.tlockout.goal;

/**
 * How a tracker aggregates progress. Claim detection currently always uses the side's combined
 * total, so this is metadata for a future team-specific tracker rather than a claim switch.
 */
public enum GoalScope {
    PLAYER,
    TEAM
}
