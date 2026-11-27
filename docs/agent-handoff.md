# Agent handoff — TLockout Fabric goal port

Updated: 2026-11-24. Written for the next coding agent continuing this repository.
Read `AGENTS.md` first; it overrides anything here. Then read `docs/goal-port-plan.md` and the tail of
`docs/manual-tests.md`.

---

## 0. The one rule that matters

**The human owner tests every gameplay mechanic in-game. You do not test mechanics.**

- Never start a Minecraft server/client, never touch `testServer/`, never run `runServer`.
- Never write automated gameplay tests (MockBukkit, GameTest, bot harnesses, boot tests).
- You may compile, inspect code, inspect the Paper API with `javap`, and validate YAML statically.
- Do not say a mechanic "works", "should be fine", or "is probably correct". The strongest claim
  allowed is "compiled and statically validated; awaiting human test".
- Every gameplay change gets a Test Handoff in the chat **and** a new entry in
  `docs/manual-tests.md` with the next stable ID. Never renumber existing entries. On a failed
  retest, add `T-0xxb` referencing the original rather than reusing the ID.

---

## 1. Current state

| Item | Value |
|---|---|
| Plugin | TLockout, a Paper plugin for a vanilla-client Lockout challenge |
| Build | `0.6.6` |
| Java | 25 |
| Paper API | `io.papermc.paper:paper-api:26.3.build.+` |
| Goal catalogue | `src/main/resources/goals.yml`, `goals-version: 7`, 209 templates |
| Config | `src/main/resources/config.yml`, `config-version: 2` (`match.auto-reset: true`) |
| Claim model | First side to `win.goals-to-win` (default 13); claims lock the cell for everyone |
| Persistence | None; claims, counters and team membership reset on restart |
| Git | No repo; do not `git init` or commit unless asked |

### Tests

All committed tests through **T-056** are reported passed by the human, except the historical
T-041 failure that T-041b superseded. No test is currently awaiting the human.

- T-001 … T-024: earlier slices (goal generation, teams/win, compass, live board, HUD).
- T-025 … T-027: goal-model foundation.
- T-028: `--allow-empty` testing flag.
- T-029: team-combined claim progress.
- T-030 … T-034: first Fabric catalogue batch.
- T-035 … T-037: consume, advancements, status effects.
- T-038 … T-040: deaths, damage totals, XP/position/hunger/riding.
- T-041: **fail** — sprint didn't count.
- T-041b: **pass** — sprint now polls `SPRINT_ONE_CM` against a round-start baseline.
- T-042 … T-046: **pass** — toggleable auto-reset + Slice 10b armor/item sets (`0.6.0`).
- T-047, T-048: **pass** — `/lockout compass` and `/lockout reroll [seed]` (`0.6.1`).
- T-049: **pass** — Board Compass `/lo compass` tooltip hint (`0.6.2`).
- T-050, T-051: **pass** — mid-game team joining & admin `/lo team set` (`0.6.3`).
- T-052 … T-054: **pass** — Slice 11 team kill counters & opponent goals (`0.6.4`).
- T-055: **pass** — Board Compass live team rosters (`0.6.5`).
- T-056: **pass** — Board GUI concrete blocks live team rosters (`0.6.6`).

### Known documentation mismatch

`docs/manual-tests.md` still marks **T-018 and T-019** as `awaiting human test`, while `AGENTS.md`
records those slices as passed. Do not silently change them based on a later "all tests pass"
message; ask the human whether to reconcile, or fix them only when they explicitly confirm those two.

---

## 2. Repository map

