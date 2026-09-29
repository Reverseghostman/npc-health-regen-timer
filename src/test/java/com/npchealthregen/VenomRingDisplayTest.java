package com.npchealthregen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VenomRingDisplayTest
{
	private static String label(VenomRing ring, long now)
	{
		return NpcHealthRegenSceneOverlay.ringState(ring, now, 5).label;
	}

	@Test
	public void waitsForRegenThenSendsThenIsTooLate()
	{
		VenomRing ring = new VenomRing();
		ring.start(100, 110);

		// Send window 105-124: dynamite must land from the regen at 110 until
		// the tick before the venom at 130, and takes 5 ticks to land.
		assertEquals("Wait 5t", label(ring, 100));
		assertEquals("Send 19t", label(ring, 105));
		assertEquals("Send 0t", label(ring, 124));
		assertEquals("Too late", label(ring, 125));
	}

	@Test
	public void noWindowWhenTheVenomLandsBeforeRegenRestoresHp()
	{
		VenomRing ring = new VenomRing();
		ring.start(100, 190);

		assertEquals("No window", label(ring, 100));
	}

	@Test
	public void showsTheResult()
	{
		VenomRing ring = new VenomRing();
		ring.start(100, -1);
		ring.onPoisonHit(130, true);
		assertEquals("Venom!", label(ring, 130));

		VenomRing missed = new VenomRing();
		missed.start(100, -1);
		missed.onTick(133);
		assertEquals("No venom", label(missed, 133));
	}
}
