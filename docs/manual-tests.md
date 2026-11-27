# Manual test log

These are executed **by the human owner in-game**, never by an agent (see `AGENTS.md` §1).
Status values: `awaiting human test` → `pass` / `fail` / `blocked`.

Build under test: `TLockout-0.5.1.jar` (Slice 10a complete; T-038–T-040 and T-041b passed).

---

### T-001 — Plugin enables on Paper 26.3

**Change:** First working build. Loads `goals.yml`, registers listener + `/lockout`.
**Setup:** Paper 26.3 server (Java 25), `TLockout-0.1.0.jar` in `plugins/`.
**Steps:**
1. Start the server and watch the console.
2. Run `/lockout` in game with no arguments.
**Expected:** Console shows `Loaded 51 goal templates.` and `TLockout enabled.`, with no
`Could not load goals.yml` line and no `api-version` warning. `/lockout` prints the help block.
**Watch for:** stack traces on enable, "legacy plugin" warning, unknown command.
**Report back:** the 15 lines of console around startup, plus what `/lockout` printed.
**Status:** fail (build 0.1.0) — `Could not load goals.yml: goal 'iron_ingot': missing required field`.
Loader bug fixed in 0.1.1; retested in T-001b.

---

### T-001b — Plugin enables (retest after the goals.yml loader fix)

**Change:** `GoalCatalog` was calling its "required field" helper on the optional `display`/`icon`
fields, so every template threw on its missing `display`. Optional fields now resolve to null and
fall back to the generated text. Error messages now name the exact field, and load logs the file path.
**Setup:** Replace the old jar in `plugins/` with `TLockout-0.1.1.jar`. **Keep** your existing
`plugins/TLockout/goals.yml` — it was never the problem and does not need deleting.
**Steps:**
1. Restart the server and watch the first ~20 console lines.
2. Run `/lockout` in game.
**Expected:** `Reading goal templates from .../plugins/TLockout/goals.yml`, then
`Loaded 51 goal templates.`, then `TLockout enabled.` No `Could not load goals.yml`.
**Watch for:** `Loaded 0`, any `entry #N (id '...')` message, or the plugin disabling itself.
**Report back:** the console excerpt; if it fails, the full error line plus the first 10 lines of
`plugins/TLockout/goals.yml`.
**Status:** pass (owner, build 0.1.1)

---

### T-002 — Board generation: 25 distinct goals, deterministic per seed

**Change:** `/lockout start [seed]` draws 25 weighted-unique templates onto a 5x5 board.
**Setup:** One player, any world. Goal: prove uniqueness and seed determinism.
**Steps:**
1. `/lockout start 12345`
2. `/lockout board` — count the tiles and note the 25 goal names.
3. `/lockout stop`
4. `/lockout start 12345` — open the board again and compare.
5. `/lockout start 99999` — open and compare against step 2.
**Expected:** 25 tiles, no duplicate goal, same board for `12345` both times, a visibly different
board for `99999`.
**Watch for:** fewer than 25 tiles, duplicates, an empty/incomplete board, or
`Not enough goal templates` (would mean `goals.yml` was not deployed).
**Report back:** pass/fail per step; screenshots of the two `12345` boards.
**Status:** pass — owner reported "everything works" (not itemized)

---

### T-003 — Board GUI is browsable and read-only

**Change:** 6x9 inventory GUI, 5x5 grid centred on rows 1–5 / columns 2–6, header clock, border panes.
**Setup:** A running challenge (from T-002).
**Steps:**
1. `/lockout board`
2. Hover several tiles and read the name + lore.
3. Click a tile.
4. Try to drag or shift-click items out of the GUI.
**Expected:** Header clock shows `Seed: <seed>` and `Claimed: 0/25`. Every tile shows its goal name,
its requirement and `Unclaimed`. Clicking prints the goal and `Still unclaimed.`. Nothing can be
moved or taken out of the GUI.
**Watch for:** items vanishing into the player's inventory, GUI closing on click, tiles overlapping
the border or header, missing icons (e.g. barrier/name tag fallbacks).
**Report back:** screenshot of the open board + whether any item could be extracted.
**Status:** pass — owner reported "everything works" (not itemized)

---

### T-004 — Item goal completes and claims the tile

**Change:** Polling tracker (`check-interval-ticks: 10`) scans inventory; first player to satisfy the
goal claims it.
**Setup:** Fresh world, **empty inventory** (important — item goals count items currently held, so
an existing stack can claim instantly), one player.
**Steps:**
1. `/lockout start 12345` and note a `Collect Nx <item>` tile on the board.
2. Collect that item one unit at a time, checking `/lockout board` after each pickup.
**Expected:** No claim until the final unit. On the last pickup, chat shows
`✔ <you> claimed Collect Nx <item> (1/25)`; the tile becomes your colour with `Claimed by <you>`;
the header reads `1/25`. Claim appears within about half a second.
**Watch for:** a claim firing before the count is reached, no claim at all, or the count not
incrementing in the header.
**Report back:** item and count used, the exact chat line, a screenshot of the claimed tile.
**Status:** pass — owner reported "everything works" (not itemized)

---

### T-005 — Lockout: a claimed goal cannot be claimed twice

**Change:** `ChallengeManager.tryClaim` refuses an already-claimed cell, so tiles are exclusive.
**Setup:** Two players online, same running challenge. Player B must **not** have the target item
before the test.
**Steps:**
1. Player A completes a `Collect Nx <item>` goal until the tile is claimed by A.
2. Player B now collects the same item in the required quantity.
3. Both players open `/lockout board`.
**Expected:** B produces no claim message and no score change. Tile stays A's colour, header stays
`1/25`, and both players see the same owner.
**Watch for:** a second claim message, the tile flipping to B's colour, count reaching 2/25.
**Report back:** pass/fail, plus the chat log of both players for the window.
**Status:** pass — owner reported "everything works" (not itemized)

---

### T-006 — Event goals: mob kills, taming, breeding

**Change:** `EntityDeathEvent` / `EntityTameEvent` / `EntityBreedEvent` increment per-goal counters.
**Setup:** Running challenge whose board contains at least one kill, tame and breed goal (re-roll with
another seed if needed). Counters reset on every `/lockout start`.
**Steps:**
1. For `Kill Nx <mob>`: kill that mob once, check the board, then repeat until the tile claims.
2. For `Tame a <mob>`: tame that animal.
3. For `Breed Nx <mob>`: feed two animals of that type and let them breed.
**Expected:** Kill goal claims exactly on the Nth kill (not before). Tame and breed claim on the
first matching event. Each claim posts the usual claim line.
**Watch for:** early claim on kill goals (off-by-one), taming a wolf counting toward a `Tame a Cat`
goal, a breed counting twice for the same pair, or no claim.
**Report back:** which goals you tested, kill counts at claim time, chat lines.
**Status:** pass — owner reported "everything works" (not itemized)

---

### T-007 — Biome and durability goals

**Change:** Biome goals compare the player's current biome key; durability goals match a worn item at
or below `remaining-percent`.
**Setup:** Running challenge containing a `Visit the <biome> biome` goal and a worn-equipment goal.
**Steps:**
1. Walk into the named biome (cross the border and keep moving a few blocks).
2. For the durability goal, use or damage the named item until remaining durability is at/below the
   stated percentage; check the board at full durability and again as you pass the threshold.
**Expected:** Biome tile claims shortly after entering. Durability tile does **not** claim at full
durability and does claim once at/below the percentage.
**Watch for:** claiming while standing in an adjacent biome, never claiming, claiming at full
durability, or claiming on an unrelated item of the same material.
**Report back:** biome/percent used, whether it claimed, screenshots of the relevant tiles.
**Status:** pass — owner reported "everything works" (not itemized)

---

### T-008 — `/lockout status`, `stop`, `reload`

**Change:** Admin lifecycle commands.
**Setup:** Running challenge with at least two claims; access to the server console or an OP account.
**Steps:**
1. `/lockout status`
2. `/lockout stop`, then `/lockout board`, then `/lockout status`.
3. `/lockout reload`.
4. `/lockout start 12345` again.
**Expected:** `status` prints the seed, `X/25`, and one line per claim with the claimer's name.
`stop` announces the challenge has ended; `board` then says no challenge is running. `reload`
re-reads config/goals, reports `51 templates`, and stops any running challenge without error.
**Watch for:** exceptions on reload, stale board still claimable after `stop`, count disagreeing
between `status` and the GUI.
**Report back:** console output of the reload, plus the command outputs.
**Status:** pass — owner reported "everything works" (not itemized)

---

## Not covered / known limits

- FFA mode — planned (`ideas.txt`); config refuses `mode: ffa` with a clear message.
- Team membership does not survive a server restart, and neither do claims. Everyone re-joins after a restart.
- The tracking-HUD idea (targeted goals in sidebar + boss bar) — still planned.
- Bingo-line victory is not implemented; the round ends on majority only.

---

## Slice 2 — teams and win condition (build 0.2.0)

Rebuilt claims: a claim is now attributed to a **side**, and a round ends when a side reaches
`win.goals-to-win`. Config is migrated automatically on first start (old file kept as `config.yml.old`).

### T-009 — Team assignment: join, admin override, auto-balance

**Change:** `/lockout join|leave` for players; `/lockout team set|auto` for admins.
**Setup:** 2+ players online, no round running, default config (red/blue concrete teams).
**Steps:**
1. Player A runs `/lockout join red`.
2. As admin: `/lockout team set <A> blue`.
3. `/lockout team list`.
4. `/lockout team auto`.
**Expected:** Step 1 replies `You joined Red`. Step 2 replies `Moved <A> to Blue`. `list` shows both
rosters with counts and never lists a player twice. `auto` reports how many players it balanced and
splits them across the two teams.
**Watch for:** unknown-team errors on valid ids, a player appearing on both teams, offline names.
**Report back:** the output of each command.
**Status:** pass (owner, 0.2.x)

---

### T-010 — Claims are attributed to a team and use its concrete colour

**Change:** claims store the side; tile and chat colours come from the side's concrete block.
**Setup:** two players on opposite teams, round running.
**Steps:**
1. Both players open `/lockout board`.
2. Player on Red claims a goal.
3. Both players re-open the board and hover the tile.
**Expected:** header shows two concrete blocks (left = first team, right = second) each reading
`0 / 13 goals`. Chat reads `✔ <player> claimed <goal> for Red (1/25)`. The tile's name and
`Claimed by <player>` use the red concrete colour, with a `Red` line in the lore, and the left
counter updates.
**Watch for:** tiles staying white, counters not updating, the wrong team's colour, missing concrete icon.
**Report back:** screenshot of the board after the claim + the chat line.
**Status:** pass (owner, 0.2.x)

---

### T-011 — Reaching the target ends the round and freezes the board

**Change:** a round finishes when a side reaches `win.goals-to-win`; board stays viewable.
**Setup:** to test quickly, set `win.goals-to-win: 2` in `config.yml`, then `/lockout reload`
(this stops any round), then `/lockout start`. Two players, opposite teams.
**Steps:**
1. Have Red claim two goals.
2. Open `/lockout board` and run `/lockout status`.
**Expected:** on the second claim the server announces `Red wins the lockout! Red 2 - 0 Blue`.
The board header reads `Winner: Red`; `status` prints the winner line.
**Watch for:** no announcement, the round continuing, a wrong score line, a stack trace.
**Report back:** the chat log around the win + a screenshot of the header.
**Status:** pass (owner, 0.2.x)

---

### T-012 — No claims are possible after the win

**Change:** `tryClaim` requires the `RUNNING` state.
**Setup:** continue from T-011 — Red has won, board still open.
**Steps:**
1. As Blue, complete any unclaimed goal on the board.
2. Re-open the board.
**Expected:** no claim message, the tile stays unclaimed, all counters unchanged. The board and
`/lockout status` still display normally.
**Watch for:** a late claim slipping through, counters changing after the win.
**Report back:** pass/fail + board screenshot.
**Status:** pass (owner, 0.2.x)

---

### T-013 — Start is refused for an empty team, warns on uneven teams

**Change:** `/lockout start` validates team membership before generating a board.
**Setup:** restart the server first (membership is in-memory, so everyone starts with no team).
**Steps:**
1. Nobody joined: `/lockout start`.
2. Only A joined red: `/lockout start`.
3. A and B on red, C on blue: `/lockout start`.
**Expected:** steps 1 and 2 refuse, naming the team that has no players. Step 3 starts the round and
adds `Heads up: the teams are uneven (2 vs 1).`
**Watch for:** a round starting with one team empty, the warning missing, wrong counts.
**Report back:** the output of all three attempts.
**Status:** pass (owner, 0.2.x)

---

### T-014 — Team changes are locked while a round is running

**Change:** `SideManager.assign` refuses when the round has locked membership.
**Setup:** round running with both teams populated.
**Steps:**
1. `/lockout join <the other team>`
2. `/lockout leave`
3. As admin: `/lockout team set <player> <the other team>`
**Expected:** all three refuse with `Teams are locked while a round is running.` Rosters unchanged,
and claims keep going to the original side.
**Watch for:** a player moving mid-round, later claims attributed to the wrong side.
**Report back:** the output of the three commands.
**Status:** pass (owner, 0.2.x)

---

### T-015 — Teammates cannot damage each other during a round

**Change:** damage between same-side players is cancelled while running (`teams.friendly-fire: false`).
**Setup:** two players on Red, one on Blue, round running.
**Steps:**
1. Red A hits Red B with a sword.
2. Red A shoots Red B with a bow.
3. Red A hits Blue C.
4. `/lockout stop`, then repeat step 1.
**Expected:** steps 1 and 2 deal no damage; step 3 damages normally; after `stop`, step 1 damages
normally again.
**Watch for:** arrow damage still applying, opponents also protected, friendly fire staying disabled
after the round ends.
**Report back:** pass/fail per step (health values before/after).
**Status:** pass (owner, 0.2.x)

---

## Regression checks after the teams change

- **T-003** board GUI: header now has two concrete counters either side of the clock, and the info
  item shows `First to 13 wins`.
- **T-004 / T-005** claims: chat lines now end with `for <Team>`; the lockout rule itself is unchanged.
- **T-008** status: now prints a per-team score line and `(Team)` after each claim.

---

## Slice 3 — board compass (build 0.2.1)

### T-016 — The board compass is handed out and opens the board

**Change:** a tagged, glint-overridden `Board Compass` is given to everyone when a round starts and
to players joining mid-round. Right-clicking it (main or off hand) opens the board GUI.
**Setup:** two players, both on a team, no round running.
**Steps:**
1. `/lockout start`
2. Look at the last hotbar slot.
3. Right-click the compass on air.
4. Right-click it while pointing at a block.
5. Close the board, then right-click a **plain** compass (one you crafted yourself).
6. `/lockout stop`, then look at the slot again.
**Expected:** step 2 shows an enchanted-looking `Board Compass` with lore
`Right-click to open the board`. Steps 3 and 4 open the board. Step 5 does nothing — a normal
compass must not open the board. Step 6 removes the compass from the inventory.
**Watch for:** no item handed out, a compass without the glint, a plain compass opening the board,
the item surviving `stop`, or the click also using/placing something.
**Report back:** a screenshot of the compass item, plus pass/fail per step.
**Status:** pass (owner, 0.2.x)

---

### T-017 — The compass cannot be lost and reaches late joiners

