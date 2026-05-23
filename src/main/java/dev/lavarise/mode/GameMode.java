package dev.lavarise.mode;

/**
 * Represents the different types of LavaRise game modes available.
 */
public enum GameMode {
    /**
     * Standard arena-based minigame.
     * Players wait in lobby, game starts, last one standing wins.
     */
    MINIGAME,

    /**
     * Server-wide or world-wide survival challenge.
     * Lava rises continuously or periodically across the world.
     */
    SURVIVAL_CHALLENGE,

    /**
     * Admin triggered special event mode.
     * Manually started, paused, and stopped by administrators.
     */
    ADMIN_EVENT;

    /**
     * Parses a string into a GameMode, defaulting to MINIGAME.
     */
    public static GameMode fromString(String modeStr) {
        if (modeStr == null || modeStr.isBlank()) return MINIGAME;
        try {
            return valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MINIGAME;
        }
    }
}
