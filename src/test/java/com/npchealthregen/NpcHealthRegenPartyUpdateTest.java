package com.npchealthregen;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NpcHealthRegenPartyUpdateTest
{
	@Test
	public void survivesTheJsonRoundTripThePartyServiceUses()
	{
		NpcHealthRegenPartyUpdate update = new NpcHealthRegenPartyUpdate(301, 1, 42, 100, true, -3, 5, true);
		update.setMemberId(7L);
		Gson gson = new Gson();

		String json = gson.toJson(update);
		NpcHealthRegenPartyUpdate received = gson.fromJson(json, NpcHealthRegenPartyUpdate.class);

		// The member id is filled in by the party service on receipt, not sent.
		assertFalse(json.contains("memberId"));
		assertEquals(0L, received.getMemberId());
		assertEquals(301, received.getWorld());
		assertEquals(1, received.getNpcId());
		assertEquals(42, received.getNpcIndex());
		assertEquals(100, received.getRegenTicks());
		assertEquals(true, received.isRegenLearned());
		assertEquals(-3, received.getWindowStartOffset());
		assertEquals(5, received.getWindowEndOffset());
		assertTrue(received.isUnverified());
	}

	@Test
	public void messagesFromVersionsWithoutTheUnverifiedFlagReadAsVerified()
	{
		String json = "{\"world\":301,\"npcId\":1,\"npcIndex\":42,\"regenTicks\":100,"
			+ "\"regenLearned\":true,\"windowStartOffset\":-3,\"windowEndOffset\":5}";

		NpcHealthRegenPartyUpdate received = new Gson().fromJson(json, NpcHealthRegenPartyUpdate.class);

		assertFalse(received.isUnverified());
		assertEquals(5, received.getWindowEndOffset());
	}
}
