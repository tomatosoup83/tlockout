# TLockout

A server-side **Lockout challenge** plugin for **Paper (Minecraft 26.3)** designed for completely vanilla clients.

Generate a 5×5 board of 25 Minecraft goals, race to complete them in **Teams** or **Free-For-All (FFA)** mode, and lock every claimed goal out for everyone else. First side to reach the win target claims victory.

---

## Features

- **Teams & Free-For-All (FFA) modes** — Play team vs. team (configurable concrete colours, friendly-fire toggle, team-shared progress & claims) or switch live to FFA (`/lo mode ffa`) where every online player gets their own colour and competes individually.
- **267 Goal Templates** — Config-driven catalogue (`goals.yml`) with 256 random-pool goals and 11 custom-only templates across 35+ tracker families:
  - Item obtain, quantity, durability, item sets, and full-inventory challenges
  - Armor sets, dyed leather pieces, enchanted armor, pumpkin timer, wolf armor
  - Mob kills (specific, unique hostile, arthropods/undead/raids, dyed sheep, conditional kills) & PvP kills
  - Dimensions, biomes, height/bedrock/Nether-roof positions, riding, sprinting
  - Consuming foods, unique foods, advancements, status effects, milk cleansing
  - Workstations, brewing potions, mining blocks, interactive block/item actions, banner shields
  - Deaths by specific causes, damage taken/dealt, XP levels, empty hunger
  - Opponent-triggered goals (2-side matches and 2-player FFA)
- **Smart Board Generation** — Deterministic seed support, anti-clustering group/type caps, and automatic **World Feasibility Scanning** around Overworld spawn so unreachable biome/structure goals are filtered out.
- **Interactive 5×5 Board GUI** — Live-updating chest UI showing unclaimed goal icons, glinted team-coloured concrete blocks for claimed goals, team/FFA rosters, scores, op-only quick Reroll button, and a Player Tracker shortcut.
- **Board Compass & Player Tracking** — Tagged, undroppable compass given to participants:
  - **Right-click**: Open the live 5×5 board
  - **Shift + Right-click**: Open the **Player Tracker** menu to point your compass toward any participating teammate or opponent (with live distance and cross-dimension last-known location tracking)
- **Live HUD (Sidebar & Boss Bar)** — Pin up to 5 target goals from the board (shared per team or personal in FFA) to see ranked progress on your sidebar and top-target progress on a live boss bar.
- **Match Controls & Polish** — Pre-round countdown with player freeze, toggleable clean-match player reset (`autoreset`), per-round `keepinventory` without touching world gamerules, mid-round joining/admin team moves, live `goalstowin` and `forcewin` controls, configurable sound cues, and harmless coloured celebration fireworks.

---

## Requirements

- **Server:** Paper `26.3+`
- **Java:** JDK `25`
- **Clients:** Vanilla Minecraft (no client-side mods required)

---

## Building from Source

Requires **Java 25** and **Gradle 9.9.2**:

```bash
gradle jar
```

The built plugin JAR is written to `build/libs/TLockout-1.6.1.jar`.

Drop the JAR into your Paper server's `plugins/` folder and start/restart the server. On startup, `config.yml` and `goals.yml` are generated automatically in `plugins/TLockout/`.

> **Optional SFTP deploy:** Copy `.deploy.env.example` to `.deploy.env` (`chmod 600 .deploy.env`), fill in your SFTP credentials, and run `./deploy.sh` to build and upload the JAR directly to a remote server.

---

## Quick Start

### Teams Mode (default)
1. Players join a team with `/lo join red` or `/lo join blue` (or an admin balances everyone with `/lo team auto`).
2. Start the match with `/lo start` (or `/lo start <seed>` for a specific board; add `--allow-empty` for solo testing).
3. Right-click your **Board Compass** (or run `/lo board`) to inspect the 25 goals, click goals to pin them to your team's HUD, and Shift + Right-click the compass to track players.
4. First team to **13 goals** wins (configurable via `win.goals-to-win` or `/lo goalstowin <1-25>`).

### Free-For-All (FFA) Mode
1. Switch mode between rounds with `/lo mode ffa` (or set `mode: ffa` in `config.yml`).
2. Online players are automatically enrolled on `/lo start` (players can pick a colour with `/lo join [color]` or opt out to spectate with `/lo leave`).
3. First player to **5 goals** wins (or highest score if all 25 goals are claimed first).

---

## Commands & Permissions

Command alias: `/lockout` or `/lo`

### Player Commands (`tlockout.play`, default: everyone)

