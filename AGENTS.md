# AGENTS.md

Operating rules for any AI agent working in this repository.

## What this project is

A Minecraft **Lockout challenge** implementation (Paper plugin — see "Stack" below): generate a board of
goals, track which team completes which goal first, and lock claimed goals out for everyone else.
Design notes live in `ideas.txt`; that file is the source of truth for scope.
Continuation handoff for another coding agent: `docs/agent-handoff.md`.

---

## 1. The hard rule: the human tests, the agent does not

**Never test game mechanics yourself. The human owner tests everything in-game on their own
self-hosted server.**

This applies to every mechanic this project adds or changes, including but not limited to:

- goal generation (board size, duplicate goals, difficulty mix, seed determinism)
- goal tracking and completion detection (items, durability, kills, biomes, structures, mobs, taming, breeding)
- claim/lockout logic and team scoring
- board GUI rendering and interaction
- timers, win conditions, tiebreakers
- rewards, compass tracking, any 1.16.1-parity behavior

The reason is practical, not bureaucratic: only the human can run the real client against a real
server and judge whether a goal actually feels and behaves correct. A green build proves nothing
about gameplay. Agents that "quickly verify" in-game behavior produce false confidence and are
violating this rule even when the intent is helpfulness.

### Do not

- Run a Minecraft server or client of any kind — no `./gradlew runServer`, `runClient`, `runPaperclip`,
  no launching `paper.jar`/`spigot.jar`, no headless servers, no local test server.
- Start, stop, or connect to a Minecraft instance, or touch the human's server process/files.
- Write or run **automated tests of mechanics**: JUnit tests over Bukkit mocks/MockBukkit,
  GameTest/`fabric-gametest`, server-boot CI, replay/bot harnesses, or "small" integration tests
  that assert gameplay behavior. These all count as testing mechanics.
- Install, download, or configure Minecraft/Fabric/Paper artifacts for the purpose of running them.
- Claim, imply, or hedge that a mechanic "works", "should be fine", "is probably correct", or "is
  verified". If it has not been tested by the human, label it **not verified**.

### Allowed

- Compiling and building the project (`./gradlew build`, `./gradlew compileJava`) — as long as the
  build does not run mechanic tests.
- Reading and reasoning about code: tracing data flow, checking API signatures against the
  dependency sources, spotting logic errors, checking config/YAML validity by inspection.
- Static tooling that never starts the game: linters, formatters, `javac`, dependency resolution,
  Gradle `dependencies`/`outdated` style tasks.
- Pure-language unit tests **only** if the human has explicitly authorized them for a specific,
  named piece of non-gameplay logic (e.g. board-placement math). Default is: no.

If a task seems to require in-game verification, that is not a reason to do it — it is a reason to
write the handoff test (section 2) and stop.

---

## 2. The handoff: how the agent reports testable work

Every change that touches gameplay must ship with a **Test Handoff** — the exact things the human
needs to try, written so they can execute it in one sitting without reading the source.

### Where it goes

1. **In the chat response** to the human, under a heading `## Test handoff`.
2. **Appended to `docs/manual-tests.md`** with a stable ID (`T-001`, `T-002`, …), preserving prior
   entries and their status. Create the file if it does not exist.

### Format

```
### T-014 — Locked goals cannot be re-claimed

**Change:** Claimed tiles now write to the team's claim set and are rejected on second completion.
**Setup:** Server running this build; two players on different teams; default board, 5x5.
**Steps:**
1. Have Team A complete the "get 1 iron ingot" goal.
2. As Team B, also obtain an iron ingot.
3. Run `/lockout board` and inspect the iron ingot tile.
**Expected:** Tile shows Team A's colour; Team B's pickup produces no claim, no score change,
and no duplicate reward. Board still shows 1/25 claimed.
**Watch for:** double scoring, tile flicker, console stack traces.
**Report back:** result (pass/fail), plus any console output and a screenshot of the board.
```

### Rules for writing handoffs

- Number them, never renumber. Supersede an old entry rather than editing history.
- One behavior per entry. If a change touches three mechanics, write three entries.
- State setup preconditions explicitly: player count, teams, board size, difficulty, config knobs.
- Give the expected result in observable terms (what the human sees), not in terms of internals.
- Name the thing to capture on failure: console log excerpt, screenshot, `/lockout` output, exact
  step where it broke.
