package dev.tlockout;

import java.util.Locale;

public final class Names {
    private Names() {
    }

    /** "minecraft:cave_spider" / "CAVE_SPIDER" -> "Cave Spider" */
    public static String pretty(String raw) {
        if (raw == null || raw.isBlank()) {
            return "?";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        StringBuilder out = new StringBuilder();
        for (String part : value.split("[_\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() ? "?" : out.toString();
    }
}
