package dev.lavarise.core;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaConfig;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GameManagerTest {

    @Mock
    LavaRisePlugin plugin;

    GameManager manager;

    private Arena arena(String name) {
        ArenaConfig cfg = new ArenaConfig(name, null,
                new Location(null, 0, 0, 0), new Location(null, 4, 0, 4),
                null, null, null, 1, 16, 10, 10, 60, 1, -64, 320,
                true, true, true, false, true, "minigame");
        return new Arena(plugin, cfg);
    }

    @BeforeEach
    void setUp() {
        manager = new GameManager(plugin);
    }

    @Test
    void registersAndLooksUpArenaCaseInsensitively() {
        manager.registerArena(arena("Volcano"));
        assertTrue(manager.getArena("volcano").isPresent());
        assertTrue(manager.getArena("VOLCANO").isPresent());
        assertEquals(1, manager.getAllArenas().size());
    }

    @Test
    void mapsPlayerToArenaWithO1Lookup() {
        Arena a = arena("Core");
        manager.registerArena(a);
        UUID id = UUID.randomUUID();

        assertFalse(manager.isInGame(id));
        manager.mapPlayer(id, a);
        assertTrue(manager.isInGame(id));
        assertSame(a, manager.getArenaForPlayer(id));

        manager.unmapPlayer(id);
        assertFalse(manager.isInGame(id));
        assertNull(manager.getArenaForPlayer(id));
    }
}
