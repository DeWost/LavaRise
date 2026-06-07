package dev.lavarise.arena;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Combat-feed state on {@link ArenaSession}: multi-kill combo, combat tag, supply drops. */
@ExtendWith(MockitoExtension.class)
class ArenaSessionCombatTest {

    @Mock
    LavaRisePlugin plugin;

    private ArenaSession session;

    private static ArenaConfig minigameConfig() {
        return new ArenaConfig("t", null,
                new Location(null, 0, 0, 0), new Location(null, 4, 0, 4),
                null, null, null, 1, 16, 10, 10, 60, 1, -64, 320,
                true, true, true, false, true, "minigame");
    }

    @BeforeEach
    void setUp() {
        session = new ArenaSession(plugin, new Arena(plugin, minigameConfig()));
    }

    @Test
    void comboGrowsForRapidKillsAndIsPerPlayer() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(1, session.registerCombo(a, 10_000));
        assertEquals(2, session.registerCombo(a, 10_000));
        assertEquals(3, session.registerCombo(a, 10_000));
        // Independent killer starts its own combo.
        assertEquals(1, session.registerCombo(b, 10_000));
    }

    @Test
    void comboResetsAfterTheWindowElapses() throws InterruptedException {
        UUID a = UUID.randomUUID();
        assertEquals(1, session.registerCombo(a, 10));
        Thread.sleep(25); // exceed the 10ms window
        assertEquals(1, session.registerCombo(a, 10));
    }

    @Test
    void combatTagRespectsDuration() throws InterruptedException {
        UUID a = UUID.randomUUID();
        assertFalse(session.isCombatTagged(a));
        session.tagCombat(a, 10_000);
        assertTrue(session.isCombatTagged(a));

        session.tagCombat(a, 5);
        Thread.sleep(20);
        assertFalse(session.isCombatTagged(a));
    }

    @Test
    void supplyDropsAreTrackedAndCopied() {
        assertTrue(session.getSupplyDrops().isEmpty());
        session.addSupplyDrop(new Location(null, 1, 2, 3));
        session.addSupplyDrop(new Location(null, 4, 5, 6));
        assertEquals(2, session.getSupplyDrops().size());
        // getSupplyDrops returns a copy — mutating it must not affect the session.
        session.getSupplyDrops().clear();
        assertEquals(2, session.getSupplyDrops().size());
    }
}