**Change:** dropping it is cancelled, it is stripped from death drops, and it is re-given on respawn
and on join while a round is active.
**Setup:** a round running, one player.
**Steps:**
1. Hold the compass and press Q.
2. Die (e.g. `/kill`), then respawn.
3. Have a second player connect while the round is still running.
**Expected:** step 1 refuses the drop and the item stays. Step 2 leaves a compass in the inventory
after respawn rather than on the ground. Step 3 hands the new player a compass immediately.
**Watch for:** the compass lying on the ground, a missing compass after respawn, two stacked
compasses, or a late joiner with none.
**Report back:** pass/fail per step (screenshot of the death drops if step 2 fails).
**Status:** pass (owner, 0.2.x)

---

## Slice 4 — ticked-off tiles (build 0.2.2)

### T-018 — Claimed goals are ticked off on the board

**Change:** a claimed tile no longer shows the goal's icon; it renders the claiming side's concrete
block with an enchantment glint, and the goal name is struck through.
**Setup:** round running, two players on opposite teams, board open.
**Steps:**
1. Have the Red player claim a goal.
2. Re-open the board and hover the claimed tile.
3. Have the Blue player claim a different goal.
4. Hover a still-unclaimed tile.
**Expected:** the Red tile is an enchanted RED concrete block, its name struck through in red, its
lore starting with `✔` followed by the requirement, `Claimed by <player>` and `Red`. The Blue tile is
the blue equivalent. Step 4 still shows the goal's own item icon and `Unclaimed`.
**Watch for:** claimed tiles still showing the old icon, no glint on the concrete, names not struck
through, wrong concrete colour, or unclaimed tiles changing appearance.
**Report back:** a screenshot showing at least one Red and one Blue claim side by side.
**Status:** pass (owner, 0.2.2)

---

## Slice 5 — live board (build 0.2.3)

### T-019 — An open board updates live when someone else claims

**Change:** every successful claim re-renders the open board in place for all viewers, and ending a
round closes viewers' boards instead of leaving them stale.
**Setup:** two players on opposite teams, a round running, both with the board open.
**Steps:**
1. Player A keeps the board open and does not click anything.
2. Player B claims a goal.
3. Without closing the board, watch A's screen.
4. With A's board still open, run `/lockout stop`.
**Expected:** within roughly half a second of B's claim, A's board shows that tile as B's team's
glinted concrete block, both team counters and the `Claimed: X/25` line update, and the header does
not reset — no reopening needed and the cursor is not interrupted. In step 4 the board closes.
**Watch for:** the tile only changing after a reopen, the GUI closing and reopening on every claim,
layout shifts, a frozen inventory, or lag with several viewers.
**Report back:** pass/fail, plus whether the update felt instant or delayed.
**Status:** pass (owner, 0.2.3)

---

## Slice 6 — targeting HUD (build 0.3.0)

Targets are **shared per team**: any member can add or remove one, and everyone on that side sees the
same ranked list. Max 5. Claimed goals drop off the list automatically.

### T-020 — Right-clicking a goal tracks it

**Change:** right-click toggles a board tile in the side's target list; left-click still shows details.
**Setup:** round running, player on a team, board open via the compass.
**Steps:**
1. Right-click an unclaimed tile.
2. Look at that tile again.
3. Right-click it a second time.
4. Right-click a tile that is already claimed (if the board has one).
**Expected:** step 1 prints `Tracking it. Shared with your team.` Step 2 shows a gold `★` before the
goal name plus `★ Tracked by your team` in the lore, and the goal appears in your sidebar.
Step 3 prints `No longer tracking that goal.` and the star clears. Step 4 says the goal is already
claimed.
**Watch for:** left-click also toggling, no feedback message, the tile only updating after a reopen,
or tracked goals behaving differently when claimed.
**Report back:** pass/fail + a screenshot of a tracked tile next to the sidebar.
**Status:** pass (owner, 0.3.0)

---

### T-021 — Sidebar shows the score and the ranked targets

**Change:** a per-player sidebar lists both team scores, then the side's targets ordered by
completion, with score numbers hidden.
**Setup:** round running, at least two tracked goals with different progress (one nearly complete,
one untouched).
**Steps:**
1. Track both goals.
2. Read the sidebar, top to bottom.
3. Collect a few items so progress changes, and watch it update.
**Expected:** the top line reads like `Red 3 - 2 Blue` in the team colours. The more-complete goal is
listed first, formatted like `1. 7/8 Iron Ingot` — green when nearly done, yellow mid-way, grey
untouched. No red score numbers appear on the right. The bottom line reads `Claimed X/25`.
**Watch for:** red numbers still visible, wrong ordering, missing or duplicated lines, flicker every
half second.
**Report back:** a screenshot of the sidebar.
**Status:** pass (owner, 0.3.0)

---

### T-022 — Boss bar tracks the top goal and advances

**Change:** the boss bar renders the highest-ranked target with a live progress bar.
**Setup:** round running, one nearly-complete goal tracked and one fresh goal tracked.
**Steps:**
1. Watch the boss bar while you finish the nearly-complete goal.
2. After it is claimed, keep watching.
**Expected:** the bar shows `7/8 <goal>` with the fill near full, in your team's colour. On the
claim, the bar switches to the next tracked goal, and the claimed goal disappears from the sidebar.
**Watch for:** the bar sticking on the finished goal, the wrong goal shown, the bar vanishing, or the
fill not moving as progress changes.
**Report back:** pass/fail, the bar text before and after the claim.
**Status:** pass (owner, 0.3.0)

---

### T-023 — Targets are shared by the whole team

**Change:** targets live on the side, not the player.
**Setup:** two players on the same team, round running.
**Steps:**
1. Player A tracks a goal by right-clicking it.
2. Player B checks their sidebar and boss bar without doing anything.
3. Player B tracks a second goal.
4. Between you, try to track a sixth goal.
**Expected:** step 2 shows the same goal on B's HUD. Step 3 adds it for both players. Step 4 refuses
the sixth with a message that the team already tracks 5.
**Watch for:** B not seeing A's target, lists diverging, or more than 5 tracked.
**Report back:** pass/fail + both players' sidebar screenshots.
**Status:** pass (owner, 0.3.0)

---

### T-024 — Progress is summed across the team

**Change:** team progress adds each member's progress together, capped at the target.
**Setup:** two players on the same team; the board contains `Collect 8x <item>` (or any count goal).
**Steps:**
1. Each player collects half of the required item (4 each for an 8-item goal), so neither alone has 8.
2. Read the HUD, then open the board.
**Expected:** the HUD reads `8/8` and the goal is claimed for the team.
**Watch for:** the HUD showing only one player's 4/8, or the claim never firing.
**Report back:** pass/fail + HUD screenshot and the claim line.
**Status:** pass (owner, 0.3.0)

---

## Slice 7 — goal-model foundation (build 0.3.1)

### T-025 — Existing 51-template catalogue still works after the refactor

**Change:** goals are now parsed and validated by per-type trackers; the board stores resolved goals;
`goals.yml` moved to version 2 with an automatic backup-and-replace upgrade. No new goal type was
added in this build.
**Setup:** build 0.3.1, two players on opposite teams, and the `plugins/TLockout/goals.yml` left over
from 0.3.0 (no `goals-version` line). Default board, 5x5.
**Steps:**
1. Start the server and watch the first ~20 console lines.
2. Check `plugins/TLockout/` for `goals.yml.v1.bak`, and check the new `goals.yml` starts with
   `goals-version: 2`.
3. Run `/lockout start 12345`, then `/lockout board`.
4. Complete one item goal (e.g. collect iron) and one kill goal (e.g. kill a zombie), and watch the
   claim line, the tile, and the sidebar/boss bar.
5. Run `/lockout status`.
**Expected:** console shows `Upgraded goals.yml to version 2 (previous version saved as
goals.yml.v1.bak).`, then `Loaded 51 goal templates.`, then `TLockout enabled`. The board has 25
tiles with the same names, requirement lore and icons as before. Both goals claim normally and score
updates in chat, board and HUD. `/lockout status` lists the claims.
**Watch for:** a goals.yml load error, a changed board for the same seed, requirement text that no
longer mentions the right item/entity, a claim that never fires, missing HUD targets, or a console
stack trace.
**Report back:** the console excerpt around startup, a board screenshot, and the `/lockout status`
output.
**Status:** pass (owner, 0.3.x)

---

### T-026 — A malformed goals.yml is rejected with a precise error

**Change:** tracker validation now runs per entry and reports the goal id and field.
**Setup:** build 0.3.1; stop the server. In `plugins/TLockout/goals.yml`, change the first template's
`item: IRON_INGOT` to `item: NOT_A_REAL_ITEM` while leaving `goals-version: 2` in place.
**Steps:**
1. Restart the server.
2. Watch the console for the disable/load error.
3. Run `/lockout` and `/lockout start` in game.
**Expected:** the plugin refuses to enable with a message naming the entry and id, e.g.
`Could not load goals.yml: goals.yml entry #1 (id 'iron_ingot'): unknown item 'NOT_A_REAL_ITEM'`.
No half-loaded board, no `/lockout` commands accepted.
**Watch for:** a generic null-pointer or YAML stack trace, the plugin enabling with a broken goal, or
`goals.yml` being overwritten despite the parse error.
**Report back:** the full console error block. Restore the original `item` value afterwards.
**Status:** pass (owner, 0.3.x)

---

### T-027 — The same seed produces the same board across restarts

**Change:** `ResolvedGoal` is now what the board stores; the weighted draw and template order are
unchanged, and no template in this build uses variants.
**Setup:** build 0.3.1, two teams, default board.
**Steps:**
1. `/lockout start 777`, record all 25 tile names and their positions (screenshot).
2. `/lockout stop`, stop the server, restart it.
3. `/lockout start 777` and open the board again.
**Expected:** the second board is identical to the first, tile for tile.
**Watch for:** a shuffled board, a duplicate goal, or fewer than 25 tiles.
**Report back:** both board screenshots (or a written list of the 25 names in order).
**Status:** pass (owner, 0.3.x)

---

### T-028 — A round can be started with an empty team for testing

**Change:** `/lockout start [seed] [--allow-empty]` now accepts `--allow-empty`, which bypasses the
"every team needs a player" check. Normal starts still refuse empty teams. The command warns in chat
and the console that it is a testing round; empty sides cannot claim goals.
**Setup:** build 0.3.2; one player assigned to Red; Blue has no players; default 5x5 board.
**Steps:**
1. Run `/lockout start` with no flag. Confirm it still refuses and names Blue.
2. Run `/lockout start 4242 --allow-empty`.
3. Open `/lockout board` and check the Blue counter at the top.
4. As the Red player, claim one goal (e.g. collect iron) and watch chat, the tile and the HUD.
5. `/lockout status`.
6. `/lockout stop`, then with both teams still empty run `/lockout start 4243 --allow-empty`.
**Expected:** step 1 refuses. Step 2 starts the round with seed 4242 and prints the testing warning
in chat and server log. Step 3 shows Blue with 0 players and 0 goals. Step 4 claims normally for Red
and Blue remains at 0. Step 5 lists Blue `0 goals, 0 players`. Step 6 starts and opens a board with
no players, with no errors.
**Watch for:** the flag not being recognised (seed parse error), a null-pointer when the check task
runs, the board/HUD omitting the empty side, a win triggering incorrectly, or an empty team letting
the other side claim twice.
**Report back:** console excerpt around the start, the chat warning, a board screenshot and the
`/lockout status` output.
**Status:** pass (owner, 0.3.3)

---

### T-029 — Team-combined progress completes a goal

**Change:** claim detection now uses the side's combined `teamProgress` (the same total the HUD
shows) instead of each player's own progress. The claim is credited to the member with the largest
personal contribution.
**Setup:** build 0.3.3; three players — two on Red, one on Blue; a running board that contains a
`Collect 8x <item>` goal (restart with another seed if needed). Both Red players online.
**Steps:**
1. Note the 8x item goal. Have Red player A collect 3 of the item and Red player B collect 5.
   Neither player alone has 8.
2. Watch the Red sidebar/boss bar and the board tile without reopening the board.
3. Confirm the claim line, the tile colour and Red's score.
4. If the board also contains a `Kill 5x <mob>` goal, have A kill 2 and B kill 3 and confirm it
   claims the same way.
**Expected:** step 2 shows the goal at `8/8` (and `5/5` for the kill goal) for Red. Within roughly
half a second the goal claims for Red; the chat line credits the player who contributed the larger
share (B with 5 items); the tile becomes Red concrete; Red's score increases by one; there is no
second claim. Blue's counter does not change.
**Watch for:** the HUD reaching 8/8 but no claim (the behaviour this fixes), a claim at 7/8, double
claiming, the claim credited to the player with the smaller share, or an event-count goal not
summing across teammates.
**Report back:** HUD/board screenshot before and after the claim, the claim chat line, and the
`/lockout status` output.
**Status:** pass (owner, 0.3.3)

---

## Slice 8 — Fabric catalogue part 1 (build 0.4.0)

### T-030 — The goals catalogue upgrades to 100 templates

**Change:** `goals.yml` is version 3 and ships 100 templates (33 single-item obtains, 64 arrows,
three colour-variant templates, 10 specific mob kills, 2 dimension goals, plus the original 51).
An older v2 file is backed up and replaced.
**Setup:** build 0.4.0; the `plugins/TLockout/goals.yml` left from 0.3.3 (`goals-version: 2`).
**Steps:**
1. Start the server and watch the first ~20 console lines.
2. Check `plugins/TLockout/` for `goals.yml.v2.bak`, and confirm the new `goals.yml` starts with
   `goals-version: 3`.
3. Run `/lockout start 12345`, then `/lockout board`.
4. Run `/lockout status`.
**Expected:** console shows `Upgraded goals.yml to version 3 (previous version saved as
goals.yml.v2.bak).`, then `Loaded 100 goal templates.`, then `TLockout enabled`. The board has 25
tiles and `/lockout status` works. The board for seed 12345 is expected to differ from 0.3.3 because
the catalogue changed.
**Watch for:** a version or unknown-item load error, a duplicate-id error, fewer than 100 templates,
the old file not being replaced, or any console stack trace.
**Report back:** startup console excerpt, the backup filename, and a board screenshot.
**Status:** pass (owner, 0.4.0)

---

### T-031 — Single-item "Obtain X" goals claim

**Change:** the Fabric single-item obtain goals are in the catalogue as `item` templates with an
explicit `Obtain X` display name.
**Setup:** build 0.4.0; one player on any team; a board containing a single-item obtain tile
(e.g. `Obtain Clock`). Restart with another seed if none appears.
**Steps:**
1. `/lockout start <seed>`, open the board, and find an `Obtain X` tile; note its item.
2. Obtain one of that item — `/give @s <item>` is fine, or collect it legitimately.
3. Wait at least one check cycle (about half a second) and watch chat, the tile and the HUD.
**Expected:** the tile reads `Obtain <item>` and its lore `1x <item>`. Possessing one claims the
goal for the team; chat shows the claim, the tile becomes the team's concrete block, and the score
increases by one.
**Watch for:** the tile falling back to `Collect 1x ...`, the item not being counted, the claim
firing on the wrong item, or a claim without the item.
**Report back:** the tile before and after, and the claim chat line.
**Status:** pass (owner, 0.4.0)

---

### T-032 — Colour-variant goals resolve to one concrete colour

