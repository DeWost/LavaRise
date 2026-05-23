package dev.lavarise.mode;

import dev.lavarise.core.LavaRisePlugin;

/**
 * Handler for the Admin Event mode.
 * Allows administrators to manually control the game flow (pause/resume).
 */
public class AdminEventMode {
    
    private final LavaRisePlugin plugin;

    public AdminEventMode(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    public void triggerEvent() {
        plugin.debug("Admin Event triggered!");
        // Broadcast to all players and setup custom rewards
    }

    public void pauseEvent() {
        plugin.debug("Admin Event paused.");
    }

    public void resumeEvent() {
        plugin.debug("Admin Event resumed.");
    }
}
