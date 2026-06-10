package dev.lavarise.feature;

import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.feature.gui.CosmeticGUIHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Config-driven cosmetics system with three categories:
 * <ul>
 *   <li><b>kill-effect</b> — particle/effect at the victim's location when a player
 *       gets a kill.</li>
 *   <li><b>win-effect</b> — fireworks/particles shown around the winner at game end.</li>
 *   <li><b>death-cry</b>  — a sound broadcast to the arena when the owner is
 *       eliminated.</li>
 * </ul>
 *
 * <p>Definitions live under {@code cosmetics:} in config.yml; per-player
 * selections are persisted in {@code cosmetics.yml} (one key per UUID).
 * The GUI is opened with {@code /lr cosmetics} (permission
 * {@code lavarise.play}) and self-registers as a Listener in its
 * constructor.</p>
 *
 * @author DeWost
 */
public final class CosmeticManager implements Listener {

    // ── PDC keys ─────────────────────────────────────────────

    private final NamespacedKey keyCategory;
    private final NamespacedKey keyCosmeticId;

    // ── Category names ────────────────────────────────────────

    private static final String CAT_KILL = "kill-effect";
    private static final String CAT_WIN = "win-effect";
    private static final String CAT_DEATH = "death-cry";

    // ── Data model ────────────────────────────────────────────

    private final LavaRisePlugin plugin;
    private final File file;
    private final FileConfiguration store;

    /** kill-effect definitions keyed by id */
    private final List<CosmeticEntry> killEffects = new ArrayList<>();
    /** win-effect definitions keyed by id */
    private final List<CosmeticEntry> winEffects = new ArrayList<>();
    /** death-cry definitions keyed by id */
    private final List<CosmeticEntry> deathCries = new ArrayList<>();

    /** UUID → (category → selected cosmetic id) */
    private final Map<UUID, Map<String, String>> selections = new HashMap<>();

    private boolean enabled;

    // ─────────────────────────────────────────────────────────

