package dev.lavarise.engine.nms;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * Writes block states directly into NMS chunk sections (bypassing physics and
 * lighting) and pushes a single chunk packet to nearby clients.
 *
 * @author DeWost
 */
public class FastBlockSetter {

    /** Flipped once if the version-specific packet constructor ever fails, to avoid log spam. */
    private static volatile boolean packetApiBroken = false;

    private final ServerLevel serverLevel;
    private final BlockState nmsState;

    private LevelChunk lastChunk = null;
    private LevelChunkSection lastSection = null;
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private int lastSectionIndex = Integer.MIN_VALUE;

    /** Count of blocks skipped because their chunk/section was not loaded. */
    private long skippedBlocks = 0L;

    public FastBlockSetter(World world, BlockData bukkitData) {
        this.serverLevel = ((CraftWorld) world).getHandle();
        this.nmsState = ((CraftBlockData) bukkitData).getState();
    }

    /**
     * Internal method to set the block state.
     */
    private boolean setBlockWithState(int x, int y, int z, BlockState stateToSet) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int sectionIndex = serverLevel.getSectionIndex(y);

        if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
            lastChunk = serverLevel.getChunkIfLoaded(chunkX, chunkZ);
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
            lastSectionIndex = Integer.MIN_VALUE; // Force section refresh
            lastSection = null;                   // Drop stale section reference
        }

        if (lastChunk == null) {
            skippedBlocks++;
            return false;
        }

        if (sectionIndex != lastSectionIndex) {
            LevelChunkSection[] sections = lastChunk.getSections();
            if (sectionIndex >= 0 && sectionIndex < sections.length) {
                lastSection = sections[sectionIndex];
            } else {
                lastSection = null;
            }
            lastSectionIndex = sectionIndex;
        }

        if (lastSection == null) {
            skippedBlocks++;
            return false;
        }

        int localX = x & 15;
        int localY = y & 15;
        int localZ = z & 15;

        // Set the block state without locking (main-thread execution assumed).
        lastSection.setBlockState(localX, localY, localZ, stateToSet);
        return true;
    }

    /**
     * Sets a block state directly in the chunk section, bypassing light and physics,
     * using the default BlockData passed in the constructor.
     */
    public boolean setBlock(int x, int y, int z) {
        return setBlockWithState(x, y, z, nmsState);
    }

    /**
     * Sets a dynamically provided block state directly in the chunk section.
     */
    public boolean setBlock(int x, int y, int z, BlockData bukkitData) {
        BlockState dynamicState = ((CraftBlockData) bukkitData).getState();
        return setBlockWithState(x, y, z, dynamicState);
    }

    /** Number of blocks skipped so far due to unloaded chunks/sections. */
    public long getSkippedBlocks() {
        return skippedBlocks;
    }

    /**
     * Sends a full chunk packet to every player who currently has this chunk in
     * view, refreshing the modified blocks client-side. No-ops if the chunk is
     * unloaded or if the version-specific packet constructor is unavailable.
     */
    public static void sendChunkUpdate(World world, int chunkX, int chunkZ) {
        if (packetApiBroken) return;

        ServerLevel level = ((CraftWorld) world).getHandle();
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) return;

        final ClientboundLevelChunkWithLightPacket packet;
        try {
            packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
        } catch (Throwable t) {
            packetApiBroken = true;
            Bukkit.getLogger().warning("[LavaRise] Chunk-update packet API changed on this server "
                    + "version; live lava rendering disabled (blocks still apply server-side). " + t);
            return;
        }

        // Only players who actually track this chunk need the update.
        final int viewDistance = world.getViewDistance();
        for (Player p : world.getPlayers()) {
            if (Math.abs((p.getLocation().getBlockX() >> 4) - chunkX) > viewDistance) continue;
            if (Math.abs((p.getLocation().getBlockZ() >> 4) - chunkZ) > viewDistance) continue;
            ServerPlayer serverPlayer = ((CraftPlayer) p).getHandle();
            serverPlayer.connection.send(packet);
        }
    }
}
