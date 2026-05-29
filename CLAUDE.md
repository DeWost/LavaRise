# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LavaRise is a **Paper 1.21.11 / Java 21 Minecraft plugin** implementing a rising-lava
minigame. Its defining characteristic is a **zero-lag NMS block engine**: instead of
`Block#setType()` (which triggers physics, block updates, and lighting per block), it writes
`BlockState` directly into `LevelChunkSection` and dispatches a single chunk packet per
modified chunk. Everything else in the codebase exists to feed that engine safely.

`group = dev.lavarise`, all source under `src/main/java/dev/lavarise/`.

## Build & Test Commands

```bash
./gradlew build            # compile + test + shadowJar (produces the plugin jar)
./gradlew test             # run JUnit 5 tests only
./gradlew jacocoTestReport # coverage report (build/reports/jacoco/test/html)
./gradlew shadowJar        # build the shaded/minimized plugin jar (the release artifact)
./gradlew runServer        # launch a Paper 1.21.11 test server with the plugin loaded (run-paper)
```

Run a single test class / method:
```bash
./gradlew test --tests "dev.lavarise.state.LobbyStateTest"
./gradlew test --tests "dev.lavarise.state.LobbyStateTest.testIsJoinable"
```

A **Gradle 9.0.0 wrapper is committed** (`gradle/wrapper/`), so `./gradlew` works out of the box.
The first build downloads the Paper dev bundle via **paperweight**, so it needs network access.

Build details (`build.gradle.kts`):
- **Gradle 9** + **paperweight-userdev 2.x** (Mojang-mapped dev bundle) + **`com.gradleup.shadow` 9.x**.
- **shadowJar** is `minimize()`d and is the **sole** release artifact (the thin `jar` task is disabled).
  No reobf step — Paper 1.20.5+ loads the Mojang-mapped jar natively.
- `processResources` expands `${version}` / `${description}` into `plugin.yml`.
- PlaceholderAPI and **Vault** (`VaultAPI`) are `compileOnly`; Adventure/MiniMessage are bundled with Paper.
- Tests use JUnit 5 + Mockito (Gradle 9 needs an explicit `junit-platform-launcher`); **JaCoCo** runs after `test`.
  MockBukkit is intentionally not used — it conflicts with the Mojang-mapped dev-bundle classpath; tests
  cover extracted pure logic instead (see `ArenaIndex`, `GameManagerTest`, `StatsManagerTest`).

## Architecture

The design layers a **finite state machine** over a **composition-root arena object**, driven
by a **batch block engine**. Understanding the data flow across these requires reading several
files together:

### Composition root: Arena → ArenaSession → GameState
- `arena/Arena.java` — one per configured arena. Holds an **immutable** `ArenaConfig` (record-style
  accessors: `config.minX()`, `config.lavaMaxY()`, `config.world()`, etc.) and a **mutable, nullable**
  `ArenaSession`. `session == null` means no game is running; `createSession()` builds one and starts
  the FSM in `LobbyState`.
- `arena/ArenaSession.java` — all per-game mutable state: alive players, spectators, current FSM
  state, `currentLavaY`, scheduler task id, and the world **snapshot** (see reset below). Player sets
  are `ConcurrentHashMap.newKeySet()` because position checks run async.
- `state/GameState.java` — the FSM interface. The session **delegates every lifecycle event**
  (`onPlayerJoin/Leave/Eliminated`, `onTick`, `isJoinable`) to its current state. Transitions go
  through `ArenaSession.transitionTo(newState)`, which calls `onExit()` then `onEnter()`.
- States (`state/`): `LobbyState` → `CountdownState` → `ActiveState` → `EndingState`. Each owns its
  own timers/`BukkitRunnable`s and must cancel them in `onExit()`.

### The lava engine (the whole point)
- `engine/nms/FastBlockSetter.java` — the NMS core. Resolves `ServerLevel`/`LevelChunk`/
  `LevelChunkSection` and calls `section.setBlockState(...)` **with no physics or lighting**. Caches
  the last chunk/section to keep iteration close to O(1). `sendChunkUpdate(...)` ships a single
  `ClientboundLevelChunkWithLightPacket` per modified chunk to players. **This is the only file that
  touches `net.minecraft.*` / `org.bukkit.craftbukkit.*`** — version-sensitive; verify against the
  target MC version when editing.
- `engine/LavaEngine.java` — ticked by `ActiveState`. Maintains a sweeping cursor `(cx, currentFillY, cz)`
  and fills up to `maxBlocksPerTick` blocks per tick toward `targetY`, raising `targetY` on each
  rise interval. Accumulates modified chunk keys and flushes packets once per tick.
- `engine/WorldResetter.java` — **O(1) sequential restore.** Iterates the arena volume in the *same
  index order* as the snapshot (`y * depth*width + z*width + x`) with a single `snapshotPointer`. If
  the current index matches the next recorded non-air block it restores that block, otherwise it sets
  air. No binary search, no schematic parsing. **The reset index math must stay byte-for-byte
  identical to `ActiveState.takeSnapshot()`'s iteration order** or restores corrupt.
- The snapshot itself is taken **asynchronously** in `ActiveState.takeSnapshot()` (reads only non-air
  blocks into parallel `int[] indices` / `BlockData[] blocks` arrays, stored on the session).

