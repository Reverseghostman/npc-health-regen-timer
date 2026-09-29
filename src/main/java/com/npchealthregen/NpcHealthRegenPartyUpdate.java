package com.npchealthregen;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * A party member's regen window for the NPC they are tracking. Party members'
 * tick counters differ, so the window is sent as offsets from the sender's
 * current tick rather than as absolute ticks. The class name is the message's
 * type label on the party service, so it must stay unique.
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
}