    /**
     * Constructs the manager, wires the GUI listener, and loads definitions
     * + persisted selections from disk.
     *
     * @param plugin the owning plugin
     */
    public CosmeticManager(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.keyCategory = new NamespacedKey(plugin, "cosmetic_category");
        this.keyCosmeticId = new NamespacedKey(plugin, "cosmetic_id");
        this.file = new File(plugin.getDataFolder(), "cosmetics.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create cosmetics.yml", e);
            }
        }
        this.store = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        load();
    }

    // ── Config loading ────────────────────────────────────────

    /**
     * (Re)loads cosmetic definitions from config.yml.
     * Safe to call from {@code /lr reload}.
     */
    public void load() {
        killEffects.clear();
        winEffects.clear();
        deathCries.clear();
        this.enabled = plugin.getConfig().getBoolean("cosmetics.enabled", true);

        loadCategory(CAT_KILL, killEffects);
        loadCategory(CAT_WIN, winEffects);
        loadCategory(CAT_DEATH, deathCries);

        // Also reload persisted selections from disk.
        store.options().copyDefaults(false);
        final FileConfiguration fresh = YamlConfiguration.loadConfiguration(file);
        store.setDefaults(fresh);
        for (String uuidStr : fresh.getKeys(false)) {
            try {
                final UUID uuid = UUID.fromString(uuidStr);
                final ConfigurationSection sec = fresh.getConfigurationSection(uuidStr);
                if (sec == null) {
                    continue;
                }
                final Map<String, String> sel = new HashMap<>();
                for (String cat : sec.getKeys(false)) {
                    sel.put(cat, sec.getString(cat, ""));
                }
                selections.put(uuid, sel);
            } catch (IllegalArgumentException ignored) {
                // Non-UUID key — skip.
            }
        }
    }

    private void loadCategory(String category, List<CosmeticEntry> target) {
        final ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("cosmetics." + category);
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            final ConfigurationSection entry = sec.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            target.add(new CosmeticEntry(
                    id,
                    entry.getString("name", id),
                    entry.getString("icon", "GRASS_BLOCK"),
                    entry.getString("effect", ""),
                    entry.getString("requires", null)));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ── Selection API ─────────────────────────────────────────

    /**
     * Returns the player's selected cosmetic id for the given category, or
     * the first defined cosmetic (the free default) if none is set.
     */
    public String getSelection(UUID uuid, String category) {
        final List<CosmeticEntry> list = listForCategory(category);
        if (list.isEmpty()) {
            return "";
        }
        final Map<String, String> sel = selections.get(uuid);
        if (sel == null) {
            return list.get(0).id();
        }
        final String chosen = sel.get(category);
        if (chosen == null || chosen.isBlank()) {
            return list.get(0).id();
        }
        // Verify the player still has the required achievement (could have been
        // reconfigured since last login).
        final CosmeticEntry entry = findEntry(list, chosen);
        if (entry == null) {
            return list.get(0).id();
        }
        if (entry.requires() != null && !isUnlocked(uuid, entry)) {
            return list.get(0).id();
        }
        return chosen;
    }

    /** Returns true if the cosmetic is free or the player has earned the required achievement. */
    public boolean isUnlocked(UUID uuid, CosmeticEntry entry) {
        if (entry.requires() == null || entry.requires().isBlank()) {
            return true;
        }
        return plugin.getAchievementManager().hasEarned(uuid, entry.requires());
    }

    /** Persist a player's selection for the given category. */
    public void setSelection(UUID uuid, String category, String cosmeticId) {
        selections.computeIfAbsent(uuid, k -> new HashMap<>()).put(category, cosmeticId);
        store.set(uuid + "." + category, cosmeticId);
        save();
    }

    // ── Effect application ────────────────────────────────────

    /**
     * Plays the killer's chosen kill-effect at the victim's location.
     * Swallows all exceptions so a bad config never breaks the game loop.
     *
     * @param killer         the player who got the kill
     * @param victimLocation the location where the victim was
     */
    public void playKillEffect(Player killer, Location victimLocation) {
        if (!enabled || killer == null || victimLocation == null) {
            return;
        }
        final String id = getSelection(killer.getUniqueId(), CAT_KILL);
        final CosmeticEntry entry = findEntry(killEffects, id);
        if (entry == null) {
            return;
        }
        final String effect = entry.effect().trim().toUpperCase(Locale.ROOT);
        if (effect.isEmpty() || effect.equals("NONE")) {
            return;
        }
        try {
            applyKillEffect(effect, victimLocation);
        } catch (Exception e) {
            plugin.getLogger().warning("[Cosmetics] kill-effect '" + id
                    + "' error: " + e.getMessage());
        }
    }

    private void applyKillEffect(String effect, Location loc) {
        switch (effect) {
            case "LIGHTNING" -> {
                if (loc.getWorld() != null) {
                    loc.getWorld().strikeLightningEffect(loc);
                }
            }
            case "FLAME" -> {
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(Particle.FLAME, loc, 40, 0.3, 0.5, 0.3, 0.05);
                }
            }
            case "EXPLOSION" -> {
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 0.5, 0.5, 0.5, 0.1);
                }
            }
            case "HEART" -> {
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(Particle.HEART, loc, 20, 0.4, 0.4, 0.4, 0.05);
                }
            }
            default -> {
                // Attempt to look up as a Particle enum value.
                try {
                    final Particle p = Particle.valueOf(effect);
                    if (loc.getWorld() != null) {
                        loc.getWorld().spawnParticle(p, loc, 20, 0.3, 0.5, 0.3, 0.05);
                    }
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("[Cosmetics] Unknown kill-effect value: " + effect);
                }
            }
        }
    }

    /**
     * Plays the winner's chosen win-effect around them at game end.
     * Swallows all exceptions so a bad config never breaks the game loop.
     *
     * @param winner the winning player
     */
    public void playWinEffect(Player winner) {
        if (!enabled || winner == null || !winner.isOnline()) {
            return;
        }
        final String id = getSelection(winner.getUniqueId(), CAT_WIN);
        final CosmeticEntry entry = findEntry(winEffects, id);
        if (entry == null) {
            return;
        }
        final String effect = entry.effect().trim().toUpperCase(Locale.ROOT);
        if (effect.isEmpty() || effect.equals("NONE")) {
            return;
        }
        try {
            applyWinEffect(effect, winner);
        } catch (Exception e) {
            plugin.getLogger().warning("[Cosmetics] win-effect '" + id
                    + "' error: " + e.getMessage());
        }
    }

    private void applyWinEffect(String effect, Player winner) {
        switch (effect) {
            case "FIREWORKS_SPIRAL" -> launchSpiralFireworks(winner);
            case "TOTEM" -> {
                if (winner.getWorld() != null) {
                    winner.getWorld().spawnParticle(
                            Particle.TOTEM_OF_UNDYING,
                            winner.getLocation().add(0, 1, 0),
                            80, 0.5, 1.0, 0.5, 0.1);
                }
            }
            case "FIREWORKS_SHOW" -> launchSpiralFireworks(winner);
            default -> {
                // Attempt to look up as a Particle enum value.
                try {
                    final Particle p = Particle.valueOf(effect);
                    if (winner.getWorld() != null) {
                        winner.getWorld().spawnParticle(
                                p, winner.getLocation().add(0, 1, 0),
                                60, 0.5, 1.0, 0.5, 0.05);
                    }
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("[Cosmetics] Unknown win-effect value: " + effect);
                }
            }
        }
    }

    private void launchSpiralFireworks(Player player) {
        final Color[] colors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME,
                Color.AQUA, Color.BLUE, Color.FUCHSIA};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || player.getWorld() == null) {
                        return;
                    }
                    // Offset each rocket around the player.
                    final double angle = idx * (2 * Math.PI / 5);
                    final double radius = 1.5;
                    final Location loc = player.getLocation().add(
                            Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    final Firework fw = player.getWorld().spawn(loc, Firework.class);
                    final FireworkMeta meta = fw.getFireworkMeta();
                    final Color c = colors[idx % colors.length];
                    meta.addEffect(FireworkEffect.builder()
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .withColor(c)
                            .withFade(Color.WHITE)
                            .withFlicker()
                            .withTrail()
                            .build());
                    meta.setPower(1);
                    fw.setFireworkMeta(meta);
                }
            }.runTaskLater(plugin, i * 10L);
        }
    }

    /**
     * Broadcasts the eliminated player's chosen death-cry sound to the entire arena.
     * Swallows all exceptions so a bad config never breaks the game loop.
     *
     * @param player      the eliminated player
     * @param sessionUuids all UUIDs of players in the arena (for broadcast)
     */
    public void playDeathCry(Player player, Iterable<UUID> sessionUuids) {
        if (!enabled || player == null) {
            return;
        }
        final String id = getSelection(player.getUniqueId(), CAT_DEATH);
        final CosmeticEntry entry = findEntry(deathCries, id);
        if (entry == null) {
            return;
        }
        final String soundKey = entry.effect().trim();
        if (soundKey.isEmpty() || soundKey.equalsIgnoreCase("none")) {
            return;
        }
        try {
            for (UUID uuid : sessionUuids) {
                final Player target = Bukkit.getPlayer(uuid);
                if (target != null && target.isOnline()) {
                    try {
                        target.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);
                    } catch (Exception ignored) {
                        // Individual sound failure must not abort the loop.
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Cosmetics] death-cry '" + id
                    + "' error: " + e.getMessage());
        }
    }

    // ── GUI ───────────────────────────────────────────────────

    /**
     * Opens the cosmetics selection GUI for the given player.
     *
     * @param player the player to show the GUI to
     */
    public void openGui(Player player) {
        // 3 categories × up to 9 items each, plus 1 header row per category → 6 rows total.
        final int rows = 6;
        final int size = rows * 9;
        final CosmeticGUIHolder holder = new CosmeticGUIHolder();
        final Inventory inv = Bukkit.createInventory(holder, size,
                plugin.getMiniMessage().deserialize("<dark_purple><bold>Cosmetics"));
        holder.setInventory(inv);

        // Row 0: kill-effects header + items
        inv.setItem(0, sectionHeader(Material.BLAZE_POWDER, "<red><bold>Kill Effects"));
        populateCategory(inv, 1, killEffects, CAT_KILL, player);

        // Row 2: win-effects header + items
        inv.setItem(18, sectionHeader(Material.FIREWORK_ROCKET, "<gold><bold>Win Effects"));
        populateCategory(inv, 19, winEffects, CAT_WIN, player);

        // Row 4: death-cry header + items
        inv.setItem(36, sectionHeader(Material.NOTE_BLOCK, "<aqua><bold>Death Cries"));
        populateCategory(inv, 37, deathCries, CAT_DEATH, player);

        player.openInventory(inv);
    }

    private ItemStack sectionHeader(Material mat, String title) {
        final ItemStack item = new ItemStack(mat);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMiniMessage().deserialize("<!italic>" + title));
            meta.lore(Collections.emptyList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void populateCategory(Inventory inv, int startSlot,
                                   List<CosmeticEntry> entries, String category, Player player) {
        final UUID uuid = player.getUniqueId();
        final String selected = getSelection(uuid, category);
        int slot = startSlot;
        for (CosmeticEntry entry : entries) {
            if (slot >= inv.getSize()) {
                break;
            }
            final boolean isSelected = entry.id().equals(selected);
            final boolean unlocked = isUnlocked(uuid, entry);
            inv.setItem(slot++, buildCosmeticItem(entry, category, isSelected, unlocked));
        }
    }

    private ItemStack buildCosmeticItem(CosmeticEntry entry, String category,
                                         boolean selected, boolean unlocked) {
        if (!unlocked) {
            // Locked: barrier with requirement in lore.
            final ItemStack item = new ItemStack(Material.BARRIER);
            final ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(plugin.getMiniMessage().deserialize(
                        "<!italic><red>" + stripMini(entry.name()) + " <dark_gray>[Locked]"));
                final List<Component> lore = new ArrayList<>();
                lore.add(plugin.getMiniMessage().deserialize(
                        "<!italic><gray>Requires achievement: <yellow>" + entry.requires()));
                meta.lore(lore);
                meta.getPersistentDataContainer().set(keyCategory, PersistentDataType.STRING, category);
                meta.getPersistentDataContainer().set(keyCosmeticId, PersistentDataType.STRING, entry.id());
                item.setItemMeta(meta);
            }
            return item;
        }

        final Material icon = parseMaterial(entry.icon());
        final ItemStack item = new ItemStack(icon);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (selected) {
                meta.displayName(plugin.getMiniMessage().deserialize(
                        "<!italic><green><bold>" + stripMini(entry.name()) + " <yellow>[Selected]"));
                // Glow effect via a fake enchant + HIDE_ENCHANTS flag.
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.displayName(plugin.getMiniMessage().deserialize(
                        "<!italic>" + entry.name()));
            }
            final List<Component> lore = new ArrayList<>();
            lore.add(plugin.getMiniMessage().deserialize(
                    "<!italic><gray>Effect: <white>" + entry.effect()));
            if (selected) {
                lore.add(plugin.getMiniMessage().deserialize("<!italic><green>✔ Currently selected"));
            } else {
                lore.add(plugin.getMiniMessage().deserialize("<!italic><yellow>Click to select"));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(keyCategory, PersistentDataType.STRING, category);
            meta.getPersistentDataContainer().set(keyCosmeticId, PersistentDataType.STRING, entry.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Strip MiniMessage tags from a string for plain-text comparison. */
    private String stripMini(String s) {
        return s.replaceAll("<[^>]+>", "");
    }

    // ── GUI events ────────────────────────────────────────────

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CosmeticGUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CosmeticGUIHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        final var pdc = item.getItemMeta().getPersistentDataContainer();
        final String category = pdc.get(keyCategory, PersistentDataType.STRING);
        final String cosmeticId = pdc.get(keyCosmeticId, PersistentDataType.STRING);
        if (category == null || cosmeticId == null) {
            return;
        }
        // Reject locked items.
        final List<CosmeticEntry> list = listForCategory(category);
        final CosmeticEntry entry = findEntry(list, cosmeticId);
        if (entry == null) {
            return;
        }
        if (!isUnlocked(player.getUniqueId(), entry)) {
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<red>That cosmetic is locked! Earn the <yellow>"
                            + entry.requires() + "</yellow> achievement first."));
            return;
        }
        setSelection(player.getUniqueId(), category, cosmeticId);
        player.sendMessage(plugin.getMiniMessage().deserialize(
                "<green>Selected <yellow>" + stripMini(entry.name())
                        + "</yellow> as your " + humanCategory(category) + " cosmetic."));
        // Refresh the GUI so the glowing selection updates immediately.
        openGui(player);
    }

    private String humanCategory(String category) {
        return switch (category) {
            case CAT_KILL -> "kill-effect";
            case CAT_WIN -> "win-effect";
            case CAT_DEATH -> "death-cry";
            default -> category;
        };
    }

    // ── Persistence ───────────────────────────────────────────

    /** Flush persisted selections to cosmetics.yml. */
    public void save() {
        try {
            store.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save cosmetics.yml", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private List<CosmeticEntry> listForCategory(String category) {
        return switch (category) {
            case CAT_KILL -> killEffects;
            case CAT_WIN -> winEffects;
            case CAT_DEATH -> deathCries;
            default -> Collections.emptyList();
        };
    }

    private CosmeticEntry findEntry(List<CosmeticEntry> list, String id) {
        for (CosmeticEntry e : list) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private Material parseMaterial(String name) {
        try {
            final Material mat = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (mat != null) {
                return mat;
            }
        } catch (Exception ignored) {
            // Fall through.
        }
        return Material.GRASS_BLOCK;
    }

    // ── Data record ───────────────────────────────────────────

    /**
     * A single cosmetic option loaded from config.
     *
     * @param id       config key / identifier
     * @param name     MiniMessage display name
     * @param icon     material name for the GUI icon
     * @param effect   particle/sound/effect identifier
     * @param requires achievement id required to unlock, or {@code null} = free
     */
    public record CosmeticEntry(String id, String name, String icon,
                                 String effect, String requires) {
    }
}
