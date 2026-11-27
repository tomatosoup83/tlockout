package dev.tlockout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Holds the competitive sides and who belongs to which one. Membership survives rounds, not restarts.
 * <p>
 * In {@link LockoutMode#TEAMS} mode there are two configured concrete-based teams. In
 * {@link LockoutMode#FFA} mode every participating player gets their own 1-member side with a
 * concrete colour from {@link ConcreteColors#PALETTE}.
 */
public final class SideManager {

    private final LockoutMode mode;
    private final Map<String, Side> sides = new LinkedHashMap<>();
    private final Map<UUID, String> byPlayer = new LinkedHashMap<>();
    /** Players who explicitly ran {@code /lo leave} in FFA so auto-enroll skips them. */
    private final Set<UUID> ffaOptOut = new LinkedHashSet<>();
    private final boolean friendlyFire;
    private boolean locked;

    private SideManager(LockoutMode mode, boolean friendlyFire) {
        this.mode = mode;
        this.friendlyFire = friendlyFire;
    }

    /**
     * @throws IllegalArgumentException when the section is missing, malformed, or does not
     *                                  describe exactly two concrete-based teams
     */
    public static SideManager load(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("config.yml is missing the 'teams' section");
        }
        SideManager manager = new SideManager(LockoutMode.TEAMS, section.getBoolean("friendly-fire", false));
        for (String id : section.getKeys(false)) {
            ConfigurationSection team = section.getConfigurationSection(id);
            if (team == null) {
                continue; // friendly-fire and any other scalars
            }
            Material concrete = concreteFor(id, team.getString("concrete"));
            String name = team.getString("name");
            if (name == null || name.isBlank()) {
                name = Names.pretty(concrete.getKey().getKey().replace("_concrete", ""));
            }
            manager.sides.put(id.toLowerCase(Locale.ROOT),
                    new Side(id.toLowerCase(Locale.ROOT), name, concrete, ConcreteColors.of(concrete)));
        }
        if (manager.sides.size() != 2) {
            throw new IllegalArgumentException("teams mode needs exactly 2 teams under 'teams', found "
                    + manager.sides.size());
        }
        return manager;
    }

    /** Creates an empty Free-For-All side manager where each participant becomes their own side. */
    public static SideManager ffa() {
        return new SideManager(LockoutMode.FFA, true);
    }

    private static Material concreteFor(String id, String raw) {
        String name = (raw == null || raw.isBlank())
                ? id.trim().toUpperCase(Locale.ROOT) + "_CONCRETE"
                : raw.trim().toUpperCase(Locale.ROOT);
        Material material;
        try {
            material = Material.matchMaterial(name);
        } catch (RuntimeException ex) {
            material = null;
        }
        if (material == null || !ConcreteColors.isConcrete(material)) {
            throw new IllegalArgumentException("team '" + id + "': '" + name
                    + "' is not one of the 16 concrete blocks");
        }
        return material;
    }

    public LockoutMode mode() {
        return mode;
    }

    public boolean isFfa() {
        return mode == LockoutMode.FFA;
    }

    public Collection<Side> all() {
        return sides.values();
    }

    /** Stable order, used for the GUI counters and rankings tie-breaking. */
    public List<Side> ordered() {
        return new ArrayList<>(sides.values());
    }

    public Side byId(String id) {
        return id == null ? null : sides.get(id.toLowerCase(Locale.ROOT));
    }

    public Side sideOf(UUID playerId) {
        String id = byPlayer.get(playerId);
        return id == null ? null : sides.get(id);
    }

    /** Returns the opposing side when there are exactly 2 sides, or null otherwise. */
    public Side otherOf(Side side) {
        if (side == null || sides.size() != 2) {
            return null;
        }
        for (Side candidate : sides.values()) {
            if (!candidate.id().equals(side.id())) {
                return candidate;
            }
        }
        return null;
    }

    public boolean locked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    // -------------------------------------------------------------- teams mode

    /**
     * Moves a player onto a side. When membership is locked (round running), only unassigned
     * players may join; existing team members stay locked.
     *
     * @return false when membership is locked for an already-assigned player or the side is unknown
     */
    public boolean assign(UUID playerId, String sideId) {
        if (locked && sideOf(playerId) != null) {
            return false;
        }
        return forceAssign(playerId, sideId);
    }

    /**
     * Moves a player onto a side even while membership is locked (for admin overrides).
     *
     * @return false only when the side id is unknown
     */
    public boolean forceAssign(UUID playerId, String sideId) {
        Side target = byId(sideId);
        if (target == null) {
            return false;
        }
        Side current = sideOf(playerId);
        if (current != null) {
            current.remove(playerId);
        }
        target.add(playerId);
        byPlayer.put(playerId, target.id());
        return true;
    }

    public void remove(UUID playerId) {
        Side current = sideOf(playerId);
        if (current != null) {
            current.remove(playerId);
            if (isFfa() && current.size() == 0) {
                sides.remove(current.id());
            }
        }
        byPlayer.remove(playerId);
    }

    // ---------------------------------------------------------------- FFA mode

    public boolean isOptedOut(UUID playerId) {
        return ffaOptOut.contains(playerId);
    }

    public Set<UUID> ffaOptOuts() {
        return Collections.unmodifiableSet(ffaOptOut);
    }

    /**
     * Enrolls a player as their own 1-member side in FFA mode, clearing any prior opt-out.
     * If {@code preferredConcrete} is provided while unlocked, updates the player's colour.
     *
     * @return the player's FFA side, or null if not in FFA mode
     */
    public Side enrollFfa(Player player, Material preferredConcrete) {
        if (!isFfa() || player == null) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        ffaOptOut.remove(playerId);
        String sideId = ffaSideId(playerId);
        Side existing = sides.get(sideId);
        if (existing != null) {
            if (preferredConcrete != null && !locked && ConcreteColors.isConcrete(preferredConcrete)
                    && existing.concrete() != preferredConcrete) {
                Side updated = new Side(sideId, player.getName(), preferredConcrete,
                        ConcreteColors.of(preferredConcrete));
                updated.add(playerId);
                for (int cell : existing.targets()) {
                    updated.addTarget(cell);
                }
                sides.put(sideId, updated);
                byPlayer.put(playerId, sideId);
                return updated;
            }
            return existing;
        }
        Material concrete = (preferredConcrete != null && ConcreteColors.isConcrete(preferredConcrete))
                ? preferredConcrete
                : nextAvailableConcrete();
        Side created = new Side(sideId, player.getName(), concrete, ConcreteColors.of(concrete));
        created.add(playerId);
        sides.put(sideId, created);
        byPlayer.put(playerId, sideId);
        return created;
    }

    /**
     * Prepares the FFA roster for a new round: removes stale offline sides from earlier rounds and
     * automatically enrolls every online player who has not explicitly opted out via {@code /lo leave}.
     *
     * @return total number of enrolled FFA players after sync
     */
    public int enrollOnlineForFfa() {
        if (!isFfa()) {
            return sides.size();
        }
        // Drop offline participants from previous rounds so stale offline sides do not affect
        // player count, opponent-goal eligibility, or leaderboards.
        Iterator<Map.Entry<String, Side>> it = sides.entrySet().iterator();
        while (it.hasNext()) {
            Side side = it.next().getValue();
            boolean anyOnline = false;
            for (UUID memberId : side.members()) {
                if (Bukkit.getPlayer(memberId) != null) {
                    anyOnline = true;
                    break;
                }
            }
            if (!anyOnline) {
                for (UUID memberId : side.members()) {
                    byPlayer.remove(memberId);
                }
                it.remove();
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            UUID id = online.getUniqueId();
            if (!ffaOptOut.contains(id) && sideOf(id) == null) {
                enrollFfa(online, null);
            }
        }
        return sides.size();
    }

    /** Opts a player out of FFA auto-enrollment and removes their FFA side when unlocked. */
    public boolean optOutFfa(UUID playerId) {
        if (!isFfa() || locked) {
            return false;
        }
        remove(playerId);
        ffaOptOut.add(playerId);
        return true;
    }

    private Material nextAvailableConcrete() {
        Set<Material> used = new HashSet<>();
        for (Side side : sides.values()) {
            used.add(side.concrete());
        }
        for (Material candidate : ConcreteColors.PALETTE) {
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return ConcreteColors.PALETTE.get(sides.size() % ConcreteColors.PALETTE.size());
    }

    private static String ffaSideId(UUID playerId) {
        return "ffa_" + playerId.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Copies membership from a previously loaded manager into this one when both use the same mode.
     * Used by {@code /lockout reload} so reloading config does not wipe team or FFA rosters.
     */
    public void transferMembershipFrom(SideManager previous) {
        if (previous == null || previous.mode != this.mode) {
            return;
        }
        if (isFfa()) {
            ffaOptOut.addAll(previous.ffaOptOut);
            for (Side old : previous.all()) {
                Side copy = new Side(old.id(), old.displayName(), old.concrete(), old.color());
                for (UUID member : old.members()) {
                    copy.add(member);
                    byPlayer.put(member, copy.id());
                }
                sides.put(copy.id(), copy);
            }
            return;
        }
        for (Side old : previous.all()) {
            Side same = sides.get(old.id());
            if (same == null) {
                continue;
            }
            for (UUID member : old.members()) {
                same.add(member);
                byPlayer.put(member, same.id());
            }
        }
    }
}