- Keep a status line on each entry: `Status: awaiting human test` → `pass` / `fail` / `blocked`.

### Pending-tests summary

End every response that touched gameplay with a short list of what is currently awaiting human
testing, by ID. Example:

> **Awaiting your test:** T-012, T-013 (goal tracking + board display). Nothing else has changed.

If nothing has been built yet, say so plainly instead of inventing test cases.

---

## 3. Working with test results

When the human reports a failure:

1. Ask for the specific artifact missing from the report (log, screenshot, step) only if it is
   genuinely needed — otherwise reproduce from reasoning.
2. Fix the cause; do not fix by weakening the goal or the tracking to make it pass.
3. Re-open the same test ID as a new entry (`T-014b`) referencing the original; do not silently
   reuse the ID.
4. Never mark a mechanic verified based on the human's "looks good" alone for a *different*
   mechanic than the one they tested.

---

## 4. Stack and conventions

- **Platform:** Paper plugin (server-side, vanilla clients), **Java 25**, **Gradle 9.9.2**,
  targeting **Minecraft/Paper 26.3** (`io.papermc.paper:paper-api:26.3.build.+`).
  `plugin.yml` uses `api-version: '26.3'`.
- **Why not Fabric:** decided in session — vanilla-client accessibility and iteration speed outweigh
  client-side GUI/mechanics control. Do not silently switch platforms; that decision is the human's.
- **Goal templates:** config-driven (`src/main/resources/goals.yml`). A new goal *instance* in an
  existing family needs only a YAML entry; a new goal *family* needs a `GoalTracker` under
  `dev.tlockout.goal.tracker` plus a `GoalType` value. A board needs at least 25 templates; the
  shipped file has 267 (256 random-pool, 11 custom-only).
- **Build:** a gitignored toolchain lives in `.tools/` for compile checks. Compiling is allowed;
  running the result is not.
  ```bash
  JAVA_HOME="$PWD/.tools/jdk-25.0.5.1+2" \
  GRADLE_USER_HOME="$PWD/.tools/gradle-home" \
  .tools/gradle-9.9.2/bin/gradle jar --no-daemon
  ```
  Output: `build/libs/TLockout-1.6.1.jar`. Delete `.tools/` to reclaim ~510 MB; it can be
  re-downloaded (Temurin 25 + Gradle 9.9.2).
- **Layout:** `dev.tlockout` — `TLockout` (plugin bootstrap), `LockoutMode` (`TEAMS` / `FFA`),
  `ChallengeManager` (round state + claims + tie-aware finish + force-win),
  `TrackingService` (completion detection), `PlayerResetService` (toggleable clean-match player reset),
  `FireworkService` (harmless coloured claim & victory fireworks),
  `GoalCatalog` / `GoalType` (templates), `Board`, `Names`,
  `Side` / `SideManager` / `ConcreteColors` (teams & FFA 1-player sides), `BoardGui`, `LockoutCommand`,
  `TeamCombatListener` (friendly-fire cancellation), `BoardCompass` (the item that opens the board
  and tracks players), `PlayerTrackerService` / `PlayerTrackerGui` (live player compass tracking
  and selector menu), `HudService` (sidebar + boss bar). The goal-model package is `dev.tlockout.goal`: `GoalSpec`,
  `ResolvedGoal`, `GoalTracker` / `GoalTrackers`, `GoalScope`, `TrackingState`, `GoalSupport`, and
  one tracker per family under `dev.tlockout.goal.tracker`.
- **Config migration:** `config.yml` carries `config-version`; `goals.yml` carries `goals-version`.
  On startup, an older `config.yml` is copied to `config.yml.old` and the bundled default is written
  back; an older `goals.yml` is copied to `goals.yml.v<old version>.bak` and replaced with the
  bundled default. Bump the relevant version whenever the shape of that file changes, or server
  owners keep a stale file.
- **Scope:** `ideas.txt`. MVP first; the "1.16.1 mechanics" nice-to-have is a separate, large effort
  and needs explicit approval before it is started.
- **No git repo yet.** Do not `git init` or commit unless asked.

---

## 5. Current status

- **Slice 1** (goal generation + tracking): passed by the human (T-001b … T-008).
- **Slice 2** (teams + win condition, 0.2.0): passed (T-009 … T-015).
- **Slice 3** (board compass, 0.2.1): passed (T-016, T-017).
- **Slice 4** (ticked-off tiles, 0.2.2): passed (T-018).
- **Slice 5** (live board, 0.2.3): passed (T-019). `ChallengeManager.tryClaim` calls
  `TLockout.refreshViews()` after every claim; `stop()` closes open boards. Never rebuild the GUI by
  re-opening it on refresh — that steals the viewer's cursor focus.
