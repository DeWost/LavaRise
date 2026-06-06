package dev.lavarise.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaNamesTest {

    @Test
    void acceptsSafeNames() {
        assertTrue(ArenaNames.isValid("volcano"));
        assertTrue(ArenaNames.isValid("Arena_1"));
        assertTrue(ArenaNames.isValid("a-b-2"));
        assertTrue(ArenaNames.isValid("A"));
    }

    @Test
    void rejectsPathTraversalAndUnsafeNames() {
        assertFalse(ArenaNames.isValid("../../evil"));
        assertFalse(ArenaNames.isValid("a/b"));
        assertFalse(ArenaNames.isValid("a\\b"));
        assertFalse(ArenaNames.isValid("with space"));
        assertFalse(ArenaNames.isValid("dots.yml"));
        assertFalse(ArenaNames.isValid(""));
        assertFalse(ArenaNames.isValid(null));
        assertFalse(ArenaNames.isValid("x".repeat(33))); // too long
    }
}
