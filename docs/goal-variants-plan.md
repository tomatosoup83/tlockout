# Additional goal variants — proposal (not started)

Status: **proposed, nothing implemented.** Written 2026-11-26 after reading the goal model, all 40
trackers, `goals.yml` (267 templates, `goals-version: 9`), `GoalGroups` and `TrackingService`.

Purpose: add more goal variety as **variants of families that already exist**, so the work is mostly
catalogue entries rather than new trackers. The human tests every mechanic (see `AGENTS.md` §1); this
document is scope only.

---

## 0. Two facts that constrain every suggestion

1. **`variants:` does not add draw weight.** `GoalCatalog.pickVariants` picks one concrete value at
   draw time from a random list of alternatives. A 16-colour variant template is still **one**
   lottery ticket in the weighted draw; it just renders a random colour on the board. So variants are
   good for *per-board freshness*, not for *raising how often a family appears*. To make a family
   appear more often you add more templates (or raise `weight`).
2. **A template is free only if its tracker already reads that parameter.** Anything else is Java.
   Checked per family below.

---

## 1. Tier A — pure YAML, no Java

Each entry below is valid against the current trackers (verified by reading them).

### 1.1 Status effects — `status_effect`

Currently 9 templates, and **`GoalGroups` has no type cap for `StatusEffect`**. Highest variety gain
per line changed.

```yaml
- { id: get_speed,             type: status_effect, effect: SPEED,             display: "Get Speed",             weight: 5 }
- { id: get_strength,          type: status_effect, effect: STRENGTH,          display: "Get Strength",          weight: 5 }
- { id: get_regeneration,      type: status_effect, effect: REGENERATION,      display: "Get Regeneration",      weight: 5 }
- { id: get_fire_resistance,   type: status_effect, effect: FIRE_RESISTANCE,   display: "Get Fire Resistance",   weight: 4 }
- { id: get_night_vision,      type: status_effect, effect: NIGHT_VISION,      display: "Get Night Vision",      weight: 4 }
- { id: get_water_breathing,   type: status_effect, effect: WATER_BREATHING,   display: "Get Water Breathing",   weight: 4 }
- { id: get_slow_falling,      type: status_effect, effect: SLOW_FALLING,      display: "Get Slow Falling",      weight: 4 }
- { id: get_invisibility,      type: status_effect, effect: INVISIBILITY,      display: "Get Invisibility",      weight: 4 }
- { id: get_haste,             type: status_effect, effect: HASTE,             display: "Get Haste",             weight: 4 }
- { id: get_resistance,        type: status_effect, effect: RESISTANCE,        display: "Get Resistance",        weight: 4 }
- { id: get_health_boost,      type: status_effect, effect: HEALTH_BOOST,      display: "Get Health Boost",      weight: 4 }
- { id: get_dolphins_grace,    type: status_effect, effect: DOLPHINS_GRACE,    display: "Get Dolphin's Grace",   weight: 4 }
- { id: get_blindness,         type: status_effect, effect: BLINDNESS,         display: "Get Blindness",         weight: 3 }
- { id: get_darkness,          type: status_effect, effect: DARKNESS,          display: "Get Darkness",          weight: 3, requires: { structures: [ancient_city] } }
- { id: get_wither_effect,     type: status_effect, effect: WITHER,            display: "Get Wither",            weight: 3 }
- { id: get_hunger_effect,     type: status_effect, effect: HUNGER,            display: "Get Hunger",            weight: 4 }
- { id: get_hero_of_the_village, type: status_effect, effect: HERO_OF_THE_VILLAGE, display: "Get Hero of the Village", weight: 2, requires: { structures: [village_plains, village_desert, village_savanna, village_snowy, village_taiga] } }
```

Note: `effect:` is parsed through `NamespacedKey`, so bare `SPEED` works (it is prefixed with
`minecraft:`). `StatusEffectGoalTracker.progress` also counts a *past* effect, so drinking the potion
then letting it expire still claims.

### 1.2 Brewing — `brew_potion`

`potions:` takes a list and matches any; include the long/strong forms so any brew of that effect
claims.

