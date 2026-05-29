# Changelog

All notable changes to LavaRise are documented here.
This project follows [Conventional Commits](https://www.conventionalcommits.org/)
and roughly [Semantic Versioning](https://semver.org/).

## [1.6.0]

### Added — surpass KteRising on its own turf
- **Enchanted kit presets.** Kit items now parse enchantments
  (`MATERIAL:AMOUNT:ENCHANT:LEVEL...`, levels may exceed vanilla caps). Ships
  with KteRising-style presets: Classic, OP, Elytra, Trident, UltraOP.
- **Kit voting** (`/lr vote`). A lobby GUI where players vote for the kit the
  whole round will use; the winner (ties random) is applied to everyone, with
  live vote counts shown.
- **Biome-aware procedural placement.** Random arenas now prefer playable biomes,
  skipping ocean/river/frozen/beach.

## [1.5.0]

### Added — full KteRising parity
- **MySQL stats backend.** New `StatsStorage` abstraction with YAML (default) and
  **MySQL** implementations, selected by `storage.type`. MySQL uses the JDK
  `java.sql` API (driver auto-provided via `plugin.yml libraries:`), an in-memory
  cache with lazy per-player load, batched async UPSERT flushing on
  `storage.sync-interval`, and database-side leaderboards. Falls back to YAML
  automatically if the connection fails.
- **bStats metrics** (`general.bstats`) — shaded and relocated to
  `dev.lavarise.libs.bstats` so it never clashes with other plugins.
- Detailed `storage` config (host / port / database / credentials / table-prefix /
  ssl / sync-interval).

## [1.4.1]

### Changed — dependency currency
- Bumped to the latest libraries: `com.gradleup.shadow` 9.4.1, JUnit 5.14.4,
  Mockito 5.18.0. `paperweight-userdev` 2.0.0-beta.21 was already latest. The
  plugin always compiles against the real Paper 1.21.11 Mojang-mapped dev bundle.

### Changed — performance
- **One damage listener.** Merged `EntityDamageByEntityEvent` handling into the
  single `EntityDamageEvent` handler — a PvP hit no longer fires two listeners
  (two arena lookups) for the same blow.
- **Engine cadence.** The fill engine now runs every `performance.engine-interval-ticks`
  ticks with an N× budget (same blocks/second), cutting scheduler passes and
  chunk-update packets vs. running every tick.

## [1.4.0]

### Added — KteRising parity pack
- **Multiple kits + selector GUI** — define any number of loadouts under `kits`
  in config; players pick one with `/lr kit` (falls back to the single legacy kit).
- **Height-gated PvP** (`gameplay.pvp-after-height`) — no fighting until the lava
  has risen N blocks.
- **Auto-pickup / auto-smelt** (`gameplay.auto-pickup` / `auto-smelt`) — mined
  drops go straight to the inventory and ores are smelted.
- **Per-kill / per-death reward commands** (`rewards.kill-commands` /
  `death-commands`) with `{killer}`/`{victim}`/`{player}` placeholders.
- **Update checker** (`general.update-check`) — async GitHub Releases check that
  logs when a newer version is out (no third-party dependency).

## [1.3.1]

### Changed
- **Sensible lava display.** The HUD/scoreboard/boss bar no longer show the raw
  (often negative) world Y as "Lava Level". `{lava_level}` is now the lava
  **height** (blocks risen, always ≥0), and a new `{lava_percent}` shows progress
  to max — matching the "Current Lava Height" convention used by KteRising.
- Added `{lava_y}` (raw Y) placeholder and PlaceholderAPI
  `%lavarise_lava_percent_<arena>%` / `%lavarise_lava_y_<arena>%`;
  `%lavarise_lava_level_<arena>%` now returns height.

## [1.3.0]

### Added
- **Random & procedural arenas:**
  - Quick-join — `/lr join` with no arena name drops into a random open game.
  - `/lr random` — generates and joins a fresh arena at a random world location.
  - Random rotation — procedural arenas are torn down and their terrain restored
    after each game, so the next one spawns somewhere new.
  - `GameManager.findRandomAvailableArena()` and a `procedural` config section
    (`radius`, `spawn-area`, lava range, `auto-on-quickjoin`).
- A reusable `ProceduralArenaFactory` and a transient-arena flag (`Arena#markTransient`).

### Changed
- `WorldResetter` now captures the snapshot at schedule time (independent of the
  session), fixing a latent reset-after-teardown race and enabling transient-arena cleanup.

## [1.2.0]

### Added
- **Full game modes** via a `GameModeHandler` strategy:
  - **Teams** (last-team-standing, friendly fire disabled, multi-winner stats).
  - **Survival Challenge** — world-wide rising lava around spawn (`/lr survival`).
  - **Admin Event** — manual start/pause/resume/stop with broadcasts and an
    optional Vault reward for the winner (`/lr event`).
- Optional **Vault** economy integration (soft hook — no hard dependency).
- Unit test suite (index parity, GameManager, StatsManager, ArenaConfig, GameMode)
  and **JaCoCo** coverage; CI now uploads test results, coverage and the jar.

### Changed
- Snapshot is now published thread-safely (volatile + ready flag) and uses a
  primitive `int[]` to avoid GC spikes; resets only read a complete snapshot.
- Chunk update packets target only players tracking the chunk, and the
  version-specific packet path degrades gracefully on Paper API changes.
- Removed misleading/unused config keys (`update-check`, `player-check-interval`,
  `disable-lava-physics`); added a `config-version` mismatch warning.

### Fixed
- Arenas were loaded before the `GameManager` existed (startup NPE).
- Snapshot/reset linear-index math unified in `ArenaIndex` to prevent drift.

## [1.1.0]

### Added
- Real lava burning death with item drops (lava + PvP) and spectator respawn.
- Grace period, lava acceleration, dynamic speed, sudden death, shrinking world
  border, starting kit, periodic pillar blocks.
- Config-driven scoreboard, boss bar, action-bar HUD, particles, sounds,
  proximity warnings; persistent stats + leaderboards; win-reward commands;
  expanded PlaceholderAPI placeholders; admin controls + arena setup wizard.

### Fixed
- Reconciled `GameManager` API with callers; coherent Gradle 9 build with a
  committed wrapper; `WorldResetter` brace bug.

## [1.0.0]
- Initial release of the zero-lag NMS rising-lava engine.
