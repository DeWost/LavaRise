package dev.lavarise.party;

import dev.lavarise.core.LavaRisePlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks parties and pending invites. A player belongs to at most one party;
 * the leader is the one who joins games for the whole group.
 *
 * @author DeWost
 */
public class PartyManager {

    private final LavaRisePlugin plugin;
    /** member UUID → their party. */
    private final Map<UUID, Party> byMember = new HashMap<>();
    /** invitee UUID → inviting leader UUID. */
    private final Map<UUID, UUID> invites = new HashMap<>();

    public PartyManager(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    public Party getParty(UUID uuid) {
        return byMember.get(uuid);
    }

    public boolean isInParty(UUID uuid) {
        return byMember.containsKey(uuid);
    }

    /** Record an invite, creating the inviter's party if they don't have one. */
    public void invite(UUID leader, UUID target) {
        Party party = byMember.get(leader);
        if (party == null) {
            party = new Party(leader);
            byMember.put(leader, party);
        }
        invites.put(target, leader);
    }

    public boolean hasInviteFrom(UUID target, UUID leader) {
        return leader.equals(invites.get(target));
    }

    public UUID getInviter(UUID target) {
        return invites.get(target);
    }

    /** Accept the pending invite: the target joins the inviter's party. */
    public Party accept(UUID target) {
        final UUID leader = invites.remove(target);
        if (leader == null) return null;
        final Party party = byMember.get(leader);
        if (party == null) return null;
        party.addMember(target);
        byMember.put(target, party);
        return party;
    }

    public void clearInvite(UUID target) {
        invites.remove(target);
    }

    /**
     * Remove a player from their party. If the leader leaves, leadership passes
     * to the next member; an emptied (or single-member) party is disbanded.
     *
     * @return the party the player was in, or {@code null} if they had none
     */
    public Party leave(UUID uuid) {
        final Party party = byMember.remove(uuid);
        if (party == null) return null;
        party.removeMember(uuid);
        if (party.size() <= 1) {
            // Disband: nobody left to play with.
            for (UUID remaining : party.getMembers()) {
                byMember.remove(remaining);
            }
        } else if (party.isLeader(uuid)) {
            party.setLeader(party.getMembers().iterator().next());
        }
        return party;
    }

    /** Disband a whole party, clearing every member's mapping. */
    public void disband(Party party) {
        for (UUID member : party.getMembers()) {
            byMember.remove(member);
        }
    }
}
