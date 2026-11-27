package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Die to a damage type, an entity, or the special falling-off-vines condition. */
public final class DeathGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        boolean configured = false;
        if (spec.param("damage-type") != null) {
            damageKey(spec.param("damage-type"));
            configured = true;
        }
        if (spec.param("entity") != null) {
            GoalSupport.entity(spec.param("entity"));
            configured = true;
        }
        if (spec.param("special") != null) {
            if (!"VINES".equalsIgnoreCase(spec.param("special"))) {
                throw new IllegalArgumentException("unknown death special '" + spec.param("special")
                        + "' (only VINES is supported)");
            }
            configured = true;
        }
        if (!configured) {
            throw new IllegalArgumentException("missing 'damage-type', 'entity' or 'special'");
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        return "Die to the required cause";
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return Material.SKELETON_SKULL.name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        if (goal.param("special") != null) {
            return "Die from fall damage next to vines";
        }
        if (goal.param("damage-type") != null) {
            return "Die to " + Names.pretty(lastPath(goal.param("damage-type")));
        }
        return "Die to a " + Names.pretty(goal.param("entity"));
    }

    @Override
    public int target(ResolvedGoal goal) {
        return 1;
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        return "DEATH:" + goal.spec().id();
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return state.playerStat(player.getUniqueId(), statKey(goal));
    }

    /** Checks a death against this goal's configured cause. */
    public boolean matches(PlayerDeathEvent event, ResolvedGoal goal) {
        DamageSource source = event.getDamageSource();
        if (source == null) {
            return false;
        }
        String special = goal.param("special");
        if (special != null) {
            return "VINES".equalsIgnoreCase(special)
                    && damageKey(source).equals("minecraft:fall")
                    && vineNear(event.getEntity());
        }

        String damageType = goal.param("damage-type");
        if (damageType != null && !damageKey(source).equals(damageKey(damageType))) {
            return false;
        }
        EntityType entityType = goal.entityParam("entity");
        if (entityType != null && !entityMatches(source, entityType)) {
            return false;
        }
        return damageType != null || entityType != null;
    }

    private static boolean entityMatches(DamageSource source, EntityType wanted) {
        Entity causing = source.getCausingEntity();
        if (causing != null && causing.getType() == wanted) {
            return true;
        }
        Entity direct = source.getDirectEntity();
        return direct != null && direct.getType() == wanted;
    }

    private static boolean vineNear(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -3; dy <= 1; dy++) {
                    Material type = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (type == Material.VINE || type == Material.TWISTING_VINES
                            || type == Material.WEEPING_VINES) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String damageKey(DamageSource source) {
        return source.getDamageType().getKey().toString();
    }

    private static String damageKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'damage-type'");
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (!text.contains(":")) {
            text = "minecraft:" + text;
        }
        NamespacedKey key;
        try {
            key = NamespacedKey.fromString(text);
        } catch (RuntimeException ex) {
            key = null;
        }
        if (key == null) {
            throw new IllegalArgumentException("invalid damage type '" + raw + "'");
        }
        return key.toString();
    }

    private static String lastPath(String raw) {
        String key = damageKey(raw);
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }
}