**Change:** coloured wool, coloured concrete and coloured glazed terracotta are single templates
whose colour is chosen when the board is generated.
**Setup:** build 0.4.0; one player; a board containing `Obtain 64 <Colour> Wool`,
`Obtain 64 <Colour> Concrete` or `Obtain <Colour> Glazed Terracotta`. The variant templates have a
low weight, so a few seeds may be needed.
**Steps:**
1. Start a board and find one of those tiles; note the colour in its name.
2. Check that the tile icon is the matching coloured item (not a generic/placeholder icon).
3. Obtain the required item in that exact colour: 64 for wool/concrete, 1 for glazed terracotta
   (`/give @s <colour>_wool 64`, etc.).
4. Confirm the claim.
**Expected:** the tile name shows the resolved colour (e.g. `Obtain 64 Light Blue Wool`), the icon is
the matching item, the lore uses the same colour, and obtaining it claims for the team.
**Watch for:** a literal `{color}` in the name, the wrong colour icon or requirement, a claim from
the wrong colour, or the 64 count being ignored.
**Report back:** a screenshot of the tile and the claim line; include the seed if it failed.
**Status:** pass (owner, 0.4.0)

---

### T-033 — Enter Nether / Enter The End claims

**Change:** new `dimension` goal type; the tracker polls the player's world environment.
**Setup:** build 0.4.0; one player; a board containing `Enter Nether` or `Enter The End`. Restart
with another seed if neither appears.
**Steps:**
1. Start a board and find the dimension tile.
2. Enter that dimension — use a portal, or for speed
   `/execute in minecraft:the_nether run tp @s 0 64 0` (use `minecraft:the_end` for the End).
3. Wait one check cycle and watch chat, the tile and the HUD.
**Expected:** the goal claims within about half a second of arriving, for the team; the tile becomes
the team's concrete block and the score increases.
**Watch for:** the goal claiming while still in the overworld, never claiming, or the wrong
dimension satisfying it.
**Report back:** the claim line, the tile, and the command or portal used.
**Status:** pass (owner, 0.4.0)

---

### T-034 — Specific-mob kill goals claim on one kill

**Change:** ten Fabric specific-mob goals are in the catalogue as `mob_kill` templates with count 1.
**Setup:** build 0.4.0; one player; a board containing a specific kill tile (e.g. `Kill Bat`).
Restart with another seed if none appears.
**Steps:**
1. Start a board and find a specific-mob kill tile; note the mob.
2. Kill one of that mob — `/summon minecraft:bat` next to you and hit it is fine.
3. Wait one check cycle and watch chat, the tile and the HUD.
**Expected:** the tile reads `Kill <mob>` with lore `1x <mob>`; one kill claims for the team, chat
shows the claim, the tile becomes the team's concrete block and the score increases by one.
**Watch for:** no claim (counter-key mismatch), a different mob counting, more than one kill being
required, or the wrong spawn-egg icon.
**Report back:** the claim line, the tile, and the mob/command used.
**Status:** pass (owner, 0.4.0)

---

## Slice 9 — event families (build 0.4.1)

### T-035 — Consume/drink and unique-food goals

**Change:** `PlayerItemConsumeEvent` now feeds specific consume goals and a side-wide unique-food
set. Cake slices are tracked through the vanilla cake-slices statistic; water bottles are
distinguished from other potions by potion type.
**Setup:** build 0.4.1; two Red players and one Blue player; a board containing a specific consume
tile (`Eat Cookie`, `Drink Water Bottle`, etc.) and an `Eat N Unique Food` tile. A few seeds may be
needed for the unique-food tile.
**Steps:**
1. Start a board and find the specific consume tile; consume that item and wait one check cycle.
2. If the tile is `Drink Water Bottle`, drink a different potion first (it should not claim), then
   drink a water bottle.
3. On the `Eat N Unique Food` tile, have both Red players eat the same food. Check the HUD count.
4. Eat distinct foods (one per type) until the target; check the claim.
5. If an `Eat a slice of Cake` tile is present, eat a cake slice.
**Expected:** the specific item claims only for the right item; a non-water potion does not satisfy
the water-bottle goal. Repeating one food across two players counts once; distinct foods increase
the shared count to the target and claim. A cake slice claims the cake goal.
**Watch for:** the same food counted twice across teammates, potions/milk counting as unique food,
water bottle not distinguished, cake not counting, or a claim with no consumption.
**Report back:** HUD counts before/after, the claim lines, and which items were consumed.
**Status:** pass (owner, 0.4.1)

---

### T-036 — Advancement goals, with recipes excluded

**Change:** `PlayerAdvancementDoneEvent` feeds a side-wide advancement set; recipe unlocks and
display-less advancements are ignored. Specific/any-of advancement goals read the same set.
**Setup:** build 0.4.1; use a fresh world or run `/advancement revoke @s everything` for each player
before starting; two Red players; a board with `Get 10 Advancements` and a specific advancement
tile (e.g. `Trade with a Villager`). A few seeds may be needed.
**Steps:**
1. Start a round; the advancement-count HUD should read `0/10`.
2. Unlock a crafting recipe (e.g. craft planks) and check that the count stays `0/10`.
3. Earn one display advancement (e.g. `/advancement grant @s only minecraft:adventure/trade`) and
   check the count becomes `1/10`; if its tile is on the board, confirm the claim.
4. Earn enough advancements to reach 10, with contributions split across the two Red players.
5. If a spyglass/goal with multiple advancement keys is on the board, grant one of its keys.
**Expected:** recipes never increase the count; each displayed advancement increases the shared
count once; reaching 10 claims; the specific tile claims when its key is earned; any-of completes
on one key.
**Watch for:** recipe unlocks counting, the count not shared across teammates, a specific key that
never matches, or the count needing more than 10.
**Report back:** the advancement HUD before/after, the granted keys, and the claim lines.
**Status:** pass (owner, 0.4.1)

---

### T-037 — Status-effect goals and milk removal

**Change:** `EntityPotionEffectEvent` feeds specific-effect goals and milk removal; "N status
effects at once" polls active effects and uses the best single player, not a team sum.
**Setup:** build 0.4.1; two Red players; a board containing a specific effect (e.g. `Get
Absorption`), `Get 3 Status Effects at once`, and `Remove Effect using Milk Bucket`. A few seeds
may be needed.
**Steps:**
1. Give player A Absorption (`/effect give @s minecraft:absorption 30 0`); confirm the specific
   goal claims.
2. Give A two effects and B two different effects; check the `3 at once` HUD.
3. Give A a third effect; confirm the goal claims and is credited to A.
4. Give A an effect, then drink a milk bucket; confirm the milk goal claims and the effect clears.
**Expected:** the specific effect claims, the two-players-with-two-each case does **not** claim
(shared progress is the best single player), one player with three effects claims, and milk with an
active effect claims the milk goal.
**Watch for:** a false 3-effect claim from 2 + 2 across teammates, the effect event not firing,
milk not counting, or the effect count continuing after milk.
**Report back:** the HUD value for step 2, the claim lines, and the effects/commands used.
**Status:** pass (owner, 0.4.1)

---

## Slice 10a — deaths, damage, movement, state (build 0.5.0)

### T-038 — Death-cause goals claim only for the matching cause

**Change:** `PlayerDeathEvent` damage type/entity matching for 13 Fabric death goals; the vine goal
also checks for vine blocks near the death point.
**Setup:** build 0.5.0; one player; a board containing one or more death tiles. Use `/gamerule
keepInventory true` if you do not want to recover items after each test death.
**Steps:**
1. Start a board and find a death tile; note its cause.
2. Die using a matching `/damage` command, then wait one check cycle. Examples:
   - `/damage @s 100 minecraft:cactus`, `... minecraft:freeze`, `... minecraft:falling_anvil`,
     `... minecraft:bad_respawn_point`, `... minecraft:fireworks`,
     `... minecraft:falling_stalactite`, `... minecraft:sweet_berry_bush`, `... minecraft:magic`
   - Bee: summon a bee, then `/damage @s 100 minecraft:sting by @e[type=bee,limit=1]`
   - Iron Golem / Polar Bear: summon one, then
     `/damage @s 100 minecraft:mob_attack by @e[type=iron_golem,limit=1]`
   - TNT Minecart: summon one, then
     `/damage @s 100 minecraft:explosion by @e[type=tnt_minecart,limit=1]`
   - Vines: stand next to vines and `/damage @s 100 minecraft:fall`
3. If another death tile is on the board, confirm it is still unclaimed.
**Expected:** only the matching death goal claims; an unrelated death cause leaves it unclaimed.
The vine condition requires fall damage while vine blocks are nearby.
**Watch for:** every death tile claiming from one death, entity goals never matching, the vine goal
matching anywhere, or the TNT minecart death not being attributed.
**Report back:** each death cause used, the death tile(s) before/after, and the claim lines.
**Status:** pass (owner, 0.5.0)

---

### T-039 — Take 200 / Deal 400 damage totals

**Change:** team-wide double counters for damage taken and dealt.
**Setup:** build 0.5.0; two Red players; a board containing `Take 200 damage` and/or
`Deal 400 damage`. Start with full health and no keep-inventory concerns.
**Steps:**
1. For `Take 200 damage`, use `/damage @s 100 minecraft:generic` twice (dying is fine; the counter
   persists for the round), or split the damage between teammates.
2. For `Deal 400 damage`, summon zombies and use
   `/damage @e[type=zombie,limit=1] 100 minecraft:player_attack by @s` four times, or hit mobs
   normally.
3. Watch the HUD after each hit and confirm the claim.
**Expected:** the HUD counts up to 200/200 or 400/400 and claims once; both teammates' damage adds
to the same team total; a single hit is counted once.
**Watch for:** one hit counted twice, counters resetting on death, the HUD skipping values, or a
claim before the target.
**Report back:** HUD values after each step, the claim lines, and the commands used.
**Status:** pass (owner, 0.5.0)

---

### T-040 — XP, position, hunger and riding goals

**Change:** polled state trackers for XP levels, height limit, bedrock, Nether roof, empty hunger
and riding (pig needs a carrot on a stick).
**Setup:** build 0.5.0; one player; a board containing these goals. Start at XP 0, full hunger, on
the ground, not mounted, in the overworld.
**Steps:**
1. XP: `/xp add @s 15 levels` (and 30 if that tile is present).
2. Height limit: `/tp @s ~ 320 ~` in the Overworld and stand on a block at that height.
3. Bedrock: `/tp @s ~ -60 ~` and stand on bedrock.
4. Nether roof: `/execute in minecraft:the_nether run tp @s 0 128 0`.
5. Hunger: `/effect give @s minecraft:hunger 30 10` until the food bar is empty.
6. Riding: summon a horse/minecart/pig and mount it (`/ride @s mount @e[...]`); for the pig, hold a
   carrot on a stick.
**Expected:** each tile claims when its condition is met, and only then; the pig tile does not
claim while mounted without a carrot on a stick.
**Watch for:** pre-existing XP/hunger/mount state auto-claiming, position goals matching the wrong
dimension, or riding a chest minecart not counting as a minecart.
**Report back:** which goal claimed at each step, plus a board/HUD screenshot.
**Status:** pass (owner, 0.5.0)

---

### T-041 — Sprint 1km counts during the round

**Change:** sprint distance is accumulated from `SPRINT_ONE_CM` statistic deltas and displayed in
metres; it is team-shared.
**Setup:** build 0.5.0; one or two Red players; a board containing `Sprint 1km`. The HUD should
start at `0/1000`.
**Steps:**
1. Start a board and confirm the sprint HUD starts at 0.
2. Sprint 1 km (a Speed effect makes this faster); with two players, split the distance.
3. Watch the HUD and confirm the claim.
**Expected:** the HUD counts up in metres and claims at 1000/1000; pre-round sprint statistics are
not included; both teammates' distance adds to the team total.
**Watch for:** the HUD showing `100000`, distance from before the round counting, only one player's
distance counting, or no claim at 1000.
**Report back:** the HUD before/after, the claim line, and how the distance was travelled.
**Status:** fail (owner, 0.5.0) — sprinting did not increase the HUD; the statistic event did not
fire for movement distance. Fixed in 0.5.1, retest as T-041b.

---

### T-041b — Sprint 1km, polled from the statistic (retest of T-041)

**Change:** sprint distance is now polled each check cycle from `SPRINT_ONE_CM` minus a per-round
baseline, instead of relying on `PlayerStatisticIncrementEvent` (which does not fire reliably for
movement statistics). The baseline is recorded when the round starts, or when a player is first
seen mid-round.
**Setup:** build 0.5.1; one or two Red players; a board containing `Sprint 1km`. Vanilla sprint
statistics may be non-zero; the HUD should still start at `0/1000`.
**Steps:**
1. Start a board and confirm the sprint HUD starts at 0.
2. Sprint roughly 200 m; check the HUD rises.
3. Sprint the rest of the 1 km; confirm the claim.
4. With two players, split another round's distance between them and confirm the team total claims.
**Expected:** the HUD counts up in metres and claims at 1000/1000. Distance from before the round
does not count; both teammates' distance adds to the same total.
**Watch for:** the HUD staying at 0, showing `100000`, counting pre-round distance, only one
player's distance counting, or no claim at 1000.
**Report back:** the HUD before/after, the claim line, and how the distance was travelled.
**Status:** pass (owner, 0.5.1)


---

## Clean-match auto-reset + Slice 10b — armor and item sets (build 0.6.0)

### T-042 — Auto-reset clears player state on start and can be toggled

**Change:** `/lockout start` now resets every participating player's inventory, armor, offhand,
cursor, active potion effects, XP, hunger/saturation, health/fire/freeze/air, mounted vehicle,
advancements and untyped statistics before the round begins (before giving the Board Compass).
Controlled by `match.auto-reset` (`config-version: 2`, default `true`) and toggled live via
`/lockout autoreset [on|off|toggle]`. `goals.yml` also upgrades to version 6 (194 templates).
**Setup:** Build `0.6.0`; existing `config.yml` (v1) and `goals.yml` (v5) in `plugins/TLockout/`;
one player on Red (plus optionally a second player not on any team as a spectator). Give the Red
player items in inventory, armor and offhand, a potion effect (`/effect give @s speed 60 0`), XP
(`/xp add @s 20 levels`), missing hunger, an advancement, and mount a horse or minecart.
**Steps:**
1. Start the server and check the startup console lines and `plugins/TLockout/`.
2. Run `/lockout autoreset` and verify it reports `ON`.
3. Run `/lockout start 1001 --allow-empty`.
4. Inspect the Red player's inventory, armor, offhand, effects, XP, hunger, health, vehicle,
   advancements and sprint statistic (e.g. `Sprint 1km` starts at `0/1000`). Confirm the Board
   Compass is still present in slot 8. If an unassigned player is online, confirm their state was
   not wiped.
5. Run `/lockout stop`, then `/lockout autoreset off` (and check `/lockout autoreset` reports `OFF`).
6. Give the Red player an iron ingot and 15 XP levels, then run `/lockout start 1002 --allow-empty`.
7. Confirm the iron ingot and XP levels are kept when auto-reset is `OFF`.
8. Run `/lockout stop` and `/lockout autoreset toggle`; verify it switches back to `ON`.
**Expected:** Startup backs up `config.yml` to `config.yml.old` (upgrading to v2) and `goals.yml`
to `goals.yml.v5.bak` (upgrading to v6) and logs `Loaded 194 goal templates.`. With auto-reset
`ON`, `/lockout start` clears all participating players' state, dismounts them, revokes
advancements, resets untyped stats and then hands out the Board Compass; chat notes
`(players reset)`. With auto-reset `OFF`, player state is preserved and chat notes
`(auto-reset off)`.
**Watch for:** Board Compass wiped after being given, non-team observers wiped, armor/offhand or
advancements left behind, console stack traces during statistic or advancement revocation, or
`config.yml`/`goals.yml` failing to upgrade.
**Report back:** startup console excerpt, pass/fail for both `ON` and `OFF` starts, and any
console errors.
**Status:** pass (owner, 0.6.0)

