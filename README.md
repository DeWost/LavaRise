<div align="center">

# 🌋 LavaRise

### The Ultimate Zero-Lag Rising-Lava Minigame Engine for Paper 1.21.11

[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21.11-blue?style=for-the-badge&logo=minecraft)](https://papermc.io/)
[![Gradle](https://img.shields.io/badge/Gradle-9-02303A?style=for-the-badge&logo=gradle)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)
[![NMS](https://img.shields.io/badge/Engine-NMS%20Optimized-red?style=for-the-badge)](#-how-it-beats-the-competition)

<p>
  <b>Thousands of blocks per tick.</b> &nbsp;•&nbsp; <b>Zero dependencies.</b> &nbsp;•&nbsp; <b>Three game modes + random arenas.</b>
</p>

</div>

---

**LavaRise** is a next-generation rising-lava minigame plugin engineered for **massive servers and massive arenas**. Where traditional plugins lean on heavy Bukkit APIs (`Block#setType`) and choke the main thread, LavaRise runs on a **zero-lag NMS engine** that writes block states straight into chunk sections.

> [!NOTE]
> **No dependencies required.** No WorldEdit, no Multiverse — drop it in and play. PlaceholderAPI and Vault are *optional* soft hooks.

## 📑 Table of Contents

- [Features](#-features)
- [Performance](#-performance)
- [Installation](#-installation)
- [Quick Start](#-quick-start--create-an-arena)
- [Commands](#-commands)
- [Game Modes](#%EF%B8%8F-game-modes)
- [The Match Flow](#-the-match-flow)
- [Random & Procedural Arenas](#-random--procedural-arenas)
- [Configuration](#%EF%B8%8F-configuration-highlights)
- [PlaceholderAPI](#-placeholderapi)
- [How It Works](#-how-it-beats-the-competition)
- [Building From Source](#%EF%B8%8F-building-from-source)
- [Troubleshooting](#%EF%B8%8F-troubleshooting)

---

## ✨ Features

| | Feature | Description |
|:--:|---|---|
| 🔥 | **Zero-Lag NMS Engine** | Writes `BlockState` directly into `LevelChunkSection`, bypassing physics & lighting; one chunk packet per modified chunk, sent only to nearby players. |
| ♻️ | **O(1) Smart Resets** | Async map snapshot + sequential-pointer restore. No schematics, no binary search — huge maps revert in milliseconds. |
| 🎮 | **Three Game Modes** | **Minigame** (FFA / Teams), admin-controlled **Events**, and world-wide **Survival Challenge**. |
| 🎲 | **Random & Procedural Arenas** | Quick-join random matchmaking, random map rotation, and on-the-fly arenas generated at random world locations. |
| 🛡️ | **Real Elimination** | Players actually burn in the lava and drop their loot (PvP too); the eliminated become spectators. |
| 📊 | **Stats & Leaderboards** | Persistent wins / games / kills / best survival time, `/lr top`, and PlaceholderAPI placeholders. |
| 🎒 | **Kits & Loadouts** | Define any number of kits in config; players pick one with `/lr kit` via a GUI. |
| 🧰 | **In-Game Setup Wizard** | Build arenas without touching YAML (`/lr create … save`). |
| 💰 | **Rewards** | Per-win, per-kill and per-death command hooks, plus optional Vault payouts (soft hook). |
| 🪄 | **Quality of Life** | Auto-pickup, auto-smelt, height-gated PvP, and an async GitHub update checker. |
| 🎚️ | **Tunable UI** | Boss bar, action-bar HUD, flicker-free scoreboard, particles, sounds, proximity warnings — each configurable with TPS-protecting cadence knobs. |
| ⚙️ | **Dynamic Difficulty** | Grace period, lava acceleration, speed scaling by player count, sudden death, and an optional shrinking world border. |

## 📈 Performance

Live-tested on a real Paper 1.21.11 server (results scale with hardware):

| Scenario | Result |
|---|:--:|
| Fill an entire **429,000-block** arena in a **single tick** | **< 10 ms** |
| **600 arenas** firing concurrently (~20M block-writes/tick) | **≈ 16–20 TPS**, no overload |
| **50 real players** in one active arena | **steady 20.0 TPS** |
| Main-thread block-write throughput | **> 40,000,000 blocks/sec** |

> [!TIP]
> The engine is effectively never your bottleneck — player count (vanilla networking) is. For very large player counts, shard arenas across backends behind a proxy such as **Velocity**. Benchmark your own hardware with `/lr stress <arena> <blocksPerTick>`.

## 🚀 Installation

1. Download `LavaRise-x.y.z.jar` from the [**Releases**](https://github.com/DeWost/LavaRise/releases) page.
2. Drop it into your server's `plugins/` folder.
3. Start the server — **Paper 1.21.11** + **Java 21** required.
4. First start generates `plugins/LavaRise/`: `config.yml`, `messages.yml`, `stats.yml`, and an empty `arenas/` folder.
5. Create an arena (below) — or just run `/lr random` to play instantly.

## ⚡ Quick Start — create an arena

**Option A — in-game wizard (recommended):**
```text
/lr create volcano        # start a setup session
/lr pos1                  # stand at one corner of the lava cuboid
/lr pos2                  # ...and the opposite corner
/lr setlobby              # where players wait
/lr setgamespawn          # where players spawn when the game starts
/lr setspectator          # where eliminated players watch from
/lr save                  # writes arenas/volcano.yml + makes it joinable
```

> [!TIP]
> In a hurry? Skip setup entirely — **`/lr random`** generates and drops you into a fresh arena anywhere in the world, and **`/lr join`** (no name) quick-joins a random open game.

**Option B — manual file** at `plugins/LavaRise/arenas/volcano.yml`:
```yaml
name: volcano
world: world
corner1: {x: -50.0, y: -64.0, z: -50.0}
corner2: {x: 50.0,  y: 100.0, z: 50.0}
lobby-spawn:     {x: 0.5, y: 80.0,  z: 0.5}
game-spawn:      {x: 0.5, y: 65.0,  z: 0.5}
spectator-spawn: {x: 0.5, y: 120.0, z: 0.5}
min-players: 2
max-players: 16
lava-rise-interval: 60   # ticks (20 = 1s)
lava-rise-amount: 1
lava-start-y: -64
lava-max-y: 100
pvp: true
keep-inventory: false
hunger: true
game-mode: minigame      # minigame | survival_challenge | admin_event
```

## 🎮 Commands

Base command `/lavarise` — aliases **`/lr`**, **`/lava`**.

### Players &nbsp;<sub>`lavarise.play`</sub>
| Command | Description |
|---|---|
| `/lr join [arena]` | Join an arena — **no name = quick-join a random open game** |
| `/lr random` | Generate & join a fresh random (procedural) arena |
| `/lr kit` | Choose your kit / loadout (GUI) |
| `/lr vote` | Vote for the lobby's kit (GUI) |
| `/lr leave` | Leave your current game |
| `/lr list` | Open the arena browser GUI |
| `/lr stats [player]` | View statistics |
| `/lr top [wins\|kills\|time]` | Leaderboards |

### Admins &nbsp;<sub>`lavarise.admin`</sub>
| Command | Description |
|---|---|
| `/lr setup <name> [radius]` | **One-command arena** — builds a ready-to-play arena centred where you stand |
| `/lr create · pos1 · pos2 · setlobby · setgamespawn · setspectator · save` | Arena setup wizard (manual, fine-grained) |
| `/lr delete <arena>` | Delete an arena |
| `/lr start \| stop <arena>` | Force-start / reset a game |
| `/lr skip <arena>` | Skip a waiting lobby to a 3-second countdown |
| `/lr freeze <arena>` | Freeze / resume the lava mid-game |
| `/lr event <start\|pause\|resume\|stop> <arena>` | Admin-event control |
| `/lr survival <start\|stop> [world]` | World-wide survival challenge |
| `/lr reload` | Reload configuration |
| `/lr stress <arena> <blocksPerTick>` | Engine benchmark |

## 🕹️ Game Modes

Set per-arena via `game-mode`:

- **`minigame`** — classic free-for-all, last player standing. Set `modes.minigame.teams-enabled: true` for **last-team-standing** play (auto-balanced teams, friendly fire off, every winning member credited).
- **`admin_event`** — staff-run: `start / pause / resume / stop`, server-wide broadcasts, and an optional **Vault** reward for the winner. Pausing freezes the lava with continuous timing.
- **`survival_challenge`** — world-wide rising lava around spawn (configurable radius); no arena needed, started with `/lr survival start`.

## 🎬 The Match Flow

Every match runs through a fully-signposted flow — each phase has its own HUD and feedback:

1. **Lobby (fill phase)** — players join and get a **kit-vote compass** in hand. Once minimum players are present a **boss bar** counts down (`Starting in Ns — X/max`), letting latecomers in. It **fast-forwards when the arena fills** and aborts back to waiting if players drop below the minimum.
2. **Countdown** — a tense per-second title + sound countdown; the boss-bar number flows straight into it (no jump).
3. **Grace window** — players are teleported in, given the winning **kit**, and the arena is **sealed** (a per-player border keeps everyone inside). A green **`🛡 Grace: Xs`** boss bar counts down; lava is frozen and **PvP is locked** so everyone can gear up untouched.
4. **Rising lava** — PvP opens with an announcement, the boss bar switches to the **lava height** bar, and the engine floods the arena from the surface up. Living players get a lava/alive/time **action bar** + a proximity warning; falling below the floor eliminates you (**void guard**).
5. **Spectating** — eliminated players fly as spectators with their own **`👁 Spectating`** HUD, following the match live.
6. **Results & reset** — a **top-3 podium** is broadcast and every player sees their **placement, survival time and kills**; fireworks for the winner; then the terrain is restored and the next match begins.

## 🎲 Random & Procedural Arenas

Three layers of "never play the same game twice":

- **Random matchmaking** — `/lr join` with no name drops you into a random open arena.
- **Random rotation** — finished procedural arenas are torn down and restored, so the next game spawns somewhere new.
- **Procedural generation** — `/lr random` (and auto quick-join) carves a brand-new arena at a random spot in the world, plays it, then cleanly reverts the terrain.

Tune it under the `procedural` section of `config.yml` (`radius`, `spawn-area`, lava range, `auto-on-quickjoin`).

## 🤖 Autonomous Auto-Arena

Set `auto-arena.enabled: true` and the plugin runs **hands-free**: it always keeps one
open random arena ready and rotates forever — *generate → players join → auto-start →
reset → remove → generate the next* — with no admin commands.

- `auto-arena.check-interval` — how often (seconds) to ensure an open arena exists.
- `auto-arena.auto-join` — also pull online players who aren't in a game into the open arena.

Lava rises from the **surface** (top block) upward, mobs/animals don't spawn inside
arenas (`gameplay.deny-mob-spawns`), and the terrain is fully restored after each round.

The rotation runs as a **self-healing workflow**: each cycle is idempotent (it only
generates when no open arena exists), generation and auto-join are isolated so one
can't break the other, and a failing world is retried with **exponential backoff**
(1→2→4→8→16 cycles) instead of being hammered. After repeated failures the workflow
**halts with a clear console warning** rather than spamming — run `/lavarise reload`
to re-arm it once the world/config is fixed.

## ⚙️ Configuration Highlights

Everything lives in `config.yml` (fully commented). Key sections:

- **`general`** — language, debug, `update-check` (async GitHub release notice), `bstats` (anonymous metrics toggle).
- **`storage`** — stats backend: `yaml` (zero-setup, default) or `mysql` (network-wide, shared across a proxy) with `sync-interval` and connection settings; auto-falls back to YAML if MySQL is unreachable.
- **`performance`** — `max-blocks-per-tick`, `engine-interval-ticks`, `preload-chunks`.
- **`arena-defaults`** — defaults for new arenas (players, countdowns, lava Y-range, pvp, keep-inventory, hunger).
- **`gameplay`** — `grace-period`, `pvp-during-grace`, `pvp-after-height` (height-gated PvP), `auto-pickup`, `auto-smelt`, `deny-mob-spawns`, `arena-border` (per-player border that keeps players inside each arena — works independently per arena), `void-elimination` + `void-buffer` (out-of-bounds guard), `acceleration`, `dynamic-speed`, `sudden-death`, `world-border`, `block-give`.
- **`kits`** — KteRising's 7 kits out of the box (Classic, OP, Elytra, ElytraOP, Trident, TridentOP, UltraOP), each with `icon`, enchantable `items`, and a per-kit `countdown` (seconds before the lava rises — fair kits get a long build, OP kits start fast). Voted with `/lr vote` (lobby compass) or `/lr kit`.
- **`procedural`** — random/procedural arena generation (surface-rising lava, biome-aware placement).
- **`auto-arena`** — fully autonomous rotation: `enabled`, `check-interval`, `auto-join`. Keeps one open arena ready at all times and recycles it after each match (requires `procedural`).
- **`rewards`** — `win-commands` (`{winner}`,`{arena}`), `kill-commands` (`{killer}`,`{victim}`), `death-commands` (`{player}`).
- **`modes`** — teams, survival radius/worlds, event broadcasts & Vault reward amount.
- **`effects`** — boss bar, action bar, scoreboard, particles, sounds — each toggleable, with `update-interval` / `interval` cadence knobs.

> [!TIP]
> **High-population preset (hold 20 TPS):** trim cosmetics when running many players per arena.
> ```yaml
> effects:
>   bossbar: {enabled: false}
>   scoreboard: {enabled: false}
>   particles: {enabled: false}
>   actionbar: {enabled: true, update-interval: 40}
>   sounds: {enabled: false}
> ```

## 🔌 PlaceholderAPI

With PlaceholderAPI installed:

| Placeholder | Returns |
|---|---|
| `%lavarise_wins%` · `%lavarise_games%` · `%lavarise_kills%` · `%lavarise_deaths%` | Player stats |
| `%lavarise_best_time%` · `%lavarise_winrate%` | Best survival time · win-rate % |
| `%lavarise_session_kills%` · `%lavarise_session_survived%` | **This match:** the player's kills · seconds survived |
| `%lavarise_session_status%` | This match: `Alive` / `Spectating` (empty if not in a game) |
| `%lavarise_players_alive_<arena>%` | Players alive in an arena |
| `%lavarise_state_<arena>%` | Arena state (Waiting / Active / …) |
| `%lavarise_lava_level_<arena>%` | Lava **height** (blocks risen) |
| `%lavarise_lava_percent_<arena>%` · `%lavarise_lava_y_<arena>%` | Progress to max % · raw world Y |

## 🧠 How it beats the competition

Standard rising-lava plugins call `Block#setType()` — physics, block updates and lighting **per block** — and freeze the server parsing schematics on reset. LavaRise instead:

1. **Async snapshot** — reads the arena off-thread at game start; no main-thread locking.
2. **Batch chunk updates** — `FastBlockSetter` injects block states into NMS chunk sections and sends one `ClientboundLevelChunkWithLightPacket` per modified chunk, only to players who can see it.
3. **Smart revert** — restores from the snapshot via an O(1) sequential pointer; practically 0 ms on the main thread.

> [!IMPORTANT]
> All version-sensitive NMS lives in a single class (`engine/nms/FastBlockSetter.java`) and **degrades gracefully** if a future Paper build changes the packet API — blocks still apply server-side.

## 🏗️ Building from source

```bash
git clone https://github.com/DeWost/LavaRise.git
cd LavaRise
./gradlew build          # compile + tests + shaded jar → build/libs/
./gradlew runServer      # launch a Paper 1.21.11 test server
./gradlew jacocoTestReport
```
Requires JDK 21. The committed Gradle 9 wrapper handles everything; the first build downloads the Paper dev bundle (needs network).

## 🛠️ Troubleshooting

> [!WARNING]
> **Lava stops rising near the edges / gaps appear** — those chunks aren't loaded. Keep `performance.preload-chunks: true` and the arena within loaded chunks; the console warns once per 25k skipped blocks.

- **Live lava not visible but blocks are solid** — a Paper update changed the chunk-packet API. Blocks still apply server-side; update the plugin (a one-time warning is logged).
- **Survival / procedural fills slowly** — world-wide volumes are huge; raise `performance.max-blocks-per-tick`.
- **Vault rewards do nothing** — install Vault + an economy plugin; startup logs `Vault economy hooked` when detected.
- **TPS dips with many players** — raise `effects.actionbar.update-interval` / `effects.particles.interval`, or use the high-population preset above.

## 🤝 Contributing

PRs welcome — see [`CONTRIBUTING.md`](CONTRIBUTING.md). Keep NMS changes isolated to `FastBlockSetter`, avoid allocation in hot paths, and run `./gradlew build` before submitting. Release notes live in [`CHANGELOG.md`](CHANGELOG.md).

## ⚖️ License

Distributed under the MIT License. See [`LICENSE`](LICENSE).

---
<div align="center">
  <sub>Crafted with passion for absolute server performance. 🌋</sub>
</div>