```yaml
- { id: brew_strength_potion,         type: brew_potion, potions: [STRENGTH, LONG_STRENGTH, STRONG_STRENGTH],             display: "Brew a Potion of Strength",         weight: 4 }
- { id: brew_regeneration_potion,     type: brew_potion, potions: [REGENERATION, LONG_REGENERATION, STRONG_REGENERATION], display: "Brew a Potion of Regeneration",     weight: 4 }
- { id: brew_fire_resistance_potion,  type: brew_potion, potions: [FIRE_RESISTANCE, LONG_FIRE_RESISTANCE],                display: "Brew a Potion of Fire Resistance",  weight: 4 }
- { id: brew_night_vision_potion,     type: brew_potion, potions: [NIGHT_VISION, LONG_NIGHT_VISION],                      display: "Brew a Potion of Night Vision",     weight: 4 }
- { id: brew_slow_falling_potion,     type: brew_potion, potions: [SLOW_FALLING, LONG_SLOW_FALLING],                      display: "Brew a Potion of Slow Falling",     weight: 3 }
- { id: brew_leaping_potion,          type: brew_potion, potions: [LEAPING, LONG_LEAPING, STRONG_LEAPING],                display: "Brew a Potion of Leaping",          weight: 4 }
- { id: brew_harming_potion,          type: brew_potion, potions: [HARMING, STRONG_HARMING],                              display: "Brew a Potion of Harming",          weight: 3 }
- { id: brew_turtle_master_potion,    type: brew_potion, potions: [TURTLE_MASTER, LONG_TURTLE_MASTER, STRONG_TURTLE_MASTER], display: "Brew a Potion of the Turtle Master", weight: 2 }
```

### 1.3 Consume / drink — `consume_item`

```yaml
- { id: eat_golden_apple,           type: consume_item, item: GOLDEN_APPLE,          display: "Eat Golden Apple",          weight: 4 }
- { id: eat_enchanted_golden_apple, type: consume_item, item: ENCHANTED_GOLDEN_APPLE, display: "Eat Enchanted Golden Apple", weight: 1 }
- { id: eat_rotten_flesh,           type: consume_item, item: ROTTEN_FLESH,          display: "Eat Rotten Flesh",          weight: 5 }
- { id: eat_spider_eye,             type: consume_item, item: SPIDER_EYE,            display: "Eat Spider Eye",            weight: 5 }
- { id: eat_pufferfish,             type: consume_item, item: PUFFERFISH,            display: "Eat Pufferfish",            weight: 4 }
- { id: eat_tropical_fish,          type: consume_item, item: TROPICAL_FISH,         display: "Eat Tropical Fish",         weight: 4 }
- { id: eat_mushroom_stew,          type: consume_item, item: MUSHROOM_STEW,         display: "Eat Mushroom Stew",         weight: 4 }
- { id: eat_beetroot_soup,          type: consume_item, item: BEETROOT_SOUP,         display: "Eat Beetroot Soup",         weight: 4 }
- { id: drink_milk_bucket,          type: consume_item, item: MILK_BUCKET,           display: "Drink Milk",                weight: 5 }
- { id: eat_dried_kelp,             type: consume_item, item: DRIED_KELP,            display: "Eat Dried Kelp",            weight: 5 }
```

Caveat: `onConsume` only counts records for `material.isEdible()` for the *unique foods* set, but
`CONSUME:<MATERIAL>` is incremented for every `PlayerItemConsumeEvent`, so milk (non-edible) still
counts for its own goal. `eat_cake` already relies on the statistic path.

### 1.4 Mob kill groups — `mob_kill_group`

Currently only 2 templates (arthropods, undead). `entities:` is a free-form list.