---

### T-043 — Full armor sets and chain armor piece (best single player)

**Change:** Added `armor_set` (full Leather, Golden, Iron, Diamond) and `armor_piece` (Chain Armor
Piece) trackers. Progress counts matching worn armor slots for the best single player on the team,
never summed across teammates.
**Setup:** Build `0.6.0`; two Red players (and one Blue player, or `--allow-empty`); a board
containing at least one full armor set (e.g. `Wear Full Iron Armor`) and `Wear a Chain Armor Piece`.
Restart with another seed if needed.
**Steps:**
1. Track the full armor set goal on the HUD.
2. Have Red player A equip helmet + chestplate (2 pieces) and Red player B equip leggings + boots
   (the other 2 pieces). Wait one check cycle and read the HUD.
3. Have Red player A equip all 4 pieces of that set at once and wait one check cycle.
4. For `Wear a Chain Armor Piece`, equip any one chainmail piece (e.g. `/give @s chainmail_boots`)
   and confirm the claim.
**Expected:** In step 2, the HUD reads `2/4` and the goal does **not** claim (split across two
players). In step 3, the HUD reaches `4/4` and claims for Red, credited to A. In step 4, equipping
any single chainmail armor piece claims the goal.
**Watch for:** 2 + 2 across teammates falsely claiming `4/4`, holding armor in inventory (not worn)
counting, wrong tier matching, or double claims.
**Report back:** HUD value during step 2, claim lines for steps 3–4, and which armor goals appeared.
**Status:** pass (owner, 0.6.0)

---

### T-044 — Colored leather, full enchanted armor, carved pumpkin timer and wolf armor

**Change:** Added `armor_piece` with dye color matching (and tinted leather icon on the board),
`armor_set` modes `UNIQUE_DYED_LEATHER` and `ENCHANTED`, `armor_timer` for cumulative carved
pumpkin wear time (reported in seconds, best single player, reset on round start), and `wolf_armor`
(`EntityEquipmentChangedEvent` + 1-tick interact confirmation).
**Setup:** Build `0.6.0`; one Red player (two Red players optional to verify best-single-player
behavior); boards containing these goals (use multiple seeds as needed):
- `Wear <Color> <Leather Piece>`
- `Wear Full Leather Armor, in different colors`
- `Wear Full Enchanted Armor`
- `Wear a Carved Pumpkin for 5 minutes`
- `Put Wolf Armor on Wolf`
**Steps:**
1. **Colored leather piece:** Check the board tile shows the resolved color name and a matching
   dyed leather icon. Equip an undyed piece of that slot (should not claim) and a wrong-color dyed
   piece (should not claim), then dye that leather piece with the exact single dye color and wear it.
2. **Full leather in different colors:** Wear 4 undyed leather pieces (HUD should stay `0/4`).
   Dye 3 pieces in 3 different colors + 1 piece in a duplicate color (HUD should read `3/4` and not
   claim). Re-dye the 4th piece a 4th distinct color and wear all 4.
3. **Full enchanted armor:** Equip 3 enchanted armor pieces + 1 unenchanted piece (HUD `3/4`, no
   claim). Enchant and equip the 4th slot.
4. **Carved pumpkin timer:** Track the goal, wear a `CARVED_PUMPKIN` on your head for ~15 seconds,
   take it off for ~10 seconds (confirm HUD pauses and does not reset), put it back on until it
   reaches `300/300` (or verify steady 1-second increments and that a new round resets it to `0/300`).
5. **Wolf armor:** Tame a wolf (`/summon wolf` + bones), then right-click it with `WOLF_ARMOR`
   (`/give @s wolf_armor`). Wait one check cycle.
**Expected:** Colored leather piece claims only for the exact piece and dye color; different-color
leather claims only when all 4 leather slots are dyed 4 distinct non-default colors; full enchanted
armor claims when all 4 armor slots are enchanted; pumpkin timer counts up in seconds while worn,
pauses when removed, claims at `300/300`, and resets on a new round; equipping wolf armor on your
tamed wolf claims `Put Wolf Armor on Wolf`.
**Watch for:** literal `{color}`/`{piece}` in tile names, untinted leather icon, undyed brown
leather counting as a dyed color, dye RGB mismatch failing to claim, pumpkin timer ticking while
unequipped or resetting when unequipped, or wolf armor failing to trigger.
**Report back:** pass/fail for each of the 5 sub-cases, HUD values observed, and any console logs.
**Status:** pass (owner, 0.6.0)

---

### T-045 — Item sets claim from team-wide online inventory union

**Change:** Added `item_set` tracker for all 15 Fabric item-set goals (5 tool tiers, horse armor,
minecarts, mushrooms, pumpkins, raw ore blocks, 4 unique saplings, 4 unique seeds, 6 unique buckets,
6 unique flowers, 7 unique workstations). Counts the union of distinct matching items across online
teammates' inventories.
**Setup:** Build `0.6.0`; two Red players online; a board containing one "obtain all" set (e.g.
`Obtain all Wooden Tools`) and/or one "obtain N unique" set (e.g. `Obtain 4 Unique Saplings` or
`Obtain 7 Unique Workstations`). Restart with another seed if needed.
**Steps:**
1. Track an item-set goal on the HUD (e.g. `Obtain all Wooden Tools`, target `5`).
2. Give Red player A a Wooden Sword and Wooden Pickaxe, and give Red player B a Wooden Sword
   (duplicate!) and Wooden Axe. Wait one check cycle and check the HUD.
3. Give Red player B a Wooden Shovel and Wooden Hoe (so the union across A and B is all 5 tools,
   while neither player holds all 5 alone). Wait one check cycle.
4. Repeat with a partial-set goal if present (e.g. 4 unique saplings split 2 + 2 across A and B).
**Expected:** In step 2, the duplicate Wooden Sword is counted once, so the HUD reads `3/5` (not
`4/5`). In step 3, the union reaches `5/5` and claims for Red, credited to the member holding the
larger share. Partial-set goals claim at their `N` target (e.g. `4/4` saplings).
**Watch for:** duplicate items across teammates double-counting, needing one player to hold every
item, offline inventory errors, or an item from the set not being recognised.
**Report back:** HUD counts at steps 2 and 3, claim line, and which item-set goals were tested.
**Status:** pass (owner, 0.6.0)

---

### T-046 — Fill Inventory with unique items (best single player + Board Compass)

**Change:** Added `unique_inventory` tracker (`Fill Inventory with unique items`, target 36) scanning
the 36 main/hotbar storage slots for the best single player on the team. The undroppable Board
Compass is recognised as its own unique slot token so carrying a normal crafted Compass alongside it
does not deadlock at `35/36`.
**Setup:** Build `0.6.0`; two Red players online; a board containing `Fill Inventory with unique
items`. Restart with another seed if needed.
**Steps:**
1. Track `Fill Inventory with unique items` on the HUD.
2. Give Red player A 20 unique items and Red player B 20 different unique items (neither has 36).
   Wait one check cycle and check the HUD.
3. Put two stacks of the same item (e.g. two separate stacks of Cobblestone) in A's inventory while
   filling all 36 slots. Check the HUD (should be `35/36`, not claimed).
4. Replace the duplicate Cobblestone stack with a regular `COMPASS` (`/give @s compass`) so A holds
   the Board Compass plus 35 other unique items including a normal Compass. Wait one check cycle.
**Expected:** In step 2, the HUD shows the best single player's count (21/36 with the Board
Compass) and does **not** sum across teammates to claim. In step 3, duplicate stacks stay at `35/36`
and do not claim. In step 4, holding a normal Compass alongside the Board Compass reaches `36/36`
and claims for Red.
**Watch for:** teammates' inventories summing to 36, duplicate stacks claiming, armor/offhand slots
counting toward the 36 storage slots, or a normal Compass colliding with the Board Compass and
staying stuck at `35/36`.
**Report back:** HUD counts at steps 2, 3 and 4, and the claim line.
**Status:** pass (owner, 0.6.0)


---

## QoL commands — compass recovery and testing reroll (build 0.6.1)

### T-047 — `/lockout compass` recovers a missing Board Compass without duplicating

**Change:** Added `/lockout compass` (`/lo compass`), player-only under `tlockout.play`, to give
back the tagged Board Compass during an active or finished round if the player lost it (e.g.
`/clear` or chest/inventory manipulation).
**Setup:** Build `0.6.1`; one player on Red; server running normally.
**Steps:**
1. While no round is running (`IDLE`), run `/lo compass`.
2. Start a round (`/lo start 2001 --allow-empty`). Confirm you have the Board Compass.
3. Run `/lo compass` while still holding the Board Compass.
4. Clear your inventory (`/clear @s`) so the Board Compass is gone, then run `/lo compass`.
5. Right-click the newly received Board Compass to confirm it opens the Lockout board.
**Expected:** Step 1 refuses with `No challenge is running.`. Step 3 tells you `You already have a
Board Compass.` and does not create a second compass. Step 4 gives a single tagged Board Compass
(in slot 8 if empty) and confirms `Gave you a Board Compass.`. Step 5 opens the board GUI.
**Watch for:** duplicate compasses, compass granted while idle, compass missing its glint/tag so
right-clicking fails to open the board, or console errors.
**Report back:** pass/fail for steps 1, 3, 4 and 5, plus any console output.
**Status:** pass (owner, 0.6.1)

---

### T-048 — `/lockout reroll [seed]` stops and starts a fresh testing board (op-only)

**Change:** Added `/lockout reroll [seed]` (`/lo reroll [seed]`), admin/op-only under
`tlockout.admin`, equivalent to `/lo stop` followed by `/lo start [seed] --allow-empty` while
respecting the current `/lockout autoreset` toggle.
**Setup:** Build `0.6.1`; one opped player on Red (Blue empty) and optionally one non-opped player.
**Steps:**
1. If a non-opped player is available, have them run `/lo reroll` and confirm permission is denied.
2. While no round is running (`IDLE`), run `/lo reroll` as op. Open the board and note its seed and
   tiles.
3. Claim at least one goal (or track a goal on the HUD) and keep the board GUI open or closed, then
   run `/lo reroll` again with no seed.
4. Run `/lo reroll 7777` and inspect the board seed and tiles; then run `/lo reroll 7777` a second
   time and confirm the same seed produces the same fresh board with 0/25 claimed.
5. Run `/lo autoreset off`, give yourself an iron ingot, then run `/lo reroll` and confirm the
   iron ingot is kept while the board rerolls.
**Expected:** Non-ops are denied. Step 2 starts a fresh round even with Blue empty and prints the
testing-round warning. Step 3 stops the previous round (clearing claims, targets, and HUD) and
immediately starts a new round with a new seed. Step 4 uses seed `7777` deterministically with
claims reset to `0/25`. Step 5 respects `autoreset off` (and chat notes `(auto-reset off)`).
**Watch for:** non-ops allowed to run it, old claims or HUD targets leaking into the rerolled
board, duplicate Board Compasses, `Seed must be a whole number` failing on valid seeds, or console
stack traces.
**Report back:** pass/fail for each step, chat output from `/lo reroll`, and any console errors.
**Status:** pass (owner, 0.6.1)

---

### T-049 — Board Compass tooltip shows `/lo compass` recovery hint

**Change:** Added a second lore line (`Lost it? Run /lo compass`) to the Board Compass item in
`BoardCompass.create()`.
**Setup:** Build `0.6.2`; one player on Red; start or reroll a round (`/lo reroll`).
**Steps:**
1. Hover over the Board Compass in your inventory/hotbar and inspect its tooltip lines.
2. Clear your inventory (`/clear @s`), run `/lo compass`, and hover over the recovered compass.
3. Right-click the compass to confirm it still opens the Lockout board.
**Expected:** Both the round-start compass and the `/lo compass`-recovered compass display:
- `Right-click to open the board`
- `Lost it? Run /lo compass`
with no italics, glint intact, and right-clicking still opens the board.
**Watch for:** italicised lore text, formatting codes rendered literally, or right-click failing to
open the board.
**Report back:** pass/fail and a screenshot of the compass tooltip (if anything looks off).
**Status:** pass (owner, 0.6.2)


---

## Mid-game team joining (build 0.6.3)

### T-050 — Unassigned players can join a team mid-game while assigned players stay locked

**Change:** `SideManager.assign` now allows unassigned players to join a team via `/lo join <team>`
while a round is running, while keeping players already on a team locked (cannot switch or
`/lo leave`). Unassigned joiners get a clean-match reset if `autoreset` is `ON`, a fresh tracking
baseline, a Board Compass, and an immediate HUD/board update.
**Setup:** Build `0.6.3`; two players — Player A on Red, Player B unassigned (no team). Ensure
`autoreset` is `ON`. Give Player B some items (e.g. an iron ingot) and 15 XP levels before joining.
**Steps:**
1. Start a round (`/lo start 3001 --allow-empty`, or `/lo reroll 3001`). Confirm Player B's sidebar
   shows `Join a team to track goals` and Player B's items/XP were not wiped on start.
2. Have Player A (already on Red) run `/lo join blue` and `/lo leave`. Confirm both are refused.
3. Have Player B run `/lo join blue`.
4. Inspect Player B's inventory, XP, Board Compass, sidebar, boss bar, and the open board's Blue
   player count.
5. Have Player B complete an unclaimed goal (e.g. an `Obtain X` tile) and confirm it claims for
   Blue.
6. Have Player B try `/lo join red` after joining Blue. Confirm it is now refused.
7. Optional (`autoreset off` check): stop, run `/lo autoreset off`, have Player B `/lo leave`,
   start a new round, give Player B an iron ingot, then have Player B run `/lo join blue` and
   confirm the iron ingot is kept.
