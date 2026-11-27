# Goal port plan — Fabric Lockout goals → TLockout

Status: Slice 13 passed (`1.0.0`, T-061–T-063). Fabric goal port complete!
Source catalogue: `lockout-fabric/lockout-fabric-main` (marin774's Lockout, 1.21.8, 230 registered goals).
This file is the working plan. `docs/manual-tests.md` is the test log; `AGENTS.md` is the operating
contract (the human tests every mechanic, the agent only compiles).

---

## 1. Target and non-goals

**Target:** the Paper plugin can generate boards from the Fabric mod's goal vocabulary, with each
claimed goal locking out for the other side exactly as today. Server-side only; vanilla clients.

**Non-goals:** client overlay board, custom textures, Board Builder, board size 3–7, team chat,
countdown, FFA/Blackout, 1.16.1 parity mixins (barter rates, raid waves, zombie conversion). Those
are separate features or separate slots in `ideas.txt`.

Of 230 Fabric goals, roughly 220 are portable. Two "have the most" goals need reversible claims and
are deferred.

---

## 2. Foundation (Slice 7)

The current `GoalTemplate` record + `GoalType` switch was sized for seven families. The port
replaces it with:

| Piece | Responsibility |
|---|---|
| `GoalSpec` | Parsed YAML template: id, type, display/requirement/icon patterns, weight, group, params, variant lists. |
| `GoalTracker` | One per detection family: validation, default display/icon, requirement text, scope, target, progress, stat key. |
| `GoalTrackers` | `GoalType → GoalTracker` registry. |
| `ResolvedGoal` | One per board cell: `GoalSpec` + resolved variant values. Board stores these. |
| `TrackingState` | Per-player counters/sets, cleared on round start. Per-side state arrives with team trackers. |
| `GoalScope` | `PLAYER` (one player satisfies it) or `TEAM` (side-wide counter). |

**Claim flow:** `TrackingService.checkGoals()` computes the side's combined progress with
`teamProgress`; every goal claims when that total reaches the target. The claimant is the member with
the largest personal contribution. `GoalScope` remains on the tracker for a future team-specific
aggregation, but does not gate claiming today.

**Existing behaviour is frozen in Slice 7:** the same 51 templates, the same generated display and
requirement strings, the same weighted draw and seed determinism, and the same HUD/board output.
No new goal types ship in this slice.

### YAML shape (goals-version 2)

```yaml
goals-version: 2
templates:
  # Existing shape keeps working unchanged.
  - { id: iron_ingot, type: item, item: IRON_INGOT, count: 8, weight: 10 }

  # Future family example: event tracking.
  - id: eat_suspicious_stew
    type: consume_item
    item: SUSPICIOUS_STEW
    display: "Eat Suspicious Stew"
    icon: SUSPICIOUS_STEW
    weight: 5

  # Future variant example: one template, one colour chosen at draw time.
  - id: obtain_64_colored_wool
    type: item
    item: "{color}_WOOL"
    count: 64
    display: "Obtain 64 {color} Wool"
    variants:
      color: [white, orange, magenta, light_blue, yellow, lime, pink,
              gray, light_gray, cyan, purple, blue, brown, green, red, black]
    weight: 4
```

Display/requirement placeholders are prettified (`light_blue` → `Light Blue`); item/icon
placeholders are uppercased into material names (`{color}_WOOL` → `RED_WOOL`). Variant values are
picked with the board's `Random(seed)`, so a seed still reproduces a board.

### Migration

`goals.yml` gains a top-level `goals-version`. On startup:

1. No file → write the bundled default.
2. File exists with `goals-version >= 3` → use it.
3. File exists below version 3 (including no version) → copy it to
   `goals.yml.v<old version>.bak`, write the bundled default, log the upgrade. A malformed YAML is
   left alone so the loader can report it.
4. `GoalCatalog` refuses to load a file below version 3 and names the file path.

Version 2 introduced the tracker model and variant placeholders; version 3 adds the first Fabric
catalogue batch. `config.yml` is unchanged, so `config-version` stays 1.

---

## 3. Detection families

Candidate Paper API hooks are listed; each must be verified against `paper-api:26.3` sources before
that family is implemented. Where no event exists, the family polls on `check-interval-ticks`
(default 10) or is deferred.

| Family | Fabric goals | Detection approach | Scope | Slice |
|---|---|---|---|---|
| `item`, `item_count` | ~35 | Existing inventory poll | player | 8 |
| `mob_kill` | 12 | `EntityDeathEvent` | player | 8 |
| `tame`, `breed` | 14 | Existing events | player | 8 |
| `biome`, `dimension` | 5 | Poll biome; poll world environment | player | 8 |
| `player_kill` | 1 | Death + different side | player | 8 |
| `item_set` | ~15 | Inventory scan for required set | player | 10 |
| `kill_counter`, `kill_unique`, `kill_tag` | 9 | Death event + mob tags, per-side sets/counters | team | 11 |
| `consume`, `unique_foods` | 15 | `PlayerItemConsumeEvent` | player/team | 9 |
| `advancement`, `advancement_count` | 19 | `PlayerAdvancementDoneEvent`; exclude recipe advancements | player/team | 9 |
| `status_effect`, `status_count` | 13 | `EntityPotionEffectEvent`; poll active effects | player | 9 |
| `death_by_damage`, `death_by_entity` | 13 | `PlayerDeathEvent` + damage source | player | 10 |
| `damage_total` | 2 | Damage events, final damage | team/player | 10 |
| `xp_level` | 2 | Poll level or level-change event | player | 10 |
| `position` | 3 | Poll Y and block below | player | 10 |
| `hunger` | 1 | Poll food level | player | 10 |
| `ride` | 3 | Poll vehicle; pig needs carrot on a stick | player | 10 |
| `stat_counter` | 1 | Statistic-increment event (`SPRINT_ONE_CM`) | player | 10 |
| `armor_wear` | 10 | Poll armor slots; pumpkin timer | player | 10 |
| `workstation` | 8 | Result-slot clicks / station events | player | 12 |
| `brew_potion` | 7 | Brew event / potion removal | player | 12 |
| `block_action` | ~15 | Specific interaction events | player | 12 |
| `mine_block` | 5 | Block-break event | player | 12 |
| `opponent_event` | 12 | Same events, beneficiary is the other side; exactly 2 teams | team | 11 |
| `unique_inventory` | 1 | Inventory scan | player | 10 |
| `have_more` | 2 | Deferred (reversible claims) | — | — |

---

## 4. Slices

Each slice ends with: compile (`jar`), a `docs/manual-tests.md` entry, and a chat handoff. The agent
never runs the server or tests mechanics.

### Slice 7 — goal-model foundation (0.3.1)

- `GoalSpec`, `GoalTracker`, `GoalTrackers`, `ResolvedGoal`, `TrackingState`, `GoalScope`.
- Seven existing trackers (`item`, `durability`, `mob_kill`, `player_kill`, `biome`, `tame`,
  `breed`) replacing the `GoalType` switch, validation and text generation.
- `Board` stores `ResolvedGoal`; GUI, HUD, claims, and tracking read through it.
- `goals-version: 2` and the backup-and-replace migration.
- No new goal types.
- Exit: the 51 templates load and behave exactly as 0.3.0.
- Tests: T-025, T-026, T-027.

### Slice 8 — free wins (0.4.0) — passed (T-030 … T-034)

Note: T-028 and T-029 were used for the empty-team flag and team-shared claims, so the
remaining slices use the shifted IDs below.

- 33 single-item obtain goals (Fabric's "Obtain X" set, excluding the custom-only Wither Skull and
  the placement/NBT goals).
- Obtain 64 Arrows.
- Three colour-variant templates: 64 coloured wool, 64 coloured concrete, and coloured glazed
  terracotta. The leather-piece variant waits for the armor tracker in Slice 10.
- Ten plain specific-mob kills. Conditional kills (Breeze with Wind Charge, Snow Golem in the
  Nether) are deferred until their condition trackers exist.
- Two dimension goals and a new `DIMENSION` type / `DimensionGoalTracker`.
- Catalogue is 100 templates; `goals-version: 3`.
- Exit: a board can contain the new goals; variants render and claim; dimensions and mob kills claim.
- Tests: T-030 (catalogue upgrade + load), T-031 (single-item obtain), T-032 (colour variant),
  T-033 (dimension), T-034 (specific mob kill).

### Slice 9 — event families (0.4.1) — passed (T-035 … T-037)

- Consume/drink: five unique-food counts and ten specific foods/drinks, including cake slices
  (statistic) and water bottles (potion type).
- Advancements: three counts (10/20/30) and 13 specific/any-of goals. Recipe unlocks and
  display-less advancements are excluded. The three custom-only Fabric advancement goals remain
  deferred.
- Status effects: eight specific effects, three "at once" counts, and milk removal. Levitation is
  deferred as a custom-only goal.
- Team state: unique foods and unique advancements are side-wide sets; "at once" uses the best
  single player rather than a sum.
- Catalogue is 143 templates; `goals-version: 4`.
- Exit: consume, advancement and status-effect goals claim, reset on a new round, and do not
  double-count across teammates.
- Tests: T-035 (consume + unique foods), T-036 (advancements), T-037 (status effects + milk).

### Slice 10a — combat, movement, state (0.5.0/0.5.1) — passed (T-038–T-040, T-041b)

- 13 death causes, Take 200/Deal 400 damage, XP 15/30, height limit/bedrock/Nether roof, empty
  hunger, ride horse/minecart/pig, Sprint 1km.
- New trackers: `death_by`, `damage_total`, `xp_level`, `position`, `empty_hunger`, `ride`,
  `stat_counter`. Death matching uses the `PlayerDeathEvent` damage source; damage totals use
  double counters; sprint polls statistic deltas against a round-start baseline.
- Catalogue is 168 templates; `goals-version: 5`.
- Exit: each family claims from the right trigger and resets on a new round.
- Tests: T-038 (deaths), T-039 (damage totals), T-040 (XP/position/hunger/ride), T-041b (sprint
  retest after the 0.5.1 polling fix).

### Slice 10b — armor and item sets + toggleable auto-reset (0.6.0) — passed (T-042 … T-046)

- Clean-match player reset on `/lockout start` (inventory, armor/offhand, cursor, effects, XP,
  hunger, health, vehicle, advancements, untyped stats), default `true` via `match.auto-reset`
  (`config-version: 2`), toggleable live via `/lockout autoreset [on|off|toggle]`.
- Armor/wearables (10): full Leather/Gold/Iron/Diamond sets, chain piece, coloured leather piece
  (with tinted GUI icon), different-coloured full leather, full enchanted armor, carved pumpkin for
  5 minutes (cumulative seconds), wolf armor (`EntityEquipmentChangedEvent` + 1-tick interact check).
- Item sets (16): all 5 tool tiers, every type of horse armor/minecart/mushroom/pumpkin/raw ore
  block, 4 unique saplings, 4 unique seeds, 6 unique buckets, 6 unique flowers, 7 workstations,
  fill-inventory-with-unique-items (36 storage slots, Board Compass recognised separately).
- Worn armor, pumpkin timer and "fill your inventory" use best-single-player semantics; obtained
  item sets are team-wide online-inventory unions.
- Catalogue is 194 templates; `goals-version: 6`.
- Exit: auto-reset toggles cleanly; armor state, pumpkin timer, wolf armor and set membership claim
  correctly without leaking between rounds.
- Tests: T-042 (auto-reset + upgrade), T-043 (armor sets + chain), T-044 (colored/enchanted/
  pumpkin/wolf), T-045 (item sets team union), T-046 (fill-inventory + Board Compass).

### Slice 11 — team counters and opponent goals (0.6.4) — passed (T-052 … T-054)

- 9 team kill/tag/sheep goals: `Kill 100 mobs`, `Kill 7/10/13/15 Unique Hostile Mobs`, `Kill 20
  Arthropods`, `Kill 30 Undead Mobs`, `Kill all Raid Mobs` (6 raid mobs incl. Vex), `Kill <Color>
  Sheep` (10 dyeable non-natural colours).
- 6 Fabric random-pool opponent goals: `Opponent dies`, `Opponent dies 3 times`, `Opponent catches
  on Fire`, `Opponent takes Fall Damage`, `Opponent Team takes 100 Damage`, `Opponent touches
  Water`. Fabric's 6 custom-only opponent goals remain deferred to Slice 13's pool filters.
- Beneficiary-is-other-side claim path (`ChallengeManager.tryClaimOpponent`, `SideManager.otherOf`);
  empty beneficiary sides cannot claim.
- Catalogue is 209 templates; `goals-version: 7`.
- Exit: team counters/sets claim once without double-counting duplicates; opponent goals credit the
  beneficiary side and display opponent progress on the HUD.
- Tests: T-052 (team kill totals/groups/unique sets + v7 upgrade), T-053 (coloured sheep), T-054
  (opponent goals + beneficiary claim + empty-side guard).

### Slice 12 — workstations, brewing, block actions, mining & conditional kills (0.7.0) — passed (T-057 … T-059)

- 8 workstation goals (`workstation`): anvil, cauldron wash, composter harvest, enchanting table,
  grindstone, loom, smithing table, stonecutter.
- 7 brewing goals (`brew_potion`): healing, invisibility, poison, swiftness, water breathing,
  weakness (matching normal/long/strong variants with tinted potion icons in `BoardGui`), and any
  lingering potion.
- 5 mine-block goals (`mine_block`): diamond ore (+ deepslate), emerald ore (+ deepslate), mob
  spawner, turtle egg, crafter.
- 15 misc interactions (`block_action` + `banner_shield`): jukebox, glow ink on crimson sign,
  flower pot, full armor stand, item frame in item frame, full campfire, full chiseled bookshelf,
  place end crystal, light candle, shoot firework from crossbow, toot goat horn, brush suspicious
  sand/gravel, enrage zombified piglin, shield disabled, decorate shield with banner.
- 2 conditional kills (`conditional_kill`): Kill Breeze using Wind Charge, Kill Snow Golem in The
  Nether.
- Catalogue is 246 templates; `goals-version: 8`.
- Exit: each supported interaction, workstation, brew, mine and conditional kill claims accurately
  without false triggers.
- Tests: T-057 (v8 upgrade + workstations + brewing), T-058 (block actions + banner shield), T-059
  (mine blocks + conditional kills).

### Slice 13 — world gating, groups, catalogue fill (1.0.0) — passed (T-061 … T-063)

- `WorldFeasibilityScanner`: scans required structures (11 unique) and biomes (39 unique) within
  `world-scan.radius` (default 750 blocks, `config-version: 3`, toggleable via
  `world-scan.enabled`) of overworld spawn, caches per world, and invalidates on `/lockout reload`.
- `GoalGroups`: ported Fabric's group caps and predecessor rules (`Kill 13/15 Unique Hostile Mobs`,
  `Breed 8 Unique Animals`, `Eat 20/25 Unique Food`, `Get 30 Advancements`) plus type-level caps
  (max 6 plain items, max 1 durability, max 3 workstations, max 2 mine blocks, max 2 tame, max 1
  position) and post-draw board shuffling.