```
src/main/java/dev/tlockout/
  TLockout.java            plugin bootstrap, config/yml migration, start/stop, refreshViews
  ChallengeManager.java    round state machine, claims, targets, winner
  TrackingService.java     all Bukkit events, poll loop, sprint baseline, team progress entry
  GoalCatalog.java         YAML load/validation, weighted draw, variants, GOALS_VERSION
  GoalType.java            supported goal families
  Board.java               ResolvedGoal[25] + Claim[25]
  BoardGui.java            54-slot chest GUI, claim/target clicks
  HudService.java          sidebar + boss bar; reads TrackingService.teamProgress
  Side.java / SideManager  teams, membership, shared targets, concrete colours
  LockoutCommand.java      /lockout subcommands + --allow-empty
  BoardCompass.java        tagged compass
  TeamCombatListener.java  friendly-fire cancellation
  Names.java, ConcreteColors.java

  goal/
    GoalSpec.java          parsed YAML template: params, variants, display/icon patterns
    ResolvedGoal.java      board cell: GoalSpec + chosen variant data
    GoalTracker.java       tracker interface, default team-progress summation
    GoalTrackers.java      GoalType -> tracker registry
    TrackingState.java     int counters, double counters, player sets, side sets
    GoalSupport.java       material/entity/biome parsing, spawn eggs, icons
    GoalScope.java         PLAYER/TEAM metadata (claiming is team-shared regardless)
    tracker/*.java         one tracker per family

src/main/resources/
  goals.yml                catalogue (v5, 168 templates)
  config.yml               config-version 1
  plugin.yml               command + permissions

docs/
  goal-port-plan.md        slice plan, design notes, remaining slices
  manual-tests.md          the human's test log and the authoritative test history
  agent-handoff.md         this file

ideas.txt                  product scope / future ideas
lockout-fabric/            third-party Fabric mod used as a goal/feature reference (see §10)
```

---

## 3. Architecture summary

### Goal data flow

1. `GoalCatalog.load` reads `goals.yml`, validates every entry through its tracker, and fills in
   generated display/icon when omitted.
2. `GoalSpec` stores a params map (all type-specific fields), optional variants, display/icon
   patterns and requirement override.
3. `GoalCatalog.draw(25, random)` performs a weighted draw and resolves each template's variant
   values (e.g. wool colour) using the board seed. `Board` stores 25 `ResolvedGoal`s.
4. `BoardGui`/`HudService` read `ResolvedGoal.display()`, `.requirement()`, `.icon()` and
   `.target()`.
5. Per check cycle, `TrackingService.checkGoals()` computes the side total and claims when it
   reaches the target. The claimant is the online member with the highest personal progress.

### Tracker contract

`GoalTracker` provides:

- `validate(GoalSpec)` — config validation, throws a field-level message.
- `defaultDisplay` / `defaultIcon` / `requirement` — generated text and icon.
- `target(ResolvedGoal)` — progress required.
- `statKey(ResolvedGoal)` — event counter key, or null for state-driven goals.
- `progress(Player, ResolvedGoal, TrackingState)` — personal progress.
- `teamProgress(Side, ResolvedGoal, TrackingState)` — default sums online progress plus offline
  event counters, capped at target. Override for team sets or "best single player" semantics.

Current special aggregations:

- `UniqueFoodsGoalTracker` / `AdvancementCountGoalTracker`: side-wide sets.
- `AdvancementGoalTracker`: side-wide set membership for any-of keys.
- `StatusEffectCountGoalTracker`: maximum simultaneous effects on one player, not a sum.
- `DamageTotalGoalTracker`: sum of double counters across members.
- `StatisticGoalTracker`: sum of per-player statistic deltas, divided by a configured divisor.

`TrackingState` owns int counters, double counters, per-player sets and per-side sets. It is cleared
on every round start. Offline members contribute event counters/sets, but **not** inventory or live
state.

### Events currently wired in `TrackingService`

- `EntityDeathEvent` — mob/player kill counters.
- `EntityTameEvent`, `EntityBreedEvent`.
- `PlayerItemConsumeEvent` — consume counters, unique-food set, potion-type keys.
- `PlayerStatisticIncrementEvent` — cake slices only. **Do not use it for sprint/movement stats**;
  that event did not fire for sprint (T-041 failure). Sprint is polled from the statistic against
  a baseline.
- `PlayerAdvancementDoneEvent` — side-wide advancement set; recipes (`recipes/`) and null-display
  advancements are excluded.
