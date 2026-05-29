<div align="center">
  <h1>🌋 LavaRise</h1>
  <p><b>The Ultimate Zero-Lag Rising-Lava Minigame Engine for Paper 1.21.11</b></p>

  ![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
  ![Paper](https://img.shields.io/badge/Paper-1.21.11-blue?style=for-the-badge)
  ![Build](https://img.shields.io/badge/Gradle-9-02303A?style=for-the-badge&logo=gradle)
  ![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)
  ![Engine](https://img.shields.io/badge/NMS-Optimized-red?style=for-the-badge)
</div>

<br>

**LavaRise** is a next-generation rising-lava minigame plugin engineered for **massive servers and massive arenas**. Where traditional plugins lean on heavy Bukkit APIs (`Block#setType`) and choke the main thread, LavaRise runs on a **zero-lag NMS engine** that writes block states straight into chunk sections — thousands of blocks per tick without breaking a sweat.

> **Zero dependencies.** No WorldEdit, no Multiverse. Drop it in and play. PlaceholderAPI and Vault are optional soft hooks.

---

## ✨ Features

- 🔥 **Zero-Lag NMS Engine** — writes `BlockState` directly into `LevelChunkSection`, bypassing physics & lighting, and ships one chunk packet per modified chunk to nearby players only.
- ♻️ **O(1) Smart Resets** — async map snapshot + sequential-pointer restore. No schematics, no binary search; huge maps revert in milliseconds.
- 🎮 **Three Game Modes** — classic **Minigame** (FFA), **Teams** (last team standing), and admin-controlled **Events** — plus a world-wide **Survival Challenge**.
- 🛡️ **Real Elimination** — players actually burn in the lava and drop their loot (PvP kills too); eliminated players become spectators.
- 📊 **Stats & Leaderboards** — persistent wins / games / kills / best survival time, with `/lr top` and PlaceholderAPI placeholders.
- 🧰 **In-Game Setup Wizard** — build arenas without touching YAML (`/lr create … save`).
- 💰 **Optional Vault Rewards** — pay the winner; soft hook, no hard dependency.
- 🎚️ **Rich, Tunable UI** — boss bar, action-bar HUD, flicker-free scoreboard, particles, sounds, proximity warnings — each configurable, with cadence knobs to protect TPS on big servers.
- ⚙️ **Dynamic Difficulty** — grace period, lava acceleration over time, speed scaling by player count, sudden death, and an optional shrinking world border.

---

## 📈 Performance

Live-tested on a real Paper 1.21.11 server (results scale with hardware):

| Scenario | Result |
|---|---|
| Fill an entire **429,000-block** arena in a **single tick** | **< 10 ms** (no TPS hit) |
| **600 arenas** firing concurrently (~20M block-writes/tick) | held **≈ 16–20 TPS**, no overload |
| **50 real players** in one active arena | steady **20.0 TPS** |
| Block-write throughput | **> 40 million blocks/second** on the main thread |

The engine is effectively never your bottleneck — player count (vanilla networking) is. For very large player counts, shard arenas across backends behind a proxy (Velocity).

---

## 🚀 Installation

1. Download `LavaRise-x.y.z.jar` from the [Releases](https://github.com/DeWost/LavaRise/releases) page.
2. Drop it into your server's `plugins/` folder.
3. Start the server — **Paper 1.21.11**, **Java 21** required.
4. On first start these are generated under `plugins/LavaRise/`:
   - `config.yml`, `messages.yml`, `stats.yml`, and an empty `arenas/` folder.
5. Create an arena (below) and play.

---

## ⚡ Quick Start — create an arena

**Option A — in-game wizard (recommended):**
```text
/lr create volcano        # start a setup session
/lr pos1                  # stand at one corner of the lava cuboid
/lr pos2                  # stand at the opposite corner
/lr setlobby              # where players wait
/lr setgamespawn          # where players spawn when the game starts
/lr setspectator          # where eliminated players watch from
/lr save                  # writes arenas/volcano.yml + makes it joinable
```
Then `/lr join volcano`.

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
Reload with `/lr reload`, then `/lr join volcano`.

---

## 🎮 Commands

Base command `/lavarise` (aliases `/lr`, `/lava`).

| Command | Permission | Description |
|---|---|---|
| `/lr join <arena>` | `lavarise.play` | Join an arena |
| `/lr leave` | `lavarise.play` | Leave your current game |
| `/lr list` | `lavarise.play` | Open the arena browser GUI |
| `/lr stats [player]` | `lavarise.play` | View statistics |
| `/lr top [wins\|kills\|time]` | `lavarise.play` | Leaderboards |
| `/lr create/pos1/pos2/setlobby/setgamespawn/setspectator/save` | `lavarise.admin` | Arena setup wizard |
| `/lr delete <arena>` | `lavarise.admin` | Delete an arena |
| `/lr start \| stop <arena>` | `lavarise.admin` | Force-start / reset a game |
| `/lr event <start\|pause\|resume\|stop> <arena>` | `lavarise.admin.event` | Admin-event control |
| `/lr survival <start\|stop> [world]` | `lavarise.admin` | World-wide survival challenge |
| `/lr reload` | `lavarise.admin` | Reload configuration |
| `/lr stress <arena> <blocksPerTick>` | `lavarise.admin` | Engine benchmark |

---

## 🕹️ Game Modes

Set per-arena via `game-mode`:

- **`minigame`** — classic free-for-all, last player standing. Set `modes.minigame.teams-enabled: true` for **last-team-standing** play (auto-balanced teams, friendly fire disabled, every winning member credited).
- **`admin_event`** — manually run by staff: `start / pause / resume / stop`, server-wide broadcasts, and an optional **Vault** reward for the winner. Pausing freezes the lava with continuous timing.
- **`survival_challenge`** — world-wide rising lava around spawn (configurable radius); no arena needed, started with `/lr survival start`.

---

## ⚙️ Configuration Highlights

Everything lives in `config.yml` (fully commented). Key sections:

- **`performance`** — `max-blocks-per-tick`, `preload-chunks`.
- **`arena-defaults`** — defaults for new arenas (players, countdowns, lava Y-range, pvp, keep-inventory, hunger).
- **`gameplay`** — `grace-period`, `acceleration`, `dynamic-speed`, `sudden-death`, `world-border`, `block-give` (pillar blocks), and the starting `kit`.
- **`rewards.win-commands`** — console commands run on win (`{winner}`, `{arena}`).
- **`modes`** — teams, survival radius/worlds, event broadcasts & reward amount.
- **`effects`** — boss bar, action bar, scoreboard, particles, sounds — each toggleable, with `update-interval` / `interval` cadence knobs.

### High-population preset (hold 20 TPS)
Trim cosmetics when running many players per arena:
```yaml
effects:
  bossbar: {enabled: false}
  scoreboard: {enabled: false}
  particles: {enabled: false}
  actionbar: {enabled: true, update-interval: 40}
  sounds: {enabled: false}
```

---

## 🔌 PlaceholderAPI

With PlaceholderAPI installed:

| Placeholder | Returns |
|---|---|
| `%lavarise_wins%` / `%lavarise_games%` / `%lavarise_kills%` / `%lavarise_deaths%` | Player stats |
| `%lavarise_best_time%` / `%lavarise_winrate%` | Best survival time / win-rate % |
| `%lavarise_players_alive_<arena>%` | Players alive in an arena |
| `%lavarise_state_<arena>%` | Arena state (Waiting/Active/…) |
| `%lavarise_lava_level_<arena>%` | Current lava Y |

---

## 🧠 How it beats the competition

Standard rising-lava plugins call `Block#setType()` — physics, block updates and lighting **per block** — and freeze the server parsing schematics on reset. LavaRise instead:

1. **Async snapshot** — reads the arena off-thread at game start; no main-thread locking.
2. **Batch chunk updates** — `FastBlockSetter` injects block states into NMS chunk sections and sends one `ClientboundLevelChunkWithLightPacket` per modified chunk, only to players who can see it.
3. **Smart revert** — restores from the snapshot via an O(1) sequential pointer; practically 0 ms on the main thread.

All version-sensitive NMS lives in a single class (`engine/nms/FastBlockSetter.java`) and degrades gracefully if a future Paper build changes the packet API.

---

## 🏗️ Building from source

```bash
git clone https://github.com/DeWost/LavaRise.git
cd LavaRise
./gradlew build          # compile + tests + shaded jar  → build/libs/
./gradlew runServer      # launch a Paper 1.21.11 test server
./gradlew jacocoTestReport
```
Requires JDK 21. The committed Gradle 9 wrapper handles everything; the first build downloads the Paper dev bundle (needs network).

---

## 🛠️ Troubleshooting

- **Lava stops rising near the edges / gaps appear** — those chunks aren't loaded. Keep `performance.preload-chunks: true` and the arena within loaded chunks; the console warns once per 25k skipped blocks.
- **Live lava not visible but blocks are solid** — a Paper update changed the chunk-packet API. Blocks still apply server-side; update the plugin (a one-time warning is logged).
- **Survival challenge fills slowly** — world-wide volumes are huge; raise `performance.max-blocks-per-tick` substantially.
- **Vault rewards do nothing** — install Vault + an economy plugin; startup logs `Vault economy hooked` when detected.
- **TPS dips with many players** — raise `effects.actionbar.update-interval` / `effects.particles.interval` or use the high-population preset above.

---

## 🤝 Contributing

PRs welcome — see [`CONTRIBUTING.md`](CONTRIBUTING.md). Keep NMS changes isolated to `FastBlockSetter`, avoid allocation in hot paths, and run `./gradlew build` before submitting. Release notes live in [`CHANGELOG.md`](CHANGELOG.md).

## ⚖️ License

Distributed under the MIT License. See [`LICENSE`](LICENSE).

---
<div align="center">
  <i>Crafted with passion for absolute server performance.</i>
</div>
