# LavaRise — Development Spec

> Machine-readable roadmap for AgentFlow's `/spec-to-board` and for human
> contributors. Each item below is intentionally **atomic** (one PR-sized unit
> of work) with explicit acceptance criteria so it can be decomposed onto a
> Kanban board and verified by the deterministic gates (`./gradlew build` =
> compile + Checkstyle + tests + JaCoCo).

## Product

LavaRise is a zero-lag rising-lava minigame plugin for Paper 1.21.11 (Java 21).
Current version: **1.6.0**. Architecture: state-machine arenas (`Lobby →
Countdown → Active → Ending`), a batch block-fill engine, pluggable game modes
(Survival, Survival Challenge, Admin Event), and pluggable stats storage
(YAML / MySQL).

## Engineering invariants (apply to every task)

- **No allocations in hot paths** — the ticking engine, block placement, and
  per-player HUD must not allocate per-tick. Use primitive arrays/maps.
- **NMS isolation** — all version-sensitive code stays in `engine/nms/` and
  degrades gracefully if the packet API changes.
- **Zero hard runtime deps** — PlaceholderAPI / Vault are `compileOnly`; the
  MySQL driver is provided via `plugin.yml libraries:`.
- **Tests + Checkstyle must stay green** — `./gradlew build` is the gate.
- **Conventional Commits** — `feat:`, `fix:`, `perf:`, `chore:`, `docs:`, etc.

## Backlog (candidate tasks)

> These are illustrative seed tasks — refine/replace with your real roadmap
> before running `/spec-to-board`. Each has acceptance criteria a reviewer (or
> AgentFlow's adversarial review stage) can check.

### T1 — Spectator mode for eliminated players
- **Why:** eliminated players currently leave; keeping them engaged improves retention.
- **Acceptance:** on elimination, player enters spectator gamemode, can fly and
  watch survivors, sees a "you placed Nth" message; rejoining mid-round is blocked.
- **Touches:** `state/ActiveState.java`, `listener/PlayerListener.java`, `mode/*`.
- **Tests:** unit test asserting elimination transitions a player to spectator state.

### T2 — Configurable lava block type per arena
- **Why:** themed arenas (e.g. "acid pit") want water/custom block instead of lava.
- **Acceptance:** `arena.fill-material` config key (default `LAVA`); engine fills
  with the configured material; invalid material logs a warning and falls back.
- **Touches:** `arena/ArenaConfig.java`, `engine/LavaEngine.java`, `config.yml`.
- **Tests:** `ArenaConfigTest` covers parse + fallback.

### T3 — Per-arena leaderboard hologram (PlaceholderAPI-only, no deps)
- **Why:** surface top survivors near the arena without a hologram plugin dep.
- **Acceptance:** new PAPI placeholders `%lavarise_top_<n>_name%` /
  `%lavarise_top_<n>_wins%`; backed by existing `StatsManager` leaderboard.
- **Touches:** `hook/PapiExpansion.java`, `data/StatsManager.java`.
- **Tests:** placeholder resolution unit test with a stubbed stats backend.

### T4 — Rejoin grace window
- **Why:** a disconnect mid-round currently eliminates the player.
- **Acceptance:** `gameplay.rejoin-grace-seconds` (default 0 = off); within the
  window a reconnecting player resumes at their last position/inventory.
- **Touches:** `core/GameManager.java`, `state/ActiveState.java`, `config.yml`.
- **Tests:** `GameManagerTest` covers rejoin within / after the window.

### T5 — `/lr stats <player>` command
- **Why:** players want to inspect their own and others' records in-game.
- **Acceptance:** new subcommand prints wins/kills/games/best-time from
  `StatsManager`; tab-completes online players; respects `lavarise.play`.
- **Touches:** `command/LavaRiseCommand.java`, `data/StatsManager.java`, `messages.yml`.
- **Tests:** command parsing/permission unit test.

## Definition of Done (every task)

1. `./gradlew build` passes (compile + Checkstyle + tests).
2. New behaviour covered by at least one JUnit test.
3. Config keys documented in `config.yml` and the README "Configuration" table.
4. `CHANGELOG.md` updated under the next version heading.
5. PR targets `main`, uses a Conventional Commit title, and links the task.