- **Slice 6** (targeting HUD, 0.3.0): passed (T-020 … T-024).
  - Targets live on `Side` (shared per team, max `Side.MAX_TARGETS` = 5); a claim removes the cell
    from every side's list so it frees a slot.
  - `HudService` owns a per-player sidebar (`NumberFormat.blank()`, invisible colour-code entries,
    team prefixes carry the text) and a per-player Adventure boss bar. It **replaces the player's
    scoreboard**, which is a known conflict with other sidebar plugins.
  - Progress is team-wide: `TrackingService.teamProgress` sums members (offline members contribute
    only event counters) and caps at the goal target.
- **Slice 7** (goal-model foundation, 0.3.1): passed (T-025 … T-027).
  - `GoalSpec` + one `GoalTracker` per family replace the single record/switch. `Board` stores
    `ResolvedGoal` cells so variant goals (colour, armor piece) can be resolved per board.
  - `GoalScope` is in place; Slice 9 added the first `TEAM`-scope trackers.
  - `goals.yml` was version 2 for this slice; it is now version 5.
  - No new goal types were added in this slice. The port plan is `docs/goal-port-plan.md`.
- **Testing utility** (0.3.2): passed (T-028). `/lockout start [seed] [--allow-empty]` starts a
  round even when a side has no players. Normal starts still refuse empty teams.
- **Team-shared claims** (0.3.3): passed (T-029). Claim detection uses
  `TrackingService.teamProgress`, the same total the HUD shows, so 3x + 5x items completes an 8x
  goal. The claim is credited to the member with the largest personal contribution.
- **Slice 8** (Fabric catalogue part 1, 0.4.0): passed (T-030 … T-034).
  - `goals.yml` now has 100 templates: 33 single-item obtains, 64 arrows, three colour-variant
    templates (wool/concrete/glazed terracotta), 10 specific mob kills and 2 dimension goals.
  - New `DIMENSION` goal type and `DimensionGoalTracker` (NETHER/END/OVERWORLD).
  - `goals.yml` was version 3 for this slice.
  - Not yet ported: conditional kills (Breeze with Wind Charge, Snow Golem in the Nether),
    custom-only goals, item sets. Group caps and world gating are still later slices.
- **Slice 9** (event families, 0.4.1): passed (T-035 … T-037).
  - Consume/drink (15 goals), advancements (16), status effects (12); for this slice `goals.yml`
    had 143 templates and was version 4.
  - New team state: unique food and unique advancement sets are shared per side; "N status effects
    at once" uses the best single player, not a team sum.
  - Advancements exclude recipe unlocks and display-less entries; cake slices use the vanilla
    cake-slices statistic; milk removal uses `EntityPotionEffectEvent` cause `MILK`.
  - Round start does not reset inventories, stats or advancements. Test advancement goals on a
    fresh world or run `/advancement revoke @s everything` before starting.
- **Slice 10a** (combat, movement, state, 0.5.0/0.5.1): passed (T-038–T-040, T-041b). T-041
  failed because the statistic event did not fire for movement distance; 0.5.1 polls
  `SPRINT_ONE_CM` against a round-start baseline instead.
  - 25 goals: 13 death causes, Take 200/Deal 400 damage, XP 15/30, height limit/bedrock/Nether roof,
    empty hunger, ride horse/minecart/pig, Sprint 1km.
  - New trackers: `death_by`, `damage_total`, `xp_level`, `position`, `empty_hunger`, `ride`,
    `stat_counter`. Damage totals use double counters; sprint uses a statistic baseline.
  - Death matching uses `PlayerDeathEvent` damage type/entity; falling-off-vines checks for vine
    blocks near the death point.
  - `goals.yml` was version 5 for this slice; it is now version 6.
  - Round start did not reset XP, hunger, mounts or vanilla statistics in 0.5.x; 0.6.0 adds a
    toggleable auto-reset.
