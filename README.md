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

- `/lavarise join <arena>` - Join an active arena.
- `/lavarise leave` - Leave your current arena.
- `/lavarise list` - List all configured arenas.
- `/lavarise admin` - Open the interactive Setup GUI.

## 📝 Configuration

Arenas are stored as simple YAML files inside `plugins/LavaRise/arenas/`.
Example `arena1.yml`:
```yaml
name: "Volcano Core"
minX: -50
minZ: -50
maxX: 50
maxZ: 50
lavaStartY: -60
lavaMaxY: 100
lavaRiseInterval: 20
lavaRiseAmount: 1
maxPlayers: 100
```

## ⚖️ License

Distributed under the MIT License. See `LICENSE` for more information.

---
<div align="center">
  <i>Crafted with passion for absolute server performance.</i>
</div>