```yaml
- id: kill_20_nether_mobs
  type: mob_kill_group
  count: 20
  entities: [BLAZE, GHAST, MAGMA_CUBE, PIGLIN, ZOMBIFIED_PIGLIN, HOGLIN, ZOGLIN, WITHER_SKELETON]
  icon: NETHERRACK
  display: "Kill 20 Nether Mobs"
  requirement: "Kill 20 nether mobs as a team"
  weight: 4

- id: kill_15_ocean_mobs
  type: mob_kill_group
  count: 15
  entities: [DROWNED, GUARDIAN, ELDER_GUARDIAN]
  icon: PRISMARINE_SHARD
  display: "Kill 15 Ocean Mobs"
  requirement: "Kill 15 ocean mobs as a team"
  weight: 3

- id: kill_20_illagers
  type: mob_kill_group
  count: 20
  entities: [PILLAGER, VINDICATOR, EVOKER, RAVAGER, WITCH, VEX]
  icon: CROSSBOW
  display: "Kill 20 Illagers"
  requirement: "Kill 20 illagers as a team"
  requires:
    structures: [pillager_outpost, mansion]
  weight: 3

- id: kill_25_animals
  type: mob_kill_group
  count: 25
  entities: [COW, PIG, SHEEP, CHICKEN, RABBIT, HORSE, DONKEY, MULE, LLAMA, GOAT, FOX, WOLF, CAT, PARROT]
  icon: WHEAT
  display: "Kill 25 Animals"
  requirement: "Kill 25 passive animals as a team"
  weight: 3
```

### 1.5 Biomes — `biome`

`BIOME` id-group limit is **1 per board**, and each entry needs a `requires:` gate so
`WorldFeasibilityScanner` can drop it when the biome is not within `world-scan.radius`.

```yaml
- { id: visit_cherry_grove,    type: biome, biome: cherry_grove,    display: "Find Cherry Grove Biome",    weight: 4, requires: { biomes: [cherry_grove] } }
- { id: visit_mangrove_swamp,  type: biome, biome: mangrove_swamp,  display: "Find Mangrove Swamp Biome",  weight: 4, requires: { biomes: [mangrove_swamp] } }
- { id: visit_lush_caves,      type: biome, biome: lush_caves,      icon: MOSS_BLOCK,    display: "Find Lush Caves Biome", weight: 3, requires: { biomes: [lush_caves] } }
- { id: visit_deep_dark,       type: biome, biome: deep_dark,       icon: SCULK,         display: "Find Deep Dark Biome",  weight: 3, requires: { biomes: [deep_dark] } }
- { id: visit_stony_peaks,     type: biome, biome: stony_peaks,     display: "Find Stony Peaks Biome",    weight: 4, requires: { biomes: [stony_peaks] } }
- { id: visit_flower_forest,   type: biome, biome: flower_forest,   display: "Find Flower Forest Biome",  weight: 4, requires: { biomes: [flower_forest] } }
- { id: visit_meadow,          type: biome, biome: meadow,          display: "Find Meadow Biome",         weight: 4, requires: { biomes: [meadow] } }
- { id: visit_crimson_forest,  type: biome, biome: crimson_forest,  icon: CRIMSON_NYLIUM, display: "Find Crimson Forest Biome", weight: 3, requires: { biomes: [crimson_forest] } }
- { id: visit_warped_forest,   type: biome, biome: warped_forest,   icon: WARPED_NYLIUM,  display: "Find Warped Forest Biome",  weight: 3, requires: { biomes: [warped_forest] } }
- { id: visit_soul_sand_valley, type: biome, biome: soul_sand_valley, display: "Find Soul Sand Valley Biome", weight: 3, requires: { biomes: [soul_sand_valley] } }
- { id: visit_basalt_deltas,   type: biome, biome: basalt_deltas,   display: "Find Basalt Deltas Biome",  weight: 3, requires: { biomes: [basalt_deltas] } }
```

Open question: the scanner locates biomes around the **overworld spawn** only. Nether biome goals may
never be gated correctly (the scan world is the overworld). Either drop the Nether entries or accept
that the gate fails open (unknown/ungated). **Decide during implementation.**

### 1.6 Mining counts — `mine_block`

`MINE_BLOCK` type cap is **2 per board**. `count:` already works.

