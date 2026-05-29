package dev.lavarise.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Lightweight update checker that queries the GitHub Releases API asynchronously
 * and logs a notice if a newer version is available. No third-party dependency —
 * uses the JDK {@link HttpClient}. Silently no-ops if the network is unavailable
 * or no release has been published yet.
 *
 * @author DeWost
 */
public final class UpdateChecker {

    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/DeWost/LavaRise/releases/latest";
    private static final String RELEASES_URL = "https://github.com/DeWost/LavaRise/releases";

    private final LavaRisePlugin plugin;

    public UpdateChecker(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    /** Run the check off the main thread. */
    public void checkAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::check);
    }

    private void check() {
        try {
            final HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build();
            final HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "LavaRise-UpdateChecker")
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();

            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return; // 404 = no releases yet

            final String latest = extractTag(response.body());
            if (latest == null) return;

            final String current = plugin.getPluginMeta().getVersion();
            if (isNewer(latest, current)) {
                plugin.getLogger().info("⬆ A new LavaRise version is available: "
                        + latest + " (you have " + current + ") — " + RELEASES_URL);
            } else {
                plugin.debug("LavaRise is up to date (" + current + ").");
            }
        } catch (Exception e) {
            plugin.debug("Update check skipped: " + e.getMessage());
        }
    }

    /** Pull {@code "tag_name": "v1.2.3"} out of the JSON without a JSON lib. */
    private String extractTag(String json) {
        int key = json.indexOf("\"tag_name\"");
        if (key < 0) return null;
        int colon = json.indexOf(':', key);
        int open = json.indexOf('"', colon + 1);
        int close = json.indexOf('"', open + 1);
        if (open < 0 || close < 0) return null;
        return json.substring(open + 1, close).trim();
    }

    /** Compare dotted numeric versions, ignoring a leading "v" and any suffix. */
    private boolean isNewer(String latest, String current) {
        final int[] a = parts(latest);
        final int[] b = parts(current);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private int[] parts(String version) {
        final String[] raw = version.replace("v", "").split("[.\\-]");
        final int[] out = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            try { out[i] = Integer.parseInt(raw[i].replaceAll("[^0-9]", "")); }
            catch (NumberFormatException ignored) { out[i] = 0; }
        }
        return out;
    }
}
