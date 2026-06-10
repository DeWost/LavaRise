package dev.lavarise.mode;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Standard minigame: free-for-all last-player-standing, or — when
 * {@code modes.minigame.teams-enabled} — last-team-standing with friendly fire
 * disabled.
 *
 * @author DeWost
 */
public final class MinigameModeHandler extends GameModeHandler {

    public MinigameModeHandler(LavaRisePlugin plugin) {
        super(plugin);
    }

    /** Teams are active only when enabled in config AND at least one team was assigned. */
    private boolean teamsActive(ArenaSession session) {
        return plugin.getConfigManager().isTeamsEnabled() && !session.aliveTeams().isEmpty();
    }

    @Override
    public void onGameStart(Arena arena, ArenaSession session) {
        if (!plugin.getConfigManager().isTeamsEnabled()) return;

        final int size = Math.max(1, plugin.getConfigManager().getTeamSize());
        final List<UUID> roster = new ArrayList<>(session.getAlivePlayers());
        final int teamCount = (roster.size() + size - 1) / size;
        if (teamCount <= 1) {
            return; // Not enough players for multiple teams → stay FFA.
        }

        Collections.shuffle(roster);
        int teamId = 0, inTeam = 0;
        for (UUID id : roster) {
            session.assignTeam(id, teamId);
            if (++inTeam >= size) { teamId++; inTeam = 0; }
        }
        broadcast(session, plugin.getMiniMessage().deserialize(
                "<gray>Teams assigned: <yellow>" + session.aliveTeams().size()
                        + "</yellow> teams of up to <yellow>" + size + "</yellow>."));
    }

    @Override
    public boolean isGameOver(Arena arena, ArenaSession session) {
        if (teamsActive(session)) {
            return session.aliveTeams().size() <= 1;
        }
        return super.isGameOver(arena, session);
    }

    @Override
    public Player resolveWinner(Arena arena, ArenaSession session) {
        if (teamsActive(session) && session.aliveTeams().size() == 1) {
            for (UUID id : session.getAlivePlayers()) {
                Player p = plugin.getServer().getPlayer(id);
                if (p != null) return p; // representative of the winning team
            }
            return null;
        }
        return super.resolveWinner(arena, session);
    }

    @Override
    public void onArenaEnd(Arena arena, ArenaSession session, Player winner) {
        // For teams, credit a win to every alive team member besides the
        // representative (EndingState already records the representative).
        if (winner == null || !teamsActive(session)) return;
        final int winningTeam = session.getTeam(winner.getUniqueId());
        for (UUID id : session.getAlivePlayers()) {
            if (id.equals(winner.getUniqueId())) continue;
            if (session.getTeam(id) == winningTeam) {
                Player p = plugin.getServer().getPlayer(id);
                if (p != null) {
                    plugin.getStatsManager().recordWin(id, p.getName());
                    plugin.getAchievementManager().checkAndAward(p);
                }
            }
        }
    }

    @Override
    public boolean allowFriendlyFire(ArenaSession session, Player attacker, Player victim) {
        if (!plugin.getConfigManager().isTeamsEnabled()) return true;
        return !session.sameTeam(attacker.getUniqueId(), victim.getUniqueId());
    }

    private void broadcast(ArenaSession session, Component message) {
        for (UUID id : session.getAllPlayerIds()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && p.isOnline()) p.sendMessage(message);
        }
    }
}