```yaml
- { id: mine_8_iron_ore,     type: mine_block, blocks: [IRON_ORE, DEEPSLATE_IRON_ORE],     count: 8,  display: "Mine 8 Iron Ore",     requirement: "Break 8 iron ore blocks",     weight: 4 }
- { id: mine_16_coal_ore,    type: mine_block, blocks: [COAL_ORE, DEEPSLATE_COAL_ORE],     count: 16, display: "Mine 16 Coal Ore",    requirement: "Break 16 coal ore blocks",    weight: 4 }
- { id: mine_4_gold_ore,     type: mine_block, blocks: [GOLD_ORE, DEEPSLATE_GOLD_ORE],     count: 4,  display: "Mine 4 Gold Ore",     requirement: "Break 4 gold ore blocks",     weight: 4 }
- { id: mine_3_ancient_debris, type: mine_block, blocks: [ANCIENT_DEBRIS],                 count: 3,  display: "Mine 3 Ancient Debris", requirement: "Break 3 ancient debris blocks", weight: 3 }
- { id: mine_32_stone,       type: mine_block, blocks: [STONE],                            count: 32, display: "Mine 32 Stone",       requirement: "Break 32 stone blocks",       weight: 4 }
```

### 1.7 Rides — `ride`

Pig is the only special-cased entity (needs carrot on a stick); everything else just checks the
vehicle type.

```yaml
- { id: ride_boat,    type: ride, entity: OAK_BOAT, display: "Ride a Boat",   weight: 5 }
- { id: ride_camel,   type: ride, entity: CAMEL,    display: "Ride a Camel",  weight: 4, requires: { biomes: [desert] } }
- { id: ride_strider, type: ride, entity: STRIDER,  display: "Ride a Strider", weight: 4 }
- { id: ride_donkey,  type: ride, entity: DONKEY,   display: "Ride a Donkey", weight: 4 }
```

**Verify before shipping:** `ride` compares `player.getVehicle().getType()` to the entity param, so
`OAK_BOAT` must resolve to an `EntityType`. Boat types are entity types in modern versions, but
confirm against `paper-api:26.3` — if `getVehicle().getType()` reports a shared type, use that instead.

### 1.8 Item sets and counts

```yaml
- id: obtain_4_unique_boats
  type: item_set
  count: 4
  items: [OAK_BOAT, SPRUCE_BOAT, BIRCH_BOAT, JUNGLE_BOAT, ACACIA_BOAT, DARK_OAK_BOAT, MANGROVE_BOAT, CHERRY_BOAT, PALE_OAK_BOAT, BAMBOO_RAFT]
  icon: OAK_BOAT
  display: "Obtain 4 Unique Boats"
  requirement: "Hold 4 different boat types"
  weight: 4

- id: obtain_8_unique_wool
  type: item_set
  count: 8
  items: [WHITE_WOOL, ORANGE_WOOL, MAGENTA_WOOL, LIGHT_BLUE_WOOL, YELLOW_WOOL, LIME_WOOL, PINK_WOOL, GRAY_WOOL, LIGHT_GRAY_WOOL, CYAN_WOOL, PURPLE_WOOL, BLUE_WOOL, BROWN_WOOL, GREEN_WOOL, RED_WOOL, BLACK_WOOL]
  icon: WHITE_WOOL
  display: "Obtain 8 Unique Wool"
  requirement: "Hold 8 different wool colours"
  weight: 4
```

Count tiers on existing families (all read `count`/`amount`/`level`):

```yaml
- { id: fill_inventory_18_unique,   type: unique_inventory, count: 18, display: "Fill Half Inventory with unique items", weight: 5 }
- { id: fill_inventory_27_unique,   type: unique_inventory, count: 27, display: "Fill 27 Inventory Slots with unique items", weight: 4 }
- { id: reach_xp_level_25,          type: xp_level, level: 25, display: "Reach XP level 25", weight: 4 }
- { id: reach_xp_level_40,          type: xp_level, level: 40, display: "Reach XP level 40", weight: 2 }
- { id: take_400_damage,            type: damage_total, mode: TAKEN, amount: 400,  display: "Take 400 damage",  weight: 3 }
- { id: deal_1000_damage,           type: damage_total, mode: DEALT, amount: 1000, display: "Deal 1000 damage", weight: 2 }
- { id: kill_50_mobs,               type: mob_kill_total, count: 50,  display: "Kill 50 mobs",  weight: 4 }
- { id: kill_150_mobs,              type: mob_kill_total, count: 150, display: "Kill 150 mobs", weight: 2 }
- { id: get_5_advancements,         type: advancement_count, count: 5, display: "Get 5 Advancements", weight: 5 }
- { id: get_2_status_effects,       type: status_effect_count, count: 2, display: "Get 2 Status Effects at once", weight: 4 }
- { id: opponent_dies_5_times,      type: opponent, trigger: DEATH, count: 5, team-size: "2", display: "Opponent dies 5 times", weight: 2 }
- { id: opponent_takes_250_damage,  type: opponent, trigger: DAMAGE_TAKEN, amount: 250, team-size: "2", display: "Opponent Team takes 250 Damage", weight: 2 }
```