**Expected:** Step 2 refuses A's switch/leave. Step 3 broadcasts `<B> joined Blue mid-round (player
reset).`, wipes B's pre-join items/XP, hands B a Board Compass, updates B's sidebar/boss bar to
Blue, and increments Blue's player count on the board. Step 5 claims normally for Blue. Step 6
refuses switching once assigned. Step 7 keeps B's items when `autoreset` is `OFF`.
**Watch for:** already-assigned players able to switch teams mid-round, mid-game joiner keeping
pre-join items/XP when `autoreset` is `ON`, missing Board Compass or HUD after joining, pre-join
sprint distance counting, or console stack traces.
**Report back:** pass/fail for each step, the chat broadcast, and any console errors.
**Status:** pass (owner, 0.6.3)

---

### T-051 — Admin `/lo team set` can add or move players mid-game

**Change:** `/lo team set <player> <team>` now uses `SideManager.forceAssign`, allowing admins to
add an unassigned player or move an existing player between teams while a round is running. Moving
an existing player clears their personal round counters/sets and resets their sprint baseline so
progress from their previous team does not carry over, without wiping their inventory.
**Setup:** Build `0.6.3`; two players — Player A on Red, Player B on Blue (plus optionally an
unassigned Player C); a running round with at least one tracked count/kill goal (e.g. `Kill 5x
Zombie` or `Sprint 1km`).
**Steps:**
1. Have Player A contribute partial event progress for Red (e.g. kill 2 zombies toward `Kill 5x
   Zombie`, or sprint ~200 m). Confirm Red's HUD reflects it.
2. As op, run `/lo team set <A> red` (same team). Confirm it reports A is already on Red and does
   nothing.
3. As op, run `/lo team set <A> blue`.
4. Inspect chat, A's inventory, A's sidebar/boss bar, Blue's HUD progress on that goal, and the
   board's Red/Blue player counts.
5. Have Player A complete an unclaimed goal and confirm it claims for Blue (not Red).
6. If an unassigned Player C is online (with `autoreset on`), run `/lo team set <C> red` and
   confirm C is reset, given the Board Compass, and added to Red mid-round.
**Expected:** Step 2 is a no-op. Step 3 broadcasts `<A> was moved to Blue mid-round.`, moves A's
HUD and boss bar to Blue, keeps A's inventory, and clears A's personal counters/sprint baseline so
A's prior Red progress does not jump onto Blue. Step 5 claims for Blue. Step 6 resets C (since C
was unassigned and `autoreset` is `ON`) and updates views.
**Watch for:** A's prior mob kills/sprint carrying over onto Blue's HUD, A's inventory wiped when
switching teams, A's claims still going to Red, friendly-fire still treating A as Red, or console
errors.
**Report back:** pass/fail for each step, HUD observations before/after step 3, and any console
output.
**Status:** pass (owner, 0.6.3)


---

## Slice 11 — team kill counters and opponent goals (build 0.6.4)

### T-052 — Team kill totals, mob groups (arthropods/undead) and unique mob sets (hostile/raid)

**Change:** Added `mob_kill_total` (`Kill 100 mobs`), `mob_kill_group` (`Kill 20 Arthropods`, `Kill
30 Undead Mobs`), and `mob_kill_unique` (`Kill 7/10/13/15 Unique Hostile Mobs`, `Kill all Raid
Mobs`). `goals.yml` upgrades from v6 to v7 (209 templates). Unique mob sets are side-wide so
killing the same mob type across teammates counts once.
**Setup:** Build `0.6.4`; existing `goals.yml` (v6) in `plugins/TLockout/`; two Red players online
(plus Blue or `--allow-empty`); boards containing these goals (use `/lo reroll` as needed).
**Steps:**
1. Start the server and confirm `goals.yml` backs up to `goals.yml.v6.bak`, upgrades to version 7,
   and logs `Loaded 209 goal templates.`.
2. **Unique hostile mobs** (`Kill 7 Unique Hostile Mobs`): track it on the HUD. Have Red player A
   kill a Zombie and a Skeleton (`2/7`). Have Red player B kill a Zombie (duplicate) and a Creeper
   (new). Confirm the HUD reads `3/7` (not `4/7`), then kill 4 more distinct hostile mobs (e.g.
   Spider, Slime, Phantom, Blaze) to reach `7/7` and claim. Confirm killing a passive mob (e.g.
   Cow) does **not** increment the hostile count.
3. **Mob groups** (`Kill 20 Arthropods` or `Kill 30 Undead Mobs`): track it, split matching kills
   across A and B (e.g. Spiders + Bees for arthropods; Zombies + Skeletons + Drowned for undead),
   confirm non-matching mobs (e.g. Creepers/Cows) do not increment it, and verify claiming at target.
4. **Kill all Raid Mobs**: track it, split kills of all 6 raid mobs (`pillager`, `vindicator`,
   `ravager`, `witch`, `vex`, `evoker`) across teammates; confirm duplicate kills don't advance the
   count and killing all 6 claims (`6/6`).
5. **Kill 100 mobs**: track it, confirm killing both passive and hostile mobs increments the shared
   team counter (and killing another player does not).
**Expected:** `goals.yml` upgrades cleanly to v7 (209 templates). Unique hostile and raid sets
deduplicate across teammates and claim once at target. Arthropod/undead groups only count matching
mob types and sum across teammates. `Kill 100 mobs` counts all non-player mobs across the team.
**Watch for:** duplicate hostile/raid mobs counting twice across teammates, Slimes/Phantoms/Ghasts
not counting as hostile (`Enemy`), passive mobs counting as hostile, players counting toward `Kill
100 mobs`, or `goals.yml` failing to upgrade.
**Report back:** startup console excerpt, pass/fail for each sub-case tested, HUD counts observed,
and any console errors.
**Status:** pass (owner, 0.6.4)

---

### T-053 — Kill coloured sheep variant claims only for matching wool colour

**Change:** Added `colored_sheep_kill` (`Kill <Color> Sheep`) with 10 dyeable non-natural sheep
colours (`orange`, `magenta`, `light_blue`, `yellow`, `lime`, `cyan`, `purple`, `blue`, `green`,
`red`) and matching `{color}_WOOL` board icon.
**Setup:** Build `0.6.4`; one Red player; a board containing `Kill <Color> Sheep` (use `/lo reroll`
until it appears).
**Steps:**
1. Inspect the board tile: confirm the resolved colour name (no literal `{color}`) and matching
   coloured wool icon.
2. Summon and kill a white sheep (and a sheep dyed a different colour). Confirm the goal does
   **not** claim.
3. Summon a sheep, dye it the exact colour shown on the tile (or `/summon sheep ~ ~ ~ {Color:...}`),
   and kill it. Wait one check cycle.
**Expected:** White or wrong-colour sheep leave the goal unclaimed. Killing a sheep of the exact
resolved wool colour claims the goal for Red.
**Watch for:** literal `{color}` in the display or lore, wrong-colour wool icon, undyed white sheep
claiming, or the exact dyed sheep failing to claim.
**Report back:** which colour was rolled, pass/fail for wrong vs right colour, and the claim line.
**Status:** pass (owner, 0.6.4)

---

### T-054 — Opponent goals evaluate the opposing side and credit the beneficiary side

**Change:** Added `opponent` tracker (`Opponent dies`, `Opponent dies 3 times`, `Opponent catches
on Fire`, `Opponent takes Fall Damage`, `Opponent Team takes 100 Damage`, `Opponent touches Water`)
and a beneficiary-is-other-side claim path (`ChallengeManager.tryClaimOpponent`). Tracking an
opponent goal on the HUD shows the opposing side's progress; when the opponent satisfies it, the
other side claims the tile. Empty beneficiary sides (in `--allow-empty` rounds) cannot claim.
**Setup:** Build `0.6.4`; two players on opposite teams — Player A on Red, Player B on Blue; boards
containing one or more opponent tiles (use `/lo reroll` as needed).
**Steps:**
1. Track an opponent tile on Red's HUD (e.g. `Opponent dies 3 times` or `Opponent Team takes 100
   Damage`).
2. Have **Blue** player B trigger partial progress (e.g. die once, or `/damage <B> 40 generic`).
   Confirm **Red's** HUD rises (`1/3` or `40/100`) while **Blue's** HUD stays `0`.
3. Have Blue player B complete the trigger (e.g. 3rd death, 100 total damage, fall damage, catching
   fire via flint & steel / lava, or stepping into water). Wait one check cycle.
4. Check the chat broadcast, the board tile colour/lore, `/lo status`, and Red's score.
5. **Empty-side guard check:** remove all players from Blue, run `/lo reroll`, and trigger an
   opponent goal as Red (e.g. touch water or die). Confirm empty Blue does **not** claim the tile.
**Expected:** In step 2, Red's HUD tracks Blue's progress. In step 3–4, **Red** (the beneficiary,
not Blue) claims the tile, the tile turns **Red** concrete, chat reads `✔ Red claimed <goal>
(triggered by <B>)`, Red's score increments by 1, and `/lo status` attributes it to Red. In step 5,
an empty team does not claim opponent goals.
**Watch for:** the triggering team claiming its own opponent goal instead of the beneficiary, HUD
showing your own progress instead of the opponent's, fire or brief water contact failing to trigger,
empty Blue claiming in `--allow-empty` mode, or console stack traces.
**Report back:** which opponent goals were tested, pass/fail for HUD + claim attribution + empty-
side check, and the claim chat line.
**Status:** pass (owner, 0.6.4)


---

## Board Compass live team rosters (build 0.6.5)

### T-055 — Board Compass tooltip lists who is on each team and stays updated

**Change:** `BoardCompass` now appends each team's live roster (`<Team>: <players>` or `(empty)`,
marking disconnected members `(offline)`) to the Board Compass lore, and refreshes existing
compasses in-place when players join/move mid-round or connect/disconnect.
**Setup:** Build `0.6.5`; two players — Player A on Red, Player B unassigned (or on Blue).
**Steps:**
1. Start a round (`/lo reroll` or `/lo start`). Hover over Player A's Board Compass.
2. Confirm the tooltip shows both teams and who is on each (e.g. `Red: <A>` and `Blue: (empty)` or
   `Blue: <B>`), with team names in their team colour and no italic text.
3. Have unassigned Player B join Blue mid-round (`/lo join blue`), or as op move a player with
   `/lo team set`.
4. Without running `/lo compass` or rerolling, hover over Player A's existing Board Compass again
   and confirm the roster updated in-place.
5. Have Player B disconnect and hover over Player A's compass to confirm B appears as
   `<B> (offline)`; reconnect B and confirm `(offline)` clears.
6. Right-click the updated compass to confirm it still opens the Lockout board.
**Expected:** Both round-start and `/lo compass`-recovered compasses show live coloured team
rosters below the `/lo compass` hint, update automatically on mid-game team changes and
disconnect/reconnect, keep glint/tag intact, and still open the board on right-click.
**Watch for:** stale rosters after mid-game `/lo join` or `/lo team set`, duplicate compasses
created on refresh, italicised lore text, compass losing its board-opening tag after refresh, or
console errors.
**Report back:** pass/fail for steps 2, 4, 5 and 6, plus a tooltip screenshot if anything looks off.
**Status:** pass (owner, 0.6.5)

---

### T-056 — Board GUI team concrete blocks list online and offline players live

**Change:** Updated `BoardGui.sideItem` (the two team concrete blocks flanking the Clock in slots 3
and 5) to show `<online> / <total> online`, followed by each online member (`● <name>`) and any
disconnected member (`○ <name> (offline)`), or `(empty)` when a team has no members. Open boards
refresh live on `PlayerJoinEvent`, `PlayerQuitEvent`, and mid-game team joins/moves.
**Setup:** Build `0.6.6`; two players — Player A on Red, Player B unassigned (or on Blue); a
running round (`/lo reroll` or `/lo start`).
**Steps:**
1. Open the Lockout board (`/lo board` or right-click the Board Compass) as Player A.
2. Hover over both team concrete blocks next to the Clock at the top of the board.
3. Keep the board GUI open as Player A while Player B joins Blue mid-round (`/lo join blue`) or
   gets moved with `/lo team set`. Hover over the concrete blocks again without reopening the board.
4. Keep the board GUI open as Player A while Player B disconnects and reconnects; hover over Blue's
   concrete block after each change.
**Expected:** Both concrete blocks show `<claims> / <winTarget> goals`, `<online> / <total> online`,
and live member lines (`● <name>` in green/white for online players, `○ <name> (offline)` in dark
gray for offline players, or `(empty)` for an empty team). Open boards update live without closing
or stealing cursor focus.
**Watch for:** stale player list while the board stays open, board closing/reopening on join/quit,
italicised lore text, or console errors.
**Report back:** pass/fail for steps 2, 3 and 4, plus a screenshot if anything looks off.
**Status:** pass (owner, 0.6.6)

---

## Slice 12 — workstations, brewing, block actions, mining & conditional kills (build 0.7.0)

### T-057 — Catalogue v8 upgrade, workstation goals and brewing goals

**Change:** Added `workstation` (8 goals: anvil, cauldron wash, composter harvest, enchanting table,
grindstone, loom, smithing table, stonecutter) and `brew_potion` (7 goals: healing, invisibility,
poison, swiftness, water breathing, weakness, lingering potion — with coloured potion icons on the
board GUI). `goals.yml` upgrades from v7 to v8 (246 templates total).
**Setup:** Build `0.7.0`; existing `goals.yml` (v7) in `plugins/TLockout/`; one player on Red
(use `/lo reroll` as needed to roll these tiles onto the board).
**Steps:**
1. Start the server and inspect startup logs and `plugins/TLockout/`.
2. **Board icons:** When a specific `Brew a Potion of ...` tile appears on the board, confirm the
   potion bottle icon is tinted in the matching vanilla potion colour (not a plain water bottle).
3. **Result-slot workstations** (test whichever appear on the board):
   - **Anvil:** Rename or repair an item in survival; also confirm clicking the result slot *without*
     enough XP levels does **not** claim.
   - **Grindstone:** Repair two damaged tools or disenchant an enchanted item and take the result.
   - **Loom:** Apply any pattern to a banner and take the output banner.
   - **Stonecutter:** Cut cobblestone/stone into stairs/slabs and take the output.
   - **Smithing Table:** Trim armor or upgrade diamond gear with a template + material and take the
     output.
4. **Enchanting Table:** Enchant an item at an enchanting table.
5. **Cauldron:** Wash dyed leather armor, a patterned banner, or a dyed shulker box in a water
   cauldron (verify simply filling/emptying a bucket or bottle does **not** claim).
6. **Composter:** Fill a composter to level 8 and right-click it to harvest bone meal (verify adding
   items before level 8 does **not** claim).
7. **Brewing:** Place a matching brewed potion (e.g. Healing / Strong Healing, Swiftness / Long /
   Strong, or any Lingering Potion for `Brew a Lingering Potion`) into a brewing stand and take it
   out of one of the bottom 3 potion slots.
**Expected:** Startup backs up `goals.yml` to `goals.yml.v7.bak`, upgrades to version 8, and logs
`Loaded 246 goal templates.`. Each workstation claims only when actually used (not merely opened or
clicked without taking), cauldron claims only on washing, composter claims on harvesting bone meal,
and brewing goals claim when collecting a matching potion (or lingering potion) from the brewing
stand.
**Watch for:** anvil claiming without enough XP, workstations claiming on mere open or failed click,
cauldron claiming on bucket fill/empty, brewing potion icons all looking identical, or console stack
traces.
**Report back:** startup console excerpt, which workstation/brewing goals were tested, pass/fail for
each, and any console errors.
**Status:** pass (owner, 0.7.0)

---

### T-058 — Block actions, world interactions and decorated banner shield

**Change:** Added `block_action` (14 event-driven goals: jukebox, glow ink on crimson sign, flower
pot, full armor stand, item frame in item frame, full campfire, full chiseled bookshelf, place end
crystal, light candle, shoot firework from crossbow, toot goat horn, brush suspicious sand/gravel,
enrage zombified piglin, shield disabled) and `banner_shield` (`Decorate Shield with Banner`,
polled across inventory storage and offhand).
**Setup:** Build `0.7.0`; one Red player (plus a second player or mob with an axe for shield
disable); boards containing these tiles (use `/lo reroll` as needed).
**Steps:** Test whichever of these tiles appear on your boards:
1. **Jukebox:** Insert a music disc into an empty jukebox.
2. **Glow Ink on Crimson Sign:** Apply a glow ink sac to an oak sign (confirm **no** claim), then
   apply a glow ink sac to a crimson sign or crimson hanging sign (confirm claim).
3. **Flower Pot:** Place any flower/sapling into a flower pot.
4. **Armor Stand:** Equip 3 armor pieces (confirm **no** claim), then equip the 4th armor piece.
5. **Item Frame in Item Frame:** Place an Item Frame inside a Glow Item Frame (confirm **no**
   claim), then place a regular Item Frame inside another regular Item Frame (confirm claim).
6. **Campfire:** Put 3 raw food items on a campfire (confirm **no** claim), then place the 4th.
7. **Chiseled Bookshelf:** Put 5 books in a chiseled bookshelf (confirm **no** claim), then insert
   the 6th book.
8. **Place End Crystal:** Place an End Crystal on obsidian or bedrock.
9. **Light Candle:** Right-click an unlit candle with flint & steel or a fire charge.
10. **Shoot Firework from Crossbow:** Load a crossbow with a firework rocket (in offhand) and shoot
    it (confirm shooting a normal arrow does **not** claim).
11. **Toot Goat Horn:** Right-click a goat horn (looking at air and/or a block) to blow it.
12. **Brush Suspicious Sand/Gravel:** Place `/setblock ~ ~ ~ suspicious_sand` (or find one), break
    one by mining (confirm **no** claim), stop brushing halfway (confirm **no** claim), then brush
    one to completion until it turns into normal sand/gravel (confirm claim).
13. **Enrage Zombified Piglin:** Hit a zombified piglin in survival so it becomes hostile toward you.
14. **Have your Shield disabled:** Raise a shield in survival and let a Vindicator / Ravager /
    axe-wielding opponent hit it to put the shield on cooldown.
15. **Decorate Shield with Banner:** Hold a plain shield (confirm **no** claim), then craft a shield
    with any banner and hold it in inventory or offhand (confirm claim).
**Expected:** Every goal claims only when its exact condition is met; negative checks (oak sign,
glow item frame, 3/4 campfire or armor stand, 5/6 bookshelf, arrow from crossbow, breaking or
half-brushing suspicious sand, plain shield) do not falsely claim.
**Watch for:** goat horn failing when right-clicking air, brush failing in survival or false-triggering
on break, armor stand / campfire / bookshelf claiming before full, crimson hanging sign not matching,
or console stack traces.
**Report back:** list of interactions tested, pass/fail for each (including negative checks), and
any console errors.
**Status:** pass (owner, 0.7.0)

---

### T-059 — Mine-block goals and conditional kills (Snow Golem in Nether, Breeze with Wind Charge)

**Change:** Added `mine_block` (`Mine Diamond Ore`, `Mine Emerald Ore`, `Mine Mob Spawner`, `Mine
Turtle Egg`, `Mine Crafter` — ores match both regular and deepslate variants) and `conditional_kill`
(`Kill Snow Golem in The Nether`, `Kill Breeze using Wind Charge`).
**Setup:** Build `0.7.0`; one Red player; boards containing one or more mine-block tiles and/or
conditional-kill tiles (use `/lo reroll` as needed).
**Steps:**
1. **Mine Diamond/Emerald Ore:** Break a `DIAMOND_ORE` or `DEEPSLATE_DIAMOND_ORE` block (and/or
   `EMERALD_ORE` / `DEEPSLATE_EMERALD_ORE`) and confirm the matching tile claims.
2. **Mine Mob Spawner / Turtle Egg / Crafter:** Break a `SPAWNER`, `TURTLE_EGG`, or `CRAFTER` block
   when present on the board and confirm it claims.
3. **Kill Snow Golem in The Nether:** Kill a Snow Golem in the Overworld (confirm **no** claim for
   the Nether goal), then go to The Nether, summon/build a Snow Golem, and kill it (confirm claim).
4. **Kill Breeze using Wind Charge:** Summon a Breeze, kill one with a sword (confirm **no** claim),
   then weaken a second Breeze and finish it off with a thrown or deflected Wind Charge (confirm
   claim).
**Expected:** Breaking either regular or deepslate diamond/emerald ore claims the ore tile; spawner,
turtle egg and crafter claim on break; Snow Golem in The Nether claims only in The Nether; Breeze
with Wind Charge claims only when killed by wind charge damage (thrown or deflected by a player).
**Watch for:** deepslate ore not counting, Overworld Snow Golem claiming the Nether goal, sword kill
claiming the Wind Charge goal, deflected Wind Charge not attributing the killer, or console errors.
**Report back:** which mining and conditional-kill goals were tested, pass/fail for each (including
negative checks), and the claim chat lines.
**Status:** pass (owner, 0.7.0)

---

## Board GUI op-only Reroll button (build 0.7.1)

### T-060 — Bottom-right Reroll Board button appears only for opped players and rerolls the board

**Change:** Added a `Reroll Board` button (`RECOVERY_COMPASS`) in slot 53 (bottom-right corner) of
the Lockout board GUI (`BoardGui`), rendered and clickable only when `player.isOp()` is true.
Clicking it calls the shared `TLockout.rerollChallenge` path (identical to `/lo reroll`, respecting
`autoreset` and `--allow-empty` semantics) and re-opens the newly rolled board for the opped player.
**Setup:** Build `0.7.1`; one opped player on Red and one non-opped player (on Red, Blue, or
unassigned); an active round (`/lo reroll` or `/lo start`).
**Steps:**
1. As the **non-opped** player, open the board (via Board Compass and/or `/lo board`) and inspect
   the bottom-right corner slot (row 6, column 9). Click that slot.
2. As the **opped** player, open the board via the Board Compass and inspect the bottom-right corner
   slot. Note the current board seed and tiles.
3. Claim at least one goal (or track a goal on the HUD), then click the `Reroll Board` button in the
   bottom-right corner.
4. Check chat, the reopened board's seed/tiles/claim count, HUD, and Board Compass.
5. Run `/lo autoreset off`, give yourself an iron ingot, open the board, and click `Reroll Board`
   again; confirm the iron ingot is kept while the board rerolls.
6. Optional: `/deop` the player, reopen the board, and confirm the bottom-right corner is back to a
   black glass pane; `/op` again and confirm the button returns.
**Expected:** Non-opped players see only the black glass pane filler in the bottom-right corner and
clicking it does nothing. Opped players see the `Reroll Board` (`RECOVERY_COMPASS`) button in the
bottom-right corner; clicking it broadcasts the same `Lockout board rerolled!` message as
`/lo reroll`, clears claims/targets/HUD, respects `autoreset`, and immediately shows the newly
rolled board.
**Watch for:** button visible or clickable for non-opped players, cursor/inventory glitches from
rerolling inside a GUI click, duplicate Board Compasses, `autoreset off` ignored, or console stack
traces.
**Report back:** pass/fail for non-op visibility, op reroll behavior, and `autoreset off`, plus any
console errors.
**Status:** pass (owner, 0.7.1)

---

## Slice 13 — world gating, anti-clustering groups, pool filters & catalogue finish (build 1.0.0)

### T-061 — Config v3 + catalogue v9 upgrade and world feasibility scan + caching

**Change:** Added `WorldFeasibilityScanner` (`world-scan.enabled: true`, `world-scan.radius: 750`
in `config.yml` v3) to scan required structures and biomes around overworld spawn on first round
start, cache results per world, and drop unreachable `requires:` goals from board generation.
`/lo reload` clears the cache and re-reads `world-scan` settings. `goals.yml` upgrades from v8 to
v9 (267 templates: 256 random pool, 11 `custom-only`).
**Setup:** Build `1.0.0`; existing `config.yml` (v2) and `goals.yml` (v8) in `plugins/TLockout/`;
one opped player on Red on a normal overworld (optionally also test with a small radius or
disabled scan).
**Steps:**
1. Start the server and check the startup console lines and `plugins/TLockout/`.
2. Run `/lo reroll 5001` (first start on this world) and inspect the server console for the world
   scan line (`World scan for '<world>' (radius 750 blocks): X/11 structures, Y/39 biomes located
   in Zms.`).
3. Run `/lo reroll 5001` a second time and verify:
   - No second `World scan for ...` line is logged (results are cached).
   - Seed `5001` produces the exact same 25 tiles as step 2.
4. Roll several boards (e.g. click the Reroll button 5–8 times) and confirm no tile requires a
   structure/biome that was reported absent near spawn (e.g. if no Ocean Monument or Mushroom
   Fields exists within 750 blocks of spawn, `Obtain Sponge`, `Kill Elder Guardian`, `Find
   Mushroom Biome`, etc. should not appear).
5. Run `/lo reload`, then `/lo reroll 5001` and confirm a fresh `World scan for ...` line is logged
   (cache cleared by reload).
6. Optional: set `world-scan.enabled: false` in `config.yml`, run `/lo reload`, and confirm `/lo
   reroll` skips scanning and logs no `World scan` line. Restore `world-scan.enabled: true` after.
**Expected:** Startup backs up `config.yml` to `config.yml.old` (upgrading to v3) and `goals.yml`
to `goals.yml.v8.bak` (upgrading to v9) and logs `Loaded 267 goal templates.`. World scan runs once
per world with fast turnaround (no chunk-generation hitch), caches cleanly across rerolls, keeps
seed determinism on the same world, filters out unreachable biome/structure goals, and invalidates
on `/lo reload`.
**Watch for:** server freeze/lag spike during world scan, scan re-running on every reroll instead
of caching, seed `5001` giving different boards on back-to-back runs, or console stack traces.
**Report back:** startup + world-scan console lines (including scan duration), pass/fail for
caching/determinism/reload, and any console errors.
**Status:** pass (owner, 1.0.0)

---

### T-062 — Anti-clustering group caps, predecessor rules and `custom-only` pool exclusion

**Change:** Added `GoalGroups` (all Fabric group caps + predecessor rules + type caps: max 6 plain
item goals, max 1 durability, max 3 workstations, max 2 mine blocks, max 1 biome, max 1 brew
potion, max 1 full armor set, max 2 tools, etc.) and `pool: custom-only` filtering so the 11
custom-only goals (`Obtain Wither Skeleton Skull`, `Get Levitation`, `Find a Nether Fortress`,
`Find an End City`, `Find a Bastion Remnant`, and the 6 custom-only opponent goals) never appear
in normal random boards.
**Setup:** Build `1.0.0`; one opped player on Red; click the Reroll button (or run `/lo reroll`)
across ~8–10 different random boards and inspect each board.
**Steps:**
1. Inspect each rolled board and count how many tiles belong to the same family:
   - Plain `item` obtains (should be at most **6** per board, not half the board).
   - `brew_potion` (at most **1**), full armor sets (at most **1**), `biome` goals (at most **1**),
     `workstation` (at most **3**), `mine_block` (at most **2**), `durability` (at most **1**).
2. Confirm none of the 11 `custom-only` goals (`Obtain Wither Skeleton Skull`, `Get Levitation`,
   `Find a Nether Fortress`, `Find an End City`, `Find a Bastion Remnant`, `Opponent obtains
   Crafting Table/Obsidian/Wheat Seeds`, `Opponent eats Food`, `Opponent hit by Egg/Snowball`)
   appear on any random board.
3. If a higher-tier unique goal appears (`Kill 13/15 Unique Hostile Mobs`, `Breed 8 Unique
   Animals`, `Eat 20/25 Unique Food`, `Get 30 Advancements`), confirm a lower-tier predecessor from
   that same ladder is also present on the same board.
4. Confirm the board feels varied and evenly shuffled (higher-tier goals aren't always stuck in the
   bottom row).
**Expected:** Every board has a healthy mix of goal types with zero group-cap violations, zero
`custom-only` leaks, and valid predecessor pairings whenever a high-tier ladder goal appears.
**Watch for:** boards flooded with >6 plain item goals, 2+ biome/armor/brew/monument goals on the
same board, `custom-only` goals appearing in random boards, or `Kill 15 Unique Hostile Mobs` /
`Get 30 Advancements` appearing without a lower tier.
**Report back:** number of boards inspected, rough category mix observed, any cap/pool violations,
and any console warnings.
**Status:** pass (owner, 1.0.0)

---

### T-063 — New Slice 13 catalogue goals (`breed_unique`, new breed/biome goals & custom-only opponent triggers)

**Change:** Added `breed_unique` (`Breed 4/6/8 Unique Animals`, team-shared unique animal set), 5
additional `breed` templates (`Fox`, `Frogs`, `Goat`, `Hoglin`, `Strider`), 2 biome goals
(`Find Ice Spikes Biome`, `Find Mushroom Biome`, plus multi-variant matching for `Badlands` /
`Jungle` / `Savanna` / `Ocean`), and extended `opponent` tracking for the 6 custom-only triggers
(`OBTAIN_ITEM`, `EAT_FOOD`, `HIT_BY_EGG`, `HIT_BY_SNOWBALL` via `ProjectileHitEvent`).
**Setup:** Build `1.0.0`; one or two Red players (plus one Blue player if testing custom-only
opponent goals); roll boards with `/lo reroll` until `Breed 4/6/8 Unique Animals` and/or one of
the new breed/biome tiles appears.
**Steps:**
1. **`Breed 4 Unique Animals`:** Track it on the HUD. Breed a Cow (HUD `1/4`), breed another Cow
   (confirm duplicate stays `1/4` — even across teammates), then breed 3 more distinct species
   (e.g. Sheep, Pig, Chicken) to reach `4/4` and claim.
2. **New breed/biome goals:** If `Breed Fox/Frogs/Goat/Hoglin/Strider` or `Find Ice Spikes /
   Mushroom / Badlands Biome` appears on a board, trigger it and confirm it claims cleanly.
3. **Optional (custom-only pool check):** Temporarily change one `pool: custom-only` template in
   `plugins/TLockout/goals.yml` (e.g. `opponent_hit_by_snowball` or `opponent_eats_food`) to
   `pool: random`, run `/lo reload`, roll it onto a 2-team board, and verify that throwing a
   snowball at an opponent (0-damage hit) or having an opponent eat food claims for the
   beneficiary side. Revert the edit afterward.
**Expected:** `Breed 4/6/8 Unique Animals` counts distinct bred species across the team without
double-counting duplicates and claims at target. New breed/biome goals claim normally (and
`Find Badlands Biome` claims in `badlands`, `eroded_badlands`, or `wooded_badlands`). Custom-only
goals load cleanly and claim when enabled in a custom pool.
**Watch for:** breeding the same animal twice counting as 2 unique animals, frog breeding not
triggering, eroded/wooded badlands failing to claim `Find Badlands Biome`, or 0-damage snowball/egg
hits failing to trigger.
**Report back:** which new goals were tested, HUD values observed, pass/fail, and any console
errors.
**Status:** pass (owner, 1.0.0)

---

## Sound effects (build 1.1.0)

### T-064 — Config v4 upgrade and distinct goal-claim SFX for your team, opponents and spectators

**Change:** Added `SoundService` (`config-version: 4`, `sounds:` section in `config.yml`). When a
non-winning goal is claimed, the claiming team hears a 2-note ascending `block.note_block.pling`
(pitch `1.0` → `1.5`, 3 ticks apart), opposing teams hear a 2-note descending
`block.note_block.bass` (pitch `0.9` → `0.6`, 3 ticks apart), and unassigned spectators hear a
neutral `block.note_block.chime`.
**Setup:** Build `1.1.0`; existing `config.yml` (v3) in `plugins/TLockout/`; two players on
opposite teams (Player A on Red, Player B on Blue), plus optionally an unassigned Player C; active
round with Master sound enabled on clients.
**Steps:**
1. Start the server and check that `config.yml` backs up to `config.yml.old` and upgrades to
   `config-version: 4` with the `sounds:` section.
2. Start a round and have **Red** (Player A) claim any non-winning goal (e.g. an `Obtain X` tile).
3. Note what Player A (Red), Player B (Blue), and optional Player C (unassigned) each hear.
4. Have **Blue** (Player B) claim a non-winning goal (or trigger an opponent goal so Red claims it)
   and note what each player hears.
**Expected:** Startup upgrades `config.yml` cleanly to v4. When Red claims, Red hears the bright
2-note ascending pling, Blue hears the 2-note descending bass drop, and unassigned spectators hear
the neutral chime. When Blue claims, Blue hears the ascending pling and Red hears the descending
bass drop. Sounds play clearly regardless of player distance or movement.
**Watch for:** both teams hearing the same sound, inverted team/opponent sounds, missing second
note on the 2-note cues, muffled/positional drop-off, or console errors.
**Report back:** pass/fail for v4 upgrade and each perspective (claiming team, opponent, spectator),
plus any console errors.
**Status:** pass (owner, 1.1.0)

---

### T-065 — Round start, match win/lose SFX and config reload/mute

**Change:** `SoundService` plays `item.goat_horn.sound.0` to all online players on round start and
reroll, `ui.toast.challenge_complete` to the winning team (and spectators) on match finish, and
`entity.warden.death` to the losing team on match finish. On the final winning claim, only the
finish cue plays (no overlapping claim chime). Reloading via `/lo reload` applies `sounds` edits.
**Setup:** Build `1.1.0`; two players on opposite teams (Red and Blue); temporarily set
`win.goals-to-win: 2` in `config.yml` (and `/lo reload`) if you want a quick match-finish test,
then restore it to `13` afterward.
**Steps:**
1. Start or reroll a round (`/lo start` or `/lo reroll` or the GUI Reroll button) and confirm all
   online players hear the goat horn start cue.
2. Have Red claim goal #1 (confirm normal ascending pling for Red / descending bass for Blue).
3. Have Red claim the final winning goal (goal #2 with `goals-to-win: 2`, or goal #13 on default).
4. Note what Red (winner) and Blue (loser) hear on that final claim.
5. Optional: set `sounds.enabled: false` in `config.yml`, run `/lo reload`, start a round and claim
   a goal, and confirm all plugin SFX are silent. Restore `sounds.enabled: true` and
   `win.goals-to-win: 13`, then `/lo reload`.
**Expected:** Round start/reroll plays the goat horn cue. The final winning claim plays **only**
`ui.toast.challenge_complete` for the winning team (and spectators) and `entity.warden.death` for
the losing team — without overlapping the regular claim pling/bass. Setting `sounds.enabled: false`
and running `/lo reload` silences all plugin SFX.
**Watch for:** overlapping claim chime + finish fanfare on the winning goal, winner and loser
hearing swapped finish sounds, `/lo reload` failing to pick up `sounds` changes, or console errors.
**Report back:** pass/fail for start cue, win vs. lose finish cues, no-overlap check, and reload,
plus any console errors.
**Status:** pass (owner, 1.1.0)

---

## Pre-round countdown & world-scan chat notices (build 1.2.0)

### T-066 — Pre-round countdown freezes participants, plays ticks, unlocks on GO, and cancels on `/lo stop`

**Change:** Added `CountdownService` and `ChallengeManager.State.COUNTDOWN` (`config-version: 5`,
`match.countdown-seconds: 5`, `match.freeze-during-countdown: true`, `sounds.countdown-tick`).
`/lo start` now runs a 5-second countdown with titles (`5`…`1` → `GO!`), chat messages, and
`countdown-tick` SFX before resetting players, rolling the board, giving Board Compasses, and
playing the `game-start` goat horn. While counting down, participating players cannot move
horizontally, jump, break/place blocks, or take/deal damage (they can still look around freely);
unassigned spectators are not frozen. `/lo stop` cancels an active countdown cleanly. `/lo reroll`
and the GUI Reroll button skip the countdown for fast board rolling.
**Setup:** Build `1.2.0`; existing `config.yml` (v4) in `plugins/TLockout/`; one player on Red
(and optionally a second unassigned player as spectator).
**Steps:**
1. Start the server and confirm `config.yml` upgrades from v4 to v5 (`config.yml.old` backup) with
   `match.countdown-seconds: 5`, `match.freeze-during-countdown: true`, and `sounds.countdown-tick`.
2. Run `/lo start --allow-empty` (or `/lo start` with both teams populated).
3. During the 5-second countdown:
   - Try walking, jumping, breaking a block, placing a block, and taking damage as the Red player.
   - Look around with the mouse (confirm camera turns smoothly).
   - Confirm you do **not** have the Board Compass or board yet, and `/lo start` refuses to double-start.
   - If an unassigned spectator is online, confirm they can still move freely.
4. Wait for `GO!`: confirm the green `GO!` title appears, the goat horn plays, player reset runs,
   the Board Compass is given, movement/blocks/damage unlock immediately, and the round starts.
5. Run `/lo stop`, then run `/lo start --allow-empty` and immediately run `/lo stop` around `3...`:
   confirm the countdown cancels, titles clear, movement unlocks, and no board starts.
6. Run `/lo reroll` (or click the GUI Reroll button) and confirm it still rolls immediately without
   a 5-second countdown.
**Expected:** Startup upgrades `config.yml` to v5. `/lo start` counts `5` → `1` (with rising pitch
on `3`, `2`, `1`) while freezing participants (camera free, no movement/block edits/damage) and
unlocks cleanly on `GO!`. `/lo stop` mid-countdown aborts cleanly. `/lo reroll` skips the countdown.
**Watch for:** rubber-banding when looking around while frozen, players staying frozen after `GO!`
or `/lo stop`, compass/board appearing before `GO!`, spectators being frozen, or console errors.
**Report back:** pass/fail for v5 upgrade, freeze/look behavior, `GO!` start, mid-countdown
`/lo stop`, and `/lo reroll`, plus any console errors.
**Status:** pass (owner, 1.2.0)

---

### T-067 — World feasibility scan prints start and completion notices to chat

**Change:** When `WorldFeasibilityScanner` performs a fresh overworld scan (first start/reroll on a
world, or after `/lo reload`), `TLockout` now broadcasts a chat notice right before the scan runs
(`Scanning world '<world>' for nearby structures & biomes (radius 750 blocks)...`) and a completion
summary right after (`World scan complete: X/11 structures, Y/39 biomes found (Zms).`). Cached
starts/rerolls stay quiet and do not reprint scan notices.
**Setup:** Build `1.2.0`; one opped player on Red; server freshly started (or right after
`/lo reload`).
**Steps:**
1. On a freshly started server (or immediately after `/lo reload`), run `/lo start --allow-empty`.
2. Watch chat: confirm `Scanning world '<world>' for nearby structures & biomes (radius 750
   blocks)...` appears first, followed by `World scan complete: X/11 structures, Y/39 biomes found
   (Zms).`, followed by the `5... 4... 3... 2... 1...` countdown.
3. Stop and run `/lo start --allow-empty` a second time (without `/lo reload`). Confirm **no** scan
   chat messages appear (world facts are cached) and the countdown starts immediately.
4. Run `/lo reload`, then click the GUI Reroll button or run `/lo reroll`: confirm the scan chat
   messages appear once again, and the freshly rolled board opens cleanly right after the scan.
**Expected:** Uncached scans print both start and completion notices in chat (with structure/biome
counts and duration); cached starts/rerolls print nothing; `/lo reload` clears the cache so the
next start/reroll prints the scan notices again.
**Watch for:** scan notice appearing on every cached reroll, `Scanning world...` failing to show up
before the scan runs, GUI Reroll button failing to reopen after an uncached scan, or console errors.
**Report back:** exact chat lines observed on uncached vs. cached start and after `/lo reload`,
plus pass/fail and any console errors.
**Status:** pass (owner, 1.2.0)

---

## Keep inventory during rounds (build 1.3.0)

### T-068 — Participating players keep inventory & XP on death during rounds, toggleable via `/lo keepinventory`

**Change:** Added `KeepInventoryService` (`config-version: 6`, `match.keep-inventory: true`) and
admin command `/lockout keepinventory [on|off|toggle]` (`/lo keepinventory`). When enabled and a
round is `RUNNING`, participating players keep their inventory and XP levels on death with zero
item/XP drops (via `PlayerDeathEvent`, without altering world gamerules). Unassigned spectators
and deaths while `IDLE` follow normal server gamerules.
**Setup:** Build `1.3.0`; existing `config.yml` (v5) in `plugins/TLockout/`; world gamerule
`keepInventory` set to `false` (`/gamerule keepInventory false`); one player on Red (plus
optionally an unassigned spectator).
**Steps:**
1. Start the server and confirm `config.yml` upgrades from v5 to v6 (`config.yml.old` backup) with
   `match.keep-inventory: true`.
2. Run `/lo keepinventory` and confirm it reports `ON`.
3. Start or reroll a round (`/lo reroll`). Give the Red player some items (e.g. a diamond pickaxe,
   cobblestone) and 10 XP levels (`/xp add @s 10 levels`), then die (`/kill @s` or mob/fall death).
4. Respawn and inspect inventory, XP levels, Board Compass (should still be just 1 compass), and
   the death location (confirm **no** dropped items and **no** XP orbs on the ground).
5. Run `/lo keepinventory off`, give yourself an item, die again, and confirm items/XP now drop
   normally while the Board Compass is still not dropped and is restored on respawn.
6. Run `/lo keepinventory toggle` (confirms switching back to `ON`), then run `/lo stop`. Give
   yourself an item while `IDLE` and die; confirm normal world gamerule applies (items drop when
   `/gamerule keepInventory` is `false`).
7. Optional: while a round is `RUNNING` with `keepinventory ON`, have an unassigned spectator die
   and confirm they follow normal server gamerules (items drop).
**Expected:** Startup upgrades `config.yml` to v6. With `keepinventory ON` during a `RUNNING`
round, participating players retain all items, armor, offhand, Board Compass (no duplicate), and
XP levels on death with zero ground drops; death-cause goals (e.g. `Die to Cactus`) and opponent
death goals still trigger normally. Turning it `OFF` or stopping the round restores normal death
drops without leaving modified world gamerules behind.
**Watch for:** duplicated items or XP orbs at the death spot, duplicate Board Compass after
respawn, world `keepInventory` gamerule getting stuck `true`, death goals failing to trigger, or
console errors.
**Report back:** pass/fail for v6 upgrade, keep-inventory ON (items/XP/no-drops/no-dup compass),
OFF toggle, and IDLE check, plus any console errors.
**Status:** pass (owner, 1.3.0)

### T-069 — Player tracker menu via Shift + Right-Click and Board UI

**Change:** Added `PlayerTrackerGui` accessible via Shift + Right-Click on the Board Compass and
via the new `Track Player` compass button in slot 45 (bottom-left) of `BoardGui`. Lists all
participating players grouped by team (teammates and enemies, excluding yourself; unassigned
spectators can track participants but do not appear as targets).
**Setup:** Build `1.4.0`; active round with at least 2 players across teams (e.g. 2 on Red, 1 on
Blue, plus optionally 1 unassigned spectator).
**Steps:**
1. Hold the Board Compass and **normal Right-Click** (not sneaking). Confirm the normal Lockout
   Board GUI still opens and shows the `Track Player` compass button in the bottom-left corner
   (slot 45).
2. Click `Track Player` in the Board GUI. Confirm the `Track Player` menu opens showing team
   concrete headers and player heads for both teammates (`Teammate · <Team>`) and enemies
   (`Enemy · <Team>`), excluding yourself.
3. Click `Back to Board` (top-left arrow) and confirm it returns to the Lockout Board GUI. Close
   the board.
4. Hold the Board Compass and **Shift + Right-Click** (both in air and while aiming at a block
   such as a chest or grass). Confirm the `Track Player` menu opens directly without interacting
   with the block.
5. Check the Board Compass lore and confirm it shows `Shift + Right-click to track a player`.
6. Optional (with an unassigned spectator): have the spectator Shift + Right-Click their Board
   Compass and confirm they can see and select participating players, while participants do **not**
   see the spectator listed as a target.
**Expected:** Both Shift + Right-Click on the compass and slot 45 in the Board UI open the
`Track Player` menu. Teammates and enemies are clearly grouped and labeled; clicking `Back to
Board` returns to the board; items cannot be taken out of the menu.
**Watch for:** normal right-click opening the tracker instead of the board, Shift + Right-Click
opening a chest instead of the menu, viewer appearing in their own tracker list, spectator showing
up as a target, or console stack traces.
**Report back:** pass/fail for Shift+Right-Click, Board UI button, Back to Board, team grouping,
and spectator behavior (if tested), plus any console errors.
**Status:** pass (owner, 1.4.0)

### T-070 — Live compass pointing, display name with distance, switching & clearing

**Change:** Added `PlayerTrackerService` and `BoardCompass.applyTracking` to point the Board
Compass needle at the selected player using `CompassMeta` lodestone tracking and update the item
name to `Tracking: <Name> (<dist>m)` as either player moves.
**Setup:** Build `1.4.0`; active round with at least 2 players (e.g. Red and Blue) in the same
dimension.
**Steps:**
1. Open the `Track Player` menu and click another player's head. Confirm the menu closes, chat
   confirms `Compass now tracking <Name> (<Team>)`, and your Board Compass item name immediately
   changes to `Tracking: <Name> (<dist>m)` with the player's name in their team color.
2. Have the tracked player walk/run in a circle around you and move farther away/closer. Watch
   both the compass needle and the distance in the item name (`<dist>m`).
3. Re-open the `Track Player` menu (Shift + Right-Click) and confirm the tracked player's head has
   a `★` prefix, enchantment glint, and live distance in lore. Click a different player (if 3+
   players) or click the currently tracked player / `Stop Tracking` barrier.
4. Confirm clearing tracking resets the compass item name back to `Board Compass` (Aqua) and
   clears lodestone tracking.
5. Re-select a target, verify `Board Compass` still cannot be dropped (`Q`), then run `/lo stop`
   (confirm any open `Track Player` menu closes and compasses are removed) or `/lo reroll`
   (confirm the new round starts with tracking cleared and item name reset to `Board Compass`).
**Expected:** Needle accurately points toward the tracked player as both players move; item name
displays `Tracking: <Name> (<dist>m)` and updates smoothly (~0.5s); switching targets updates
immediately; stopping tracking or starting a new round restores `Board Compass`.
**Watch for:** needle pointing to world spawn instead of the player, needle spinning in the same
dimension, compass losing its enchantment glint or becoming droppable, duplicate compasses, or
console errors.
**Report back:** pass/fail for needle accuracy, live distance in item name, switching/clearing,
and round stop/reroll reset, plus any console errors.
**Status:** pass (owner, 1.4.0)

### T-071 — Cross-dimension last-known location and offline target handling

**Change:** `PlayerTrackerService` snapshots each player's location per world (on periodic ticks,
teleports/portals, dimension changes, deaths, and disconnects) so tracking across dimensions points
to the target's last-known location in the viewer's dimension (their portal).
**Setup:** Build `1.4.0`; active round with 2 players (Player A tracking Player B) in the
Overworld, with access to a Nether portal (or `/execute in minecraft:the_nether run tp`).
**Steps:**
1. While both players are in the Overworld, have Player A track Player B and confirm
   `Tracking: <B> (<dist>m)`.
2. Have Player B enter the Nether through a portal (or teleport to the Nether from a known
   Overworld spot). Check Player A's compass needle and display name.
3. Have Player A walk away from that portal spot and verify the compass needle stays pointed at
   Player B's last Overworld location (the portal) and the distance updates relative to that spot,
   showing `Tracking: <B> (Nether · <dist>m)`.
4. Have Player A follow into the Nether and verify the compass immediately switches to live
   tracking of Player B inside the Nether (`Tracking: <B> (<dist>m)` with needle pointing straight
   to Player B, not spinning).
5. Optional: test when Player B disconnects while tracked (confirm `Tracking: <B> (offline ·
   <dist>m)` or `(offline)` and that tracking automatically resumes when Player B rejoins), and
   when Player B is in a dimension Player A is in that Player B has never visited (confirm
   `Tracking: <B> (<Dim>)` with spinning needle rather than pointing to spawn).
**Expected:** Cross-dimension tracking points to the target's last known position (portal) in the
viewer's dimension with `(<Dim> · <dist>m)`; entering the same dimension (including Nether/End)
immediately switches to live player pointing; offline targets show `(offline)` and resume cleanly
on rejoin.
**Watch for:** compass pointing to Overworld spawn when target enters Nether, needle spinning
inside the Nether even when both players are in the Nether, distance failing to update, or console
errors.
**Report back:** pass/fail for portal last-known pointing, same-dimension Nether tracking, and
offline/rejoin behavior (if tested), plus any console errors.
**Status:** pass (owner, 1.4.0)

### T-072 — Config v7 upgrade and `/lo mode` live switching

**Change:** Added `LockoutMode` (`TEAMS` | `FFA`), `config-version: 7` with
`win.ffa-goals-to-win: 5` and support for `mode: ffa`, plus admin command `/lo mode [teams|ffa]`.
**Setup:** Build `1.5.0`; existing `config.yml` (v6) in `plugins/TLockout/`; an opped player.
**Steps:**
1. Start the server and confirm `config.yml` upgrades from v6 to v7 (`config.yml.old` backup)
   with `win.ffa-goals-to-win: 5`.
2. Run `/lo mode` and confirm it reports `Teams (first to 13 goals wins)`.
3. Run `/lo mode ffa` and confirm it broadcasts `Lockout mode set to FFA (first to 5 goals wins)`.
4. Start a quick testing round (`/lo reroll`), then try `/lo mode teams` while the round is
   running. Confirm it refuses with a message to `/lo stop` first.
5. Run `/lo stop`, then `/lo mode teams` and confirm it switches back cleanly (`first to 13 goals
   wins`), and `/lo mode ffa` switches to FFA again.
6. Optional: set `mode: ffa` in `config.yml`, run `/lo reload`, and confirm the plugin starts in
   FFA mode without disabling itself.
**Expected:** `config.yml` upgrades to v7; `/lo mode` switches cleanly between Teams (target 13)
and FFA (target 5) when idle, refuses while running/counting down, and `mode: ffa` in `config.yml`
loads cleanly on reload/restart.
**Watch for:** plugin refusing to start on `mode: ffa`, stale win target (13 vs 5) after switching
modes, or console errors.
**Report back:** pass/fail for v7 upgrade, `/lo mode` switching & running guard, and `config.yml`
`mode: ffa` reload (if tested), plus any console errors.
**Status:** pass (owner, 1.5.0)

### T-073 — FFA auto-enrollment, `/lo leave` opt-out, colour choice & mid-round join

**Change:** In FFA mode, `/lo start` (and `/lo reroll`) automatically enrolls every online player
as their own 1-member coloured `Side` unless they opted out via `/lo leave`. Players can pick a
colour beforehand with `/lo join [color]` or join mid-round.
**Setup:** Build `1.5.0`; mode set to `FFA` (`/lo mode ffa`); at least 2 online players (ideally 3
to test opt-out + mid-round join).
**Steps:**
1. Without running `/lo join`, run `/lo start` (with ≥2 players online; or `/lo start
   --allow-empty` if solo). Confirm all online players are automatically enrolled with distinct
   colours and receive a Board Compass (and freeze during countdown + reset if `autoreset` is ON).
2. Run `/lo stop`. Have Player A run `/lo join lime` and confirm their colour is set to Lime. Have
   Player B run `/lo leave` and confirm they receive the opt-out spectator message.
3. Run `/lo team list` and confirm it shows enrolled FFA players with their colours and lists
   Player B under opted-out spectators.
4. Run `/lo start` (with ≥2 non-opted-out players, or `--allow-empty`). Confirm Player A has Lime,
   Player B is NOT enrolled (spectating: sidebar says `Spectating (FFA)` and cannot claim goals).
5. While the round is running, have Player B run `/lo join` (or `/lo join orange`). Confirm
   broadcast `<B> joined the FFA mid-round`, Player B gets reset (if `autoreset` is ON) + compass,
   and can now claim goals.
**Expected:** Online players auto-enroll on `/lo start`; `/lo leave` keeps a player out as a
spectator; `/lo join [color]` picks a concrete colour and allows mid-round joining cleanly.
**Watch for:** opted-out player getting forcibly enrolled, duplicate colours when <16 players,
mid-round joiner failing to get a compass or failing to claim, or console errors.
**Report back:** pass/fail for auto-enroll, `/lo join [color]`, `/lo leave` opt-out, and mid-round
join, plus any console errors.
**Status:** pass (owner, 1.5.0)

### T-074 — FFA claiming, lockout, PvP, win target (5) & 2-player vs 3+-player opponent goals

**Change:** Claims in FFA attribute directly to the claiming player (using their concrete colour,
without redundant `for <Name>`), PvP works between all FFA players, first to `win.ffa-goals-to-win`
(default 5) wins (with tie-aware fallback if all 25 cells are claimed), and `opponent` goals are
only eligible in 2-player FFA.
**Setup:** Build `1.5.0`; mode set to `FFA`.
**Steps:**
1. Start a 2-player FFA round (`/lo start`). Confirm PvP hits work between both players (no
   friendly-fire cancellation).
2. Have Player A complete a goal. Confirm chat says `✔ <A> claimed <Goal> (1/25)` (no duplicate
   `for <A>`), the tile turns into Player A's glinted concrete colour, Player A hears the win/team
   claim chime, and Player B hears the opponent claim sound.
3. Have Player B try to complete the same locked goal and confirm it cannot be re-claimed.
4. Have one player reach 5 claimed goals (using quick goals or creative/give for testing). Confirm
   the round immediately ends at 5 goals, announces `<Winner> wins the lockout!` with ranked
   scores, plays win/lose sounds, freezes further claims, and keeps the board viewable.
5. Check opponent-goal gating: in a **2-player** FFA (reroll a few times if needed), confirm
   `Opponent ...` goals *can* appear and trigger properly for the other player; in a **3+ player**
   FFA (or 1-player `--allow-empty` solo FFA), reroll several times and confirm **no** `Opponent
   ...` goals appear on the board.
**Expected:** FFA claims lock out all other players, show each player's own colour, allow full
PvP, end at 5 goals, and allow opponent goals only in 2-player FFA.
**Watch for:** chat saying `claimed X for <Name>`, friendly fire blocking PvP in FFA, round not
ending at 5 goals, or opponent goals appearing in 3+ player FFA.
**Report back:** pass/fail for FFA claim/lockout/PvP, win at 5 goals, and 2p vs 3p+ opponent-goal
gating, plus any console errors.
**Status:** pass (owner, 1.5.0)

### T-075 — FFA Board UI, HUD sidebar, Board Compass lore & Player Tracker grid

**Change:** Adapted `BoardGui` (top-8 ranked concrete headers + full Clock leaderboard),
`HudService` (Leader + personal score/rank sidebar), `BoardCompass` (compact FFA roster lore), and
`PlayerTrackerGui` (grid layout across rows 1–5) for multi-player FFA.
**Setup:** Build `1.5.0`; active FFA round with 2+ players (ideally 3+ to see ranking and tracker
grid).
**Steps:**
1. Open the Lockout Board (`/lo board` or right-click compass). Confirm row 0 shows ranked player
   concrete blocks (`#1 <Name>`, `#2 <Name>`, ...) flanking the Clock, and hovering the Clock shows
   `Mode: FFA` plus the full ranked leaderboard.
