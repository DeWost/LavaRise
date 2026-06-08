package dev.lavarise.party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure party state logic — invites, membership, leader transfer, disband. */
class PartyManagerTest {

    private PartyManager pm;
    private UUID leader;
    private UUID a;
    private UUID b;

    @BeforeEach
    void setUp() {
        pm = new PartyManager(null); // plugin is unused by the state methods
        leader = UUID.randomUUID();
        a = UUID.randomUUID();
        b = UUID.randomUUID();
    }

    @Test
    void inviteThenAcceptJoinsTheParty() {
        pm.invite(leader, a);
        assertTrue(pm.hasInviteFrom(a, leader));
        final Party party = pm.accept(a);
        assertNotNull(party);
        assertTrue(party.isMember(a));
        assertTrue(party.isMember(leader));
        assertTrue(party.isLeader(leader));
        assertEquals(2, party.size());
        // Invite is consumed.
        assertNull(pm.getInviter(a));
    }

    @Test
    void acceptWithoutInviteReturnsNull() {
        assertNull(pm.accept(a));
    }

    @Test
    void leaderLeavingTransfersLeadership() {
        pm.invite(leader, a);
        pm.accept(a);
        pm.invite(leader, b);
        pm.accept(b);
        assertEquals(3, pm.getParty(leader).size());

        pm.leave(leader);
        final Party party = pm.getParty(a);
        assertNotNull(party);
        assertFalse(party.isMember(leader));
        assertTrue(party.isLeader(a) || party.isLeader(b));
        assertEquals(2, party.size());
    }

    @Test
    void partyDisbandsWhenItDropsToOne() {
        pm.invite(leader, a);
        pm.accept(a);
        pm.leave(a);
        // Only the leader remained → party disbanded for everyone.
        assertFalse(pm.isInParty(leader));
        assertFalse(pm.isInParty(a));
    }

    @Test
    void disbandClearsEveryMember() {
        pm.invite(leader, a);
        pm.accept(a);
        pm.disband(pm.getParty(leader));
        assertFalse(pm.isInParty(leader));
        assertFalse(pm.isInParty(a));
    }
}
