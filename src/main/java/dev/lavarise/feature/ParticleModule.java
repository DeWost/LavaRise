package dev.lavarise.feature;

import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Utility for spawning aesthetic particles during game modes.
 */
public class ParticleModule {

    /**
     * Spawns random lava particles across the surface of the lava.
     */
    public static void spawnLavaSurfaceParticles(World world, int minX, int maxX, int minZ, int maxZ, int lavaY) {
        for (int i = 0; i < 30; i++) {
            double x = minX + Math.random() * (maxX - minX + 1);
            double z = minZ + Math.random() * (maxZ - minZ + 1);
            world.spawnParticle(Particle.LAVA, x, lavaY + 1.1, z, 1);
        }
    }
}
