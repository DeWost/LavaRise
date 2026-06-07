package dev.lavarise.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Pure placement math for end-of-match results.
 * <p>
 * Survivors rank first (tied at the top), then players in <b>reverse</b>
 * elimination order — the last player eliminated placed higher than the first
 * one out. No Bukkit dependencies, so the ordering is unit-tested directly.
 * </p>
 *
 * @author DeWost
 */
public final class MatchResults {

    private MatchResults() {}

    /**
     * Build the final ranking.
     *
     * @param survivors        players still alive at game end (order irrelevant)
     * @param eliminationOrder players in the order they were eliminated (first out first)
     * @return the ranking; index 0 is 1st place. Each player appears once.
     */
    public static List<UUID> ranking(Collection<UUID> survivors, List<UUID> eliminationOrder) {
        final List<UUID> ranking = new ArrayList<>(survivors.size() + eliminationOrder.size());
        for (UUID survivor : survivors) {
            if (!ranking.contains(survivor)) ranking.add(survivor);
        }
        for (int i = eliminationOrder.size() - 1; i >= 0; i--) {
            final UUID u = eliminationOrder.get(i);
            if (!ranking.contains(u)) ranking.add(u);
        }
        return ranking;
    }
}
