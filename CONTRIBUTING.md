# Contributing to LavaRise

First off, thank you for considering contributing to LavaRise! It's people like you that make open source such a great community.

## Development Setup

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR-USERNAME/LavaRise.git`
3. Open the project in IntelliJ IDEA (or your preferred IDE)
4. Run `./gradlew build` to verify the setup

> **Requirements:** JDK 21. The first build downloads and remaps the Paper
> 1.21.11 Mojang-mapped dev bundle (a few minutes); subsequent builds are cached.

### Claude Code on the web

This repo ships a **SessionStart hook** (`.claude/hooks/session-start.sh`) that
warms the Gradle caches and the Paper dev bundle automatically when a
[Claude Code on the web](https://code.claude.com/docs/en/claude-code-on-the-web)
session starts, so tests and the linter are ready immediately. It only runs in
the remote environment (`CLAUDE_CODE_REMOTE=true`) and is a no-op locally.

## The Development Workflow

```
 branch  →  build  →  checkstyle + test  →  changelog  →  PR → main
```

1. **Branch** off `main`: `git checkout -b feature/my-new-feature`
2. **Build & verify** continuously with `./gradlew build` (see gates below).
3. **Document** new config keys in `config.yml` + the README table, and add a
   `CHANGELOG.md` entry under the next version heading.
4. **Open a PR** targeting `main` with a Conventional Commit title.

### Quality gates — `./gradlew build`

A single command runs every gate CI enforces:

| Gate | Task | Notes |
|------|------|-------|
| Compile | `compileJava` | Java 21, real Paper dev bundle |
| Lint | `checkstyleMain` | `config/checkstyle/checkstyle.xml`; non-blocking for now |
| Tests | `test` | JUnit 5 + Mockito |
| Coverage | `jacocoTestReport` | report at `build/reports/jacoco/test/html` |

Run a single test while iterating:

```bash
./gradlew test --tests "dev.lavarise.core.GameManagerTest"
```

Run only the linter:

```bash
./gradlew checkstyleMain   # report → build/reports/checkstyle/main.html
```

> **Checkstyle is currently advisory** (`ignoreFailures = true`) — it reports
> issues without breaking the build while the existing backlog is cleaned up.
> Please don't add new violations, and fix any you touch.

## Coding Standards

- **NMS Code**: Be careful when modifying NMS (net.minecraft.server) code. All
  version-sensitive code lives in `engine/nms/` and must degrade gracefully.
  Always test your changes on the target Minecraft version.
- **Garbage Collection**: Avoid object allocations in hot paths (e.g., ticking
  arenas, block placement, per-player HUD). Use primitive arrays and primitive
  maps where applicable.
- **Java 21**: We utilize modern Java 21 features.
- **Braces**: always brace `if`/`for`/`while` bodies (enforced by Checkstyle).

## Commit & PR Conventions

This project follows [Conventional Commits](https://www.conventionalcommits.org/):

```
feat:  a new user-facing feature        perf:  a performance improvement
fix:   a bug fix                         docs:  documentation only
chore: tooling / deps / housekeeping     refactor: no behaviour change
```

1. Create a new branch: `git checkout -b feature/my-new-feature`
2. Commit your changes: `git commit -m 'feat: add spectator mode for eliminated players'`
3. Push to the branch: `git push origin feature/my-new-feature`
4. Submit a Pull Request targeting the `main` branch.

Please ensure `./gradlew build` passes before opening the PR.

## Planning larger work — `SPEC.md` & AgentFlow

Bigger features are tracked as atomic, PR-sized tasks in [`SPEC.md`](SPEC.md),
each with explicit acceptance criteria. That file doubles as the input to the
optional [AgentFlow](https://github.com/UrRhb/agentflow) pipeline
(`/spec-to-board`), which can decompose it onto a Kanban board and dispatch
Claude Code workers. The deterministic gate for every task is the same
`./gradlew build`. You don't need AgentFlow to contribute — `SPEC.md` is just
the shared source of truth for what's planned.

## Releasing (maintainers)

Releases are tag-driven: pushing a `v*` tag (e.g. `v1.6.0`) triggers
`.github/workflows/release.yml`, which builds the shaded jar and creates a
GitHub Release with auto-generated notes. Bump `version` in `build.gradle.kts`
and update `CHANGELOG.md` before tagging.
