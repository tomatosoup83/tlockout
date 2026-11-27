package dev.tlockout.goal.tracker;

import dev.tlockout.Names;
import dev.tlockout.goal.GoalScope;
import dev.tlockout.goal.GoalSpec;
import dev.tlockout.goal.GoalSupport;
import dev.tlockout.goal.GoalTracker;
import dev.tlockout.goal.ResolvedGoal;
import dev.tlockout.goal.TrackingState;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Kill a specific mob under an extra condition: dimension (e.g. Snow Golem in The Nether)
 * and/or damage type (e.g. Breeze killed with a Wind Charge).
 */
public final class ConditionalKillGoalTracker implements GoalTracker {

    @Override
    public GoalScope scope() {
        return GoalScope.PLAYER;
    }

    @Override
    public void validate(GoalSpec spec) {
        GoalSupport.entity(spec.param("entity"));
        boolean hasCondition = false;
        if (spec.param("dimension") != null) {
            parseDimension(spec.param("dimension"));
            hasCondition = true;
        }
        if (spec.param("damage-type") != null) {
            damageKey(spec.param("damage-type"));
            hasCondition = true;
        }
        if (!hasCondition) {
            throw new IllegalArgumentException("missing 'dimension' or 'damage-type'");
        }
        if (spec.intParam("count", 1) <= 0) {
            throw new IllegalArgumentException("'count' must be positive");
        }
    }

    @Override
    public String defaultDisplay(GoalSpec spec) {
        EntityType entity = GoalSupport.entity(spec.param("entity"));
        String base = "Kill " + GoalSupport.prettyEntity(entity);
        if (spec.param("dimension") != null) {
            return base + " in " + dimensionLabel(parseDimension(spec.param("dimension")));
        }
        if (spec.param("damage-type") != null) {
            return base + " using " + Names.pretty(lastPath(spec.param("damage-type")));
        }
        return base;
    }

    @Override
    public String defaultIcon(GoalSpec spec) {
        return GoalSupport.spawnEgg(GoalSupport.entity(spec.param("entity"))).name();
    }

    @Override
    public String requirement(ResolvedGoal goal) {
        EntityType entity = goal.entityParam("entity");
        String name = entity == null ? "mob" : GoalSupport.prettyEntity(entity);
        StringBuilder sb = new StringBuilder("Kill a ").append(name);
        if (goal.param("damage-type") != null) {
            sb.append(" with ").append(Names.pretty(lastPath(goal.param("damage-type"))));
        }
        if (goal.param("dimension") != null) {
            sb.append(" in ").append(dimensionLabel(parseDimension(goal.param("dimension"))));
        }
        return sb.toString();
    }

    @Override
    public int target(ResolvedGoal goal) {
        return Math.max(1, goal.intParam("count", 1));
    }

    @Override
    public String statKey(ResolvedGoal goal) {
        return "COND_KILL:" + goal.spec().id();
    }

    @Override
    public int progress(Player player, ResolvedGoal goal, TrackingState state) {
        return Math.min(state.playerStat(player.getUniqueId(), statKey(goal)), target(goal));
    }

    public boolean matches(EntityDeathEvent event, ResolvedGoal goal) {
        EntityType wanted = goal.entityParam("entity");
        if (wanted == null || event.getEntityType() != wanted) {
            return false;
        }
        String dim = goal.param("dimension");
        if (dim != null) {
            World.Environment required = parseDimension(dim);
            if (event.getEntity().getWorld().getEnvironment() != required) {
                return false;
            }
        }
        String dmg = goal.param("damage-type");
        if (dmg != null) {
            DamageSource source = event.getDamageSource();
            if (source == null) {
                return false;
            }
            String expected = damageKey(dmg);
            String actual = source.getDamageType().getKey().toString();
            if (!actual.equals(expected)) {
                // Also accept a direct wind charge entity when checking minecraft:wind_charge.
                if (!expected.equals("minecraft:wind_charge")
                        || !(source.getDirectEntity() instanceof AbstractWindCharge)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static World.Environment parseDimension(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing 'dimension'");
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "OVERWORLD", "NORMAL" -> World.Environment.NORMAL;
            case "NETHER", "THE_NETHER" -> World.Environment.NETHER;
            case "END", "THE_END" -> World.Environment.THE_END;
            default -> throw new IllegalArgumentException("unknown dimension '" + raw + "'");
        };
    }

    private static String dimensionLabel(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> "The Overworld";
            case NETHER -> "The Nether";
            case THE_END -> "The End";
            default -> Names.pretty(environment.name());
        };
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
