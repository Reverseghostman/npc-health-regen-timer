package com.npchealthregen;

final class RegenTimer
{
	static final int PRECISE_WINDOW_TICKS = 3;
	private static final int MAX_LEARNING_UNCERTAINTY = 6;
	/*
	 * The regen countdown pauses while an NPC is dead, but not for the whole
	 * killing-hit-to-respawn time the client measures: the NPC keeps counting on
	 * the tick(s) before its death sequence starts and on its respawn tick. The
	 * pause is therefore (measured dead ticks - adjustment), for a small constant
	 * adjustment learned per NPC type. Until learned, cover the plausible range.
	 */
	static final int DEFAULT_DEATH_PAUSE_ADJUST_MIN = 0;
	static final int DEFAULT_DEATH_PAUSE_ADJUST_MAX = 2;
	private static final int PLAUSIBLE_DEATH_PAUSE_ADJUST_MIN = -3;
	private static final int PLAUSIBLE_DEATH_PAUSE_ADJUST_MAX = 6;
	// A death only noticed at despawn was recorded after the death sequence began.
	private static final int UNRELIABLE_DEATH_SLACK_TICKS = 5;
	enum State
	{
		OBSERVING,
		TRACKING,
		WAITING_FOR_RESPAWN
	}

	static final class Window
	{
		private final long earliestTicks;
		private final long latestTicks;

		Window(long earliestTicks, long latestTicks)
		{
			this.earliestTicks = earliestTicks;
			this.latestTicks = latestTicks;
		}

		long getEarliestTicks()
		{
			return earliestTicks;
		}

		long getLatestTicks()
		{
			return latestTicks;
		}
	}

	private State state = State.OBSERVING;
	private int lastHealthRatio = -1;
	private int lastHealthScale = -1;
	private long lastHealthSampleTick = -1;
	private int lastExactHitpoints = -1;
	private long lastExactSampleTick = -1;
	private int lastExactDefence = -1;
	private long lastDefenceSampleTick = -1;
	private long windowStartTick = -1;
	private long windowEndTick = -1;
	private int observedRegens;
	private long possibleIntervalMinimum = -1;
	private long possibleIntervalMaximum = -1;
	private long deathTick = -1;
	private int appliedRespawnTicks;
	private long expectedRespawnTick = -1;
	private int deathPauseAdjustMin = DEFAULT_DEATH_PAUSE_ADJUST_MIN;
	private int deathPauseAdjustMax = DEFAULT_DEATH_PAUSE_ADJUST_MAX;
	private boolean deathPauseLearned;
	private boolean deathPauseUpdated;
	// The current phase came from a party member rather than this client's own
	// observations; cleared by the next local observation.
	private boolean phaseFromParty;
	// The current phase was carried through time this client could not see the NPC (out of view,
	// logged out, or a respawn it saw late). An unseen death or respawn moves the real phase, so a
	// party member who has been watching is likelier right than this. Cleared by the next local
	// observation or by a share from a member whose own phase is not in doubt.
	private boolean unverified;
	// Consecutive deaths since the phase was last observed. The carried window is
	// always rebuilt from the phase before the first of them, so a later
	// calibration can re-derive it rather than compound per-kill rounding.
	private long chainAnchorStart = -1;
	private long chainAnchorEnd = -1;
	private long chainDeadTicks;
	private int chainDeaths;
	private long chainSlack;
	private boolean chainReliable;
	private final InspectionPhaseTracker inspectionPhase = new InspectionPhaseTracker();

	void reset()
	{
		clearDeathChain();
		phaseFromParty = false;
		unverified = false;
		inspectionPhase.reset();
		state = State.OBSERVING;
		lastHealthRatio = -1;
		lastHealthScale = -1;
		lastHealthSampleTick = -1;
		lastExactHitpoints = -1;
		lastExactSampleTick = -1;
		lastExactDefence = -1;
		lastDefenceSampleTick = -1;
		windowStartTick = -1;
		windowEndTick = -1;
		resetIntervalLearning();
		deathTick = -1;
		appliedRespawnTicks = 0;
		expectedRespawnTick = -1;
	}

	int sampleHealth(int healthRatio, int healthScale, long tick, int preferredInterval)
	{
		return sampleHealth(healthRatio, healthScale, tick, preferredInterval, 0);
	}

