package dev.tlockout;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

/**
 * A competitive side. In teams mode each side holds several players; in the planned FFA mode
 * a side will hold exactly one, which is why claims and scoring reference sides, not players.
 */
public final class Side {

    /** How many goals a side may track at once on its HUD. */
    public static final int MAX_TARGETS = 5;

    private final String id;
    private final String displayName;
    private final Material concrete;
    private final TextColor color;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<Integer> targets = new LinkedHashSet<>();

    public Side(String id, String displayName, Material concrete, TextColor color) {
        this.id = id;
        this.displayName = displayName;
        this.concrete = concrete;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** The concrete block that identifies this side, also used as its GUI icon. */
    public Material concrete() {
        return concrete;
    }

    public TextColor color() {
        return color;
    }

    public Collection<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    public int size() {
        return members.size();
    }

    public boolean has(UUID playerId) {
        return members.contains(playerId);
    }

    void add(UUID playerId) {
        members.add(playerId);
    }

    boolean remove(UUID playerId) {
        return members.remove(playerId);
    }

    // ------------------------------------------------------- tracked targets

    /** Board cell indices this side is tracking, in insertion order. */
    public Collection<Integer> targets() {
        return Collections.unmodifiableSet(targets);
    }

    public boolean isTargeted(int cell) {
        return targets.contains(cell);
    }

    public void addTarget(int cell) {
        targets.add(cell);
    }

    public void removeTarget(int cell) {
        targets.remove(cell);
    }

    public void clearTargets() {
        targets.clear();
    }
}
