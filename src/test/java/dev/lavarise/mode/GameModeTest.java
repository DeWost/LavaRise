package dev.lavarise.mode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameModeTest {

    @Test
    void parsesKnownValuesCaseInsensitively() {
        assertEquals(GameMode.SURVIVAL_CHALLENGE, GameMode.fromString("survival_challenge"));
        assertEquals(GameMode.ADMIN_EVENT, GameMode.fromString("ADMIN_EVENT"));
        assertEquals(GameMode.MINIGAME, GameMode.fromString("MiniGame"));
    }

    @Test
    void defaultsToMinigameOnUnknownOrNull() {
        assertEquals(GameMode.MINIGAME, GameMode.fromString(null));
        assertEquals(GameMode.MINIGAME, GameMode.fromString(""));
        assertEquals(GameMode.MINIGAME, GameMode.fromString("not_a_mode"));
    }
}