- `GoalSpec` metadata: `pool: random|custom-only`, `team-size: any|2|2+`, and
  `requires: { biomes, structures }` enforced in `GoalCatalog.draw`.
- Final `goals.yml` (`goals-version: 9`, 267 templates: 256 random pool, 11 `custom-only`): adds
  `breed_unique` (`Breed 4/6/8 Unique Animals`), 5 missing breed goals (`Fox`, `Frogs`, `Goat`,
  `Hoglin`, `Strider`), 2 missing biome goals (`Ice Spikes`, `Mushroom Fields`, plus multi-variant
  `BiomeGoalTracker`), 5 custom-only obtain/effect/advancement goals, 6 custom-only opponent goals
  (`OBTAIN_ITEM`, `EAT_FOOD`, `HIT_BY_EGG`, `HIT_BY_SNOWBALL`), world-gating annotations on 42
  templates, and balanced weights.
- Exit: no unreachable goals; no family floods the board; custom-only goals excluded from random
  boards; full 1.0.0 catalogue loads and starts.
- Tests: T-061 (v3/v9 upgrade + world scan + caching), T-062 (group caps + predecessors + pool
  exclusion), T-063 (new breed_unique, breed/biome & custom-only opponent goals).

---

## 5. World gating and anti-clustering

- `world-scan.enabled`, `world-scan.radius: 750`, `world-scan.dimensions` (Slice 13).
- Structures use the world locate API; biomes use the biome locate API if Paper exposes it,
  otherwise structure-only gating with a config note.
