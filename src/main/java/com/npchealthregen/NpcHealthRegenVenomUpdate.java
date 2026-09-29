package com.npchealthregen;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * A party member envenomed the NPC they are tracking, or cancelled that venom
 * by logging out. Times are offsets from the sender's current tick. The class
 * name is the message's type label on the party service, so it must stay unique.
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
}
