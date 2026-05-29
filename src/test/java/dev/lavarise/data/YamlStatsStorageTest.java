package dev.lavarise.data;

import dev.lavarise.core.LavaRisePlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YamlStatsStorageTest {

    @Mock
    LavaRisePlugin plugin;

    @TempDir
    File dataFolder;

    YamlStatsStorage stats;

    @BeforeEach
    void setUp() {
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        stats = new YamlStatsStorage(plugin);
    }

    @Test
    void recordsStatsAndKeepsBestSurvivalTime() {
        UUID a = UUID.randomUUID();
        stats.recordGamePlayed(a, "Alice");
        stats.recordWin(a, "Alice");
        stats.recordKill(a, "Alice");
        stats.recordSurvivalTime(a, "Alice", 120);
        stats.recordSurvivalTime(a, "Alice", 60); // worse — must be ignored

        assertEquals(1, stats.getWins(a));
        assertEquals(1, stats.getGames(a));
        assertEquals(1, stats.getKills(a));
        assertEquals(120L, stats.getBestTime(a));
    }

    @Test
    void leaderboardSortsDescending() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stats.recordWin(a, "A");
        stats.recordWin(b, "B");
        stats.recordWin(b, "B");

        List<StatsManager.Entry> top = stats.top("wins", 10);
        assertEquals("B", top.get(0).name());
        assertEquals(2L, top.get(0).value());
    }
}
