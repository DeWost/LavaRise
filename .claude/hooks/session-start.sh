#!/bin/bash
# LavaRise — SessionStart hook for Claude Code on the web.
#
# Warms the Gradle caches and the paperweight Paper dev bundle so that
# `./gradlew build`, the linter (Checkstyle) and the test suite are ready
# to run the moment a session starts. Safe to run repeatedly (idempotent)
# and fully non-interactive.
set -euo pipefail

# Only run in the remote (Claude Code on the web) environment. Local users
# already have their Gradle caches and don't need the warm-up.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}"

# Make the build non-interactive and CI-friendly.
export GRADLE_OPTS="-Dorg.gradle.daemon=false ${GRADLE_OPTS:-}"

echo "[session-start] Warming Gradle caches and Paper dev bundle…"

# `compileJava` + `compileTestJava` pulls every compile/test dependency and
# triggers paperweight to download & remap the Paper dev bundle (the slow,
# network-heavy step). We deliberately skip running tests here to keep the
# warm-up fast; the container state is cached after this completes.
if ./gradlew --no-daemon compileJava compileTestJava; then
  echo "[session-start] Gradle warm-up complete."
else
  # Don't fail the whole session if warm-up hits a transient network issue —
  # the agent can still run ./gradlew later. Surface the problem instead.
  echo "[session-start] WARNING: Gradle warm-up failed; dependencies may download on first build." >&2
fi
