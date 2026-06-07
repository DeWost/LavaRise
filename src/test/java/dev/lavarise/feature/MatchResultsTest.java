package dev.lavarise.feature;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-of-match placement: survivors first, then reverse elimination order. */
class MatchResultsTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID D = UUID.randomUUID();

    @Test
    void singleWinnerThenReverseEliminationOrder() {
        // A survived; C was out first, then B. Ranking: A, B (last out), C (first out).
        List<UUID> ranking = MatchResults.ranking(List.of(A), List.of(C, B));
        assertEquals(List.of(A, B, C), ranking);
    }

    @Test
    void multipleSurvivorsRankAboveEliminated() {
        // Lava-max ending with two survivors; both rank above the eliminated.
        List<UUID> ranking = MatchResults.ranking(List.of(A, D), List.of(C, B));
        assertEquals(A, ranking.get(0));
        assertEquals(D, ranking.get(1));
        assertEquals(List.of(B, C), ranking.subList(2, 4));
    }

    @Test
    void noSurvivorsRanksLastEliminatedFirst() {
        // Everyone died; the last eliminated placed highest.
        List<UUID> ranking = MatchResults.ranking(List.of(), List.of(C, B, A));
        assertEquals(List.of(A, B, C), ranking);
    }

    @Test
    void emptyInputsGiveEmptyRanking() {
        assertTrue(MatchResults.ranking(List.of(), List.of()).isEmpty());
    }

    @Test
    void noDuplicatesWhenPlayerAppearsInBoth() {
        // Defensive: a player both "alive" and in the elimination list appears once.
        List<UUID> ranking = MatchResults.ranking(List.of(A), List.of(A));
        assertEquals(List.of(A), ranking);
    }
}
