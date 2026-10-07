package com.npchealthregen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

public class RegenTimerTest
{
	@Test
	public void fullHealthInspectionPreservesSharedPhase()
	{
		RegenTimer timer = new RegenTimer();
		timer.applySharedWindow(50, 52, 100);
		timer.sampleInspectionStats(90, -1, 100, 60, 63, 100);
		timer.sampleInspectionStats(100, -1, 100, 260, 263, 100);
		assertEquals(0, timer.getObservedRegens());
		assertTrue(timer.isPhaseFromParty());
		assertEquals(350, timer.getUpcomingWindowTicks(263, 100)[0]);
		assertEquals(352, timer.getUpcomingWindowTicks(263, 100)[1]);
	}

	@Test
	public void firstInspectionAfterObservationGapCannotOverridePartyCorrection()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleInspectionStats(50, 50, 200, 0, 0, 100);
		timer.sampleInspectionStats(51, 50, 200, 60, 60, 100);
		timer.markUnverified();
		assertTrue(timer.applySharedWindow(175, 177, 100));
		timer.sampleInspectionStats(80, 60, 200, 240, 243, 100);
		assertTrue(timer.isPhaseFromParty());
		assertEquals(275, timer.getUpcomingWindowTicks(243, 100)[0]);
		assertEquals(277, timer.getUpcomingWindowTicks(243, 100)[1]);
		assertFalse(timer.isUnverified());
	}

	@Test
	public void observationGapBeforeFirstHealClearsExactSamples()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleInspectionStats(50, 50, 200, 0, 0, 100);
		timer.markUnverified();
		timer.sampleInspectionStats(80, 60, 200, 240, 243, 100);
		assertEquals(RegenTimer.State.OBSERVING, timer.getState());
		assertEquals(0, timer.getObservedRegens());
	}

	@Test
	public void fullHealthStillAllowsDefenceRegenEvidence()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleInspectionStats(100, 50, 100, 0, 0, 100);
		timer.sampleInspectionStats(100, 51, 100, 60, 60, 100);
		assertEquals(1, timer.getObservedRegens());
		assertEquals(RegenTimer.State.TRACKING, timer.getState());
	}

	@Test
	public void changedHealthBarScaleDoesNotLookLikeHealing()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(15, 30, 10);
		timer.sampleHealth(30, 60, 11);
		assertEquals(0, timer.getObservedRegens());
		assertNull(timer.getUpcomingWindow(11, 100));
		timer.sampleHealth(31, 60, 12);
		assertEquals(1, timer.getObservedRegens());
	}

	@Test
	public void invalidRatioCannotCalibrateTimer()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(20, 30, 10);
		timer.sampleHealth(31, 30, 11);
		timer.sampleHealth(21, 30, 12);
		assertEquals(0, timer.getObservedRegens());
	}

	@Test
	public void disablingRespawnEstimateClearsCountdownAndCorrectsPhase()
	{
		RegenTimer timer = new RegenTimer();
		timer.setDeathPauseAdjustment(0, 0, true);
		timer.markNow(50);
		timer.onDeath(80, 20);
		timer.updateExpectedRespawnTicks(0);
		assertEquals(-1, timer.getRespawnTicksRemaining(90));
		assertEquals(10, timer.getRespawnTicksElapsed(90));
		assertEquals(60, timer.getUpcomingWindow(90, 100).getEarliestTicks());
		assertEquals(25, timer.onRespawn(105));
		assertEquals(70, timer.getUpcomingWindow(105, 100).getEarliestTicks());
	}

	@Test
	public void learnsObservationWindowFromHealthIncrease()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(20, 30, 10);
		timer.sampleHealth(21, 30, 14);

		RegenTimer.Window window = timer.getUpcomingWindow(10, 100);
		assertEquals(1, window.getEarliestTicks());
		assertEquals(4, window.getLatestTicks());
		assertEquals(RegenTimer.State.TRACKING, timer.getState());
		assertEquals(1, timer.getObservedRegens());
	}

	@Test
	public void detectsIntervalFromSecondObservedRegen()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(20, 30, 10);
		timer.sampleHealth(21, 30, 14);
		timer.sampleHealth(21, 30, 60);

		assertEquals(50, timer.sampleHealth(22, 30, 64));
		assertEquals(2, timer.getObservedRegens());
	}

	@Test
	public void additionalObservationsReduceSplashWindowError()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(20, 30, 48);
		timer.sampleHealth(21, 30, 52);
		timer.sampleHealth(21, 30, 96);
		assertEquals(48, timer.sampleHealth(22, 30, 100));
		timer.sampleHealth(22, 30, 148);
		assertEquals(50, timer.sampleHealth(23, 30, 152));
	}

	@Test
	public void repeatsWindowAtActiveInterval()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(20, 30, 10);
		timer.sampleHealth(21, 30, 14);

		RegenTimer.Window window = timer.getUpcomingWindow(25, 100);
		assertEquals(86, window.getEarliestTicks());
		assertEquals(89, window.getLatestTicks());
	}

	@Test
	public void reportsWhenInsideWindow()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(20, 30, 10);
		timer.sampleHealth(21, 30, 14);

		RegenTimer.Window window = timer.getUpcomingWindow(12, 100);
		assertEquals(0, window.getEarliestTicks());
		assertEquals(2, window.getLatestTicks());
	}

	@Test
	public void unexpectedHealthIncreaseImmediatelyReanchorsPrediction()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50);
		assertEquals(40, timer.getUpcomingWindow(110, 100).getEarliestTicks());

		timer.sampleHealth(20, 30, 115);
		timer.sampleHealth(21, 30, 116);

		assertEquals(0, timer.getUpcomingWindow(116, 100).getEarliestTicks());
		assertEquals(99, timer.getUpcomingWindow(117, 100).getEarliestTicks());
		assertEquals(99, timer.getUpcomingWindow(117, 100).getLatestTicks());
	}

	@Test
	public void unavailableHealthBarDoesNotCreateFalseRegen()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(20, 30, 10);
		timer.sampleHealth(-1, 0, 11);

		assertEquals(0, timer.sampleHealth(21, 30, 12));
		assertNull(timer.getUpcomingWindow(12, 100));
	}

	@Test
	public void exactInspectionReadingsDetectHealthRegeneration()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleExactHealth(250, 10);
		timer.sampleExactHealth(250, 13);

		assertEquals(0, timer.sampleExactHealth(251, 14));
		assertEquals(1, timer.getUpcomingWindow(13, 100).getEarliestTicks());
		assertEquals(1, timer.getUpcomingWindow(13, 100).getLatestTicks());
	}

	@Test
	public void exactInspectionReadingsCanLearnInterval()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleExactHealth(250, 10);
		timer.sampleExactHealth(251, 14);
		timer.sampleExactHealth(251, 110);

		assertEquals(100, timer.sampleExactHealth(252, 114));
	}

	@Test
	public void keepsPreferredWholeIntervalWhenDelayedWindowsAlsoAllowIt()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleExactHealth(250, 10, 100);
		timer.sampleExactHealth(251, 14, 100);
		timer.sampleExactHealth(251, 109, 100);

		assertEquals(100, timer.sampleExactHealth(252, 113, 100));
	}

	@Test
	public void fourTickSplashDelayCannotTurnOneHundredTicksIntoOneHundredAndThree()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(20, 30, 99, 100, 3);
		timer.sampleHealth(21, 30, 103, 100, 3);
		timer.sampleHealth(21, 30, 202, 100, 3);

		assertEquals(100, timer.sampleHealth(22, 30, 206, 100, 3));
	}

	@Test
	public void fourTickSplashDelayCannotTurnOneHundredTicksIntoNinetySeven()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(20, 30, 99, 100, 3);
		timer.sampleHealth(21, 30, 103, 100, 3);
		timer.sampleHealth(21, 30, 199, 100, 3);

		assertEquals(100, timer.sampleHealth(22, 30, 200, 100, 3));
	}

	@Test
	public void slowerSplashWeaponDoesNotSaveAnUncertainInterval()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(20, 30, 99, 100, 5);
		timer.sampleHealth(21, 30, 103, 100, 5);
		timer.sampleHealth(21, 30, 205, 100, 5);

		assertEquals(0, timer.sampleHealth(22, 30, 207, 100, 5));
	}

	@Test
	public void healthBarRefinesAnOverlappingInspectionWindowWithoutDoubleCounting()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleExactHealth(250, 10, 100);
		timer.sampleExactHealth(251, 14, 100);
		assertEquals(1, timer.getObservedRegens());

		timer.sampleHealth(20, 30, 11, 100);
		assertEquals(0, timer.sampleHealth(21, 30, 12, 100));
		assertEquals(1, timer.getObservedRegens());
		assertEquals(0, timer.getUpcomingWindow(12, 100).getEarliestTicks());
		assertEquals(0, timer.getUpcomingWindow(12, 100).getLatestTicks());
	}

	@Test
	public void combinedSourcesKeepReanchoringFutureRegens()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleExactHealth(250, 10, 100);
		timer.sampleExactHealth(251, 14, 100);
		timer.sampleHealth(20, 30, 110, 100);
		assertEquals(100, timer.sampleHealth(21, 30, 112, 100));

		assertEquals(0, timer.getUpcomingWindow(112, 100).getEarliestTicks());
		assertEquals(0, timer.getUpcomingWindow(112, 100).getLatestTicks());
		assertEquals(98, timer.getUpcomingWindow(113, 100).getEarliestTicks());
		assertEquals(99, timer.getUpcomingWindow(113, 100).getLatestTicks());
	}

	@Test
	public void duplicateObservationOnSameTickIsCountedOnce()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50);
		timer.markNow(50);

		assertEquals(1, timer.getObservedRegens());
	}

	@Test
	public void manualMarksCanLearnExactInterval()
	{
		RegenTimer timer = new RegenTimer();
		assertEquals(0, timer.markNow(50));
		assertEquals(100, timer.markNow(150));

		RegenTimer.Window window = timer.getUpcomingWindow(175, 100);
		assertEquals(75, window.getEarliestTicks());
		assertEquals(75, window.getLatestTicks());
	}

	@Test
	public void deathKeepsOverlayStateAndMeasuredRespawnCorrectsPhase()
	{
		RegenTimer timer = new RegenTimer();
		timer.setDeathPauseAdjustment(0, 0, true);
		timer.markNow(50);
		timer.onDeath(80, 20);

		assertEquals(RegenTimer.State.WAITING_FOR_RESPAWN, timer.getState());
		assertEquals(20, timer.getRespawnTicksRemaining(80));
		assertEquals(90, timer.getUpcomingWindow(80, 100).getEarliestTicks());

		assertEquals(22, timer.onRespawn(102));
		assertEquals(RegenTimer.State.TRACKING, timer.getState());
		assertEquals(70, timer.getUpcomingWindow(102, 100).getEarliestTicks());
	}

	@Test
	public void unknownRespawnCountsUpUntilNpcReturns()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50);
		timer.onDeath(80, 0);

		assertEquals(RegenTimer.State.WAITING_FOR_RESPAWN, timer.getState());
		assertEquals(-1, timer.getRespawnTicksRemaining(85));
		assertEquals(5, timer.getRespawnTicksElapsed(85));
		assertEquals(25, timer.onRespawn(105));
	}

	@Test
	public void unlearnedDeathPauseCoversEveryPlausibleAdjustment()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50, 100);
		timer.onDeath(80, 20);
		timer.onRespawn(100);

		// Paused for 18-20 of the 20 measured dead ticks: next heal at 168-170.
		RegenTimer.Window window = timer.getUpcomingWindow(100, 100);
		assertEquals(68, window.getEarliestTicks());
		assertEquals(70, window.getLatestTicks());
	}

	@Test
	public void learnedDeathPauseKeepsBackToBackKillsFromDrifting()
	{
		// Engine model: the countdown runs on the tick after the killing hit and
		// on the respawn tick, so it pauses 2 ticks less than the measured time.
		RegenTimer timer = new RegenTimer();
		timer.markNow(50, 100);
		timer.onDeath(80, 20);
		timer.onRespawn(100);
		timer.markNow(168, 100);
		assertEquals(2, timer.getDeathPauseAdjustMin());
		assertEquals(2, timer.getDeathPauseAdjustMax());
		assertTrue(timer.consumeDeathPauseUpdate());
		assertFalse(timer.consumeDeathPauseUpdate());

		// Three more kills with no heal observed in between.
		for (long death = 200; death <= 400; death += 100)
		{
			timer.onDeath(death, 20);
			timer.onRespawn(death + 20);
		}

		// 168 + 3 * 18 = 222, so the next heal is at 422. Shifting by the full
		// 20 measured ticks each kill would have predicted 428.
		RegenTimer.Window window = timer.getUpcomingWindow(420, 100);
		assertEquals(2, window.getEarliestTicks());
		assertEquals(2, window.getLatestTicks());
	}

	@Test
	public void calibratesAcrossSeveralUnobservedKills()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50, 100);
		for (long death = 80; death <= 280; death += 100)
		{
			timer.onDeath(death, 20);
			timer.onRespawn(death + 20);
		}
		RegenTimer.Window unlearned = timer.getUpcomingWindow(300, 100);
		assertEquals(4, unlearned.getEarliestTicks());
		assertEquals(10, unlearned.getLatestTicks());

		// 50 + 3 * (20 - 2) = 104, seen on the next cycle at 304.
		timer.markNow(304, 100);

		assertEquals(2, timer.getDeathPauseAdjustMin());
		assertEquals(2, timer.getDeathPauseAdjustMax());
	}

	@Test
	public void contradictingObservationReplacesLearnedDeathPause()
	{
		RegenTimer timer = new RegenTimer();
		timer.setDeathPauseAdjustment(2, 2, true);
		timer.markNow(50, 100);
		timer.onDeath(80, 20);
		timer.onRespawn(100);

		timer.markNow(170, 100);

		assertEquals(0, timer.getDeathPauseAdjustMin());
		assertEquals(0, timer.getDeathPauseAdjustMax());
		assertEquals(0, timer.getUpcomingWindow(170, 100).getLatestTicks());
	}

	@Test
	public void implausibleObservationDoesNotChangeDeathPause()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50, 100);
		timer.onDeath(80, 20);
		timer.onRespawn(100);

		// A heal 30 ticks from any plausible pause: some other mechanic.
		timer.markNow(200, 100);

		assertFalse(timer.consumeDeathPauseUpdate());
		assertFalse(timer.isDeathPauseLearned());
		assertEquals(0, timer.getDeathPauseAdjustMin());
		assertEquals(2, timer.getDeathPauseAdjustMax());
		RegenTimer.Window window = timer.getUpcomingWindow(200, 100);
		assertEquals(0, window.getEarliestTicks());
		assertEquals(0, window.getLatestTicks());
	}

	@Test
	public void deathOnlySeenAtDespawnWidensWindowAndIsNotLearnedFrom()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50, 100);
		timer.onDeath(80, 20, false);
		timer.onRespawn(100);

		RegenTimer.Window window = timer.getUpcomingWindow(100, 100);
		assertEquals(68, window.getEarliestTicks());
		assertEquals(75, window.getLatestTicks());

		timer.markNow(170, 100);
		assertFalse(timer.consumeDeathPauseUpdate());
		assertEquals(0, timer.getUpcomingWindow(170, 100).getLatestTicks());
	}

	@Test
	public void sharedWindowFillsAnUncalibratedTimer()
	{
		RegenTimer timer = new RegenTimer();

		assertTrue(timer.applySharedWindow(120, 124, 100));

		assertEquals(RegenTimer.State.TRACKING, timer.getState());
		assertTrue(timer.isPhaseFromParty());
		RegenTimer.Window window = timer.getUpcomingWindow(100, 100);
		assertEquals(20, window.getEarliestTicks());
		assertEquals(24, window.getLatestTicks());
	}

	@Test
	public void sharedWindowNarrowsACompatibleLocalWindow()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(10, 30, 80, 100, 0);
		timer.sampleHealth(11, 30, 90, 100, 0);

		// Local heal somewhere in 81-90; a party member saw the next one at 185-187.
		assertTrue(timer.applySharedWindow(185, 187, 100));

		RegenTimer.Window window = timer.getUpcomingWindow(100, 100);
		assertEquals(85, window.getEarliestTicks());
		assertEquals(87, window.getLatestTicks());
	}

	@Test
	public void sharedWindowThatContradictsLocalObservationIsIgnored()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(10, 30, 80, 100, 0);
		timer.sampleHealth(11, 30, 90, 100, 0);

		assertFalse(timer.applySharedWindow(150, 155, 100));

		assertFalse(timer.isPhaseFromParty());
		assertEquals(81, timer.getUpcomingWindow(100, 100).getEarliestTicks());
	}

	@Test
	public void widerSharedWindowDoesNotReplaceLocalPrecision()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(90, 100);

		assertFalse(timer.applySharedWindow(185, 195, 100));
		assertEquals(90, timer.getUpcomingWindow(100, 100).getEarliestTicks());
	}

	@Test
	public void sharedWindowReplacesAPhaseCarriedThroughTimeOutOfSight()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(10, 30, 80, 100, 0);
		timer.sampleHealth(11, 30, 90, 100, 0);
		timer.markUnverified();

		// A kill and respawn while out of sight can have moved the phase by any amount; a
		// member who watched says the next heal is at 150-155.
		assertTrue(timer.applySharedWindow(150, 155, 100));

		assertTrue(timer.isPhaseFromParty());
		assertFalse(timer.isUnverified());
		RegenTimer.Window window = timer.getUpcomingWindow(100, 100);
		assertEquals(50, window.getEarliestTicks());
		assertEquals(55, window.getLatestTicks());
	}

	@Test
	public void sharedWindowFromAnUnsureMemberDoesNotReplaceACarriedPhase()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(10, 30, 80, 100, 0);
		timer.sampleHealth(11, 30, 90, 100, 0);
		timer.markUnverified();

		assertFalse(timer.applySharedWindow(150, 155, 100, true));

		assertTrue(timer.isUnverified());
		assertEquals(81, timer.getUpcomingWindow(100, 100).getEarliestTicks());
	}

	@Test
	public void sharedWindowThatAgreesNarrowsACarriedPhaseAndVerifiesIt()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(10, 30, 80, 100, 0);
		timer.sampleHealth(11, 30, 90, 100, 0);
		timer.markUnverified();

		assertTrue(timer.applySharedWindow(185, 187, 100));

		assertFalse(timer.isUnverified());
		RegenTimer.Window window = timer.getUpcomingWindow(100, 100);
		assertEquals(85, window.getEarliestTicks());
		assertEquals(87, window.getLatestTicks());
	}

	@Test
	public void sharedWindowThatIsNoNarrowerLeavesACarriedPhaseUnverified()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(10, 30, 80, 100, 0);
		timer.sampleHealth(11, 30, 90, 100, 0);
		timer.markUnverified();

		assertFalse(timer.applySharedWindow(180, 195, 100));

		// It agrees but adds nothing, so a later member who disagrees can still correct it.
		assertTrue(timer.isUnverified());
	}

	@Test
	public void ambiguousSharedWindowDoesNotReplaceACarriedPhase()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleHealth(10, 30, 40, 100, 0);
		timer.sampleHealth(11, 30, 60, 100, 0);
		timer.markUnverified();

		// 58-145 fits both this cycle's 41-60 and the next cycle's, so it says nothing.
		assertFalse(timer.applySharedWindow(58, 145, 100));

		assertTrue(timer.isUnverified());
		assertFalse(timer.isPhaseFromParty());
	}

	@Test
	public void adoptingAnUnsureWindowLeavesTheTimerUnsureUntilASureOneArrives()
	{
		RegenTimer timer = new RegenTimer();

		assertTrue(timer.applySharedWindow(120, 124, 100, true));
		assertTrue(timer.isUnverified());

		assertTrue(timer.applySharedWindow(150, 152, 100, false));
		assertFalse(timer.isUnverified());
		assertEquals(50, timer.getUpcomingWindow(100, 100).getEarliestTicks());
	}

	@Test
	public void anObservedHealVerifiesACarriedPhase()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50, 100);
		timer.markUnverified();
		assertTrue(timer.isUnverified());

		timer.markNow(150, 100);

		assertFalse(timer.isUnverified());
	}

	@Test
	public void aPhaseInDoubtDoesNotTeachTheDeathPause()
	{
		RegenTimer sure = new RegenTimer();
		sure.markNow(50, 100);
		sure.onDeath(80, 0);
		sure.onRespawn(100);
		sure.markNow(168, 100);
		// The countdown only paused for 18 of the 20 dead ticks.
		assertTrue(sure.isDeathPauseLearned());
		assertEquals(2, sure.getDeathPauseAdjustMin());

		RegenTimer doubtful = new RegenTimer();
		doubtful.markNow(50, 100);
		doubtful.onDeath(80, 0);
		doubtful.onRespawn(100);
		doubtful.markUnverified();
		doubtful.markNow(168, 100);

		// The same heal would fit, but an anchor carried through time out of sight proves nothing.
		assertFalse(doubtful.isDeathPauseLearned());
		assertFalse(doubtful.isUnverified());
	}

	@Test
	public void thereIsNothingToDoubtWithoutAPhase()
	{
		RegenTimer timer = new RegenTimer();

		timer.markUnverified();

		assertFalse(timer.isUnverified());
	}

	@Test
	public void resetClearsTheDoubt()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50, 100);
		timer.markUnverified();

		timer.reset();

		assertFalse(timer.isUnverified());
	}

	@Test
	public void sharedWindowIsNotAppliedWhileDead()
	{
		RegenTimer timer = new RegenTimer();
		timer.onDeath(10, 20);

		assertFalse(timer.applySharedWindow(120, 124, 100));
	}

	@Test
	public void firstOwnHealKeepsSharedPrecisionAndTakesOver()
	{
		RegenTimer timer = new RegenTimer();
		timer.applySharedWindow(120, 120, 100);

		// A splashed heal only narrows it to 119-122.
		timer.sampleHealth(10, 30, 115, 100, 3);
		timer.sampleHealth(11, 30, 122, 100, 3);

		assertFalse(timer.isPhaseFromParty());
		RegenTimer.Window window = timer.getUpcomingWindow(122, 100);
		assertEquals(98, window.getEarliestTicks());
		assertEquals(98, window.getLatestTicks());
	}

	@Test
	public void carriedPhaseSharpensTheFirstSplashedHealAfterRespawn()
	{
		RegenTimer timer = new RegenTimer();
		timer.setDeathPauseAdjustment(2, 2, true);
		timer.markNow(50, 100);
		timer.onDeath(80, 20);
		timer.onRespawn(100);

		// The splash only shows the heal happened somewhere in 167-170.
		timer.sampleHealth(10, 30, 160, 100, 3);
		timer.sampleHealth(11, 30, 170, 100, 3);

		RegenTimer.Window window = timer.getUpcomingWindow(170, 100);
		assertEquals(98, window.getEarliestTicks());
		assertEquals(98, window.getLatestTicks());
	}

	@Test
	public void lateExpectedRespawnValueUpdatesCurrentCountdownAndPhase()
	{
		RegenTimer timer = new RegenTimer();
		timer.setDeathPauseAdjustment(0, 0, true);
		timer.markNow(50);
		timer.onDeath(80, 20);

		timer.updateExpectedRespawnTicks(50);

		assertEquals(40, timer.getRespawnTicksRemaining(90));
		assertEquals(70, timer.getUpcomingWindow(130, 100).getEarliestTicks());
		assertEquals(50, timer.onRespawn(130));
	}

	@Test
	public void resetClearsCalibration()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(50);
		timer.reset();

		assertNull(timer.getUpcomingWindow(80, 100));
		assertEquals(RegenTimer.State.OBSERVING, timer.getState());
	}

	@Test
	public void sparseInspectionDoesNotWidenCompatiblePrecisePhase()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleExactStats(50, 20, 10, 100);
		timer.sampleExactStats(51, 21, 11, 100);
		timer.sampleExactStats(51, 21, 90, 100);
		timer.sampleExactStats(52, 22, 121, 100);
		RegenTimer.Window window = timer.getUpcomingWindow(122, 100);
		assertEquals(89, window.getEarliestTicks());
		assertEquals(89, window.getLatestTicks());
		assertEquals(2, timer.getObservedRegens());
	}

	@Test
	public void wideInspectionEvidenceDoesNotLearnMidpoint112()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleExactHealth(50, 0, 100);
		timer.sampleExactHealth(51, 10, 100);
		timer.sampleExactHealth(51, 110, 100);
		assertEquals(0, timer.sampleExactHealth(52, 124, 100));
		assertFalse(timer.isPhasePrecise());
	}

	@Test
	public void skippedCyclesKeepPhaseWithoutLearningTwoHundredTickRate()
	{
		RegenTimer timer = new RegenTimer();
		timer.markNow(10, 100);
		timer.sampleExactHealth(50, 200, 100);
		assertEquals(0, timer.sampleExactHealth(51, 212, 100));
		assertEquals(0, timer.sampleExactHealth(52, 312, 100));
		assertTrue(timer.isPhasePrecise());
		assertEquals(97, timer.getUpcomingWindow(313, 100).getEarliestTicks());
	}

	@Test
	public void frequentRecastsNarrowAnEightyFourTickWindow()
	{
		RegenTimer timer = new RegenTimer();
		timer.sampleExactHealth(50, 0, 100);
		timer.sampleExactHealth(51, 85, 100);
		assertEquals(84, timer.getPhaseUncertaintyTicks());
		assertFalse(timer.isPhasePrecise());
		// Elapsing time alone must not make a wide phase look precise.
		timer.getUpcomingWindow(84, 100);
		assertFalse(timer.isPhasePrecise());
		timer.sampleExactHealth(51, 99, 100);
		assertEquals(0, timer.sampleExactHealth(52, 102, 100));
		assertTrue(timer.isPhasePrecise());
		assertEquals(1, timer.getPhaseUncertaintyTicks());
	}

	@Test
	public void inspectionDelayAndMinuteSpacedReadingsRefineTheDisplayedWindow()
	{
		RegenTimer timer = new RegenTimer();
		long previousWidth = 100;
		for (long result : new long[]{3, 85, 180, 275, 370, 465, 560, 655, 747})
		{
			int hp = 51 + (int) Math.floorDiv(result - 50, 100);
			int learned = timer.sampleInspectionStats(hp, -1, 200, result - 3, result, 100);
			assertTrue(learned == 0 || learned == 100);
			RegenTimer.Window window = timer.getUpcomingWindow(result + 1, 100);
			if (window != null)
			{
				assertTrue(timer.getPhaseUncertaintyTicks() <= previousWidth);
				previousWidth = timer.getPhaseUncertaintyTicks();
				long end = result + 1 + window.getLatestTicks();
				long actual = 50 + Math.floorDiv(end - 50, 100) * 100;
				assertTrue("result=" + result + " start=" + (result + 1 + window.getEarliestTicks())
					+ " end=" + end + " width=" + previousWidth,
					actual >= end - previousWidth);
			}
		}
		assertTrue(previousWidth <= 10);
	}

	@Test
	public void overheadKeepsShowingApproximateWideWindows()
	{
		assertEquals("Regen: ~11-41t",
			NpcHealthRegenSceneOverlay.formatRegenCountdown(new RegenTimer.Window(11, 41)));
		assertEquals("Regen: ~NOW-84t",
			NpcHealthRegenSceneOverlay.formatRegenCountdown(new RegenTimer.Window(0, 84)));
	}
}
