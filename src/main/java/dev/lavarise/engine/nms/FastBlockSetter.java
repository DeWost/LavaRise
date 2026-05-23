package dev.lavarise.engine.nms;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collection;

public class FastBlockSetter {
    private final ServerLevel serverLevel;
    private final BlockState nmsState;

    private LevelChunk lastChunk = null;
    private LevelChunkSection lastSection = null;
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private int lastSectionIndex = Integer.MIN_VALUE;

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
        }

        if (lastChunk == null) return false;

        if (sectionIndex != lastSectionIndex) {
            LevelChunkSection[] sections = lastChunk.getSections();
            if (sectionIndex >= 0 && sectionIndex < sections.length) {
                lastSection = sections[sectionIndex];
            } else {
                lastSection = null;
            }
            lastSectionIndex = sectionIndex;
        }

        if (lastSection == null) return false;

        int localX = x & 15;
        int localY = y & 15;
        int localZ = z & 15;

        // Set the block state without locking (lock is handled externally if needed, 
        // but we assume main thread execution here).
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

    /**
     * Sends a chunk update packet to all provided players.
     */
    public static void sendChunkUpdate(World world, int chunkX, int chunkZ, Collection<? extends Player> players) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) return;

        // In 1.21.1, the packet constructor is ClientboundLevelChunkWithLightPacket(LevelChunk, LightEngine, BitSet, BitSet)
        // Paper often provides a simpler constructor or we can just send the chunk data.
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);

        for (Player p : players) {
            ServerPlayer serverPlayer = ((CraftPlayer) p).getHandle();
            serverPlayer.connection.send(packet);
        }
    }
}