	int sampleHealth(
		int healthRatio,
		int healthScale,
		long tick,
		int preferredInterval,
		int observationDelayTicks)
	{
		if (state == State.WAITING_FOR_RESPAWN)
		{
			return 0;
		}

		if (healthRatio < 0 || healthScale <= 0 || healthRatio > healthScale)
		{
			lastHealthRatio = -1;
			lastHealthScale = -1;
			lastHealthSampleTick = -1;
			return 0;
		}

		int detectedInterval = 0;
		if (lastHealthRatio >= 0 && healthScale == lastHealthScale && healthRatio > lastHealthRatio)
		{
			long start;
			if (observationDelayTicks > 0)
			{
				start = Math.max(0, tick - observationDelayTicks);
			}
			else
			{
				start = lastHealthSampleTick < tick ? lastHealthSampleTick + 1 : tick;
			}
			detectedInterval = recordRegenWindow(start, tick, preferredInterval);
		}

		lastHealthRatio = healthRatio;
		lastHealthScale = healthScale;
		lastHealthSampleTick = tick;
		return detectedInterval;
	}

	int sampleHealth(int healthRatio, int healthScale, long tick)
	{
		return sampleHealth(healthRatio, healthScale, tick, 0);
	}

	int sampleExactHealth(int hitpoints, long tick, int preferredInterval)
	{
		return sampleExactHealth(hitpoints, tick, tick, preferredInterval);
	}

	private int sampleExactHealth(int hitpoints, long earliestTick, long tick, int preferredInterval)
	{
		if (state == State.WAITING_FOR_RESPAWN)
		{
			return 0;
		}

		if (hitpoints < 0)
		{
			lastExactHitpoints = -1;
			lastExactSampleTick = -1;
			return 0;
		}

		int detectedInterval = 0;
		if (lastExactHitpoints >= 0 && hitpoints > lastExactHitpoints)
		{
			long start = lastExactSampleTick < tick ? lastExactSampleTick + 1 : tick;
			detectedInterval = recordRegenWindow(start, tick, preferredInterval);
		}

		lastExactHitpoints = hitpoints;
		lastExactSampleTick = earliestTick;
		return detectedInterval;
	}

	int sampleExactHealth(int hitpoints, long tick)
	{
		return sampleExactHealth(hitpoints, tick, 0);
	}

	int sampleExactStats(int hitpoints, int defence, long tick, int preferredInterval)
	{
		return sampleExactStats(hitpoints, defence, tick, tick, preferredInterval);
	}

	private int sampleExactStats(int hitpoints, int defence, long earliestTick, long tick, int preferredInterval)
	{
		int interval = sampleExactHealth(hitpoints, earliestTick, tick, preferredInterval);
		if (state == State.WAITING_FOR_RESPAWN)
		{
			return interval;
		}
		if (defence >= 0 && lastExactDefence >= 0 && defence > lastExactDefence)
		{
			long start = lastDefenceSampleTick < tick ? lastDefenceSampleTick + 1 : tick;
			int defenceInterval = recordRegenWindow(start, tick, preferredInterval);
			if (defenceInterval > 0)
			{
				interval = defenceInterval;
			}
		}
		lastExactDefence = defence;
		lastDefenceSampleTick = defence >= 0 ? earliestTick : -1;
		return interval;
	}

	int sampleInspectionStats(int hp, int defence, int maximumHp,
		long earliestTick, long resultTick, int preferredInterval)
	{
		if (state == State.WAITING_FOR_RESPAWN)
		{
			return 0;
		}
		long previousStart = windowStartTick;
		long previousEnd = windowEndTick;
		int learned = sampleExactStats(hp, defence, earliestTick, resultTick, preferredInterval);
		int interval = learned > 0 ? learned : preferredInterval;
		long[] phase = inspectionPhase.sample(hp, maximumHp, earliestTick, resultTick, interval);
		if (phase != null)
		{
			// Intersect with other sources only when one periodic translation fits.
			long start = phase[0];
			long end = phase[1];
			if (chainDeaths > 0)
			{
				long[] narrowed = resolveDeathChain(start, end, interval);
				start = narrowed[0];
				end = narrowed[1];
			}
			else if (previousStart >= 0 && interval == preferredInterval)
			{
				long first = -Math.floorDiv(previousEnd - start, interval);
				long last = Math.floorDiv(end - previousStart, interval);
				if (first == last)
				{
					start = Math.max(start, previousStart + first * interval);
					end = Math.min(end, previousEnd + first * interval);
				}
			}
			windowStartTick = start;
			windowEndTick = end;
			state = State.TRACKING;
			phaseFromParty = false;
			unverified = false;
		}
		return learned;
	}

