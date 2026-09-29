package com.npchealthregen;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * A party member's regen window for the NPC they are tracking. Party members'
 * tick counters differ, so the window is sent as offsets from the sender's
 * current tick rather than as absolute ticks. The class name is the message's
 * type label on the party service, so it must stay unique.
 * <p>
 * {@code unverified} says the sender's window was carried through time it could not see the NPC
 * (so a kill or respawn may have moved it). A receiver that is itself unsure prefers a window that
 * is not. Versions that predate the field never set it, which reads as "verified".
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class NpcHealthRegenPartyUpdate extends PartyMemberMessage
{
	int world;
	int npcId;
	int npcIndex;
	int regenTicks;
	boolean regenLearned;
	int windowStartOffset;
	int windowEndOffset;
	boolean unverified;
}