### 1.9 Durability tiers — `durability`

`DURABILITY` type cap is **1 per board**; more templates widen the pool without changing the cap.

```yaml
- { id: worn_axe,     type: durability, item: DIAMOND_AXE,   remaining-percent: 25, weight: 4 }
- { id: worn_iron_sword, type: durability, item: IRON_SWORD, remaining-percent: 50, weight: 4 }
- { id: worn_elytra,  type: durability, item: ELYTRA,        remaining-percent: 50, weight: 3 }
- { id: nearly_broken_shield, type: durability, item: SHIELD, remaining-percent: 10, weight: 3 }
```

### 1.10 Armor timers — `armor_timer`

`ArmorTimerGoalTracker.isWearing` checks `getArmorContents()`, so any helmet item works.

```yaml
- { id: wear_turtle_helmet_3m, type: armor_timer, item: TURTLE_HELMET, seconds: 180, display: "Wear a Turtle Helmet for 3 minutes", requirement: "Wear a turtle helmet for 3 minutes total", weight: 3, requires: { biomes: [beach] } }
- { id: wear_pumpkin_10m,      type: armor_timer, item: CARVED_PUMPKIN, seconds: 600, display: "Wear a Carved Pumpkin for 10 minutes", weight: 2 }
- { id: wear_skull_2m,         type: armor_timer, item: WITHER_SKELETON_SKULL, seconds: 120, display: "Wear a Wither Skeleton Skull for 2 minutes", weight: 3 }
```

### 1.11 Death causes — `death_by`

**Verify each `damage-type` key against the server's damage-type registry before shipping** — the
tracker compares the raw `minecraft:<key>` string, so a typo silently never matches.

```yaml
- { id: die_to_magma_block,    type: death_by, damage-type: "minecraft:hot_floor",      display: "Die to Magma Block",     requirement: "Die to a magma block",       weight: 4 }
- { id: die_to_lightning,      type: death_by, damage-type: "minecraft:lightning_bolt", display: "Die to Lightning",       requirement: "Die to a lightning bolt",    weight: 3 }
- { id: die_to_wither_effect,  type: death_by, damage-type: "minecraft:wither",        display: "Die to Wither Effect",   requirement: "Die to wither damage",       weight: 3 }
- { id: die_to_dragon_breath,  type: death_by, damage-type: "minecraft:dragon_breath", display: "Die to Dragon's Breath", requirement: "Die to dragon's breath",     weight: 2 }
```

---

## 2. Tier B — needs Java

### 2.1 Generalise `stat_counter` (highest value / smallest diff)

`StatisticGoalTracker` is generic, but only **one** statistic is ever populated:
`TrackingService.updateSprintProgress` maintains a `Map<UUID, Integer> sprintBaselines` and writes
`STAT:SPRINT_ONE_CM` only. Vanilla movement stats do **not** fire `PlayerStatisticIncrementEvent`
(that was the 0.5.1 bug), so any other statistic goal currently sits at 0 forever.

Change: replace `sprintBaselines` with `Map<UUID, Map<String, Integer>>` built from the unclaimed
`stat_counter` goals on the board, and snapshot each referenced `Statistic` at round start
(`resetStats` already snapshots sprint; extend it). Then these become one-line YAML each:

