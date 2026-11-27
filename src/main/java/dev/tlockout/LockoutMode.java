package dev.tlockout;

import java.util.Locale;

/** Game mode for Lockout rounds: fixed teams or every-player-for-themselves FFA. */
public enum LockoutMode {
    TEAMS("teams", "Teams"),
    FFA("ffa", "FFA");

    private final String configKey;
    private final String displayName;

    LockoutMode(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    public static LockoutMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TEAMS;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "teams", "team" -> TEAMS;
            case "ffa", "freeforall", "free-for-all", "free_for_all" -> FFA;
            default -> throw new IllegalArgumentException("unknown mode '" + raw + "' (expected 'teams' or 'ffa')");
        };
    }
}
