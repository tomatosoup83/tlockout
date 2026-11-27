# Pending Manual Tests — TLockout `1.6.1`

All tests currently awaiting human in-game verification (`T-072` through `T-079`), compiled into one
checklist for build `build/libs/TLockout-1.6.1.jar`.

Canonical history lives in `docs/manual-tests.md`. Update results by reporting pass/fail per ID.

## Quick checklist

- [x] **T-079** — `/lockout` prints plugin version (`v1.6.1`)
- [x] **T-072** — Config upgrade (to v8) & `/lo mode [teams|ffa]` live switching
- [x] **T-076** — `/lo goalstowin [1-25]` live win-target setting & mid-round win trigger
- [x] **T-077** — `/lo forcewin <team|player>` op-only force finish
- [x] **T-078** — Harmless coloured fireworks on goal claim & match victory
- [x] **T-073** — FFA auto-enrollment, `/lo leave` opt-out, `/lo join [color]` & mid-round join
- [x] **T-074** — FFA claiming, lockout, PvP, win target (5) & 2p vs 3p+ opponent-goal gating
- [x] **T-075** — FFA Board UI, HUD sidebar, Board Compass lore & Player Tracker grid

---

## Detailed test steps

### T-079 — `/lockout` prints the plugin version

**Change:** `LockoutCommand#help` displays `TLockout v<version> - usage` (from
`plugin.getPluginMeta().getVersion()`) when `/lockout` or `/lo` is typed with no arguments.
**Setup:** Build `1.6.1`; any player or server console.
**Steps:**
1. Run `/lockout` (and `/lo`) with no arguments.
2. Check the first line of the help output.
**Expected:** Header prints `TLockout v1.6.1 - usage` followed by the command list.
**Watch for:** missing version string, stale version number, or console errors.
**Status:** pass (owner, 1.6.1)

---

### T-072 — Config upgrade & `/lo mode` live switching

**Change:** Added `LockoutMode` (`TEAMS` | `FFA`), `win.ffa-goals-to-win: 5` and support for
`mode: ffa` (upgraded through `config-version: 8` in `1.6.1`), plus admin command
`/lo mode [teams|ffa]`.
**Setup:** Build `1.6.1`; existing `config.yml` (v6/v7) in `plugins/TLockout/`; an opped player.
**Steps:**
1. Start the server and confirm `config.yml` upgrades to v8 (`config.yml.old` backup) containing
   both `win.ffa-goals-to-win: 5` and `match.fireworks-on-claim: true`.
2. Run `/lo mode` and confirm it reports `Teams (first to 13 goals wins)`.
3. Run `/lo mode ffa` and confirm it broadcasts `Lockout mode set to FFA (first to 5 goals wins)`.
4. Start a quick testing round (`/lo reroll`), then try `/lo mode teams` while running. Confirm it
   refuses with a message to `/lo stop` first.
5. Run `/lo stop`, then `/lo mode teams` (confirms `first to 13 goals wins`), then `/lo mode ffa`.
6. Optional: set `mode: ffa` in `config.yml`, run `/lo reload`, and confirm the plugin loads cleanly
   in FFA mode without disabling itself.
**Expected:** Config upgrades cleanly; `/lo mode` switches between Teams (13) and FFA (5) when idle,
refuses while running/counting down, and `mode: ffa` in `config.yml` loads cleanly.
**Watch for:** plugin disabling on `mode: ffa`, stale win target (13 vs 5) after switching, or
console errors.
**Status:** pass (owner, 1.6.1)

---

### T-076 — In-game `/lo goalstowin [1-25]` win-target command

**Change:** Added admin command `/lo goalstowin [1-25]` to view/change the goals needed to win for
the active mode without editing `config.yml`, updating open UI/HUD live and immediately finishing
a running round if lowered to or below a side's score.
**Setup:** Build `1.6.1`; an opped player (plus a non-op player if testing permission denial).
**Steps:**
1. While `IDLE` in Teams mode, run `/lo goalstowin` and confirm it reports `Goals to win (Teams):
   13`.
2. Run `/lo goalstowin 0` and `/lo goalstowin 26` and confirm both are rejected cleanly (`between 1
   and 25`).
3. Run `/lo goalstowin 7`, switch to FFA (`/lo mode ffa`) and confirm `/lo goalstowin` reports `5`.
   Set FFA to `/lo goalstowin 3`, switch back to `/lo mode teams`, and confirm Teams still remembers
   `7`.
4. Start a round (`/lo reroll`), open `/lo board`, and confirm the Clock says `First to 7 wins` and
   team headers show `0 / 7 goals`.