	void invalidateInspectionHealth()
	{
		inspectionPhase.invalidateSample();
		lastExactHitpoints = -1;
		lastExactSampleTick = -1;
	}

	int markNow(long tick, int preferredInterval)
	{
		return recordRegenWindow(tick, tick, preferredInterval);
	}

	int markNow(long tick)
	{
		return markNow(tick, 0);
	}

	void onDeath(long tick, int expectedRespawnTicks)
	{
		onDeath(tick, expectedRespawnTicks, true);
	}

	/**
	 * @param deathTickReliable false when the death was only noticed as the NPC
	 * despawned, after its death sequence (and regen pause) had already begun
	 */
	void onDeath(long tick, int expectedRespawnTicks, boolean deathTickReliable)
	{
		inspectionPhase.reset();
		lastExactDefence = -1;
		lastDefenceSampleTick = -1;
		lastHealthRatio = -1;
		lastHealthScale = -1;
		lastHealthSampleTick = -1;
		lastExactHitpoints = -1;
		lastExactSampleTick = -1;
		resetIntervalLearning();
		deathTick = tick;
		appliedRespawnTicks = Math.max(0, expectedRespawnTicks);
		expectedRespawnTick = expectedRespawnTicks > 0 ? tick + expectedRespawnTicks : -1;

		if (chainDeaths == 0)
		{
			chainAnchorStart = windowStartTick;
			chainAnchorEnd = windowEndTick;
			chainDeadTicks = 0;
			chainSlack = 0;
			chainReliable = true;
		}
		chainDeaths++;
		if (!deathTickReliable)
		{
			chainSlack += UNRELIABLE_DEATH_SLACK_TICKS;
			chainReliable = false;
		}
		applyDeathChain(appliedRespawnTicks);
		state = State.WAITING_FOR_RESPAWN;
	}

	/** @return the measured killing-hit-to-respawn ticks */
	int onRespawn(long tick)
	{
		if (state != State.WAITING_FOR_RESPAWN)
		{
			return 0;
		}

		long measured = Math.max(0, tick - deathTick);
		int actualRespawnTicks = measured > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) measured;
		chainDeadTicks += actualRespawnTicks;
		applyDeathChain(0);