`Walk 1km` (`WALK_ONE_CM`), `Swim 200m` (`SWIM_ONE_CM`), `Elytra 1km` (`AVIATE_ONE_CM`),
`Boat 1km` (`BOAT_ONE_CM`), `Trade 10 Times` (`TRADED_WITH_VILLAGER`), `Catch 10 Fish`
(`FISH_CAUGHT`), `Jump 100 Times` (`JUMP`), `Mine 100 Blocks` (`MINE_BLOCK` — note the statistic name
collides with the `MINE_BLOCK` goal type string but not with the type enum).

This is the change that unlocks a whole family rather than a few entries.

### 2.2 Other Java work (each independent)

- **More workstations**: `BARREL`, `LECTERN`, `FLETCHING_TABLE`, `CARTOGRAPHY_TABLE` need new
  `WorkstationGoalTracker.Station` values, `workstationFor(InventoryType)` cases and event wiring.
- **Splash potions**: `BrewPotionGoalTracker` only supports `mode: LINGERING`; add a `SPLASH` mode.
- **Counted `tame` / `block_action`**: both trackers hard-code `target() == 1`.
- **New `block_action` actions**: enum value + one handler each.
- **"Have the most" goals**: still deferred (needs reversible claims — see `goal-port-plan.md` §6).

---

## 3. Mandatory companion edits (easy to forget)

1. **`GoalGroups` (code, not YAML).** Caps are keyed by **explicit id lists**; there is no type cap
   for `STATUS_EFFECT`, `CONSUME_ITEM`, `DEATH_BY` or `MOB_KILL_GROUP`. Every new id must be added to
   the right group or it bypasses anti-clustering entirely (a board could draw 10 effects). Groups to
   extend:
   - `EFFECT` (limit 4) — all new `status_effect` ids.
   - `EFFECT_X` (limit 1) — new `status_effect_count` ids.
   - `EAT_FOOD` (limit 3) — new `consume_item` ids.
   - `DEATH_DAMAGE` (limit 3) and `DAMAGE` (limit 1) — new `death_by` / `damage_total` ids.
   - `KILL_HOSTILES_OF_TYPE` (limit 1) — new `mob_kill_group` ids.
   - `BIOME` (limit 1) — new `biome` ids.
   - `BREW_POTION` (limit 1) — new `brew_potion` ids.
   - `RIDE` (limit 2) — new `ride` ids.
   - `XP_LEVELS` (limit 1) — new `xp_level` ids.
   - `OPPONENT_GOALS` / `OPPONENT_DEATH_DAMAGE` — new `opponent` ids.
   - `OBTAIN_ALL` (limit 2) / `TAME` type cap (2) — new item-set and tame ids.
   - Consider adding **type caps** for `STATUS_EFFECT` (e.g. 3) and `CONSUME_ITEM` (e.g. 3) — this is
     the systemic fix, but it changes board generation for existing templates, so it needs the
     human's sign-off.
2. **Bump `goals-version` 9 → 10** in `src/main/resources/goals.yml` **and** `GoalCatalog.GOALS_VERSION`.
   Existing servers then back up to `goals.yml.v9.bak` and get the new catalogue instead of silently
   keeping a stale file.
3. **Minimum-25 safety**: already far exceeded (267), but `GoalCatalog.draw` throws if
   `templates.size() < amount`; adding entries only widens the margin.
4. **`docs/manual-tests.md`** — one entry per mechanic touched, plus a `## Test handoff` in chat
   (`AGENTS.md` §2).

---

## 4. Decision log

| # | Decision | State |
|---|---|---|
| 1 | Which batch lands first? | **Open** — options were effects/potions/foods; world & travel; all of Tier A; `stat_counter` generalisation. |
| 2 | Add a `StatusEffect`/`ConsumeItem` **type cap** in `GoalGroups`, or keep extending id lists? | **Open** (changes existing board generation). |
| 3 | Nether biome goals — keep them gated on an overworld-only scan, or drop them? | **Open**. |
| 4 | Verify `damage-type` keys (`hot_floor`, `lightning_bolt`, `wither`, `dragon_breath`) against `paper-api:26.3` | **Open** (must verify before shipping). |
| 5 | Verify boat entity types resolve in `ride` (e.g. `OAK_BOAT`) | **Open**. |

---

## 5. Status

Nothing implemented. No build produced. Nothing awaiting human test.
