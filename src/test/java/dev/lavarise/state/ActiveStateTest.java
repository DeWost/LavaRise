package dev.lavarise.state;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.data.ConfigManager;
import dev.lavarise.data.StatsManager;
import dev.lavarise.feature.BossBarModule;
import dev.lavarise.feature.ScoreboardModule;
import dev.lavarise.mode.GameModeHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveStateTest {

    @Mock private LavaRisePlugin plugin;
    @Mock private Arena arena;
    @Mock private ArenaSession session;
    @Mock private Player player;
    @Mock private ConfigManager cfg;
    @Mock private Server server;
    @Mock private PluginManager pluginManager;
    @Mock private GameModeHandler modeHandler;
    @Mock private StatsManager statsManager;
    @Mock private BossBarModule bossBarModule;
    @Mock private ScoreboardModule scoreboardModule;

    private ActiveState activeState;

    @BeforeEach
    void setUp() {
        // The constructor caches plugin.getConfigManager().
        when(plugin.getConfigManager()).thenReturn(cfg);
        activeState = new ActiveState(plugin, arena, session);
    }

    @Test
    void testActiveStateBlocksJoiningAndIsRunning() {
        // Mid-round joins are rejected and the game is reported as running.
        assertFalse(activeState.isJoinable());
        assertTrue(activeState.isGameRunning());
    }

    @Test
    void testEliminationSendsPlacementMessage() {
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(session.getModeHandler()).thenReturn(modeHandler);
        when(plugin.getStatsManager()).thenReturn(statsManager);
        when(plugin.getBossBarModule()).thenReturn(bossBarModule);
        when(plugin.getScoreboardModule()).thenReturn(scoreboardModule);
        when(plugin.getMiniMessage()).thenReturn(MiniMessage.miniMessage());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");
        when(session.getAllPlayerIds()).thenReturn(Collections.emptySet());
        when(modeHandler.isGameOver(arena, session)).thenReturn(false);

        // Two survivors remain, so the eliminated player placed #3. A tagless
        // template keeps the rendered component a single TextComponent we can read.
        when(session.getAliveCount()).thenReturn(2);
        when(cfg.getMessage("player.placement")).thenReturn("PLACE={place} ALIVE={alive}");

        activeState.onPlayerEliminated(player);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(captor.capture());
        assertEquals("PLACE=3 ALIVE=2", ((TextComponent) captor.getValue()).content());
    }
}
