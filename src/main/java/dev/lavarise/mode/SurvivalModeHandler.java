package dev.lavarise.mode;

import dev.lavarise.core.LavaRisePlugin;

/**
 * World-wide survival challenge. The lava covers a large radius around the world
 * spawn rather than a hand-built arena, so we skip the volume snapshot (scanning
 * and restoring millions of blocks would be impractical). The default
 * last-survivor win condition still applies; the game also ends when the lava
 * reaches its max Y or an admin stops it.
 *
 * @author DeWost
 */
public final class SurvivalModeHandler extends GameModeHandler {

    public SurvivalModeHandler(LavaRisePlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean shouldSnapshot() {
        return false;
    }
}
