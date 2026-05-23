package dev.lavarise.command;

import dev.lavarise.arena.Arena;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.scheduler.BukkitRunnable;
import dev.lavarise.engine.nms.FastBlockSetter;

/**
 * Main command executor for /lavarise.
 */
public class LavaRiseCommand implements CommandExecutor, TabCompleter {
    private final LavaRisePlugin plugin;

    public LavaRiseCommand(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getMiniMessage().deserialize("<red>Usage: /lavarise <join|leave|list>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("join")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(plugin.getMiniMessage().deserialize("<red>Usage: /lavarise join <arena>"));
                return true;
            }

            String arenaName = args[1];
            Arena arena = plugin.getArenaRepository().getArena(arenaName).orElse(null);
            if (arena == null) {
                player.sendMessage(plugin.getMiniMessage().deserialize("<red>Arena not found."));
                return true;
            }

            plugin.getGameManager().addPlayerToArena(player, arena);
            return true;
        }

        if (args[0].equalsIgnoreCase("leave")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            plugin.getGameManager().removePlayer(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMiniMessage().deserialize("<green>Arenas: " + String.join(", ", plugin.getArenaRepository().getArenas().keySet())));
                return true;
            }
            plugin.getGuiManager().open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("stress")) {
            if (!sender.hasPermission("lavarise.admin")) {
                sender.sendMessage("No permission.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(plugin.getMiniMessage().deserialize("<red>Usage: /lavarise stress <arena> <blocksPerTick>"));
                return true;
            }
            
            String arenaName = args[1];
            Arena arena = plugin.getArenaRepository().getArena(arenaName).orElse(null);
            if (arena == null) {
                sender.sendMessage(plugin.getMiniMessage().deserialize("<red>Arena not found."));
                return true;
            }
            
            int blocksPerTick;
            try {
                blocksPerTick = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("<red>Invalid number.");
                return true;
            }
            
            sender.sendMessage(plugin.getMiniMessage().deserialize("<gold>Starting stress test on " + arenaName + " at " + blocksPerTick + " blocks/tick..."));
            
            new BukkitRunnable() {
                int cx = arena.getConfig().minX();
                int cy = arena.getConfig().lavaStartY();
                int cz = arena.getConfig().minZ();
                FastBlockSetter setter = new FastBlockSetter(arena.getConfig().world(), org.bukkit.Material.LAVA.createBlockData());
                
                @Override
                public void run() {
                    long start = System.nanoTime();
                    int processed = 0;
                    while(processed < blocksPerTick) {
                        if(cy > arena.getConfig().lavaMaxY()) {
                            sender.sendMessage(plugin.getMiniMessage().deserialize("<green>Stress test finished."));
                            this.cancel();
                            return;
                        }
                        setter.setBlock(cx, cy, cz);
                        processed++;
                        cx++;
                        if (cx > arena.getConfig().maxX()) {
                            cx = arena.getConfig().minX();
                            cz++;
                            if (cz > arena.getConfig().maxZ()) {
                                cz = arena.getConfig().minZ();
                                cy++;
                            }
                        }
                    }
                    long end = System.nanoTime();
                    double ms = (end - start) / 1_000_000.0;
                    if (ms > 10.0) { // Only log if it took more than 10ms (half a tick)
                        plugin.getLogger().warning("Stress test tick took " + ms + "ms for " + blocksPerTick + " blocks.");
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
            
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("join");
            completions.add("leave");
            completions.add("list");
            if (sender.hasPermission("lavarise.admin")) completions.add("stress");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("stress"))) {
            completions.addAll(plugin.getArenaRepository().getArenas().keySet());
        }
        return completions.stream()
                .filter(c -> c.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
