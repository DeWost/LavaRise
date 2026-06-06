package dev.lavarise.arena;

import java.util.regex.Pattern;

/**
 * Validation for arena names. Names become file names ({@code <name>.yml}) and
 * YAML keys, so they must be restricted to a safe character set to prevent path
 * traversal and malformed config.
 *
 * @author DeWost
 */
public final class ArenaNames {

    /** 1–32 chars: letters, digits, underscore or hyphen. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private ArenaNames() {}

    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches();
    }
}