2. Right-click an unclaimed goal in the Board GUI to track it (`★ Tracked`, without `Shared with
   your team`). Check your right-hand HUD sidebar and confirm it shows the leader line (`1st: ...`
   or `Tied: ...`), your score & rank (`You: X/5 (#rank)`), and your tracked goals without text
   overflow or clipping.
3. Hover over your Board Compass in your inventory and confirm the lore shows `FFA Players (N):`
   with coloured player names.
4. Shift + Right-Click your Board Compass (or click `Track Player` in slot 45 of the Board UI).
   Confirm the `Track Player` menu uses a clean grid of player heads (excluding yourself) showing
   `FFA · X/5 goals`, live distance/dimension, and lets you point your compass at any other player.
**Expected:** All four surfaces render cleanly for FFA with no redundant team labels, no sidebar
overflow, and working player tracking across all FFA participants.
**Watch for:** sidebar lines overflowing or pushing tracked goals off-screen, player tracker menu
running out of rows, duplicate player name on claimed tile lore, or console errors.
**Report back:** pass/fail for Board UI header/clock, HUD sidebar, compass lore, and Player
Tracker grid, plus any console errors.
**Status:** pass (owner, 1.5.0)

### T-076 — In-game `/lo goalstowin [1-25]` win-target command

**Change:** Added admin command `/lo goalstowin [1-25]` to view and change the goals needed to win
for the active mode (`Teams` or `FFA`) without editing `config.yml`, updating open Board UI/HUD
live and finishing a running round immediately if lowered to or below a side's current score.
**Setup:** Build `1.6.0`; an opped player (plus a non-op player if testing permission denial).
**Steps:**
1. While `IDLE` in Teams mode, run `/lo goalstowin` and confirm it reports `Goals to win (Teams):
   13`.