- Results are cached per world and invalidated by `/lockout reload`.
- Group caps are code constants (like Fabric), keyed by template id; variants inherit their parent
  template's group.
- Same seed + same world facts = same board. A different world may drop goals, so determinism is
  per-world, not cross-world.

---

## 6. Deferred and unsupported

- `HAVE_MORE_XP_LEVELS`, `HAVE_MORE_UNIQUE_CRAFTS` — need reversible claims; Paper's lockout claims
  are permanent. Deferred until the human chooses a semantic.
- `FILL_BUNDLE_WITH_BUNDLES` — nested-bundle restrictions, custom-only in Fabric.
- Shield disable, brush completion, composter output, loom/grindstone results, goat-horn toot, wolf
  armor — implement only after verifying a reliable Paper event; otherwise poll or drop.
- 1.16.1 parity mechanics — separate effort.
- Board size 3–7, client overlay, board builder, team chat, countdown, FFA/Blackout — separate
  features.

---

## 7. Decisions

1. Scope: all slices, or stop after Slice 10 and reassess. **Open.**
2. All goals claim on the side's combined progress; the claim is credited to the largest personal
   contributor. Team-specific aggregation is reserved for later trackers. **Accepted.**
3. "Have most" goals are deferred rather than redefined. **Accepted for the plan.**
4. `goals.yml` upgrade backs up and replaces rather than merging. **Accepted for Slice 7.**
5. Weights and icons ship with defensible defaults; the human tunes feel in-game. **Accepted.**