5. Claim 2 goals for your side, then run `/lo goalstowin 2`. Confirm chat announces `Goals to win
   (Teams) set to 2.` followed immediately by your side winning the lockout and freezing claims.
**Expected:** `/lo goalstowin` updates the active mode's win target (1–25), remembers each mode's
target across `/lo mode` switches until `/lo reload`, updates UI/HUD live, and finishes a running
round if lowered to/below a side's score.
**Watch for:** non-ops able to run `/lo goalstowin`, stale target on Board UI/HUD, or round not
ending when lowered to a side's current score.
**Status:** pass (owner, 1.6.1)

---

### T-077 — Op-only `/lo forcewin <team|player>` command

**Change:** Added admin command `/lo forcewin <team|player>` to force-end a `RUNNING` round and
award the win to the specified team (Teams mode) or player (FFA mode), freezing claims and
triggering finish sounds + victory fireworks.
**Setup:** Build `1.6.1`; an opped player; test in Teams mode (Red vs Blue) and optionally in FFA.
**Steps:**
1. While `IDLE`, run `/lo forcewin red` and confirm it refuses (`No round is currently running.`).
2. Start a round (`/lo reroll`), check tab-completion for `/lo forcewin <TAB>` suggests team IDs
   (`red`, `blue`), and run `/lo forcewin blue`.
3. Confirm chat broadcasts `[Force-ended by <Admin>] Blue wins the lockout!` with the score line,
   finish sounds play, victory fireworks launch for online Blue members, and open boards/sidebars
   show Blue as winner.
4. Try completing an unclaimed goal after `/lo forcewin` and confirm claims are frozen/rejected.
5. Run `/lo forcewin red` while `FINISHED` and confirm it refuses (`No round is currently running.`).
6. Optional (FFA): `/lo stop`, `/lo mode ffa`, `/lo reroll`, check `/lo forcewin <TAB>` suggests
   enrolled player names, and run `/lo forcewin <PlayerName>`.
**Expected:** Cleanly force-ends a running round for the named team/player with admin attribution,
finish sounds, victory fireworks, updated UI/HUD, and locked claims; refuses when idle or finished.
**Watch for:** claims still accepted after force-win, missing sound/fireworks, wrong winner in
Board Clock/HUD, or console errors.
**Status:** pass (owner, 1.6.1)

---

### T-078 — Harmless coloured fireworks on goal completion and match victory

**Change:** `FireworkService` (`config-version: 8`, `match.fireworks-on-claim: true`) launches a
coloured celebration firework on goal claim and a 3-rocket victory burst on match win, cancelling
all explosion damage at `EventPriority.LOWEST` via a PDC tag.
**Setup:** Build `1.6.1`; a player in Survival mode.
**Steps:**
1. Confirm `match.fireworks-on-claim: true` is present in `config.yml` (v8).
2. Start a round (`/lo reroll`) in Survival mode (test at low health or inside a 2-block-tall
   enclosed ceiling/tunnel). Complete a goal.
3. Confirm a firework coloured to match your team/FFA colour launches from you and detonates, and
   confirm you take **zero** damage, **zero** knockback, and **zero** progress on `Take 200 damage`
   / `Opponent Team takes 100 Damage` goals (if on the board).
4. Complete the winning goal (or finish via `/lo goalstowin` / `/lo forcewin`) and confirm a
   3-rocket staggered victory burst launches for the winning side's online players with zero damage.
5. Optional: verify a regular vanilla firework shot from a crossbow still deals normal damage.
**Expected:** Coloured firework on every claim and 3-rocket burst on win; zero damage to players
or entities even in tight spaces and zero damage/death-goal triggers.
**Watch for:** firework hurting/killing the claimant in a cave, triggering damage/death goals,
wrong colour, or console errors.
**Status:** pass (owner, 1.6.1)

---

### T-073 — FFA auto-enrollment, `/lo leave` opt-out, colour choice & mid-round join

**Change:** In FFA mode, `/lo start` (and `/lo reroll`) automatically enrolls every online player
as their own 1-member coloured `Side` unless they opted out via `/lo leave`. Players can pick a
colour beforehand with `/lo join [color]` or join mid-round.
**Setup:** Build `1.6.1`; mode set to `FFA` (`/lo mode ffa`); at least 2 online players (ideally 3
to test opt-out + mid-round join).
**Steps:**
1. Without running `/lo join`, run `/lo start` (≥2 online; or `/lo start --allow-empty` solo).
   Confirm all online players auto-enroll with distinct colours, receive a Board Compass, freeze
   during countdown, and reset if `autoreset` is ON.
2. Run `/lo stop`. Have Player A run `/lo join lime` (confirms Lime). Have Player B run `/lo leave`
   (confirms opt-out spectator message).
