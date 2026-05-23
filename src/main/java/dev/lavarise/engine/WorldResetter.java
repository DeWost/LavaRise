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

        private int cx, cy, cz;
        private int snapshotPointer = 0;

        public ResetTask(LavaRisePlugin plugin, Arena arena) {
            this.plugin = plugin;
            this.arena = arena;
            this.config = arena.getConfig();
            this.maxBlocksPerTick = plugin.getConfigManager().getMaxBlocksPerTick() * 10; // We can reset much faster with NMS
            this.airData = Material.AIR.createBlockData();
            this.fastBlockSetter = new FastBlockSetter(config.world(), this.airData);

            this.cx = config.minX();
            this.cy = config.lavaStartY();
            this.cz = config.minZ();
        }

        @Override
        public void run() {
            int processed = 0;
            World world = config.world();
            Set<Long> modifiedChunks = new HashSet<>();
            
            int[] snapshotIndices = arena.getSession().getSnapshotIndices();
            BlockData[] snapshotBlocks = arena.getSession().getSnapshotBlocks();
            boolean hasSnapshot = snapshotIndices != null && snapshotBlocks != null;

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
                    // Calculate index using the same order as in ActiveState.takeSnapshot
                    int width = config.maxX() - config.minX() + 1;
                    int depth = config.maxZ() - config.minZ() + 1;
                    
                    int localX = cx - config.minX();
                    int localZ = cz - config.minZ();
                    int localY = cy - config.lavaStartY();
                    
                    // Index calculation: y * (depth * width) + z * width + x
                    int index = localY * (depth * width) + localZ * width + localX;
                    
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
                    FastBlockSetter.sendChunkUpdate(world, chunkX, chunkZ, world.getPlayers());
                }
            }
        }
        }
    }
}
