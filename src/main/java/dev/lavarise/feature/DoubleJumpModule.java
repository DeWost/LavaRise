package dev.lavarise.feature;

import dev.lavarise.arena.Arena;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives alive in-game players a configurable double-jump ability.
 * <p>
 * While the active phase is running, each alive player has
 * {@code setAllowFlight(true)} so the server knows they can "fly".
 * When they double-tap space ({@link PlayerToggleFlightEvent}) we intercept
 * it, cancel the actual flight toggle, and instead launch them upward plus
 * forward.  A per-player cooldown (stored as next-allowed millis) prevents
 * spamming; during the cooldown we simply ignore the toggle (allowFlight
 * stays {@code true} so they never start falling from a missed press).
 * </p>
 *
 * <p>Config path: {@code gameplay.double-jump}</p>
 *
 * @author DeWost
 */
public final class DoubleJumpModule implements Listener {

    private final LavaRisePlugin plugin;

    private boolean enabled;
    private int cooldownSeconds;
    private double power;
    private double forward;
    private String particleName;
    private String soundKey;

    /** UUID → next-allowed System.currentTimeMillis() */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public DoubleJumpModule(LavaRisePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * (Re)load config — safe to call from {@code /lr reload}.
     * Mirrors the pattern used by {@link PowerUpModule#load()}.
     */
    public void load() {
        final ConfigurationSection sec =
                plugin.getConfig().getConfigurationSection("gameplay.double-jump");
        if (sec == null) {
            enabled = false;
            return;
        }
        enabled = sec.getBoolean("enabled", true);
        cooldownSeconds = Math.max(1, sec.getInt("cooldown", 3));
        power = sec.getDouble("power", 0.9);
        forward = sec.getDouble("forward", 0.6);
        particleName = sec.getString("particle", "CLOUD");
        soundKey = sec.getString("sound", "entity.bat_takeoff");
    }

    /** Whether the double-jump feature is switched on in config. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Reset a single player's double-jump state (clear cooldown, revoke
     * flight).  Called by {@link dev.lavarise.state.ActiveState}'s
     * {@code cleanupSessionEffects()} so nothing leaks past a match.
     */
    public void reset(Player player) {
        cooldowns.remove(player.getUniqueId());
        try {
            player.setAllowFlight(false);
            player.setFlying(false);
        } catch (Throwable ignored) {
            // Player may have gone offline — best-effort.
        }
    }

    // ── Event handling ───────────────────────────────────────

    /**
     * Intercept double-space: launch the player and start the cooldown.
     * We only act when:
     * <ol>
     *   <li>The module is enabled.</li>
     *   <li>The player is alive in a running LavaRise game.</li>
     *   <li>The player is NOT in CREATIVE or SPECTATOR (they have real flight).</li>
     * </ol>
     */
    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!enabled) {
            return;
        }

        final Player player = event.getPlayer();
        final GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            return;
        }

        // Confirm the player is alive in a running LavaRise session.
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena == null || arena.getSession() == null) {
            return;
        }
        if (!arena.getSession().isAlive(player.getUniqueId())) {
            return;
        }

        // Always cancel the event — we never want real flight to engage.
        event.setCancelled(true);
        player.setFlying(false);

        // Cooldown gate — send action-bar feedback and ignore until ready.
        final long now = System.currentTimeMillis();
        final long nextAllowed = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextAllowed) {
            final long remaining = (nextAllowed - now + 999L) / 1000L;
            player.sendActionBar(plugin.getMiniMessage().deserialize(
                    "<red>Double jump on cooldown! <bold>" + remaining + "s</bold>"));
            return;
        }

        // Launch: horizontal component from look direction, vertical from config.
        final Vector direction = player.getLocation().getDirection().normalize();
        final Vector boost = new Vector(
                direction.getX() * forward,
                power,
                direction.getZ() * forward);
        player.setVelocity(boost);

        // Particle burst at foot level.
        try {
            final Particle particle = Particle.valueOf(particleName.toUpperCase());
            player.getWorld().spawnParticle(particle,
                    player.getLocation().add(0, 0.1, 0),
                    20, 0.3, 0.1, 0.3, 0.05);
        } catch (Throwable ignored) {
            // Invalid particle name in config — skip silently.
        }

        // Sound.
        try {
            player.playSound(player.getLocation(), soundKey, 0.8f, 1.2f);
        } catch (Throwable ignored) {
            // Invalid sound key — skip silently.
        }

        // Register cooldown.
        cooldowns.put(player.getUniqueId(), now + cooldownSeconds * 1000L);
    }
}
