package dev.lavarise.state;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaConfig;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.feature.ScoreboardModule;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LobbyStateTest {

    @Mock private LavaRisePlugin plugin;
    @Mock private Arena arena;
    @Mock private ArenaSession session;
    @Mock private Player player;
    @Mock private ArenaConfig arenaConfig;
    @Mock private ScoreboardModule scoreboardModule;
    @Mock private Server server;

    private LobbyState lobbyState;

    @BeforeEach
    void setUp() {
        when(arena.getConfig()).thenReturn(arenaConfig);
        when(plugin.getMiniMessage()).thenReturn(MiniMessage.miniMessage());
        when(plugin.getScoreboardModule()).thenReturn(scoreboardModule);
        
        lobbyState = new LobbyState(plugin, arena, session);
    }

    @Test
    void testIsJoinable() {
        assertTrue(lobbyState.isJoinable());
    }

    @Test
    void testGetDisplayName() {
        assertEquals("Waiting", lobbyState.getDisplayName());
    }

    @Test
    void testOnPlayerJoinTeleportsToLobbySpawn() {
        Location mockLocation = mock(Location.class);
        when(arenaConfig.lobbySpawn()).thenReturn(mockLocation);
        when(arenaConfig.maxPlayers()).thenReturn(10);
        when(arenaConfig.minPlayers()).thenReturn(2);
        when(session.getAllPlayerIds()).thenReturn(Collections.emptySet());
        
        lobbyState.onPlayerJoin(player);
        
        verify(player).teleport(mockLocation);
        verify(scoreboardModule).setupScoreboard(player, session);
        verify(scoreboardModule).updateScoreboard(session);
    }
}