- `EntityPotionEffectEvent` — specific effect counters; milk removal via `Cause.MILK`.
- `PlayerDeathEvent` — loops board cells, asks `DeathGoalTracker.matches`, increments a per-goal
  death counter.
- `EntityDamageEvent` / `EntityDamageByEntityEvent` — double counters for damage taken/dealt.
- Poll loop: item counts, durability, biome, dimension, XP level, position, hunger, vehicle,
  status-effect count, sprint statistic.

### Claims and progress

- `ChallengeManager.tryClaimFor(side, player, cell)` is the single claim path.
- `TrackingService.checkGoals()` uses `teamProgress` for every goal, not per-player progress.
- A claim removes the cell from every side's target list and refreshes views in place.
- Claim attribution is cosmetic but matters for chat: pick the highest personal contributor.

### Config / catalogue migration

- `config.yml` has `config-version: 1`; `TLockout.migrateConfig` backs up to `config.yml.old` and
  writes the bundled default when the version is older.
- `goals.yml` has `goals-version`; `GoalCatalog.GOALS_VERSION` is the required value. On startup,
  `TLockout.prepareGoalsFile` backs up an older file to `goals.yml.v<old version>.bak` and writes the
  bundled catalogue.
- **Bump `goals-version` whenever the shipped catalogue changes**, even when the YAML shape does not;
  otherwise existing installs keep their old file and will not see new goals. Update
  `GOALS_VERSION` and the header in `goals.yml`.
- Bump `config-version` only if `config.yml`'s shape changes.

### Variants

One YAML template can generate different cells via `variants`:

```yaml
- id: obtain_64_colored_wool
  type: item
  item: "{color}_WOOL"
  count: 64
  display: "Obtain 64 {color} Wool"
  weight: 3
  variants:
    color: [white, orange, magenta, light_blue, yellow, lime, pink, gray,
            light_gray, cyan, purple, blue, brown, green, red, black]
```

Display/requirement placeholders are prettified; material placeholders are uppercased. Variant
selection uses the board's `Random(seed)`, preserving determinism.

---

## 4. Supported goal types and YAML

Current `GoalType` values (22): `ITEM`, `DURABILITY`, `MOB_KILL`, `PLAYER_KILL`, `BIOME`, `TAME`,
`BREED`, `DIMENSION`, `CONSUME_ITEM`, `UNIQUE_FOODS`, `ADVANCEMENT`, `ADVANCEMENT_COUNT`,
`STATUS_EFFECT`, `STATUS_EFFECT_COUNT`, `MILK_EFFECT`, `DEATH_BY`, `DAMAGE_TOTAL`, `XP_LEVEL`,
`POSITION`, `EMPTY_HUNGER`, `RIDE`, `STAT_COUNTER`.

Key params:

| Type | Params |
|---|---|
| `item` | `item`, `count` |
| `durability` | `item`, `remaining-percent` |
| `mob_kill`, `tame`, `breed` | `entity`, `count` (count optional for tame) |
| `player_kill` | `count` |
| `biome` | `biome` |
| `dimension` | `dimension: NETHER\|END\|OVERWORLD` |
| `consume_item` | `item`, optional `potion` (only when item is `POTION`) |
| `unique_foods` | `count` |
| `advancement` | `advancements: [namespaced:key, ...]` (any one) |
| `advancement_count` | `count` |
| `status_effect` | `effect` |
| `status_effect_count` | `count` |
| `milk_effect` | none |
| `death_by` | at least one of `damage-type`, `entity`, `special: VINES` |
| `damage_total` | `mode: TAKEN\|DEALT`, `amount` |
| `xp_level` | `level` |
| `position` | `mode: HEIGHT_LIMIT\|BEDROCK\|NETHER_ROOF` |
| `empty_hunger` | none |
| `ride` | `entity` |
| `stat_counter` | `statistic`, `amount`, optional `divisor` |

Common optional fields: `display`, `requirement`, `icon`, `weight` (default 10), `group`
(reserved for Slice 13).

