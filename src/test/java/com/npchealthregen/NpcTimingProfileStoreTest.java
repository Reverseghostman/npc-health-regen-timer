package com.npchealthregen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
	public void invalidStoredTimingsFallBackToUnknown()
	{
		NpcTimingProfileStore.Profile profile = NpcTimingProfileStore.decode("broken");
		assertEquals(0, profile.getRegenTicks());
		assertEquals(0, profile.getRespawnTicks());
	}
}
