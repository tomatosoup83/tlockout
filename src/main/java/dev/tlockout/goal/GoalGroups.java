package dev.tlockout.goal;

import dev.tlockout.GoalType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Anti-clustering group caps and predecessor rules applied during board generation.
 * <p>
 * Mirrors Fabric Lockout's {@code GoalGroup} rules (keyed by template id so variants inherit their
 * parent template's group), plus type-level caps so plain item obtains, workstations and mining
 * goals cannot flood a single 5x5 board.
 */
public final class GoalGroups {

    private record IdGroup(String name, List<String> ids, int limit, Set<String> requirePredecessor) {
        IdGroup(String name, List<String> ids, int limit) {
            this(name, ids, limit, Set.of());
        }
    }

    private record TypeGroup(String name, GoalType type, int limit) {
    }

    private static final List<IdGroup> ID_GROUPS = List.of(
            new IdGroup("END", List.of(
                    "enter_end", "eat_chorus_fruit", "obtain_dragon_egg", "obtain_end_rod",
                    "get_levitation", "get_the_city_at_the_end_of_the_game_advancement",
                    "brew_lingering_potion"), 2),
            new IdGroup("END_STRUCTURE", List.of(
                    "get_levitation", "get_the_city_at_the_end_of_the_game_advancement"), 1),
            new IdGroup("TOOLS", List.of(
                    "obtain_wooden_tools", "obtain_stone_tools", "obtain_iron_tools",
                    "obtain_golden_tools", "obtain_diamond_tools"), 2),
            new IdGroup("DIAMOND", List.of(
                    "obtain_diamond_tools", "wear_diamond_armor"), 1),
            new IdGroup("ARMOR", List.of(
                    "wear_leather_armor", "wear_iron_armor", "wear_golden_armor",
                    "wear_diamond_armor"), 1),
            new IdGroup("ARMOR_SPECIAL", List.of(
                    "wear_colored_leather_piece", "wear_unique_colored_leather_armor",
                    "wear_carved_pumpkin_5m", "wear_chain_armor_piece", "fill_armor_stand"), 2),
            new IdGroup("KILL_MOB", List.of(
                    "kill_colored_sheep", "kill_elder_guardian", "kill_ghast", "kill_snow_golem",
                    "kill_snow_golem_in_nether", "kill_silverfish", "kill_stray", "kill_witch",
                    "kill_zombie_villager", "kill_zoglin", "kill_bat",
                    "kill_breeze_using_wind_charge", "kill_guardian"), 2),
            new IdGroup("LEGACY_MOB_HUNT", List.of(
                    "zombie_hunt", "skeleton_hunt", "spider_hunt", "creeper_hunt",
                    "enderman_hunt", "blaze_hunt", "drowned_hunt", "witch_hunt",
                    "phantom_hunt", "slime_hunt"), 2),
            new IdGroup("WITCH_CONFLICT", List.of("witch_hunt", "kill_witch"), 1),
            new IdGroup("SNOWMAN", List.of("kill_snow_golem", "kill_snow_golem_in_nether"), 1),
            new IdGroup("KILL_UNIQUE_HOSTILES", List.of(
                    "kill_7_unique_hostile_mobs", "kill_10_unique_hostile_mobs",
                    "kill_13_unique_hostile_mobs", "kill_15_unique_hostile_mobs"),
                    2, Set.of("kill_13_unique_hostile_mobs", "kill_15_unique_hostile_mobs")),
            new IdGroup("KILL_HOSTILES_OF_TYPE", List.of(
                    "kill_20_arthropods", "kill_30_undead_mobs"), 1),
            new IdGroup("FORTRESS", List.of(
                    "brew_healing_potion", "brew_invisibility_potion", "brew_lingering_potion",
                    "brew_poison_potion", "brew_swiftness_potion", "brew_water_breathing_potion",
                    "brew_weakness_potion", "place_end_crystal", "obtain_ender_chest",
                    "get_local_brewery_advancement", "obtain_all_horse_armor"), 3),
            new IdGroup("BREW_POTION", List.of(
                    "brew_healing_potion", "brew_invisibility_potion", "brew_lingering_potion",
                    "brew_poison_potion", "brew_swiftness_potion", "brew_water_breathing_potion",
                    "brew_weakness_potion"), 1),
            new IdGroup("HORSE", List.of("tame_horse", "ride_horse", "obtain_all_horse_armor"), 1),
            new IdGroup("BREED", List.of(
                    "breed_chicken", "breed_cow", "breed_fox", "breed_goat", "breed_hoglin",
                    "breed_pig", "breed_rabbit", "breed_sheep", "breed_strider", "breed_frogs"), 2),
            new IdGroup("BREED_X_UNIQUE", List.of(
                    "breed_4_unique_animals", "breed_6_unique_animals", "breed_8_unique_animals"),
                    2, Set.of("breed_8_unique_animals")),
            new IdGroup("RIDE", List.of(
                    "ride_horse", "ride_minecart", "ride_pig",
                    "get_this_boat_has_legs_advancement"), 2),
            new IdGroup("EAT_FOOD", List.of(
                    "eat_cake", "eat_chorus_fruit", "eat_cookie", "eat_glow_berry",
                    "eat_poisonous_potato", "eat_pumpkin_pie", "eat_rabbit_stew",
                    "eat_suspicious_stew"), 3),
            new IdGroup("EAT_X_UNIQUE_FOOD", List.of(
                    "eat_5_unique_foods", "eat_10_unique_foods", "eat_15_unique_foods",
                    "eat_20_unique_foods", "eat_25_unique_foods"),
                    2, Set.of("eat_20_unique_foods", "eat_25_unique_foods")),
            new IdGroup("EFFECT", List.of(
                    "get_absorption", "get_bad_omen", "get_glowing", "get_jump_boost",
                    "get_levitation", "get_mining_fatigue", "get_nausea", "get_poison",
                    "get_weakness"), 4),
            new IdGroup("EFFECT_X", List.of(
                    "get_3_status_effects", "get_4_status_effects", "get_6_status_effects"), 1),
            new IdGroup("DEATH_DAMAGE", List.of(
                    "die_to_anvil", "die_to_bee_sting", "die_to_berry_bush", "die_to_cactus",
                    "die_by_falling_off_vines", "die_to_falling_stalactite", "die_to_firework",
                    "die_to_intentional_game_design", "die_to_iron_golem", "die_to_polar_bear",
                    "die_by_magic", "die_to_tnt_minecart", "die_by_freezing",
                    "opponent_dies", "opponent_dies_3_times",
                    "deal_400_damage", "opponent_takes_100_damage", "take_200_damage"), 3),
            new IdGroup("BIOME", List.of(
                    "visit_desert", "visit_plains", "visit_forest", "visit_taiga", "visit_swamp",
                    "visit_jungle", "visit_savanna", "visit_badlands", "visit_ocean",
                    "visit_ice_spikes", "visit_mushroom",
                    "get_hot_tourist_destinations_advancement"), 1),
            new IdGroup("OPPONENT_HARD", List.of(
                    "opponent_obtains_crafting_table", "opponent_touches_water"), 1),
            new IdGroup("OPPONENT_TAKES_DAMAGE", List.of(
                    "opponent_takes_100_damage", "opponent_takes_fall_damage",
                    "opponent_catches_on_fire"), 1),
            new IdGroup("OPPONENT_GOALS", List.of(
                    "opponent_takes_100_damage", "opponent_takes_fall_damage",
                    "opponent_catches_on_fire", "opponent_obtains_crafting_table",
                    "opponent_obtains_obsidian", "opponent_obtains_seeds",
                    "opponent_touches_water", "opponent_eats_food",
                    "opponent_hit_by_egg", "opponent_hit_by_snowball",
                    "opponent_dies", "opponent_dies_3_times"), 3),
            new IdGroup("OPPONENT_DEATH_DAMAGE", List.of(
                    "opponent_dies", "opponent_dies_3_times", "opponent_takes_100_damage"), 1),
            new IdGroup("OPPONENT_HIT_BY", List.of(
                    "opponent_hit_by_egg", "opponent_hit_by_snowball"), 1),
            new IdGroup("DAMAGE", List.of(
                    "deal_400_damage", "opponent_takes_100_damage", "take_200_damage"), 1),
            new IdGroup("ADVANCEMENT", List.of(
                    "get_10_advancements", "get_20_advancements", "get_30_advancements",
                    "get_a_terrible_fortress_advancement", "get_any_spyglass_advancement",
                    "get_bullseye_advancement", "get_eye_spy_advancement",
                    "get_hot_tourist_destinations_advancement",
                    "get_not_quite_nine_lives_advancement", "get_oh_shiny_advancement",
                    "get_sniper_duel_advancement", "get_the_city_at_the_end_of_the_game_advancement",
                    "get_this_boat_has_legs_advancement", "get_those_were_the_days_advancement",
                    "get_what_a_deal_advancement", "get_hired_help_advancement",
                    "get_wax_on_advancement", "get_wax_off_advancement"), 4),
            new IdGroup("X_ADVANCEMENTS", List.of(
                    "get_10_advancements", "get_20_advancements", "get_30_advancements"),
                    2, Set.of("get_30_advancements")),
            new IdGroup("VILLAGE", List.of(
                    "eat_pumpkin_pie", "eat_cookie", "get_what_a_deal_advancement",
                    "obtain_bell", "die_to_iron_golem", "eat_rabbit_stew",
                    "wear_chain_armor_piece"), 3),
            new IdGroup("MONUMENT", List.of(
                    "get_mining_fatigue", "kill_elder_guardian", "kill_guardian",
                    "obtain_sponge", "die_by_magic"), 1),
            new IdGroup("REDSTONE", List.of(
                    "obtain_redstone_comparator", "obtain_redstone_lamp", "obtain_clock",
                    "obtain_redstone_repeater", "obtain_piston", "obtain_activator_rail",
                    "obtain_detector_rail", "obtain_powered_rail", "obtain_dispenser",
                    "obtain_observer"), 3),
            new IdGroup("TNT", List.of("obtain_all_minecarts", "obtain_tnt", "die_to_tnt_minecart"), 1),
            new IdGroup("STRIDER", List.of("breed_strider", "get_this_boat_has_legs_advancement"), 1),
            new IdGroup("SHEEP", List.of("breed_sheep", "kill_colored_sheep"), 1),
            new IdGroup("OBTAIN_ALL", List.of(
                    "obtain_all_horse_armor", "obtain_all_minecarts", "obtain_all_pumpkins",
                    "obtain_all_mushrooms", "obtain_all_raw_ore_blocks",
                    "obtain_4_unique_seeds"), 2),
            new IdGroup("OBTAIN_SEEDS", List.of(
                    "obtain_4_unique_seeds", "opponent_obtains_seeds"), 1),
            new IdGroup("HONEY", List.of(
                    "drink_honey_bottle", "get_wax_on_advancement", "get_wax_off_advancement"), 1),
            new IdGroup("IRON_HEAVY", List.of(
                    "use_anvil", "die_to_anvil", "obtain_all_minecarts",
                    "get_hired_help_advancement"), 1),
            new IdGroup("LEATHER_HEAVY", List.of(
                    "wear_leather_armor", "wear_unique_colored_leather_armor"), 1),
            new IdGroup("FIREWORKS", List.of(
                    "die_to_firework", "shoot_firework_from_crossbow"), 1),
            new IdGroup("WOLF", List.of("tame_wolf", "put_wolf_armor_on_wolf"), 1),
            new IdGroup("TRIAL_CHAMBERS", List.of(
                    "kill_breeze_using_wind_charge", "kill_silverfish"), 1),
            new IdGroup("RAID", List.of("get_bad_omen", "kill_all_raid_mobs"), 1),
            new IdGroup("COLORED_BLOCKS", List.of(
                    "obtain_64_colored_wool", "obtain_64_colored_concrete",
                    "obtain_colored_glazed_terracotta"), 1),
            new IdGroup("ARROWS", List.of("arrow", "obtain_64_arrows"), 1),
            new IdGroup("XP_LEVELS", List.of("reach_xp_level_15", "reach_xp_level_30"), 1)
    );

