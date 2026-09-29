package com.npchealthregen;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VenomRingTest
{
	@Test
	public void freshNpcFirstProcIsThirtyTicksAfterTheVenomHit()
	{
		VenomRing ring = new VenomRing();

		assertTrue(ring.start(100, 106));

		assertEquals(VenomRing.Status.COUNTING, ring.getStatus());
		assertEquals(130, ring.getProcEarliest());
		assertEquals(130, ring.getProcLatest());
		// Land from the regen at 106 to the tick before the venom at 130.
		assertArrayEquals(new long[]{101, 124}, ring.getDynamiteWindow(5));

		ring.onPoisonHit(130, true);
		assertEquals(VenomRing.Status.PROCCED, ring.getStatus());
		assertEquals(30, ring.getLastObservedDelay());
	}

	@Test
	public void learnsAFirstProcDelayThatDiffers()
	{
		VenomRing ring = new VenomRing();
		ring.start(100, -1);
		ring.onPoisonHit(129, true);
		assertEquals(29, ring.getFreshDelay());

		ring.onDeath(200);
		ring.start(300, -1);
		assertEquals(329, ring.getProcLatest());
	}

	@Test
	public void killedByAnotherHitCarriesTheTimerIntoTheNextLife()
	{
		VenomRing ring = new VenomRing();
		ring.onPoisonHit(100, true);
		// Killed 12 ticks after the last venom hit: 12-13 of 30 already counted.
		ring.onDeath(112);

		ring.start(300, -1);

		assertEquals(317, ring.getProcEarliest());
		assertEquals(318, ring.getProcLatest());
	}

	@Test
	public void killedByTheVenomHitStartsTheNextLifeFresh()
	{
		VenomRing ring = new VenomRing();
		ring.onPoisonHit(100, true);
		ring.onDeath(100);

		ring.start(300, -1);

		assertEquals(330, ring.getProcEarliest());
		assertEquals(330, ring.getProcLatest());
	}

	@Test
	public void reportsNoVenomWhenNothingProcsThenClears()
	{
		VenomRing ring = new VenomRing();
		ring.start(100, -1);

		ring.onTick(132);
		assertEquals(VenomRing.Status.COUNTING, ring.getStatus());
		ring.onTick(133);
		assertEquals(VenomRing.Status.NO_VENOM, ring.getStatus());
		ring.onTick(138);
		assertEquals(VenomRing.Status.NONE, ring.getStatus());
	}

	@Test
	public void doesNotStartOnAnAlreadyPoisonedNpc()
	{
		VenomRing ring = new VenomRing();
		ring.onPoisonHit(100, false);

		assertFalse(ring.start(110, -1));
		assertTrue(ring.start(131, -1));
	}

	@Test
	public void ownVenomHitIsNotOverriddenByAPartyMember()
	{
		VenomRing ring = new VenomRing();
		ring.start(100, -1);

		ring.startShared(101, 140, 142, -1);

		assertFalse(ring.isShared());
		assertEquals(130, ring.getProcLatest());
	}

	@Test
	public void partyVenomHitStartsTheRing()
	{
		VenomRing ring = new VenomRing();

		ring.startShared(100, 129, 131, 106);

		assertTrue(ring.isShared());
		assertEquals(VenomRing.Status.COUNTING, ring.getStatus());
		assertArrayEquals(new long[]{101, 123}, ring.getDynamiteWindow(5));
	}
}