### Orchestration & wiring
- `core/LavaRisePlugin.java` — `JavaPlugin` entry point and service locator (static `getInstance()`).
  `onEnable()` initializes in strict order: config → `ArenaRepository` → `GameManager` → command →
  listeners → PlaceholderAPI hook. `onDisable()` force-ends all games and saves arenas.
- `core/GameManager.java` — registry of arenas + an **O(1) `playerArenaMap` (UUID → Arena)** for
  hot-path player lookups. Owns join/leave gating (full / not-joinable checks). Thread-safe via
  `ConcurrentHashMap`.
- `data/ConfigManager.java` — loads `config.yml` once and **caches** all values as primitive fields
  (avoids YAML lookups in hot paths). Also loads `messages.yml` and resolves MiniMessage strings.
- `data/ArenaRepository.java` — loads/saves per-arena YAML files under `plugins/LavaRise/arenas/`.
- `listener/ArenaEventRouter.java` — global Bukkit events → per-arena rules (block break/place, PvP).
  Notably **cancels native LAVA/FIRE_TICK damage** — elimination is decided solely by
  `ActiveState`'s Y-level checks, not by Bukkit damage.
- `listener/PlayerListener.java` — quit/disconnect cleanup.
- `command/LavaRiseCommand.java` — `/lavarise` (`/lr`, `/lava`) executor + tab completer.

### Game modes (strategy over the FSM)
- `mode/GameMode.java` — enum (`MINIGAME`, `SURVIVAL_CHALLENGE`, `ADMIN_EVENT`).
- `mode/GameModeHandler.java` — **the seam**. An abstract strategy with default = classic FFA, created
  once per `ArenaSession` (`forConfig`) and queried at fixed hooks: `onGameStart`, `isGameOver`,
  `resolveWinner`, `onPlayerEliminated`, `onArenaEnd`, `allowFriendlyFire`, `shouldSnapshot`. The FSM
  states call these instead of hard-coding win/mode logic — so adding a mode means adding a handler,
  not editing `ActiveState`.
- `MinigameModeHandler` (teams), `AdminEventModeHandler` (broadcasts + Vault reward),
  `SurvivalModeHandler` (skips snapshot). `SurvivalChallengeMode` is the world-wide controller that
  synthesises an ephemeral `Arena` around world spawn (registered in `GameManager`, never persisted).

### Supporting packages
- `feature/` — UI/effects modules (`ScoreboardModule`, `BossBarModule`, `ParticleModule`,
  `SoundModule`) and `feature/gui/` (inventory GUIs; identity via `LavaRiseGUIHolder`).
- `data/StatsManager.java` — persistent per-player stats (`stats.yml`) + leaderboards.
- `api/events/` — custom Bukkit events (`ArenaStartEvent`, `ArenaEndEvent`, `PlayerEliminatedEvent`)
  fired from states so other plugins can react.
- `hook/PapiExpansion.java` — PlaceholderAPI expansion; `hook/VaultHook.java` — optional Vault economy
  (soft hook; all `net.milkbowl.*` refs confined here behind a plugin-presence guard).

## Conventions & Constraints

- **Performance is the architecture.** Avoid object allocation in hot paths (per-tick engine loops,
  block placement, position checks). Prefer primitive arrays / cached fields — see `ConfigManager`
  and the snapshot arrays for the pattern. (Stated in `CONTRIBUTING.md`.)
- **Thread safety.** The snapshot read and player position check run **async**
  (`runTaskTimerAsynchronously`). Any mutation that Bukkit requires on the main thread — notably
  `eliminatePlayer` (gamemode change, teleport) — is bounced back via
  `getScheduler().runTask(...)`. Keep this split when adding logic; reading `player.getLocation()` is
  safe async on Paper but most writes are not.
- **NMS is isolated** to `engine/nms/FastBlockSetter.java`. Keep version-specific code there.
- **Time units are ticks** (20 = 1s) throughout config and timers, unless a field name says seconds.
- **User-facing text is MiniMessage**, via `plugin.getMiniMessage()` and `messages.yml` — don't
  hardcode legacy `§` color codes.
- **Commit style** follows Conventional Commits (`feat:`, `chore:`, `docs:`, `ci:` — see `git log`).
- CI (`.github/workflows/gradle.yml`) runs `./gradlew build` on push/PR to `main` (JDK 21, Temurin).

## Elimination model (important)

Elimination is driven by **real lava/fire damage**, not a Y-level poll. `ArenaEventRouter` lets players
burn during a running game; on death `PlayerListener#onPlayerDeath` drops items (unless the arena has
`keep-inventory`), marks them eliminated, and respawns them as a spectator. Before the game starts,
`ArenaEventRouter` shields players from environmental lava/fire. Win/elimination decisions route through
the session's `GameModeHandler`.

## Status

The tree compiles and tests pass on a fresh `./gradlew build` (Gradle 9, JDK 21). The historical
`GameManager`/import inconsistencies have been reconciled (`getArenaForPlayer`, `ArenaEventRouter`
import, etc.). Keep NMS changes isolated to `engine/nms/FastBlockSetter.java` and re-run the build —
the dev bundle download requires network on the first run.