    /**
     * Type-level anti-clustering caps so one broad family (especially plain item obtains) cannot
     * dominate a board.
     */
    private static final List<TypeGroup> TYPE_GROUPS = List.of(
            new TypeGroup("PLAIN_ITEMS", GoalType.ITEM, 6),
            new TypeGroup("DURABILITY", GoalType.DURABILITY, 1),
            new TypeGroup("WORKSTATIONS", GoalType.WORKSTATION, 3),
            new TypeGroup("MINE_BLOCKS", GoalType.MINE_BLOCK, 2),
            new TypeGroup("TAME", GoalType.TAME, 2),
            new TypeGroup("POSITION", GoalType.POSITION, 1)
    );

    private GoalGroups() {
    }

    /**
     * True when {@code candidate} respects every group cap and predecessor rule against the
     * templates already chosen for the current board.
     */
    public static boolean canAdd(GoalSpec candidate, List<GoalSpec> chosen) {
        String id = candidate.id();
        for (IdGroup group : ID_GROUPS) {
            int index = group.ids().indexOf(id);
            if (index < 0) {
                continue;
            }
            int matches = 0;
            boolean hasPredecessor = false;
            for (GoalSpec picked : chosen) {
                int pickedIndex = group.ids().indexOf(picked.id());
                if (pickedIndex >= 0) {
                    matches++;
                    if (pickedIndex < index) {
                        hasPredecessor = true;
                    }
                }
            }
            if (matches + 1 > group.limit()) {
                return false;
            }
            if (group.requirePredecessor().contains(id) && !hasPredecessor) {
                return false;
            }
        }
        for (TypeGroup group : TYPE_GROUPS) {
            if (candidate.type() != group.type()) {
                continue;
            }
            int matches = 0;
            for (GoalSpec picked : chosen) {
                if (picked.type() == group.type()) {
                    matches++;
                }
            }
            if (matches + 1 > group.limit()) {
                return false;
            }
        }
        return true;
    }

    /** Returns every template id referenced by {@link #ID_GROUPS} (useful for static validation). */
    public static List<String> allReferencedIds() {
        List<String> ids = new ArrayList<>();
        for (IdGroup group : ID_GROUPS) {
            ids.addAll(group.ids());
        }
        return ids;
    }
}
