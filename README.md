<div align="center">
  <h1>🌋 LavaRise</h1>
  <p><b>The Ultimate Zero-Lag Rising Lava Minigame Engine for Paper 1.21.11</b></p>
  
  ![Java Version](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
  ![Server API](https://img.shields.io/badge/Paper-1.21.11-blue?style=for-the-badge)
  ![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)
  ![Optimization](https://img.shields.io/badge/NMS-Optimized-red?style=for-the-badge&logo=apache)
</div>

<br>

**LavaRise** is a next-generation rising lava minigame plugin engineered specifically for massive servers and massive arenas. Unlike traditional plugins that rely on heavy Bukkit APIs causing massive TPS drops, LavaRise operates on a **Zero-Lag NMS Engine** capable of setting thousands of blocks per tick without breaking a sweat.

---

## 🔥 Features

- **Zero Dependencies**: Doesn't require WorldEdit or Multiverse. Drop it in and play.
- **NMS Block Engine**: Bypasses Bukkit's heavy lighting and physics calculations by writing directly to chunk sections (`LevelChunkSection`).
- **O(1) Smart Resets**: Uses a highly optimized Sequential Pointer algorithm for arena resets. No `Arrays.binarySearch`, no WorldEdit schematics. Harmlessly restores huge maps instantly.
- **Zero-Allocation Tracking**: Uses asynchronous block scanning and `ConcurrentHashMap` iteration for tracking players. Your Garbage Collector will thank you.
- **Rich UI**: BossBar for lava level tracking, ActionBars, custom Titles, and immersive particle/sound effects.
- **PlaceholderAPI Hook**: Fully supports `PlaceholderAPI` for dynamic text and leaderboards.

## 🚀 Installation

1. Download the latest `LavaRise.jar` from the [Releases](#) page.
2. Drop the jar file into your `/plugins` folder.
3. Start the server (Requires **Paper 1.21.11** and **Java 21**).
4. Configure your arenas in `/plugins/LavaRise/arenas`.

## ⚙️ How it beats the competition

Standard Rising Lava plugins typically use `Block#setType()` which calculates physics, block updates, and lighting for *every single block*. When resetting an arena, they usually freeze the server by parsing huge schematic files.

**LavaRise** takes a completely different approach:
1. **Async Snapshots**: At the start of a match, it reads the map asynchronously. No main-thread locking.
2. **Batch Chunk Updates**: The `FastBlockSetter` manually injects block data into NMS chunks and dispatches a single `ClientboundLevelChunkWithLightPacket` to clients.
3. **Smart Revert**: When the game ends, the arena reverts back to its original state by referencing the async snapshot using an O(1) sequential array lookup, practically taking 0 ms on the main thread.

## 🎮 Commands

Base command `/lavarise` (aliases `/lr`, `/lava`).

**Players**
- `/lr join <arena>` — Join an arena.
- `/lr leave` — Leave your current game.
- `/lr list` — Open the arena browser GUI.
- `/lr stats [player]` — View statistics.
- `/lr top [wins|kills|time]` — Leaderboard.

**Admins** (`lavarise.admin`)
- `/lr create <name>` then `/lr pos1`, `/lr pos2`, `/lr setlobby`, `/lr setgamespawn`, `/lr setspectator`, `/lr save` — interactive arena setup wizard.
- `/lr delete <arena>` — Remove an arena.
- `/lr start|stop <arena>` — Force-start / reset a game.
- `/lr event <start|pause|resume|stop> <arena>` — Admin-event control.
- `/lr survival <start|stop> [world]` — World-wide survival challenge.
- `/lr reload` — Reload config.
- `/lr stress <arena> <blocksPerTick>` — Engine benchmark.

## 🕹️ Game Modes

Set per-arena via `game-mode` (or arena default in `config.yml`):
- **minigame** — classic free-for-all, last player standing. Enable `modes.minigame.teams-enabled` for last-team-standing with friendly fire off.
- **admin_event** — manually started/paused/resumed by admins, optional Vault reward for the winner.
- **survival_challenge** — world-wide rising lava around spawn (radius configurable); started with `/lr survival start`.

## 📝 Configuration

Global settings live in `plugins/LavaRise/config.yml`; arenas are saved as YAML
under `plugins/LavaRise/arenas/` by the setup wizard. Example `volcano.yml`:
```yaml
name: Volcano
world: world
corner1: {x: -50.0, y: -64.0, z: -50.0}
corner2: {x: 50.0, y: 100.0, z: 50.0}
lobby-spawn: {x: 0.0, y: 80.0, z: 0.0}
game-spawn: {x: 0.0, y: 65.0, z: 0.0}
spectator-spawn: {x: 0.0, y: 120.0, z: 0.0}
min-players: 2
max-players: 16
lava-rise-interval: 60   # ticks
lava-rise-amount: 1
lava-start-y: -64
lava-max-y: 100
pvp: true
keep-inventory: false
hunger: true
game-mode: minigame
```

## 🛠️ Troubleshooting

- **Lava stops rising near the edges / gaps appear:** the edge chunks aren't loaded. Enable `performance.preload-chunks` (default on) and keep the arena within loaded chunks; the console warns once per 25k skipped blocks.
- **Live lava not visible but blocks are solid:** a Paper update changed the chunk-packet API — blocks still apply server-side; update the plugin. The warning is logged once.
- **Survival challenge is slow to fill:** world-wide volumes are huge; raise `performance.max-blocks-per-tick` substantially.
- **Vault rewards do nothing:** install Vault + an economy plugin; the console logs `Vault economy hooked` on startup when detected.

## ⚖️ License

Distributed under the MIT License. See `LICENSE` for more information.

---
<div align="center">
  <i>Crafted with passion for absolute server performance.</i>
</div>