- **Clean-match auto-reset + Slice 10b** (armor & item sets, 0.6.0): passed (T-042 … T-046).
  - `PlayerResetService` resets participating players' inventory, armor/offhand, cursor, potion
    effects, XP, hunger/saturation, health/fire/freeze/air, mounted vehicle, advancements and
    untyped statistics on `/lockout start` (and queues offline team members for reset on join).
    Controlled by `match.auto-reset` (`config-version: 2`, default `true`) and toggled live with
    `/lockout autoreset [on|off|toggle]`.
  - 26 new goals (194 templates total, `goals-version: 6`): 10 armor/wearables (full Leather/Gold/
    Iron/Diamond sets, chain piece, random colored leather piece with dyed board icon, full leather
    in 4 different colors, full enchanted armor, carved pumpkin 5-minute cumulative timer, wolf
    armor) and 16 item sets (5 tool tiers, horse armor, minecarts, mushrooms, pumpkins, raw ore
    blocks, 4 unique saplings, 4 unique seeds, 6 unique buckets, 6 unique flowers, 7 workstations,
    fill inventory with 36 unique items).
  - New trackers: `armor_set`, `armor_piece`, `armor_timer`, `wolf_armor`, `item_set`,
    `unique_inventory`. Worn armor, pumpkin timer and fill-inventory use best-single-player
    semantics; `item_set` uses team-wide union of online inventories.
- **QoL commands** (0.6.1): passed (T-047, T-048).
  - `/lockout compass` (`tlockout.play`, player-only): re-gives the tagged Board Compass during an
    active/finished round if missing, without duplicating.
  - `/lockout reroll [seed]` (`tlockout.admin`, op-only): stops any active/finished round and
    immediately starts a fresh testing round (`--allow-empty` semantics, respecting `autoreset`).
- **Board Compass tooltip hint** (0.6.2): passed (T-049).
  - Added `Lost it? Run /lo compass` as a second lore line on the Board Compass item.
- **Mid-game team joining** (0.6.3): passed (T-050, T-051).
  - Unassigned players can `/lo join <team>` while a round is running (existing team members stay
    locked and cannot self-switch or `/lo leave`). Unassigned joiners are reset if `autoreset` is
    `ON`, get a fresh tracking baseline + Board Compass, and have views refreshed immediately.
  - Admins can use `/lo team set <player> <team>` mid-round to add unassigned players or move
    existing players (moves clear the player's personal round counters/sprint baseline without
    wiping inventory).
- **Slice 11** (team kill counters & opponent goals, 0.6.4): passed (T-052 … T-054).
  - 15 new goals (209 templates total, `goals-version: 7`): 9 team kill/tag/sheep goals (`Kill 100
    mobs`, `Kill 7/10/13/15 Unique Hostile Mobs`, `Kill 20 Arthropods`, `Kill 30 Undead Mobs`,
    `Kill all Raid Mobs`, `Kill <Color> Sheep`) and Fabric's 6 random-pool opponent goals
    (`Opponent dies`, `Opponent dies 3 times`, `Opponent catches on Fire`, `Opponent takes Fall
    Damage`, `Opponent Team takes 100 Damage`, `Opponent touches Water`). Fabric's 6 custom-only
    opponent goals are held for Slice 13's pool filters.
  - New trackers: `mob_kill_total`, `mob_kill_unique`, `mob_kill_group`, `colored_sheep_kill`,
    `opponent`. Opponent goals evaluate the opposing side (`SideManager.otherOf`) and award claims
    to the beneficiary side via `ChallengeManager.tryClaimOpponent` (empty sides cannot claim).
- **Board Compass live team rosters** (0.6.5): passed (T-055).
  - `BoardCompass` appends each team's live roster (`<Team>: <players>`, `(empty)`, `(offline)`)
    to the Board Compass lore and refreshes existing compasses in-place on round start, mid-game
    joins/moves, and player join/quit.
- **Board GUI team concrete rosters** (0.6.6): passed (T-056).
  - `BoardGui.sideItem` (the team concrete blocks flanking the Clock) shows `<online> / <total>
    online` and lists online (`● <name>`) and offline (`○ <name> (offline)`) members live on open
    boards.
