package com.npchealthregen;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.*;

public class NpcHealthRegenVenomUpdateTest
{
	@Test
	public void timestampAndOffsetsSurvivePartyJsonRoundTrip()
	{
		NpcHealthRegenVenomUpdate update = new NpcHealthRegenVenomUpdate(
			301, 1, 42, false, -5, 24, 25, true, 0, 10_000L);
		update.setMemberId(2L);
		Gson gson = new Gson();
		String json = gson.toJson(update);
		assertFalse(json.contains("memberId"));
		NpcHealthRegenVenomUpdate received = gson.fromJson(json, NpcHealthRegenVenomUpdate.class);
		assertEquals(10_000L, received.getSentAtMillis());
		assertEquals(-5, received.getAppliedOffset());
		assertEquals(24, received.getProcEarliestOffset());
		assertEquals(25, received.getProcLatestOffset());
		assertTrue(received.isFullHpKnown());
	}

	@Test
	public void olderMessagesHaveNoDeliveryTimestamp()
	{
		String json = "{\"world\":301,\"npcId\":1,\"npcIndex\":42,"
			+ "\"appliedOffset\":0,\"procEarliestOffset\":30,\"procLatestOffset\":30}";
		NpcHealthRegenVenomUpdate update = new Gson().fromJson(json, NpcHealthRegenVenomUpdate.class);
		assertEquals(0, update.getSentAtMillis());
		assertEquals(30, update.getProcLatestOffset());
	}
}