		state = windowStartTick >= 0 ? State.TRACKING : State.OBSERVING;
		deathTick = -1;
		appliedRespawnTicks = 0;
		expectedRespawnTick = -1;
		return actualRespawnTicks;
	}

	void updateExpectedRespawnTicks(int expectedTicks)
	{
		if (state != State.WAITING_FOR_RESPAWN || deathTick < 0)
		{
			return;
		}

		appliedRespawnTicks = Math.max(0, expectedTicks);
		expectedRespawnTick = appliedRespawnTicks > 0 ? deathTick + appliedRespawnTicks : -1;
		applyDeathChain(appliedRespawnTicks);
	}

	void setDeathPauseAdjustment(int minimum, int maximum, boolean learned)
	{
		deathPauseAdjustMin = Math.min(minimum, maximum);
		deathPauseAdjustMax = Math.max(minimum, maximum);
		deathPauseLearned = learned;
		deathPauseUpdated = false;
		if (chainDeaths > 0)
		{
			applyDeathChain(state == State.WAITING_FOR_RESPAWN ? appliedRespawnTicks : 0);
		}
	}

	int getDeathPauseAdjustMin()
	{
		return deathPauseAdjustMin;
	}

	int getDeathPauseAdjustMax()
	{
		return deathPauseAdjustMax;
	}

	boolean isDeathPauseLearned()
	{
		return deathPauseLearned;
	}

	/** @return whether an observation has refined the adjustment since the last call */
	boolean consumeDeathPauseUpdate()
	{
		boolean updated = deathPauseUpdated;
		deathPauseUpdated = false;
		return updated;
	}

	/**
	 * Rebuilds the carried phase from the phase before the chain's first death.
	 * @param pendingDeadTicks dead ticks assumed for a death still awaiting respawn
	 */
	private void applyDeathChain(long pendingDeadTicks)
	{
		if (chainAnchorStart < 0)
		{
			return;
		}

		long dead = chainDeadTicks + pendingDeadTicks;
		windowStartTick = chainAnchorStart + dead - (long) chainDeaths * deathPauseAdjustMax;
		windowEndTick = chainAnchorEnd + dead - (long) chainDeaths * deathPauseAdjustMin + chainSlack;
	}

	private void clearDeathChain()
	{
		chainAnchorStart = -1;
		chainAnchorEnd = -1;
		chainDeadTicks = 0;
		chainDeaths = 0;
		chainSlack = 0;
		chainReliable = false;
	}

	/**
	 * Uses the first heal observed after one or more deaths to learn the pause
	 * adjustment, then narrows the observation with the carried phase.
	 * @return the narrowed observation window
	 */
	private long[] resolveDeathChain(long start, long end, int interval)
	{
		if (chainDeaths <= 0 || state == State.WAITING_FOR_RESPAWN)
		{
			return new long[]{start, end};
		}

		if (interval > 0 && chainReliable && chainAnchorStart >= 0)
		{
			calibrateDeathPause(start, end, interval);
		}

		long[] result = narrowWithCarriedPhase(start, end, interval);
		clearDeathChain();
		return result;
	}

	/**
	 * Intersects [start, end] with the carried window when exactly one periodic
	 * translation of it overlaps; otherwise returns [start, end] unchanged.
	 */
	private long[] narrowWithCarriedPhase(long start, long end, int interval)
	{
		long[] narrowed = periodicIntersection(start, end, interval);
		return narrowed != null ? narrowed : new long[]{start, end};
	}

	/**
	 * @return [start, end] intersected with the one periodic translation of the
	 * current window that overlaps it, or null if none or several overlap
	 */
	private long[] periodicIntersection(long start, long end, int interval)
	{
		if (windowStartTick < 0 || interval <= 0)
		{
			return null;
		}

		long first = -Math.floorDiv(windowEndTick - start, interval);
		long last = Math.floorDiv(end - windowStartTick, interval);
		if (first != last)
		{
			return null;
		}
		return new long[]{
			Math.max(start, windowStartTick + first * interval),
			Math.min(end, windowEndTick + first * interval)};
	}

	boolean applySharedWindow(long start, long end, int interval)
	{
		return applySharedWindow(start, end, interval, false);
	}

	/**
	 * Merges a regen window shared by a party member tracking the same NPC,
	 * already converted to this client's ticks.
	 * @param senderUnverified whether the sender's own phase was carried through time it could not
	 * see the NPC, so is in doubt itself
	 * @return whether the local window changed
	 */
	boolean applySharedWindow(long start, long end, int interval, boolean senderUnverified)
	{
		if (state == State.WAITING_FOR_RESPAWN || start > end || interval <= 0)
		{
			return false;
		}

		long newStart = start;
		long newEnd = end;
		// A local window spanning a whole cycle says nothing about the phase.
		boolean localNarrow = windowStartTick >= 0 && windowEndTick - windowStartTick < interval;
		if (localNarrow)
		{
			int overlaps = periodicOverlaps(start, end, interval);
			if (overlaps == 1)
			{
				long[] narrowed = periodicIntersection(start, end, interval);
				// No narrower: keep the phase already held.
				if (narrowed[1] - narrowed[0] >= windowEndTick - windowStartTick)
				{
					return false;
				}
				newStart = narrowed[0];
				newEnd = narrowed[1];
			}
			else if (overlaps != 0 || !unverified || senderUnverified)
			{
				// Ambiguous, or it contradicts a phase this client observed or one the sender is
				// no surer of: keep the first-hand phase.
				return false;
			}
			// Otherwise it contradicts a phase carried through time out of sight, which an unseen
			// death or respawn can have moved, and the sender has been watching: take the sender's.
		}

		windowStartTick = newStart;
		windowEndTick = newEnd;
		state = State.TRACKING;
		// The carried chain is superseded by the merged phase.
		clearDeathChain();
		phaseFromParty = true;
		// The merged phase is only as sure as its least sure source; a verified local window
		// narrowed by an unverified share is still inside what was verified.
		unverified = senderUnverified && (unverified || !localNarrow);
		return true;
	}

	/**
	 * @return how many translations of the local window by whole intervals overlap [start, end]:
	 * 0 when they contradict each other, 1 when compatible, 2 for two or more (ambiguous)
	 */
	private int periodicOverlaps(long start, long end, int interval)
	{
		long first = -Math.floorDiv(windowEndTick - start, interval);
		long last = Math.floorDiv(end - windowStartTick, interval);
		return (int) Math.max(0, Math.min(2, last - first + 1));
	}

	boolean isPhaseFromParty()
	{
		return phaseFromParty;
	}

	/**
	 * Notes that the NPC went out of sight, or the client away, with the phase still being
	 * carried on: a death or respawn in that time would have moved it unseen.
	 */
	void markUnverified()
	{
		if (windowStartTick >= 0)
		{
			unverified = true;
			// The anchor the pause would be learned from is in doubt too.
			chainReliable = false;
		}
	}

	/** @return whether the phase was carried through time the NPC could not be seen */
	boolean isUnverified()
	{
		return unverified;
	}

	/**
	 * Widens the phase by ticks on each side, for time that passed without
	 * observing game ticks (such as while logged out).
	 */
	void widenPhase(long ticks)
	{
		if (ticks <= 0)
		{
			return;
		}
		// Negative ticks mean "no window", so clamp at zero.
		if (chainDeaths > 0 && chainAnchorStart >= 0)
		{
			chainAnchorStart = Math.max(0, chainAnchorStart - ticks);
			chainAnchorEnd += ticks;
			applyDeathChain(state == State.WAITING_FOR_RESPAWN ? appliedRespawnTicks : 0);
		}
		else if (windowStartTick >= 0)
		{
			windowStartTick = Math.max(0, windowStartTick - ticks);
			windowEndTick += ticks;
		}
	}

	/** @return the next regen window as absolute {start, end} ticks, or null */
	long[] getUpcomingWindowTicks(long now, int intervalTicks)
	{
		if (windowStartTick < 0 || intervalTicks <= 0)
		{
			return null;
		}

		long start = windowStartTick;
		long end = windowEndTick;
		if (end < now)
		{
			long cycles = (now - end + intervalTicks - 1L) / intervalTicks;
			start += cycles * intervalTicks;
			end += cycles * intervalTicks;
		}
		return new long[]{start, end};
	}

	private void calibrateDeathPause(long start, long end, int interval)
	{
		// heal = anchorPhase + deadTicks - deaths * adjustment + cycles * interval,
		// with anchorPhase in [anchorStart, anchorEnd] and heal in [start, end].
		long deaths = chainDeaths;
		long low = chainAnchorStart + chainDeadTicks - end;
		long high = chainAnchorEnd + chainDeadTicks - start;
		if (high - low >= interval)
		{
			return;
		}
		double expected = deaths * (deathPauseAdjustMin + deathPauseAdjustMax) / 2.0;
		long cycles = Math.round((expected - (low + high) / 2.0) / interval);
		low += cycles * interval;
		high += cycles * interval;
		long minimum = Math.max(PLAUSIBLE_DEATH_PAUSE_ADJUST_MIN, ceilDiv(low, deaths));
		long maximum = Math.min(PLAUSIBLE_DEATH_PAUSE_ADJUST_MAX, Math.floorDiv(high, deaths));
		if (minimum > maximum)
		{
			// No plausible whole-tick adjustment fits: leave the model untouched.
			return;
		}

		long intersectedMinimum = Math.max(minimum, deathPauseAdjustMin);
		long intersectedMaximum = Math.min(maximum, deathPauseAdjustMax);
		if (intersectedMinimum <= intersectedMaximum)
		{
			minimum = intersectedMinimum;
			maximum = intersectedMaximum;
		}
		if (minimum != deathPauseAdjustMin || maximum != deathPauseAdjustMax || !deathPauseLearned)
		{
			deathPauseAdjustMin = (int) minimum;
			deathPauseAdjustMax = (int) maximum;
			deathPauseLearned = true;
			deathPauseUpdated = true;
		}
		applyDeathChain(0);
	}

	private static long ceilDiv(long dividend, long divisor)
	{
		return -Math.floorDiv(-dividend, divisor);
	}

	Window getUpcomingWindow(long now, int intervalTicks)
	{
		long[] window = getUpcomingWindowTicks(now, intervalTicks);
		return window == null ? null
			: new Window(Math.max(0, window[0] - now), Math.max(0, window[1] - now));
	}

	long getRespawnTicksRemaining(long now)
	{
		return expectedRespawnTick < 0 ? -1 : Math.max(0, expectedRespawnTick - now);
	}

	/** @return the tick the NPC is expected to respawn, or -1 if unknown */
	long getExpectedRespawnTick()
	{
		return expectedRespawnTick;
	}

	long getRespawnTicksElapsed(long now)
	{
		return deathTick < 0 ? 0 : Math.max(0, now - deathTick);
	}

	int getObservedRegens()
	{
		return observedRegens;
	}

	State getState()
	{
		return state;
	}

	boolean isPhasePrecise()
	{
		return windowStartTick >= 0 && getPhaseUncertaintyTicks() <= PRECISE_WINDOW_TICKS;
	}

	long getPhaseUncertaintyTicks()
	{
		return windowStartTick < 0 ? -1 : windowEndTick - windowStartTick;
	}

	private int recordRegenWindow(long start, long end, int preferredInterval)
	{
		if (start > end)
		{
			return 0;
		}

		if (chainDeaths > 0)
		{
			long[] narrowed = resolveDeathChain(start, end, preferredInterval);
			start = narrowed[0];
			end = narrowed[1];
		}
		else if (phaseFromParty && observedRegens == 0)
		{
			// First own heal on a shared phase: keep the shared precision.
			long[] narrowed = narrowWithCarriedPhase(start, end, preferredInterval);
			start = narrowed[0];
			end = narrowed[1];
		}
		phaseFromParty = false;
		unverified = false;

		if (observedRegens > 0 && start <= windowEndTick && end >= windowStartTick)
		{
			// The health bar and inspection panel can report the same heal at
			// different times. Intersect their windows so the more precise source
			// tightens the phase without counting one regeneration twice.
			windowStartTick = Math.max(windowStartTick, start);
			windowEndTick = Math.min(windowEndTick, end);
			state = State.TRACKING;
			return 0;
		}

		long previousStart = windowStartTick;
		long previousEnd = windowEndTick;
		long minimumElapsed = Math.max(1, start - previousEnd);
		long maximumElapsed = end - previousStart;
		boolean skippedOrAmbiguousCycles = false;
		// Preserve a compatible phase from the preceding cycle. Sparse inspections
		// should refine a precise observation, rather than widen it again.
		if (observedRegens > 0 && preferredInterval > 0)
		{
			long firstCycle = Math.max(1,
				(minimumElapsed + preferredInterval - 1) / preferredInterval);
			long lastCycle = maximumElapsed / preferredInterval;
			skippedOrAmbiguousCycles = lastCycle > 1;
			if (firstCycle == lastCycle)
			{
				long shift = firstCycle * preferredInterval;
				start = Math.max(start, previousStart + shift);
				end = Math.min(end, previousEnd + shift);
			}
		}
		windowStartTick = start;
		windowEndTick = end;
		state = State.TRACKING;

		if (observedRegens == 0)
		{
			observedRegens = 1;
			return 0;
		}

		observedRegens++;
		// Learn only from the raw evidence, not bounds narrowed using the very
		// interval being tested. A gap spanning multiple cycles is not one period.
		if (maximumElapsed <= 0 || skippedOrAmbiguousCycles)
		{
			return 0;
		}

		if (possibleIntervalMinimum < 0)
		{
			possibleIntervalMinimum = minimumElapsed;
			possibleIntervalMaximum = maximumElapsed;
		}
		else
		{
			long intersectedMinimum = Math.max(possibleIntervalMinimum, minimumElapsed);
			long intersectedMaximum = Math.min(possibleIntervalMaximum, maximumElapsed);
			if (intersectedMinimum <= intersectedMaximum)
			{
				possibleIntervalMinimum = intersectedMinimum;
				possibleIntervalMaximum = intersectedMaximum;
			}
			else
			{
				possibleIntervalMinimum = minimumElapsed;
				possibleIntervalMaximum = maximumElapsed;
			}
		}

		// A midpoint of a broad range is not a measured regeneration rate.
		if (possibleIntervalMaximum - possibleIntervalMinimum > MAX_LEARNING_UNCERTAINTY)
		{
			return 0;
		}

		if (preferredInterval > 0)
		{
			if (preferredInterval >= possibleIntervalMinimum
				&& preferredInterval <= possibleIntervalMaximum)
			{
				return preferredInterval;
			}
		}

		long roundedInterval = (possibleIntervalMinimum + possibleIntervalMaximum + 1L) / 2L;
		return roundedInterval > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) roundedInterval;
	}

	private void resetIntervalLearning()
	{
		observedRegens = 0;
		possibleIntervalMinimum = -1;
		possibleIntervalMaximum = -1;
	}
}
