package dev.lavarise.feature;

import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dynamic Scoreboard wrapper using Teams for flicker-free updates.
 */
public class ScoreboardModule {
    private final LavaRisePlugin plugin;
    // Map to hold the Teams for each player so we can update them without a runnable
    private final Map<UUID, Team[]> playerTeams = new HashMap<>();

    public ScoreboardModule(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    public void setupScoreboard(Player player, ArenaSession session) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("lavarise", Criteria.DUMMY, plugin.getMiniMessage().deserialize("<red><bold>LAVA RISE</bold>"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.getScore(" ").setScore(5);
        
        Team playersTeam = board.registerNewTeam("players");
        playersTeam.addEntry(ChatColor.RED.toString());
        obj.getScore(ChatColor.RED.toString()).setScore(4);
        
        Team lavaTeam = board.registerNewTeam("lava");
        lavaTeam.addEntry(ChatColor.GOLD.toString());
        obj.getScore(ChatColor.GOLD.toString()).setScore(3);
        
        obj.getScore("  ").setScore(2);
        obj.getScore(ChatColor.GRAY + "dev.lavarise").setScore(1);

        player.setScoreboard(board);
        playerTeams.put(player.getUniqueId(), new Team[]{playersTeam, lavaTeam});
        updateScoreboardForPlayer(player, session);
    }
    
    public void updateScoreboard(ArenaSession session) {
        for (UUID uuid : session.getAllPlayerIds()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                updateScoreboardForPlayer(p, session);
            }
        }
    }
    
    private void updateScoreboardForPlayer(Player player, ArenaSession session) {
        Team[] teams = playerTeams.get(player.getUniqueId());
        if (teams != null) {
            teams[0].setPrefix(ChatColor.WHITE + "Players: " + ChatColor.GREEN + session.getAliveCount());
            teams[1].setPrefix(ChatColor.WHITE + "Lava Level: " + ChatColor.RED + session.getCurrentLavaY());
        }
    }
    
    public void cleanup(Player player) {
        playerTeams.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