3. Run `/lo team list` and confirm it shows enrolled FFA players with colours plus Player B under
   opted-out spectators.
4. Run `/lo start` (≥2 non-opted-out players, or `--allow-empty`). Confirm Player A has Lime and
   Player B is spectating (`Spectating (FFA)` on sidebar, cannot claim goals).
5. While running, have Player B run `/lo join` (or `/lo join orange`). Confirm broadcast `<B>
   joined the FFA mid-round`, Player B resets (if `autoreset` is ON) + gets compass, and can claim.
**Expected:** Online players auto-enroll on `/lo start`; `/lo leave` keeps a player out as spectator;
`/lo join [color]` picks a concrete colour and allows mid-round joining cleanly.
**Watch for:** opted-out player getting forcibly enrolled, duplicate colours (<16 players),
mid-round joiner missing compass or unable to claim, or console errors.
**Status:** pass (owner, 1.6.1)

---

### T-074 — FFA claiming, lockout, PvP, win target (5) & 2p vs 3p+ opponent goals

**Change:** FFA claims attribute directly to the claiming player (their concrete colour, no
redundant `for <Name>`), full PvP works between all FFA players, first to `win.ffa-goals-to-win`
(default 5) wins (with tie-aware fallback at 25/25), and `opponent` goals are only eligible in
2-player FFA.
**Setup:** Build `1.6.1`; mode set to `FFA` (with default target 5 — run `/lo goalstowin 5` if you
changed it in T-076).
**Steps:**
1. Start a 2-player FFA round (`/lo start`). Confirm PvP hits work between both players (no
   friendly-fire cancellation).
2. Have Player A complete a goal. Confirm chat says `✔ <A> claimed <Goal> (1/25)` (no duplicate
   `for <A>`), tile becomes Player A's glinted concrete colour, claim firework & team/opponent
   sounds play properly.
3. Have Player B try to complete the same locked goal and confirm it cannot be re-claimed.
4. Have one player reach 5 goals. Confirm the round ends immediately at 5, announces `<Winner>
   wins the lockout!` with ranked scores, plays win/lose sounds + victory fireworks, freezes claims,
   and keeps the board viewable.
5. Check opponent-goal gating: in **2-player** FFA (reroll a few times if needed), confirm
   `Opponent ...` goals *can* appear and trigger; in **3+ player** FFA (or 1-player `--allow-empty`
   solo FFA), reroll several times and confirm **no** `Opponent ...` goals appear.
**Expected:** Claims lock out everyone else, show each player's colour, allow full PvP, end at 5
goals, and allow opponent goals only in 2-player FFA.
**Watch for:** chat saying `claimed X for <Name>`, friendly fire blocking PvP, round not ending at
5 goals, or opponent goals appearing in 3+ player FFA.
**Status:** pass (owner, 1.6.1)

---

### T-075 — FFA Board UI, HUD sidebar, Board Compass lore & Player Tracker grid

**Change:** Adapted `BoardGui` (top-8 ranked concrete headers + full Clock leaderboard),
`HudService` (Leader + personal score/rank sidebar), `BoardCompass` (compact FFA roster lore), and
`PlayerTrackerGui` (grid layout across rows 1–5) for multi-player FFA.
**Setup:** Build `1.6.1`; active FFA round with 2+ players (ideally 3+ to see ranking & grid).
**Steps:**
1. Open the Lockout Board (`/lo board` or right-click compass). Confirm row 0 shows ranked player
   concrete blocks (`#1 <Name>`, `#2 <Name>`, ...) flanking the Clock, and hovering the Clock shows
   `Mode: FFA` plus the full ranked leaderboard.
2. Right-click an unclaimed goal to track it (`★ Tracked`, no `Shared with your team`). Check the
   sidebar and confirm it shows the leader line (`1st: ...` / `Tied: ...`), your score & rank
   (`You: X/5 (#rank)`), and your tracked goals without overflow/clipping.
3. Hover over your Board Compass in inventory and confirm lore shows `FFA Players (N):` with
   coloured player names.
4. Shift + Right-Click your Board Compass (or click `Track Player` at slot 45 in Board UI). Confirm
   the `Track Player` menu uses a clean grid of player heads (excluding yourself) showing
   `FFA · X/5 goals`, live distance/dimension, and points your compass at any selected player.
**Expected:** All four UI surfaces render cleanly for FFA with no redundant team labels, no sidebar
overflow, and working player tracking across all FFA participants.
**Watch for:** sidebar lines overflowing or clipping tracked goals, tracker menu running out of
rows, duplicate player name on claimed tile lore, or console errors.
**Status:** pass (owner, 1.6.1)
