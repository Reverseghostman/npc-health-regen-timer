package com.npchealthregen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RecoveryCalculatorTest
{
	@Test
	public void reversesScaledHealthRatioIntoPossibleRange()
	{
		RecoveryCalculator.Range range = RecoveryCalculator.healthFromRatio(15, 30, 225);

		assertEquals(109, range.getMinimum());
		assertEquals(116, range.getMaximum());
	}

	@Test
	public void recognisesFullAndDeadHealthRatios()
	{
		RecoveryCalculator.Range full = RecoveryCalculator.healthFromRatio(30, 30, 225);
		RecoveryCalculator.Range dead = RecoveryCalculator.healthFromRatio(0, 30, 225);

		assertEquals(225, full.getMinimum());
		assertEquals(225, full.getMaximum());
		assertEquals(0, dead.getMinimum());
		assertEquals(0, dead.getMaximum());
	}

	@Test
	public void calculatesFullHealthCountdownFromExactInspection()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50);

		RecoveryCalculator.TickWindow result = RecoveryCalculator.timeUntilFull(
			new RecoveryCalculator.Range(222, 222), 225, 60, 60, 100, timer);

		assertEquals(290, result.getEarliest());
		assertEquals(290, result.getLatest());
	}

	@Test
	public void projectsInspectedHealthAsRegenCyclesPass()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50);

		RecoveryCalculator.Range result = RecoveryCalculator.projectHealth(
			new RecoveryCalculator.Range(220, 220), 225, 60, 251, 100, timer);

		assertEquals(222, result.getMinimum());
		assertEquals(222, result.getMaximum());
	}

	@Test
	public void predictsDefenceAtFullHealthWithOnePointPerCycle()
	{
		RecoveryCalculator.Range result = RecoveryCalculator.defenceAtFull(
			210, 240, new RecoveryCalculator.Range(220, 220), 225);

		assertEquals(215, result.getMinimum());
		assertEquals(215, result.getMaximum());
	}

	@Test
	public void requiresARegenPhaseForCountdown()
	{
		assertNull(RecoveryCalculator.timeUntilFull(
			new RecoveryCalculator.Range(220, 220), 225, 10, 10, 100, new RegenTimer()));
	}
}