The shipped catalogue is 168 templates: 55 item, 20 mob_kill, 13 advancement, 13 death_by, 10
consume_item, 9 biome, 8 status_effect, 5 unique_foods, 5 breed, 4 durability, 4 tame, 3
advancement_count, 3 status_effect_count, 3 position, 3 ride, 2 dimension, 2 damage_total, 2
xp_level, 1 player_kill, 1 milk_effect, 1 empty_hunger, 1 stat_counter.

Weight distribution is currently item-heavy (item templates ~43% of total weight). Group caps are
not implemented yet; Slice 13 is where that gets fixed.

---

## 5. Remaining work

### 5.1 Decision needed before 10b: clean-match reset

Rounds currently do **not** clear player state. Consequences:

- Item-set goals may auto-claim from items already held.
- "Reach XP 30", empty hunger, and riding goals may auto-claim if the player starts in that state.
- Specific advancement goals can be impossible if the advancement was earned in an earlier round.
- Vanilla statistics are not reset; sprint is handled with a baseline, but future statistic goals
  would need the same care.

Fabric clears inventories, effects, XP, hunger, stats and advancements at match start. Before 10b,
ask the human which they want:

1. Add a clean-match reset to `/lockout start` (recommended; inventory, armor/offhand, effects,
   XP, hunger, advancements, stats, optionally health). This is a gameplay change and needs its own
   test ID.
2. Leave rounds stateless-free and require `/clear`, fresh worlds, etc. for testing.
3. Add an admin command such as `/lockout resetplayers` that the human runs before starting.

Do not implement a reset without an explicit answer.

### 5.2 Slice 10b — armor and item sets (next planned build, `0.6.0`)

Goals to add (~26):

**Armor/wearables (10)**
- Full Leather / Golden / Iron / Diamond armor.
- Wear a Chain Armor Piece.
- Wear a coloured leather piece (random colour and piece).
- Full leather armor in different colours.
- Full enchanted armor.
- Carved pumpkin for 5 minutes (cumulative across the round).
- Put wolf armor on a wolf.

**Item sets (16)**
- All five tool tiers.
- Every type of horse armor, minecart, mushroom, pumpkin, raw ore block.
- 4 unique saplings, 4 unique seeds, 6 unique buckets, 6 unique flowers, 7 workstations.
- Fill inventory with unique items.

Suggested design:

- `ARMOR_SET` / `ARMOR_PIECE`: scan `PlayerInventory#getArmorContents`; one player must wear the set.
  For leather colour checks use `LeatherArmorMeta#getColor`; for enchanted use `ItemMeta#hasEnchants`.
- `ARMOR_TIMER`: cumulative equipped ticks per player (reset on round start); do not require
  continuous wear.
- `WOLF_ARMOR`: verify the Paper 26.3 API first. If there is no reliable wolf-body-armor accessor or
  equipment event, defer the goal rather than guess.
- `ITEM_SET`: configured list of materials; team-wide union of inventories; use `min-count` if a set
  requires duplicates. **Do not** scan offline players' inventories.
- `UNIQUE_INVENTORY`: best single player's distinct-item count (not a team sum).
- Worn armor and fill-inventory use a best-single-player check; obtained sets are team-wide.
- Check Fabric implementations for exact lists (tools, minecarts, mushrooms, pumpkins, raw ores,
  buckets, flowers, workstations). The Fabric source is under
  `lockout-fabric/lockout-fabric-main/src/main/java/me/marin/lockout/lockout/goals/` and
  `.../interfaces/`.

Tests: **T-042** armor sets, **T-043** colored/enchanted/pumpkin/wolf, **T-044** item sets,
**T-045** fill-inventory/team union. Adjust the split if a goal is deferred.

### 5.3 Slice 11 — team counters and opponent goals (`0.6.1`)

- Kill 100 mobs; 7/10/13/15 unique hostile mobs; 20 arthropods; 30 undead mobs; kill all raid
  mobs; kill a coloured sheep.
- Team-wide damage taken/deaths (some pieces already exist as counters).
- Opponent goals (12): dies, dies 3 times, catches fire, takes fall damage, takes 100 damage,
  touches water, eats food, obtains crafting table/obsidian/seeds, hit by egg/snowball.
