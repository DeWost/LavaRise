package dev.lavarise.feature;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Awards players for climbing high above the lava-start Y during a game.
 * <p>
 * Each tier in {@code gameplay.height-rewards.tiers} is granted exactly once
 * per player per game when their feet reach {@code lavaStartY + tier.height}.
 * Rewards include a chat message, a title, a potion effect, console commands
 * and optional XP (via {@link LevelManager}). All tier processing is wrapped
 * in try/catch so a misconfigured tier can never break the game loop.
 * </p>
 * <p>
 * Call {@link #check(Arena, ArenaSession)} periodically from
 * {@link dev.lavarise.state.ActiveState} (every 10–20 ticks is sufficient).
 * Call {@link #clear(ArenaSession)} in
 * {@link dev.lavarise.state.ActiveState#cleanupSessionEffects()} on game end.
 * </p>
 *
 * @author DeWost
 */
public final class HeightRewardModule {

    // ── Tier definition ─────────────────────────────────────

    /**
     * Immutable value-object representing one configured height-reward tier.
     */
    private static final class Tier {
        final int height;
        final String message;
        final PotionEffectType effectType;
        final int effectDurationTicks;
        final int effectAmplifier;
        final List<String> commands;
        final long xp;

        Tier(int height,
             String message,
             PotionEffectType effectType,
             int effectDurationTicks,
             int effectAmplifier,
             List<String> commands,
             long xp) {
            this.height = height;
            this.message = message;
            this.effectType = effectType;
            this.effectDurationTicks = effectDurationTicks;
            this.effectAmplifier = effectAmplifier;
            this.commands = Collections.unmodifiableList(commands);
            this.xp = xp;
        }
    }

    // ── Module fields ────────────────────────────────────────

    private final LavaRisePlugin plugin;

    private boolean enabled;
    private final List<Tier> tiers = new ArrayList<>();

    /**
     * Tracks which tier heights have already been awarded this game per player.
     * Key: arena name → Map(UUID → Set of awarded tier heights).
     * ConcurrentHashMap for safe access across scheduler and main-thread ticks.
     */
    private final Map<String, Map<UUID, Set<Integer>>> awarded = new ConcurrentHashMap<>();

    // ── Constructor & lifecycle ──────────────────────────────

    /**
     * Constructs the module and performs an initial config load.
     *
     * @param plugin owning plugin instance
     */
    public HeightRewardModule(LavaRisePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * (Re)load tier definitions from {@code gameplay.height-rewards}. Safe to
     * call from {@code /lr reload}.
     */
    public void load() {
        tiers.clear();
        final ConfigurationSection sec =
                plugin.getConfig().getConfigurationSection("gameplay.height-rewards");
        if (sec == null) {
            enabled = false;
            return;
        }
        enabled = sec.getBoolean("enabled", true);
        if (!enabled) {
            return;
        }

        final List<Map<?, ?>> tierList = sec.getMapList("tiers");
        for (final Map<?, ?> raw : tierList) {
            try {
                final int height = intFrom(raw, "height", 0);
                if (height <= 0) {
                    plugin.getLogger().warning("HeightRewardModule: skipping tier with height ≤ 0.");
                    continue;
                }
                final String message = stringFrom(raw, "message",
                        "<gold>You reached <bold>" + height + "</bold> blocks high!");

                // Optional potion effect
                PotionEffectType effectType = null;
                int effectDurationTicks = 100;
                int effectAmplifier = 0;
                final String effectId = stringFrom(raw, "effect", null);
                if (effectId != null && !effectId.isBlank()) {
                    effectType = resolveEffect(effectId);
                    effectDurationTicks = intFrom(raw, "effect-duration", 5) * 20;
                    effectAmplifier = intFrom(raw, "effect-amplifier", 0);
                }

                // Optional commands
                final List<String> commands = new ArrayList<>();
                final Object cmdRaw = raw.get("commands");
                if (cmdRaw instanceof List<?> cmdList) {
                    for (final Object entry : cmdList) {
                        if (entry instanceof String s && !s.isBlank()) {
                            commands.add(s);
                        }
                    }
                }

                // Optional XP
                final long xp = longFrom(raw, "xp", 0L);

                tiers.add(new Tier(height, message, effectType, effectDurationTicks,
                        effectAmplifier, commands, xp));
            } catch (Throwable t) {
                plugin.getLogger().warning("HeightRewardModule: failed to parse a tier entry: " + t.getMessage());
            }
        }

        // Sort ascending by height so we can iterate cheaply
        tiers.sort((a, b) -> Integer.compare(a.height, b.height));

        if (tiers.isEmpty() && enabled) {
            plugin.getLogger().warning("HeightRewardModule: enabled but no valid tiers configured.");
        }

        plugin.debug("HeightRewardModule loaded " + tiers.size() + " tier(s), enabled=" + enabled);
    }

    /** Whether this module is enabled and has at least one tier. */
    public boolean isEnabled() {
        return enabled && !tiers.isEmpty();
    }

    // ── Public API ───────────────────────────────────────────

    /**
     * Check every alive player in the session for newly reached height tiers and
     * award them once per game. Call every 10–20 ticks from the active-game loop.
     *
     * @param arena   the active arena
     * @param session the current game session
     */
    public void check(Arena arena, ArenaSession session) {
        if (!isEnabled()) {
            return;
        }

        final int lavaStartY = arena.getConfig().lavaStartY();
        final String arenaName = arena.getName();

        for (final UUID uuid : session.getAlivePlayers()) {
            final Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!session.isAlive(uuid)) {
                continue;
            }

            final int feetY = player.getLocation().getBlockY();
            final int heightAboveStart = feetY - lavaStartY;

            if (heightAboveStart <= 0) {
                continue;
            }

            // Retrieve (or create) the awarded set for this player in this arena
            final Map<UUID, Set<Integer>> arenaMap =
                    awarded.computeIfAbsent(arenaName, k -> new ConcurrentHashMap<>());
            final Set<Integer> reachedTiers =
                    arenaMap.computeIfAbsent(uuid, k -> new HashSet<>());

            for (final Tier tier : tiers) {
                if (heightAboveStart >= tier.height && !reachedTiers.contains(tier.height)) {
                    reachedTiers.add(tier.height);
                    try {
                        award(player, tier);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("HeightRewardModule: award failed for "
                                + player.getName() + " at height " + tier.height + ": " + t.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Reset all per-game tracking for this session. Call from
     * {@link dev.lavarise.state.ActiveState}'s {@code cleanupSessionEffects()}
     * on both normal and forced game end.
     *
     * @param session the session that is ending
     */
    public void clear(ArenaSession session) {
        if (session == null) {
            return;
        }
        awarded.remove(session.getArena().getName());
    }

    // ── Internal ─────────────────────────────────────────────

    /**
     * Grant a single tier's rewards to the player. Every reward type is wrapped
     * in its own try/catch so a bad effect or command can't abort the rest.
     */
    private void award(Player player, Tier tier) {
        // Chat message
        try {
            final String text = tier.message.replace("{height}", String.valueOf(tier.height));
            final Component msg = plugin.getMiniMessage().deserialize(text);
            player.sendMessage(msg);
        } catch (Throwable t) {
            plugin.getLogger().warning("HeightRewardModule: message parse failed: " + t.getMessage());
        }

        // Title
        try {
            final String text = tier.message.replace("{height}", String.valueOf(tier.height));
            final Component sub = plugin.getMiniMessage().deserialize(text);
            final Component main = plugin.getMiniMessage().deserialize(
                    "<gold><bold>✦ HEIGHT REWARD! ✦</bold></gold>");
            player.showTitle(Title.title(main, sub,
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2),
                            Duration.ofMillis(500))));
        } catch (Throwable t) {
            plugin.getLogger().warning("HeightRewardModule: title send failed: " + t.getMessage());
        }

        // Sound
        try {
            player.playSound(player.getLocation(), "ui.toast.challenge_complete", 0.8f, 1.2f);
        } catch (Throwable ignored) {
            // Bad sound key — skip silently.
        }

        // Potion effect
        if (tier.effectType != null) {
            try {
                player.addPotionEffect(new PotionEffect(
                        tier.effectType,
                        tier.effectDurationTicks,
                        tier.effectAmplifier,
                        false, true, true));
            } catch (Throwable t) {
                plugin.getLogger().warning("HeightRewardModule: effect application failed for "
                        + player.getName() + ": " + t.getMessage());
            }
        }

        // Console commands
        for (final String cmd : tier.commands) {
            try {
                final String resolved = cmd.replace("{player}", player.getName());
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), resolved);
            } catch (Throwable t) {
                plugin.getLogger().warning("HeightRewardModule: command failed ["
                        + cmd + "]: " + t.getMessage());
            }
        }

        // XP via LevelManager (null-safe)
        if (tier.xp > 0) {
            try {
                final LevelManager lm = plugin.getLevelManager();
                if (lm != null && lm.isEnabled()) {
                    lm.addXp(player, tier.xp, "height_reward");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("HeightRewardModule: XP award failed for "
                        + player.getName() + ": " + t.getMessage());
            }
        }
    }

    // ── Config helpers ───────────────────────────────────────

    private PotionEffectType resolveEffect(String key) {
        try {
            return Registry.EFFECT.get(NamespacedKey.minecraft(key.toLowerCase(java.util.Locale.ROOT)));
        } catch (Throwable t) {
            plugin.getLogger().warning("HeightRewardModule: could not resolve potion effect '"
                    + key + "': " + t.getMessage());
            return null;
        }
    }

    private static int intFrom(Map<?, ?> map, String key, int def) {
        final Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static long longFrom(Map<?, ?> map, String key, long def) {
        final Object val = map.get(key);
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static String stringFrom(Map<?, ?> map, String key, String def) {
        final Object val = map.get(key);
        if (val instanceof String s) {
            return s;
        }
        return def;
    }
}
