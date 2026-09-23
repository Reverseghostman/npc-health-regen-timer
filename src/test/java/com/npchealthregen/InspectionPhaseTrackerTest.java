package com.npchealthregen;

import org.junit.Test;
import static org.junit.Assert.*;

public class InspectionPhaseTrackerTest
{
	@Test
	public void minuteSpacedCastsProgressivelyRefineDespiteResponseDelay()
	{
		InspectionPhaseTracker tracker = new InspectionPhaseTracker();
		long previousWidth = 100;
		long[] bounds = null;
		for (long result : new long[]{3, 85, 180, 275, 370, 465, 560, 655, 747})
		{
			int hp = 51 + (int) Math.floorDiv(result - 50, 100);
			bounds = tracker.sample(hp, 200, result - 3, result, 100);
			if (bounds != null)
			{
				long width = bounds[1] - bounds[0];
				assertTrue(width <= previousWidth);
				previousWidth = width;
				long trueHeal = 50 + Math.floorDiv(bounds[1] - 50, 100) * 100;
				assertTrue(trueHeal >= bounds[0]);
			}
		}
		assertNotNull(bounds);
		assertEquals(10, previousWidth);
		assertTrue(previousWidth >= 3);
	}

	@Test
	public void identicalTimingProvidesNoArtificialPrecision()
	{
		InspectionPhaseTracker tracker = new InspectionPhaseTracker();
		tracker.sample(50, 200, 0, 3, 100);
		long[] first = tracker.sample(51, 200, 100, 103, 100);
		for (int i = 2; i < 10; i++)
		{
			long[] next = tracker.sample(50 + i, 200, i * 100, i * 100 + 3, 100);
			assertEquals(first[1] - first[0], next[1] - next[0]);
		}
	}

	@Test
	public void damageAndFullHealthCannotSupplyFalseNoHealEvidence()
	{
		InspectionPhaseTracker tracker = new InspectionPhaseTracker();
		tracker.sample(50, 100, 0, 0, 100);
		long[] known = tracker.sample(51, 100, 60, 60, 100);
		tracker.invalidateSample();
		long[] afterDamage = tracker.sample(40, 100, 140, 140, 100);
		assertEquals(known[1] - known[0], afterDamage[1] - afterDamage[0]);
		assertNull(tracker.sample(100, 100, 150, 150, 100));
		assertNull(tracker.sample(100, 100, 250, 250, 100));
	}

	@Test
	public void countsSeveralHealsAcrossMissedCasts()
	{
		InspectionPhaseTracker tracker = new InspectionPhaseTracker();
		tracker.sample(50, 200, 0, 3, 100);
		long[] bounds = tracker.sample(53, 200, 260, 263, 100);
		assertNotNull(bounds);
		assertTrue(bounds[1] - bounds[0] < 100);
		long trueHeal = 50 + Math.floorDiv(bounds[1] - 50, 100) * 100;
		assertTrue(trueHeal >= bounds[0]);
	}

	@Test
	public void delayedSamplesNeverExcludeTheActualPhase()
	{
		for (int truePhase = 0; truePhase < 100; truePhase++)
		{
			InspectionPhaseTracker tracker = new InspectionPhaseTracker();
			for (int i = 0; i < 20; i++)
			{
				long cast = i * 93L;
				long sampled = cast + i % 4;
				long result = cast + 5;
				int hp = 100 + (int) Math.floorDiv(sampled - truePhase, 100);
				long[] bounds = tracker.sample(hp, 1000, cast, result, 100);
				if (bounds != null)
				{
					long actual = truePhase + Math.floorDiv(bounds[1] - truePhase, 100) * 100;
					assertTrue("phase " + truePhase + " sample " + i, actual >= bounds[0]);
				}
			}
		}
	}
}