| Command | Description |
| --- | --- |
| `/lo` | Show plugin version and command help |
| `/lo join [team\|color]` | Join a team (Teams mode) or join/pick a colour in FFA |
| `/lo leave` | Leave your team (between rounds) or opt out of FFA to spectate |
| `/lo board` | Open the live 5×5 Lockout board GUI |
| `/lo compass` | Receive a replacement Board Compass during an active/finished round |
| `/lo status` | Print current seed, scores, rankings, and claimed goals |
| `/lo track <1-25\|clear>` | Pin/unpin a goal (1–25) to your HUD, or clear tracked goals |
| `/lo team list` | View current team or FFA rosters |

### Admin Commands (`tlockout.admin`, default: op)

| Command | Description |
| --- | --- |
| `/lo start [seed] [--allow-empty]` | Start a round with optional seed (`--allow-empty` skips empty-side checks) |
| `/lo reroll [seed]` | Immediately stop and start a fresh board (skips countdown) |
| `/lo stop` | Cancel countdown or stop the current round |
| `/lo mode [teams\|ffa]` | View or switch between Teams and FFA mode (between rounds) |
| `/lo goalstowin [1-25]` | View or change the goals needed to win for the active mode |
| `/lo forcewin <team\|player>` | Force-end a running round and award victory to a team/player |
| `/lo team auto` | Randomly balance all online players across teams |
| `/lo team set <player> <team>` | Assign or move a player to a team (works before or mid-round) |
| `/lo autoreset [on\|off\|toggle]` | Toggle full player state reset on round start |
| `/lo keepinventory [on\|off\|toggle]` | Toggle keeping inventory & XP on death during active rounds |
| `/lo reload` | Reload `config.yml` and `goals.yml` (stops any active round and clears world-scan cache) |

---

## Configuration

All settings live in `plugins/TLockout/config.yml` and `plugins/TLockout/goals.yml`:

- **`config.yml`** — Mode (`teams` / `ffa`), teams & concrete colours, friendly-fire, win targets (`goals-to-win` & `ffa-goals-to-win`), match toggles (`auto-reset`, `keep-inventory`, `countdown-seconds`, `freeze-during-countdown`, `fireworks-on-claim`), world-scan radius, evaluation tick interval, chat prefix (MiniMessage), and sound cues.
- **`goals.yml`** — Full catalogue of goal templates, weights, variants, pools (`random` / `custom-only`), team-size rules, and biome/structure requirements.

Both files carry version headers (`config-version`, `goals-version`). When upgrading the plugin, older configs are automatically backed up (`.old` / `.v<n>.bak`) and migrated to the latest bundled defaults.

---

## Project Structure

```text
src/main/
├── java/dev/tlockout/
│   ├── TLockout.java              # Plugin bootstrap & service wiring
│   ├── ChallengeManager.java      # Round lifecycle, claims, win & tie logic
│   ├── Board.java                 # 5x5 board state & deterministic generation
│   ├── BoardGui.java              # Interactive 5x5 chest UI
│   ├── BoardCompass.java          # Tagged compass item & lore rosters
│   ├── HudService.java            # Per-player sidebar & boss bar HUD
│   ├── TrackingService.java       # Event & tick evaluation for goals
│   ├── PlayerTrackerService.java  # Live compass player tracking
│   ├── PlayerTrackerGui.java      # Player selector GUI
│   ├── PlayerResetService.java    # Clean-match player reset
│   ├── CountdownService.java      # Pre-round countdown & freeze
│   ├── KeepInventoryService.java  # Round-scoped keep-inventory
│   ├── FireworkService.java       # Harmless claim & victory fireworks
│   ├── SoundService.java          # Configurable match sound cues
│   ├── SideManager.java           # Teams & FFA side management
│   ├── LockoutCommand.java        # /lockout command & tab completion
│   └── goal/
│       ├── GoalSpec.java          # Goal template specification
│       ├── GoalGroups.java        # Anti-clustering & predecessor rules
│       ├── WorldFeasibilityScanner.java
│       └── tracker/               # 35+ modular goal family trackers
└── resources/
    ├── plugin.yml
    ├── config.yml
    └── goals.yml
```

---

## Credits

- **[Lockout (Fabric)](https://github.com/marin774/lockout-fabric)** by **Mar1n (`marin774`)** — TLockout's goal catalogue, anti-clustering rules, world feasibility scanning, and overall game design are ported/adapted from the original Fabric mod (inspired by Smallant's Lockout Bingo mod).
