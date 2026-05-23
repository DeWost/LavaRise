package dev.lavarise.mode;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.World;

/**
 * Handler for the Survival Challenge mode.
 * Manages world-wide lava rising events that affect all players in a world.
 */
public class SurvivalChallengeMode {
    
    private final LavaRisePlugin plugin;
    private World activeWorld;

    public SurvivalChallengeMode(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    public void startChallenge(World world) {
        this.activeWorld = world;
        plugin.debug("Survival Challenge started in world: " + world.getName());
        // Initialize global lava engine for the world
    }

    public void stopChallenge() {
        if (activeWorld != null) {
            plugin.debug("Survival Challenge stopped in world: " + activeWorld.getName());
            this.activeWorld = null;
        }
    }
}
