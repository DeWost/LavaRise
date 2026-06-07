package dev.lavarise.arena;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Exercises the per-match results tracking on {@link ArenaSession}
 * (elimination order, session kills, survival time) directly — the data the
 * end-game podium and placement screen are built from.
 */
@ExtendWith(MockitoExtension.class)
class ArenaSessionResultsTest {

    @Mock
    LavaRisePlugin plugin;

    private ArenaSession session;

    private static ArenaConfig minigameConfig() {
        return new ArenaConfig("t", null,
                new Location(null, 0, 0, 0), new Location(null, 4, 0, 4),
                null, null, null, 1, 16, 10, 10, 60, 1, -64, 320,
                true, true, true, false, true, "minigame");
    }

    private static Player player() {
        Player p = mock(Player.class);
        lenient().when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        return p;
    }

    @BeforeEach
    void setUp() {
        session = new ArenaSession(plugin, new Arena(plugin, minigameConfig()));
    }

    @Test
    void tracksEliminationOrderAndSurvivors() {
        Player a = player();
        Player b = player();
        Player c = player();
        session.enroll(a);
        session.enroll(b);
        session.enroll(c);
        assertEquals(3, session.getAliveCount());

        session.markEliminated(b); // first out
        session.markEliminated(c); // second out

        assertEquals(List.of(b.getUniqueId(), c.getUniqueId()), session.getEliminationOrder());
        assertEquals(1, session.getAliveCount());
        assertTrue(session.isAlive(a.getUniqueId()));
        assertTrue(session.isSpectator(b.getUniqueId()));
        assertFalse(session.isAlive(b.getUniqueId()));
    }

    @Test
    void markEliminatedIsIdempotent() {
        Player a = player();
        session.enroll(a);
        assertTrue(session.markEliminated(a));
        // Already eliminated → no double entry in the elimination order.
        assertFalse(session.markEliminated(a));
        assertEquals(1, session.getEliminationOrder().size());
    }

    @Test
    void tracksPerMatchKills() {
        Player a = player();
        session.recordSessionKill(a.getUniqueId());
        session.recordSessionKill(a.getUniqueId());
        assertEquals(2, session.getSessionKills(a.getUniqueId()));
        assertEquals(0, session.getSessionKills(UUID.randomUUID()));
    }

    @Test
    void survivalTimeRecordedOnElimination() {
        Player a = player();
        session.enroll(a);
        session.markEliminated(a);
        assertTrue(session.getSurvivalSeconds(a.getUniqueId()) >= 0);
    }
}
