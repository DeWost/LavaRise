package dev.lavarise.feature;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mid-game chaos events that randomly apply wild effects to all alive players in
 * an arena for a short configurable duration. Events are triggered on a periodic
 * tick cadence from {@link dev.lavarise.state.ActiveState}; only one event may be
 * active per arena at a time.
 *
 * <p>Built-in events:
 * <ul>
 *   <li>{@code speed_surge} — Speed II + Jump Boost II</li>
 *   <li>{@code adrenaline} — Regeneration II + Strength I</li>
 *   <li>{@code low_gravity} — Slow Falling + Jump Boost I</li>
 *   <li>{@code blinding_fog} — Blindness (capped at 5 s)</li>
 *   <li>{@code meteor_shower} — falling fireballs above random alive players (no grief: yield 0, not incendiary)</li>
 * </ul>
 *
 * @author DeWost
 */
public final class ChaosEventModule {

    // ── Event definitions ───────────────────────────────────

    private enum EventType {
        SPEED_SURGE("speed_surge", "<red>Speed Surge"),
        ADRENALINE("adrenaline", "<gold>Adrenaline Rush"),
        LOW_GRAVITY("low_gravity", "<aqua>Low Gravity"),
        BLINDING_FOG("blinding_fog", "<dark_gray>Blinding Fog"),
        METEOR_SHOWER("meteor_shower", "<dark_red>Meteor Shower");

        final String id;
        final String displayName;

        EventType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        static EventType fromId(String id) {
            for (EventType t : values()) {
                if (t.id.equals(id)) {
                    return t;
                }
            }
            return null;
        }
    }

    // ── State per arena ─────────────────────────────────────

    private static final class ActiveEvent {
        final EventType type;
        final BukkitTask revertTask;
        final BukkitTask subTask;   // meteor repeating sub-task; may be null

        ActiveEvent(EventType type, BukkitTask revertTask, BukkitTask subTask) {
            this.type = type;
            this.revertTask = revertTask;
            this.subTask = subTask;
        }

        void cancel() {
            try {
                if (revertTask != null && !revertTask.isCancelled()) {
                    revertTask.cancel();
                }
            } catch (Throwable ignored) {
                // best-effort
            }
            try {
                if (subTask != null && !subTask.isCancelled()) {
                    subTask.cancel();
                }
            } catch (Throwable ignored) {
                // best-effort
            }
        }
    }

    // ── Module fields ───────────────────────────────────────

    private final LavaRisePlugin plugin;

    private boolean enabled;
    private int intervalSeconds;
    private int durationSeconds;
    private final List<EventType> enabledEvents = new ArrayList<>();

    /** Currently running event keyed by arena name. */
    private final Map<String, ActiveEvent> activeEvents = new ConcurrentHashMap<>();

    // ── Potion effect constants (looked up once at load time) ──

    private PotionEffectType typeSpeed;
    private PotionEffectType typeJumpBoost;
    private PotionEffectType typeRegeneration;
    private PotionEffectType typeStrength;
    private PotionEffectType typeSlowFalling;
    private PotionEffectType typeBlindness;

