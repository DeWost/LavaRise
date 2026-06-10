package dev.lavarise.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the localization catalogs: every bundled translation must define the
 * exact same key set as the canonical English {@code messages.yml}, so a newly
 * added English key can't silently ship without a translation (and a stray key
 * can't linger in a translation).
 *
 * @author DeWost
 */
class MessageCatalogParityTest {

    private FileConfiguration load(String resource) {
        final InputStream in = getClass().getResourceAsStream("/" + resource);
        assertNotNull(in, "bundled resource missing: " + resource);
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @Test
    void turkishCatalogMatchesEnglishKeySet() {
        final Set<String> english = new TreeSet<>(load("messages.yml").getKeys(true));
        final Set<String> turkish = new TreeSet<>(load("messages_tr.yml").getKeys(true));

        final Set<String> missingInTr = new TreeSet<>(english);
        missingInTr.removeAll(turkish);
        final Set<String> extraInTr = new TreeSet<>(turkish);
        extraInTr.removeAll(english);

        assertTrue(missingInTr.isEmpty(), "messages_tr.yml is missing keys: " + missingInTr);
        assertTrue(extraInTr.isEmpty(), "messages_tr.yml has unknown keys: " + extraInTr);
        assertEquals(english, turkish);
    }
}