- Exactly-2-teams gating for opponent goals. The beneficiary is the **other** side, not the actor.
  Add a claim path that supports that attribution.
- Fabric marks some of these as custom-only; decide with the human whether to include them in the
  random pool now or leave them for Slice 13's pool filters.
- Tests: **T-046**, **T-047**, **T-048**.

### 5.4 Slice 12 — workstations, brewing, block actions (`0.7.0`)

- Workstations: anvil, cauldron, composter, enchanting table, grindstone, loom, smithing table,
  stonecutter.
- Brewing: healing, invisibility, poison, swiftness, water breathing, weakness, lingering potion.
- Misc interactions: jukebox, candle, glow ink on crimson sign, fill campfire, fill chiseled
  bookshelf, armor stand, item frame in item frame, crossbow firework, place end crystal, brush on
  suspicious block, decorate shield with banner, fill composter.
- Conditional kills: Breeze with Wind Charge, Snow Golem in the Nether.
- Custom-only goals: Wither Skeleton Skull, Find Fortress, Find End City, Find Bastion, Levitation,
  Fill Bundle.
- Verify every event/API against the Paper jar before implementing. For stations without a clean
  event, use result-slot clicks or defer.
- Tests: **T-049**, **T-050**, **T-051**.

### 5.5 Slice 13 — world gating, groups, catalogue fill (`1.0.0`)

- Scan required structures/biomes near spawn (default radius 750), cache per world, invalidate on
  reload; do not spawn chunks wildly.
- Port Fabric `GoalGroup` caps/predecessors (tools max 2, food max 3, effects max 4, etc.).
- Add `pool: random|custom-only`, `team-size: any|2|2+`, `requires: { structures, biomes }`.
- Final weights/icons/display/requirement pass; item goals currently dominate ~43% of weight.
- Tests: **T-052**, **T-053**, **T-054**.

### 5.6 Deferred/backlog

- `HAVE_MORE_XP_LEVELS`, `HAVE_MORE_UNIQUE_CRAFTS`: need reversible claims; Paper claims are
  currently permanent. Deferred until the human chooses semantics.
- 1.16.1 parity (barter rates, raid waves, zombie conversion): separate large effort; needs explicit
  approval.
- Board size 3–7, client overlay, Board Builder, team chat, countdown, FFA, Blackout: separate
  features from `ideas.txt`; do not fold them into the goal port unasked.
- Persistence, rewards, player-tracking compass, bingo-line win, structure goals: still unimplemented.
- Custom textures are impossible server-side; use vanilla items.

---

## 6. Known issues and traps

1. **Round state is not reset** — see §5.1. This is the biggest open gameplay issue.
2. **Offline inventories are unreadable.** Team progress includes offline event counters and sets,
   but not inventory count/armor/live state.
3. **No world gating yet.** Structure/biome-dependent goals can be generated on worlds where they
   are unreachable.
4. **No group caps yet.** The board can contain many item goals or many goals from one family.
5. **Advancement keys are syntax-validated only.** A typo will load and silently never claim.
   Consider a runtime warning via `Bukkit.getAdvancement(key)` during load, but do not make it fatal
   without human approval.
6. **Movement statistics do not reliably fire** `PlayerStatisticIncrementEvent`. Sprint uses a
   polled baseline. Reuse that pattern for future movement/stat goals.
7. **Damage totals use `getFinalDamage()` double counters**, not raw damage. If the human reports
   unexpected totals, check armor/resistance and whether fatal hits are capped.
8. **Death matching is approximate for falling-off-vines** (checks vine blocks in a 5×5×5 area
   around the death point). The human passed it, but broaden/narrow only with a new test.
9. **T-018/T-019 log mismatch** — see §1.
10. **Claim permanence** — no unclaim path; "have most" goals cannot work without one.
11. **Board target is fixed at 13 of 25**, board size fixed at 5×5, two teams only. Do not change
    these while porting goals unless asked.
12. **Item weight dominance** — see §4; Slice 13 owns this.

---

## 7. Paper API / static validation workflow

Build (allowed; does not start the game):

