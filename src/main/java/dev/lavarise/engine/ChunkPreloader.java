package dev.lavarise.engine;

import dev.lavarise.arena.ArenaConfig;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Utility for asynchronously loading all chunks in an arena bounds
 * prior to a game starting to prevent lag spikes.
 */
public class ChunkPreloader {

    /**
     * Preloads all chunks that intersect with the arena configuration.
     *
     * @param config The arena config
     * @return A CompletableFuture that completes when all chunks are loaded
     */
    public static CompletableFuture<Void> preloadArenaChunks(ArenaConfig config) {
        World world = config.world();
        int minChunkX = config.minX() >> 4;
        int maxChunkX = config.maxX() >> 4;
        int minChunkZ = config.minZ() >> 4;
        int maxChunkZ = config.maxZ() >> 4;

        List<CompletableFuture<Chunk>> futures = new ArrayList<>();

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                futures.add(world.getChunkAtAsync(x, z));
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
