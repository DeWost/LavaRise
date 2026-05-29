package dev.lavarise.engine;

import dev.lavarise.arena.ArenaConfig;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import dev.lavarise.engine.nms.FastBlockSetter;
import org.bukkit.Chunk;
import java.util.HashSet;
import java.util.Set;

/**
 * Core engine for making the lava rise efficiently.
 * Uses a tick-based iterator approach to process a limited number of blocks per tick,
 * avoiding any TPS drops even in massive arenas.
 */
public class LavaEngine {
    private final LavaRisePlugin plugin;
    private final ArenaConfig config;
    private final ArenaSession session;
    private final int maxBlocksPerTick;
    private final BlockData lavaData;
    private final FastBlockSetter fastBlockSetter;

    private int currentFillY;
    private int cx, cz;
    private int targetY;
    private long lastWarnedSkip = 0L;

    public LavaEngine(LavaRisePlugin plugin, ArenaConfig config, ArenaSession session, int maxBlocksPerTick) {
        this.plugin = plugin;
        this.config = config;
        this.session = session;
        this.maxBlocksPerTick = maxBlocksPerTick;
        this.lavaData = Material.LAVA.createBlockData();
        this.fastBlockSetter = new FastBlockSetter(config.world(), this.lavaData);

        this.currentFillY = session.getCurrentLavaY();
        this.cx = config.minX();
        this.cz = config.minZ();
        this.targetY = this.currentFillY;
    }

    /**
     * Increases the target Y level by the configured amount.
     */
    public void riseLava() {
        int amount = config.lavaRiseAmount();
        this.targetY = Math.min(this.targetY + amount, config.lavaMaxY());
    }

    /**
     * Called every tick to place lava blocks up to maxBlocksPerTick.
     */
    public void processBatch() {
        if (currentFillY >= targetY) {
            if (currentFillY >= config.lavaMaxY() && !session.isLavaAtMax()) {
                session.setCurrentLavaY(config.lavaMaxY());
            }
            return;
        }

        int processed = 0;
        World world = config.world();
        Set<Long> modifiedChunks = new HashSet<>();

        while (processed < maxBlocksPerTick) {
            if (currentFillY >= targetY) {
                session.setCurrentLavaY(targetY);
                break;
            }

            // Set block directly using NMS without physics/light
            boolean modified = fastBlockSetter.setBlock(cx, currentFillY, cz);
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
                    currentFillY++;
                }
            }
        }

        // Refresh modified chunks for the players tracking them.
        if (!modifiedChunks.isEmpty()) {
            for (long chunkKey : modifiedChunks) {
                int chunkX = (int) chunkKey;
                int chunkZ = (int) (chunkKey >> 32);
                FastBlockSetter.sendChunkUpdate(world, chunkX, chunkZ);
            }
        }

        // Surface unloaded-chunk skips once per 25k so admins notice gaps.
        long skipped = fastBlockSetter.getSkippedBlocks();
        if (skipped - lastWarnedSkip >= 25_000) {
            lastWarnedSkip = skipped;
            plugin.getLogger().warning("LavaRise: " + skipped + " lava blocks skipped (unloaded chunks) "
                    + "in arena " + config.name() + " — consider enabling chunk preloading or shrinking the arena.");
        }
    }

    public void shutdown() {
        // Cleanup if needed
    }
}