- **Slice 12** (workstations, brewing, block actions, mining & conditional kills, 0.7.0): passed
  (T-057 … T-059).
  - 37 new goals (246 templates total, `goals-version: 8`): 8 workstations (anvil, cauldron wash,
    composter harvest, enchanting table, grindstone, loom, smithing table, stonecutter), 7 brewing
    goals (healing, invisibility, poison, swiftness, water breathing, weakness, lingering potion;
    with tinted potion icons in `BoardGui`), 5 mine-block goals (diamond/emerald ore incl.
    deepslate, mob spawner, turtle egg, crafter), 15 misc interactions (jukebox, glow ink on
    crimson sign, flower pot, full armor stand, item frame in item frame, full campfire, full
    chiseled bookshelf, place end crystal, light candle, shoot firework from crossbow, toot goat
    horn, brush suspicious sand/gravel, enrage zombified piglin, shield disabled, decorate shield
    with banner), and 2 conditional kills (Breeze with Wind Charge, Snow Golem in The Nether).
  - New trackers: `workstation`, `brew_potion`, `mine_block`, `block_action`, `banner_shield`,
    `conditional_kill`.
- **Board GUI op-only Reroll button** (0.7.1): passed (T-060).
  - `BoardGui` renders a `Reroll Board` (`RECOVERY_COMPASS`) button in slot 53 (bottom-right
    corner) only when `viewer.isOp()`. Clicking it runs `TLockout.rerollChallenge` (shared with
    `/lo reroll`) on the next tick and re-opens the freshly rolled board for the clicking op.
- **Slice 13** (world gating, anti-clustering groups, pool filters & catalogue finish, 1.0.0):
  passed (T-061 … T-063).
  - `WorldFeasibilityScanner` (`world-scan.enabled: true`, `world-scan.radius: 750`,
    `config-version: 3`): scans required structures (11) and biomes (39) around overworld spawn,
    caches per world, invalidates on `/lo reload`, and drops unreachable `requires:` goals.
  - `GoalGroups`: ports Fabric's anti-clustering group caps and predecessor rules plus type caps
    (max 6 plain items, max 1 durability, max 3 workstations, max 2 mine blocks, max 2 tame, max 1
    position) and shuffles the final 25 cells after draw.
  - `GoalSpec` metadata: `pool: random|custom-only`, `team-size: any|2|2+`, and
    `requires: { biomes, structures }`.
  - Catalogue finished (`goals-version: 9`, 267 templates: 256 random-pool + 11 `custom-only`):
    adds `breed_unique` (`Breed 4/6/8 Unique Animals`), 5 missing breed goals (`Fox`, `Frogs`,
    `Goat`, `Hoglin`, `Strider`), 2 missing biome goals (`Ice Spikes`, `Mushroom Fields`, plus
    multi-biome matching in `BiomeGoalTracker`), 5 custom-only obtain/effect/advancement goals, 6
    custom-only opponent goals (`OBTAIN_ITEM`, `EAT_FOOD`, `HIT_BY_EGG`, `HIT_BY_SNOWBALL` via
    `ProjectileHitEvent`), world-gating on 42 templates, and balanced weights.
- **Sound effects** (1.1.0): passed (T-064, T-065).
  - `SoundService` (`config-version: 4`, `sounds:` section in `config.yml`, reloaded via `/lo
    reload`): 2-note ascending `block.note_block.pling` (1.0 -> 1.5) for your team's claim, 2-note
    descending `block.note_block.bass` (0.9 -> 0.6) for opponent's claim, neutral
    `block.note_block.chime` for spectators, `item.goat_horn.sound.0` on round start/reroll,
    `ui.toast.challenge_complete` for winner/spectators on finish, and `entity.warden.death` for
    losers on finish (no overlapping claim sound on the winning claim).
- **Pre-round countdown & world-scan chat notices** (1.2.0): passed (T-066, T-067).
  - `CountdownService` + `ChallengeManager.State.COUNTDOWN` (`config-version: 5`,
    `match.countdown-seconds: 5`, `match.freeze-during-countdown: true`, `sounds.countdown-tick`):
    `/lo start` counts down with Adventure titles, chat ticks, and SFX while freezing participating
    players (look-around allowed; movement/jump/block edits/damage blocked) until `GO!`. `/lo stop`
    cancels cleanly; `/lo reroll` and the GUI Reroll button skip the countdown.
  - `TLockout.prepareWorldAndRun` broadcasts `Scanning world '<world>'...` before an uncached
    `WorldFeasibilityScanner` pass (scheduled 1 tick later so chat flushes first) and `World scan
    complete: X/11 structures, Y/39 biomes found (Zms).` afterward; cached starts stay silent.