    public ChaosEventModule(LavaRisePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** (Re)load config — safe to call from {@code /lr reload}. */
    public void load() {
        enabledEvents.clear();
        final ConfigurationSection sec =
                plugin.getConfig().getConfigurationSection("gameplay.chaos-events");
        if (sec == null) {
            enabled = false;
            return;
        }
        enabled = sec.getBoolean("enabled", true);
        intervalSeconds = Math.max(10, sec.getInt("interval", 60));
        durationSeconds = Math.max(3, sec.getInt("duration", 12));

        final List<String> ids = sec.getStringList("events");
        if (ids.isEmpty()) {
            // default: all events enabled
            enabledEvents.addAll(Arrays.asList(EventType.values()));
        } else {
            for (String id : ids) {
                final EventType t = EventType.fromId(id.toLowerCase(java.util.Locale.ROOT));
                if (t != null) {
                    enabledEvents.add(t);
                }
            }
        }
        if (enabledEvents.isEmpty()) {
            enabled = false;
        }

        // Resolve potion types via Registry (Paper 1.21+)
        typeSpeed       = resolveEffect("speed");
        typeJumpBoost   = resolveEffect("jump_boost");
        typeRegeneration = resolveEffect("regeneration");
        typeStrength    = resolveEffect("strength");
        typeSlowFalling = resolveEffect("slow_falling");
        typeBlindness   = resolveEffect("blindness");
    }

    private PotionEffectType resolveEffect(String key) {
        try {
            return Registry.EFFECT.get(NamespacedKey.minecraft(key));
        } catch (Throwable t) {
            plugin.getLogger().warning("ChaosEventModule: could not resolve potion effect '" + key + "': " + t.getMessage());
            return null;
        }
    }

    /** Returns whether this module is enabled and has at least one event configured. */
    public boolean isEnabled() {
        return enabled && !enabledEvents.isEmpty();
    }

    /** Returns how many seconds between automatic event triggers. */
    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    // ── Public API ──────────────────────────────────────────

    /**
     * Pick a random enabled event and apply it to all alive players in the arena.
     * Silently no-ops if an event is already active for this arena.
     *
     * @param arena   the arena in which to trigger the event
     * @param session the current game session
     */
    public void triggerRandom(Arena arena, ArenaSession session) {
        if (!isEnabled()) {
            return;
        }
        final String arenaName = arena.getName();
        if (activeEvents.containsKey(arenaName)) {
            return; // one event at a time per arena
        }

        final List<EventType> shuffled = new ArrayList<>(enabledEvents);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        final EventType chosen = shuffled.get(0);

        try {
            activate(chosen, arena, session);
        } catch (Throwable t) {
            plugin.getLogger().warning("ChaosEventModule: triggerRandom failed for arena "
                    + arenaName + ": " + t.getMessage());
        }
    }

    /**
     * Immediately cancel the active chaos event for the given session (if any)
     * and strip its effects from all arena players. Called from
     * {@code ActiveState.cleanupSessionEffects()} on both normal game end and
     * {@code forceEnd()}.
     *
     * @param session the game session being ended
     */
    public void clearActive(ArenaSession session) {
        if (session == null) {
            return;
        }
        final String arenaName = session.getArena().getName();
        final ActiveEvent event = activeEvents.remove(arenaName);
        if (event == null) {
            return;
        }
        event.cancel();
        try {
            stripEffects(event.type, session);
        } catch (Throwable t) {
            plugin.getLogger().warning("ChaosEventModule: clearActive cleanup failed for arena "
                    + arenaName + ": " + t.getMessage());
        }
    }

    // ── Internal activation ─────────────────────────────────

    private void activate(EventType type, Arena arena, ArenaSession session) {
        final String arenaName = arena.getName();
        final int durationTicks = durationSeconds * 20;

        // Apply effects immediately to all alive players
        applyEffects(type, arena, session, durationTicks);

        // Announce the event
        announce(type, session);

        // Sub-task (meteor shower only)
        final BukkitTask subTask = buildSubTask(type, arena, session);

        // Schedule auto-revert after duration
        final BukkitTask revertTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                activeEvents.remove(arenaName);
                stripEffects(type, session);
                broadcastToArena(session, plugin.getMiniMessage().deserialize(
                        "<gray>The <white>" + type.displayName + " <gray>chaos event has ended."));
            } catch (Throwable t) {
                plugin.getLogger().warning("ChaosEventModule: revert task failed for arena "
                        + arenaName + ": " + t.getMessage());
            }
        }, durationTicks);

        activeEvents.put(arenaName, new ActiveEvent(type, revertTask, subTask));
    }

    // ── Effect application per type ─────────────────────────

    private void applyEffects(EventType type, Arena arena, ArenaSession session, int durationTicks) {
        for (UUID uuid : session.getAlivePlayers()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                continue;
            }
            try {
                applyToPlayer(type, p, durationTicks);
            } catch (Throwable t) {
                plugin.getLogger().warning("ChaosEventModule: applyToPlayer failed for "
                        + p.getName() + ": " + t.getMessage());
            }
        }
    }

    private void applyToPlayer(EventType type, Player player, int durationTicks) {
        switch (type) {
            case SPEED_SURGE -> {
                addEffect(player, typeSpeed,       durationTicks, 1); // Speed II
                addEffect(player, typeJumpBoost,   durationTicks, 1); // Jump Boost II
            }
            case ADRENALINE -> {
                addEffect(player, typeRegeneration, durationTicks, 1); // Regeneration II
                addEffect(player, typeStrength,     durationTicks, 0); // Strength I
            }
            case LOW_GRAVITY -> {
                addEffect(player, typeSlowFalling, durationTicks, 0); // Slow Falling
                addEffect(player, typeJumpBoost,   durationTicks, 0); // Jump Boost I
            }
            case BLINDING_FOG -> {
                // Cap blindness at 5 s even if duration is longer — mild enough to be fun
                final int blindTicks = Math.min(durationTicks, 5 * 20);
                addEffect(player, typeBlindness, blindTicks, 0);
            }
            case METEOR_SHOWER -> {
                // No potion effects — meteors handled by the sub-task
            }
            default -> {
                // Unknown event type — no-op
            }
        }
    }

    private void addEffect(Player player, PotionEffectType effectType, int durationTicks, int amplifier) {
        if (effectType == null || player == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(effectType, durationTicks, amplifier, false, true, true));
    }

    // ── Effect removal per type ─────────────────────────────

    private void stripEffects(EventType type, ArenaSession session) {
        for (UUID uuid : session.getAllPlayerIds()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                continue;
            }
            try {
                stripFromPlayer(type, p);
            } catch (Throwable ignored) {
                // best-effort per-player cleanup
            }
        }
    }

    private void stripFromPlayer(EventType type, Player player) {
        switch (type) {
            case SPEED_SURGE -> {
                removeEffect(player, typeSpeed);
                removeEffect(player, typeJumpBoost);
            }
            case ADRENALINE -> {
                removeEffect(player, typeRegeneration);
                removeEffect(player, typeStrength);
            }
            case LOW_GRAVITY -> {
                removeEffect(player, typeSlowFalling);
                removeEffect(player, typeJumpBoost);
            }
            case BLINDING_FOG -> {
                removeEffect(player, typeBlindness);
            }
            case METEOR_SHOWER -> {
                // no potion effects to strip
            }
            default -> {
                // Unknown event type — no-op
            }
        }
    }

    private void removeEffect(Player player, PotionEffectType effectType) {
        if (effectType == null || player == null) {
            return;
        }
        player.removePotionEffect(effectType);
    }

    // ── Meteor shower sub-task ──────────────────────────────

    /**
     * Build the repeating meteor task for {@code METEOR_SHOWER} events. Every ~2 s
     * a small fireball is spawned a few blocks above a random alive player. The
     * fireball has zero explosion yield and is not incendiary — cosmetic fun only.
     */
    private BukkitTask buildSubTask(EventType type, Arena arena, ArenaSession session) {
        if (type != EventType.METEOR_SHOWER) {
            return null;
        }
        final int meteorIntervalTicks = 40; // ~2 s between meteors
        return plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                spawnMeteor(arena, session);
            } catch (Throwable t) {
                plugin.getLogger().warning("ChaosEventModule: meteor spawn failed: " + t.getMessage());
            }
        }, 0L, meteorIntervalTicks);
    }

    private void spawnMeteor(Arena arena, ArenaSession session) {
        final Set<UUID> alive = session.getAlivePlayers();
        if (alive.isEmpty()) {
            return;
        }
        // Pick a random alive player as the target
        final List<UUID> aliveList = new ArrayList<>(alive);
        final UUID targetId = aliveList.get(ThreadLocalRandom.current().nextInt(aliveList.size()));
        final Player target = plugin.getServer().getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            return;
        }

        final World world = target.getWorld();
        final Location spawnLoc = target.getLocation().add(
                ThreadLocalRandom.current().nextDouble(-3, 3),
                6 + ThreadLocalRandom.current().nextDouble(0, 3),
                ThreadLocalRandom.current().nextDouble(-3, 3));

        // Safety: keep the spawn inside the arena footprint
        final var cfg = arena.getConfig();
        final double clampedX = Math.max(cfg.minX(), Math.min(cfg.maxX(), spawnLoc.getX()));
        final double clampedZ = Math.max(cfg.minZ(), Math.min(cfg.maxZ(), spawnLoc.getZ()));
        spawnLoc.setX(clampedX);
        spawnLoc.setZ(clampedZ);

        final Fireball fireball = world.spawn(spawnLoc, Fireball.class, fb -> {
            fb.setIsIncendiary(false);
            fb.setYield(0f); // zero explosion yield — no terrain damage
            // Aim gently at the target
            final Location tLoc = target.getLocation().add(0, 1, 0);
            final org.bukkit.util.Vector dir = tLoc.toVector()
                    .subtract(spawnLoc.toVector()).normalize().multiply(0.6);
            fb.setVelocity(dir);
            fb.setDirection(dir);
        });
        // Safety: schedule removal in case the fireball flies away without detonating
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (fireball.isValid()) {
                fireball.remove();
            }
        }, 100L); // 5 s max lifetime
    }

    // ── Announcement ────────────────────────────────────────

    private void announce(EventType type, ArenaSession session) {
        final Component titleComp = plugin.getMiniMessage().deserialize(
                "<dark_red><bold>⚠ CHAOS EVENT!</bold></dark_red>");
        final Component subtitleComp = plugin.getMiniMessage().deserialize(
                "<yellow>" + type.displayName + "</yellow>");
        final Title title = Title.title(titleComp, subtitleComp,
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(600)));

        final Component chat = plugin.getMiniMessage().deserialize(
                "<dark_red>⚠ <white>CHAOS: <yellow>" + type.displayName + "</yellow><white>! "
                        + "<gray>(lasts " + durationSeconds + "s)");

        for (UUID uuid : session.getAllPlayerIds()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                continue;
            }
            try {
                p.showTitle(title);
                p.sendMessage(chat);
                p.playSound(p.getLocation(), "entity.ender_dragon.growl", 0.6f, 1.2f);
            } catch (Throwable ignored) {
                // cosmetic — never break the game
            }
        }
    }

    private void broadcastToArena(ArenaSession session, Component msg) {
        for (UUID uuid : session.getAllPlayerIds()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }
}
