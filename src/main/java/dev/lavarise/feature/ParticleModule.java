package dev.lavarise.feature;

import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Utility for spawning aesthetic particles along the lava surface during games.
 *
 * @author DeWost
 */
public final class ParticleModule {

    private ParticleModule() {}

    /**
     * Spawns {@code count} particles randomly across the lava surface.
     *
     * @param world    the arena world
     * @param minX     minimum X bound
     * @param maxX     maximum X bound
     * @param minZ     minimum Z bound
     * @param maxZ     maximum Z bound
     * @param lavaY    current lava Y-level
     * @param particle the particle type to spawn
     * @param count    how many particles to spawn this call
     */
    public static void spawnSurfaceParticles(World world, int minX, int maxX, int minZ, int maxZ,
                                             int lavaY, Particle particle, int count) {
        for (int i = 0; i < count; i++) {
            double x = minX + Math.random() * (maxX - minX + 1);
            double z = minZ + Math.random() * (maxZ - minZ + 1);
            world.spawnParticle(particle, x, lavaY + 1.1, z, 1);
        }
    }

    /**
     * Backwards-compatible helper using the default lava particle.
     */
    public static void spawnLavaSurfaceParticles(World world, int minX, int maxX, int minZ, int maxZ, int lavaY) {
        spawnSurfaceParticles(world, minX, maxX, minZ, maxZ, lavaY, Particle.LAVA, 30);
    }
}