2. Run `/lo goalstowin 0` and `/lo goalstowin 26` and confirm both are rejected cleanly (`between 1
   and 25`).
3. Run `/lo goalstowin 7`, then switch to FFA (`/lo mode ffa`) and confirm `/lo goalstowin`
   reports `5` (FFA's own target). Set FFA to `/lo goalstowin 3`, switch back to `/lo mode teams`,
   and confirm Teams still remembers `7`.
4. Start a round (`/lo reroll`), open the Board UI (`/lo board`), and confirm the Clock says
   `First to 7 wins` and team headers show `0 / 7 goals`.
5. Claim 2 goals for your side, then run `/lo goalstowin 2`. Confirm chat announces `Goals to win
   (Teams) set to 2.` followed immediately by your side winning the lockout and freezing claims.
**Expected:** `/lo goalstowin` updates the active mode's win target (1–25), remembers each mode's
target across `/lo mode` switches until `/lo reload`, updates UI/HUD immediately, and ends a
running round if lowered to or below a side's score.
**Watch for:** non-ops able to run `/lo goalstowin`, stale target on Board UI/HUD, or round not
ending when lowered to a side's current claim count.
**Report back:** pass/fail for validation, per-mode memory, live UI update, and mid-round win
trigger, plus any console errors.
**Status:** pass (owner, 1.6.0)

### T-077 — Op-only `/lo forcewin <team|player>` command

**Change:** Added admin command `/lo forcewin <team|player>` to force-end a `RUNNING` round and
award the win to the specified team (Teams mode) or player (FFA mode), freezing claims,
broadcasting the force-ended winner, and triggering finish sounds + victory fireworks.
**Setup:** Build `1.6.0`; an opped player; test in Teams mode (e.g. Red vs Blue) and optionally in
FFA mode.
**Steps:**
1. While `IDLE`, run `/lo forcewin red` and confirm it refuses (`No round is currently running.`).
2. Start a round (`/lo reroll`), verify tab-completion for `/lo forcewin <TAB>` suggests team IDs
   (`red`, `blue`), and run `/lo forcewin blue`.
3. Confirm chat broadcasts `[Force-ended by <Admin>] Blue wins the lockout!` with the score line,
   finish sounds play (win for Blue, lose for Red, spectator cue for spectators), victory fireworks
   launch for online Blue members, and open boards/sidebars show Blue as winner.
4. Try completing an unclaimed goal after `/lo forcewin` and confirm claims are frozen/rejected.
5. Run `/lo forcewin red` again while `FINISHED` and confirm it refuses (`No round is currently
   running.`).
6. Optional (FFA): run `/lo stop`, `/lo mode ffa`, `/lo reroll`, check `/lo forcewin <TAB>`
   suggests enrolled player names, and run `/lo forcewin <PlayerName>`.
**Expected:** `/lo forcewin` cleanly finishes a running round for the chosen team/player with
admin attribution, finish sounds, victory fireworks, updated UI/HUD, and locked claims; refuses
when idle or already finished.
**Watch for:** claims still accepted after force-win, missing sound/fireworks, wrong winner in
Board Clock/HUD, or console errors.
**Report back:** pass/fail for Teams force-win, claim freeze, idle/finished guards, and FFA
force-win (if tested), plus any console errors.
**Status:** pass (owner, 1.6.0)

### T-078 — Harmless coloured fireworks on goal completion and match victory

**Change:** Added `FireworkService` (`config-version: 8`, `match.fireworks-on-claim: true`) which
launches a firework coloured in the claiming side's colour on every goal claim and a 3-rocket
victory burst on match win, cancelling all explosion damage at `EventPriority.LOWEST` via a PDC
tag.
**Setup:** Build `1.6.0`; existing `config.yml` (v6/v7) in `plugins/TLockout/`; a player in Survival
mode.
**Steps:**
1. Start the server and confirm `config.yml` upgrades to v8 (`config.yml.old` backup) with
   `match.fireworks-on-claim: true`.
2. Start a round (`/lo reroll`) in Survival mode (e.g. set yourself to 1–2 hearts or stand inside
   a 2-block-tall enclosed ceiling/tunnel). Complete a goal.
3. Confirm a firework coloured to match your team/FFA colour launches from you and detonates, and
   confirm you take **zero** damage, **zero** knockback, and **zero** progress on `Take 200 damage`
   / `Opponent Team takes 100 Damage` goals (if on the board).
4. Complete the winning goal (or use `/lo goalstowin` / `/lo forcewin` to finish) and confirm a
   3-rocket staggered victory burst launches for the winning side's online players, also dealing
   zero damage.
5. Optional: verify a regular vanilla firework shot from a crossbow still deals normal damage to
   mobs/players (only tagged celebration fireworks are cancelled).
**Expected:** Every goal claim launches a coloured celebration firework and match win launches a
3-rocket victory burst; neither deals any damage to players or entities even in tight spaces or
counts toward damage/death goals.
**Watch for:** firework explosion hurting/killing the claimant in a cave, triggering damage-taken
or death goals, wrong colour, or console errors.
**Report back:** pass/fail for v8 upgrade, claim firework colour/visual, zero damage in Survival /
enclosed space, and victory burst, plus any console errors.
**Status:** pass (owner, 1.6.0)

### T-079 — `/lockout` prints the plugin version

**Change:** Updated `LockoutCommand#help` to display `TLockout v<version> - usage` (read from
`plugin.getPluginMeta().getVersion()`) when `/lockout` or `/lo` is typed with no arguments.
**Setup:** Build `1.6.1`; any player or server console.
**Steps:**
1. Run `/lockout` (and `/lo`) with no arguments.
2. Check the first line of the help output.
**Expected:** Header prints `TLockout v1.6.1 - usage` (matching the deployed jar version) followed
by the full command usage list.
**Watch for:** missing version string, wrong/stale version number, or console errors.
**Report back:** pass/fail plus exact first line printed if it differs.
**Status:** pass (owner, 1.6.1)
