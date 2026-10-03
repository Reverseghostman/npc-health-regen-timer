package com.npchealthregen;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * A party member envenomed the NPC they are tracking, or cancelled that venom
 * by logging out. Times are offsets from the sender's current tick; the send
 * timestamp lets recipients subtract delivery time without comparing independent
 * client tick counters. The class name is the message's type label on the party
 * service, so it must stay unique.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class NpcHealthRegenVenomUpdate extends PartyMemberMessage
{
	int world;
	int npcId;
	int npcIndex;
	boolean cancelled;
	int appliedOffset;
	int procEarliestOffset;
	int procLatestOffset;
	boolean fullHpKnown;
	int fullHpOffset;
	long sentAtMillis;

	// Keep the original constructor and accept messages from older plugin versions.
	public NpcHealthRegenVenomUpdate(int world, int npcId, int npcIndex, boolean cancelled,
		int appliedOffset, int procEarliestOffset, int procLatestOffset,
		boolean fullHpKnown, int fullHpOffset)
	{
		this(world, npcId, npcIndex, cancelled, appliedOffset, procEarliestOffset,
			procLatestOffset, fullHpKnown, fullHpOffset, 0);
	}

	public NpcHealthRegenVenomUpdate(int world, int npcId, int npcIndex, boolean cancelled,
		int appliedOffset, int procEarliestOffset, int procLatestOffset,
		boolean fullHpKnown, int fullHpOffset, long sentAtMillis)
	{
		this.world = world;
		this.npcId = npcId;
		this.npcIndex = npcIndex;
		this.cancelled = cancelled;
		this.appliedOffset = appliedOffset;
		this.procEarliestOffset = procEarliestOffset;
		this.procLatestOffset = procLatestOffset;
		this.fullHpKnown = fullHpKnown;
		this.fullHpOffset = fullHpOffset;
		this.sentAtMillis = sentAtMillis;
	}
}
