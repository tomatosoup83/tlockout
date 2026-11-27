package dev.tlockout;

import java.util.Locale;

public enum GoalType {
    ITEM,
    DURABILITY,
    MOB_KILL,
    PLAYER_KILL,
    BIOME,
    TAME,
    BREED,
    DIMENSION,
    CONSUME_ITEM,
    UNIQUE_FOODS,
    ADVANCEMENT,
    ADVANCEMENT_COUNT,
    STATUS_EFFECT,
    STATUS_EFFECT_COUNT,
    MILK_EFFECT,
    DEATH_BY,
    DAMAGE_TOTAL,
    XP_LEVEL,
    POSITION,
    EMPTY_HUNGER,
    RIDE,
    STAT_COUNTER,
    ARMOR_SET,
    ARMOR_PIECE,
    ARMOR_TIMER,
    WOLF_ARMOR,
    ITEM_SET,
    UNIQUE_INVENTORY,
    MOB_KILL_TOTAL,
    MOB_KILL_UNIQUE,
    MOB_KILL_GROUP,
    COLORED_SHEEP_KILL,
    OPPONENT,
    WORKSTATION,
    BREW_POTION,
    MINE_BLOCK,
    BLOCK_ACTION,
    BANNER_SHIELD,
    CONDITIONAL_KILL,
    BREED_UNIQUE;

    public static GoalType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("goal is missing a type");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown goal type '" + raw + "'");
        }
    }

    public boolean entityBased() {
        return this == MOB_KILL || this == TAME || this == BREED;
    }

    public boolean itemBased() {
        return this == ITEM || this == DURABILITY;
    }
}
