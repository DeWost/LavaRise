# 🌋 LavaRise — The Zero-Lag Rising-Lava Minigame Engine

**Thousands of blocks per tick · Zero dependencies · Three game modes + random arenas**

LavaRise is a next-generation rising-lava minigame plugin built for **massive servers and massive arenas**. Where traditional plugins lean on heavy Bukkit calls (`Block#setType`) and choke the main thread, LavaRise runs on a **zero-lag NMS engine** that writes block states straight into chunk sections — flooding a 429,000-block arena in **under 10 ms**.

> **No dependencies required.** No WorldEdit, no Multiverse — drop it in and play. PlaceholderAPI and Vault are *optional* soft hooks.

---

## ✨ Features

- **🔥 Zero-Lag NMS Engine** — writes `BlockState` directly into `LevelChunkSection`, bypassing physics & lighting; one chunk packet per modified chunk, sent only to nearby players.
- **♻️ O(1) Smart Resets** — async map snapshot + sequential-pointer restore. No schematics — huge maps revert in milliseconds.
- **🎮 Three Game Modes** — **Minigame** (FFA / Teams), admin-controlled **Events**, and world-wide **Survival Challenge**.
- **🎲 Random & Procedural Arenas** — quick-join matchmaking, random map rotation, and on-the-fly arenas carved at random world locations. Set `auto-arena.enabled: true` for a fully **hands-free** rotation.
- **⚔️ Battle-Royale Hype** — killstreak & multi-kill call-outs, bounties on top players, combat-log protection, a glowing **final showdown**, and optional **supply drops**.
- **🛡️ Real Elimination** — players burn in the lava and drop their loot (PvP too); the eliminated become spectators with a live HUD.
- **📊 Stats & Leaderboards** — persistent wins / games / kills / best survival time, `/lr top`, and PlaceholderAPI placeholders.
- **🎒 Kits & Loadouts** — 7 ready-made kits + per-player custom kits; players pick via GUI or lobby voting.
- **🧰 One-Command Setup** — `/lr setup <name>` builds a ready-to-play arena where you stand.
- **🎚️ Fully Tunable UI** — boss bar, action-bar HUD, flicker-free scoreboard, particles, sounds — each with TPS-protecting cadence knobs.
- **⚙️ Dynamic Difficulty** — grace period, lava acceleration, speed scaling by player count, sudden death, and an optional shrinking world border.
- **🌐 Network-Ready** — MySQL storage shared across a proxy, party & matchmaking-queue systems, and bStats charts.

## 📈 Performance

Live-tested on a real Paper 1.21.11 server (scales with hardware):

| Scenario | Result |
|---|---|
| Fill a **429,000-block** arena in a single tick | **< 10 ms** |
| **600 arenas** firing concurrently (~20M writes/tick) | **≈ 16–20 TPS** |
| **50 real players** in one active arena | **steady 20.0 TPS** |
| Main-thread block-write throughput | **> 40,000,000 blocks/sec** |

The engine is effectively never your bottleneck — player count (vanilla networking) is.

## 🚀 Installation

1. Download `LavaRise-x.y.z.jar` and drop it in your server's `plugins/` folder.
2. Start the server — **Paper 1.21.11** and **Java 21** required.
3. First start generates `plugins/LavaRise/` with a fully-commented `config.yml`.
4. Run `/lr setup arena1 50` to build an arena where you stand — or just `/lr random` to play instantly.

## 🎮 Core Commands

| Command | What it does |
|---|---|
| `/lr join [arena]` | Join an arena — no name quick-joins a random open game |
| `/lr play` | MCPvP-style: pick a kit, drop straight into a game |
| `/lr random` | Generate & join a fresh procedural arena |
| `/lr queue` · `/lr party` | Matchmaking queue · group up with friends |
| `/lr setup <name> [radius]` | One-command arena builder (admin) |
| `/lr top` · `/lr stats` | Leaderboards · player statistics |

Full command, permission, config and PlaceholderAPI reference: **[GitHub README](https://github.com/DeWost/LavaRise#readme)**.

## 🔗 Links

- **Source & full docs:** https://github.com/DeWost/LavaRise
- **Issues & support:** https://github.com/DeWost/LavaRise/issues
- **License:** MIT

---

*Crafted with passion for absolute server performance. 🌋*
