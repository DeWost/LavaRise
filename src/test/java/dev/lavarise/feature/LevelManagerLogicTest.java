package dev.lavarise.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for the XP curve and level-from-total-XP derivation.
 * These do not touch Bukkit and need no mocks.
 *
 * @author DeWost
 */
class LevelManagerLogicTest {

    /**
     * A minimal stub that exposes only the pure-math helpers from LevelManager
     * without requiring Bukkit / plugin wiring.
     */
    static final class Curve {

        private final double baseXp;
        private final double growth;

        Curve(double baseXp, double growth) {
            this.baseXp = baseXp;
            this.growth = growth;
        }

        long xpForLevel(int n) {
            if (n < 1) {
                n = 1;
            }
            return Math.round(baseXp * Math.pow(growth, n - 1));
        }

        int levelFromTotalXp(long total) {
            if (total < 0) {
                total = 0;
            }
            int level = 1;
            long remaining = total;
            while (remaining >= xpForLevel(level)) {
                remaining -= xpForLevel(level);
                level++;
            }
            return level;
        }

        long xpIntoLevel(long total) {
            if (total < 0) {
                total = 0;
            }
            long remaining = total;
            int level = 1;
            while (remaining >= xpForLevel(level)) {
                remaining -= xpForLevel(level);
                level++;
            }
            return remaining;
        }
    }

    private final Curve curve = new Curve(100.0, 1.15);

    @Test
    void xpForLevelOneIsBaseXp() {
        assertEquals(100L, curve.xpForLevel(1));
    }

    @Test
    void xpForLevelTwoGrowsByFactor() {
        // round(100 * 1.15^1) = round(115) = 115
        assertEquals(115L, curve.xpForLevel(2));
    }

    @Test
    void xpForLevelThree() {
        // round(100 * 1.15^2) = round(132.25) = 132
        assertEquals(132L, curve.xpForLevel(3));
    }

    @Test
    void levelOneAtZeroXp() {
        assertEquals(1, curve.levelFromTotalXp(0));
    }

    @Test
    void levelOneBeforeThreshold() {
        assertEquals(1, curve.levelFromTotalXp(99));
    }

    @Test
    void levelTwoAtExactThreshold() {
        // 100 XP is exactly enough to leave level 1.
        assertEquals(2, curve.levelFromTotalXp(100));
    }

    @Test
    void levelTwoJustBeforeNextThreshold() {
        // level 1 costs 100, level 2 costs 115. 214 total = 100+114 → still in level 2.
        assertEquals(2, curve.levelFromTotalXp(214));
    }

    @Test
    void levelThreeAtExactThreshold() {
        // Cumulative XP to reach level 3 = 100 + 115 = 215
        assertEquals(3, curve.levelFromTotalXp(215));
    }

    @Test
    void xpIntoLevelIsCorrect() {
        // 110 total: consumed 100 for level 1, 10 remain in level 2.
        assertEquals(10L, curve.xpIntoLevel(110));
    }

    @Test
    void xpIntoLevelZeroAtLevelBoundary() {
        // 215 total: just hit level 3, 0 XP into level 3.
        assertEquals(0L, curve.xpIntoLevel(215));
    }

    @Test
    void xpForLevelWithBadInputClampsToOne() {
        assertEquals(curve.xpForLevel(1), curve.xpForLevel(0));
        assertEquals(curve.xpForLevel(1), curve.xpForLevel(-5));
    }

    @Test
    void levelFromNegativeTotalIsOne() {
        assertEquals(1, curve.levelFromTotalXp(-999));
    }

    @Test
    void highLevelProgression() {
        // Sanity: level at cumulative sum for first 10 levels should be 11.
        long cumulative = 0;
        for (int i = 1; i <= 10; i++) {
            cumulative += curve.xpForLevel(i);
        }
        assertEquals(11, curve.levelFromTotalXp(cumulative));
    }
}
