package com.npchealthregen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NpcTimingProfileStoreTest
{
	@Test
	public void decodesStoredTimings()
	{
		NpcTimingProfileStore.Profile profile = NpcTimingProfileStore.decode("50,20");
		assertEquals(50, profile.getRegenTicks());
		assertEquals(20, profile.getRespawnTicks());
	}

	@Test
	public void decodesStoredDeathPause()
	{
		int[] pause = NpcTimingProfileStore.decodeDeathPause("-1,2");
		assertEquals(-1, pause[0]);
		assertEquals(2, pause[1]);
		assertNull(NpcTimingProfileStore.decodeDeathPause(null));
		assertNull(NpcTimingProfileStore.decodeDeathPause("3,1"));
		assertNull(NpcTimingProfileStore.decodeDeathPause("broken"));
	}

	@Test
	public void invalidStoredTimingsFallBackToUnknown()
	{
		NpcTimingProfileStore.Profile profile = NpcTimingProfileStore.decode("broken");
		assertEquals(0, profile.getRegenTicks());
		assertEquals(0, profile.getRespawnTicks());
	}
}