```bash
JAVA_HOME="$PWD/.tools/jdk-25.0.5.1+2" \
GRADLE_USER_HOME="$PWD/.tools/gradle-home" \
.tools/gradle-9.9.2/bin/gradle jar --no-daemon --offline
```

Output: `build/libs/TLockout-<version>.jar`.

Paper API jar:

```bash
find .tools/gradle-home -name 'paper-api-*.jar' | head -1
```

Use `javap -classpath <paper-api.jar>` to verify event classes, methods, enum constants and
registries before implementing a new family. For material/entity/effect/statistic names, extract
the constants and compare every YAML value (including expanded variants) against them. This caught
`REDSTONE_COMPARATOR` vs `COMPARATOR` and `REDSTONE_REPEATER` vs `REPEATER` in Slice 8.

To validate YAML with Bukkit's actual loader without a server, compile a throwaway class in
`build/tmp/` with all cached jars on the classpath and call `new YamlConfiguration().load(file)`.
This catches nested-list/map assumptions. Do not commit throwaway classes.

The catalogue also requires the correct `goals-version`. Bump it when adding/removing shipped
entries and update `GoalCatalog.GOALS_VERSION`; otherwise existing installs keep the old file.

---

## 8. Test and handoff protocol

Use the next free IDs: **T-042 onward**. If a test fails, add `T-0xxb` referencing the original, as
T-041b did.

For every gameplay change:

1. Append a full entry to `docs/manual-tests.md`:
   - `**Change:**`, `**Setup:**`, numbered `**Steps:**`, `**Expected:**`, `**Watch for:**`,
     `**Report back:**`, `**Status:** awaiting human test`.
2. Add the same information under `## Test handoff` in the chat response.
3. Update `AGENTS.md` current status and `docs/goal-port-plan.md` status/tests.
4. Compile; do not run the game.
5. State explicitly that the mechanic is not verified until the human reports back.

On a pass/fail report:

- Update the status line only (`pass (owner, version)` / `fail (owner, version)`).
- On fail, do not weaken the goal or tracking; find the cause, fix it, and add a `b` retest entry.
- Do not mark a different mechanic verified because the human said "all tests pass" for another
  set; check which IDs they actually mean.

Versioning: bump the plugin version for every shipped change (the repo has used patch/minor
versions per slice). Bump `goals.yml` `goals-version` when the catalogue changes.

---

## 9. Immediate next steps

1. Read `AGENTS.md`, this file, `docs/goal-port-plan.md`, and the tail of `docs/manual-tests.md`.
2. Ask the human the clean-match-reset question in §5.1.
3. Implement the chosen reset (if any) with its own test ID, or proceed to 10b with the existing
   stateless-free rounds.
4. Implement 10b in the order armor → item sets → fill-inventory, checking the Fabric reference for
   exact material lists and the Paper API for wolf armor.
5. Append T-042 … T-045 (adjusted if needed), compile, and hand off. Do not run the server.

---

## 10. Fabric reference and licensing

The reference implementation is `lockout-fabric/lockout-fabric-main/` (marin774's Lockout, MC
1.21.8, Fabric). Useful places:

- `src/main/java/me/marin/lockout/lockout/goals/` — goal classes by category.
- `.../lockout/interfaces/` — shared goal interfaces and set definitions.
- `.../generator/BoardGenerator.java`, `GoalGroup.java`, `GoalRequirements.java` — pool filtering,
  group caps and world requirements.
- `.../lockout/DefaultGoalRegister.java` — the full registered goal list and gating.
- `src/main/resources/assets/lockout/textures/` — the mod's custom textures (not usable directly).

**Licensing caution:** `fabric.mod.json` says "All-Rights-Reserved", while the bundled `LICENSE` is
GPLv3 (© 2025 marin774). Do not copy code or textures from it without the human's explicit decision.
Use it as behavioural reference for goal lists and gating only.

The Fabric project targets 1.21.8 and a modded client; this project deliberately targets Paper with
vanilla clients. Do not switch platforms or add client-side requirements without an explicit human
decision.
