package dev.lavarise.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The auto-arena workflow's retry backoff is a pure function — verify the
 * exponential schedule and its cap so a broken world is never hammered.
 */
class AutoArenaControllerTest {

    @Test
    void backoffGrowsExponentiallyFromOne() {
        assertEquals(1, AutoArenaController.backoffCycles(1));
        assertEquals(2, AutoArenaController.backoffCycles(2));
        assertEquals(4, AutoArenaController.backoffCycles(3));
        assertEquals(8, AutoArenaController.backoffCycles(4));
        assertEquals(16, AutoArenaController.backoffCycles(5));
    }

    @Test
    void backoffIsCappedAtMax() {
        assertEquals(AutoArenaController.MAX_BACKOFF_CYCLES, AutoArenaController.backoffCycles(6));
        assertEquals(AutoArenaController.MAX_BACKOFF_CYCLES, AutoArenaController.backoffCycles(100));
    }

    @Test
    void backoffNeverBelowOne() {
        // Defensive: non-positive inputs still yield a sane (≥1) window.
        assertEquals(1, AutoArenaController.backoffCycles(0));
        assertTrue(AutoArenaController.backoffCycles(-3) >= 1);
    }

    @Test
    void deadLetterThresholdMatchesMaxBackoffStep() {
        // By the time we dead-letter, the backoff has reached its cap — the
        // workflow has genuinely given the world maximum room to recover.
        assertEquals(AutoArenaController.MAX_BACKOFF_CYCLES,
                AutoArenaController.backoffCycles(AutoArenaController.MAX_CONSECUTIVE_FAILURES));
    }
}