- **Keep inventory during rounds** (1.3.0): passed (T-068).
  - `KeepInventoryService` (`config-version: 6`, `match.keep-inventory: true`, toggled live with
    `/lo keepinventory [on|off|toggle]`): keeps participating players' inventory and XP levels on
    death during `RUNNING` rounds via `PlayerDeathEvent` (clearing drops and dropped XP) without
    modifying world gamerules or affecting spectators / `IDLE` deaths.
- **Player-tracking Board Compass** (1.4.0): passed (T-069 … T-071).
  - `PlayerTrackerService` + `PlayerTrackerGui`: Shift + Right-click on the Board Compass or
    clicking the `Track Player` compass button in `BoardGui` (slot 45) opens a chest menu listing
    all participating players (both teammates and enemies, grouped by team; spectators can browse
    and track participants without appearing as targets themselves).
  - `BoardCompass` points to the selected player via `CompassMeta` lodestone tracking
    (`setLodestoneTracked(false)`) and updates its display name to `Tracking: <Name> (<dist>m)`
    every 10 ticks when changed; across dimensions it points to the target's last-known location
    in the viewer's dimension (`Tracking: <Name> (<Dim> · <dist>m)`), or spins if unseen there.
- **Free-For-All (FFA) mode** (1.5.0): passed (T-072 … T-075).
  - `LockoutMode` (`TEAMS` | `FFA`), `config-version: 7` (`mode: teams|ffa`,
    `win.ffa-goals-to-win: 5`), and live `/lo mode [teams|ffa]` admin switch between rounds.
  - Every participating player gets their own 1-member `Side` with a distinct colour from
    `ConcreteColors.PALETTE`. Online players auto-enroll on `/lo start` and `/lo reroll` unless
    they opted out with `/lo leave` (players can also pick a colour with `/lo join [color]` or
    join mid-round).
  - First to `win.ffa-goals-to-win` (default 5) wins; if all 25 goals are claimed first, highest
    score(s) win with tie support. Opponent goals (`team-size: "2"`) remain eligible only in
    2-player FFA and are automatically excluded in 3+ player or 1-player testing FFA.
  - Adapted `BoardGui` (ranked top-8 header + full Clock leaderboard), `HudService` (leader +
    personal rank sidebar), `BoardCompass` (compact FFA roster lore), and `PlayerTrackerGui`
    (grid layout for up to 45 FFA players).
- **Win-target command, force-win & goal fireworks** (1.6.0): passed (T-076 … T-078).
  - `/lo goalstowin [1-25]` (`tlockout.admin`): sets the active mode's goals-to-win target in-game
    (persisted per mode in memory until `/lo reload`; immediately updates open UI/HUD and finishes
    the round if a side already meets the lowered target).
  - `/lo forcewin <team|player>` (`tlockout.admin`): force-ends a `RUNNING` round and awards the
    win to the named team (or FFA player), freezing claims and playing finish sounds + fireworks.
  - `FireworkService` (`config-version: 8`, `match.fireworks-on-claim: true`): launches a coloured
    celebration firework on goal claim and a 3-rocket victory burst on match finish, tagged in PDC
    with all explosion damage cancelled at `EventPriority.LOWEST` so it never hurts players/mobs
    or counts toward damage/death goals.
- **Help header plugin version** (1.6.1): passed (T-079).
  - Typing `/lockout` or `/lo` prints `TLockout v<version> - usage` using
    `plugin.getPluginMeta().getVersion()`. All tests in `docs/pending-tests.md` have passed.
- Teams are defined by a concrete block (`teams.<id>.concrete`); the side's text colour is that
  dye's colour, and the block is also its GUI icon.
- On the board, a claimed tile is rendered as the claiming side's **glinted concrete block** with the
  goal name struck through; unclaimed tiles keep the goal's own icon. Do not reintroduce the goal
  icon for claimed tiles without being asked.
- The `Board Compass` is a tagged `COMPASS` with a glint override; it is given on round start and on
  join-while-active, cannot be dropped, is stripped from death drops, and is removed on `stop`.
  Tagging lives in `BoardCompass` under the `board_compass` persistent-data key — never identify it
  by display name.
- A round ends at `win.goals-to-win` (default 13 in Teams) or `win.ffa-goals-to-win` (default 5 in
  FFA), or when all 25 cells are claimed, and freezes claims; the board stays viewable and shows
  the winner(s).
- Claims and team/FFA membership are in-memory: both reset on restart.
- Not implemented: persistence, rewards, bingo-line win, structure goals.
