package dev.lavarise.engine;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaConfig;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;
import dev.lavarise.engine.nms.FastBlockSetter;
import org.bukkit.Chunk;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;

/**
 * Handles clearing the lava from the arena after the game ends.
 * Uses a BukkitRunnable to clear a set number of blocks per tick, avoiding lag.
 */
public class WorldResetter {

    /**
     * Resets the arena by changing all lava blocks back to air.
     */
    public static void resetArena(LavaRisePlugin plugin, Arena arena) {
        new ResetTask(plugin, arena).runTaskTimer(plugin, 1L, 1L);
    }

    private static class ResetTask extends BukkitRunnable {
        private final LavaRisePlugin plugin;
        private final Arena arena;
        private final ArenaConfig config;
        private final int maxBlocksPerTick;
        private final BlockData airData;
        private final FastBlockSetter fastBlockSetter;

        // Snapshot captured at schedule time so the task is independent of the
        // (soon-to-be-destroyed) session and never reads a half-written snapshot.
        private final boolean hasSnapshot;
        private final int[] snapshotIndices;
        private final BlockData[] snapshotBlocks;

        private int cx, cy, cz;
        private int snapshotPointer = 0;

        /** Reused per tick to avoid allocating a Set every reset pass. */
        private final Set<Long> modifiedChunks = new HashSet<>();

        public ResetTask(LavaRisePlugin plugin, Arena arena) {
            this.plugin = plugin;
            this.arena = arena;
            this.config = arena.getConfig();
            this.maxBlocksPerTick = plugin.getConfigManager().getMaxBlocksPerTick() * 10; // We can reset much faster with NMS
            this.airData = Material.AIR.createBlockData();
            this.fastBlockSetter = new FastBlockSetter(config.world(), this.airData);

            final var session = arena.getSession();
            this.hasSnapshot = session != null && session.isSnapshotReady();
            this.snapshotIndices = hasSnapshot ? session.getSnapshotIndices() : null;
            this.snapshotBlocks = hasSnapshot ? session.getSnapshotBlocks() : null;

            this.cx = config.minX();
            this.cy = config.lavaStartY();
            this.cz = config.minZ();
        }

        @Override
        public void run() {
            int processed = 0;
            World world = config.world();
            modifiedChunks.clear();

            while (processed < maxBlocksPerTick) {
                if (cy > config.lavaMaxY()) {
                    this.cancel();
                    plugin.debug("WorldResetter finished for arena " + arena.getName());
                    
                    // Final chunk updates
                    sendChunkUpdates(world, modifiedChunks);
                    return;
                }
                
                boolean modified = false;
                if (hasSnapshot) {
                    // Same y→z→x order as ActiveState.takeSnapshot (see ArenaIndex).
                    int index = ArenaIndex.index(config, cx, cy, cz);

                    if (snapshotPointer < snapshotIndices.length && snapshotIndices[snapshotPointer] == index) {
                        // Snapshot block is not air, use fast NMS block setter for it too!
                        modified = fastBlockSetter.setBlock(cx, cy, cz, snapshotBlocks[snapshotPointer]);
                        snapshotPointer++;
                    } else {
                        modified = fastBlockSetter.setBlock(cx, cy, cz);
                    }
                } else {
                    modified = fastBlockSetter.setBlock(cx, cy, cz);
                }

                if (modified) {
                    long chunkKey = Chunk.getChunkKey(cx >> 4, cz >> 4);
                    modifiedChunks.add(chunkKey);
                }

                processed++;
                cx++;
                if (cx > config.maxX()) {
                    cx = config.minX();
                    cz++;
                    if (cz > config.maxZ()) {
                        cz = config.minZ();
                        cy++;
                    }
                }
            }
            
            sendChunkUpdates(world, modifiedChunks);
        }
        
        private void sendChunkUpdates(World world, Set<Long> modifiedChunks) {
            if (!modifiedChunks.isEmpty()) {
                for (long chunkKey : modifiedChunks) {
                    int chunkX = (int) chunkKey;
                    int chunkZ = (int) (chunkKey >> 32);
                    FastBlockSetter.sendChunkUpdate(world, chunkX, chunkZ);
                }
            }
        }
    }
}
