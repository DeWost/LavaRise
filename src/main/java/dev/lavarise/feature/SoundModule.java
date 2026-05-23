package dev.lavarise.feature;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Standardized sound effects for LavaRise events.
 */
public class SoundModule {

    public static void playLavaRiseSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_AMBIENT, 0.5f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_POP, 0.5f, 0.8f);
    }

    public static void playEliminationSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_DEATH, 1.0f, 0.5f);
    }

    public static void playWinSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    public static void playCountdownSound(Player player, boolean isFinal) {
        if (isFinal) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        }
    }
}
