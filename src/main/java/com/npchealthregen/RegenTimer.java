package com.npchealthregen;

final class RegenTimer
{
	static final int PRECISE_WINDOW_TICKS = 3;
	private static final int MAX_LEARNING_UNCERTAINTY = 6;
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
	private final InspectionPhaseTracker inspectionPhase = new InspectionPhaseTracker();

	void reset()
	{
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
			if (previousStart >= 0 && interval == preferredInterval)
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

		if (windowStartTick >= 0)
		{
			windowStartTick += appliedRespawnTicks;
			windowEndTick += appliedRespawnTicks;
		}
		state = State.WAITING_FOR_RESPAWN;
	}

	int onRespawn(long tick)
	{
		if (state != State.WAITING_FOR_RESPAWN)
		{
			return 0;
		}

		long measured = Math.max(0, tick - deathTick);
		int actualRespawnTicks = measured > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) measured;
		int correction = actualRespawnTicks - appliedRespawnTicks;
		if (windowStartTick >= 0)
		{
			windowStartTick += correction;
			windowEndTick += correction;
		}

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

		int newAppliedTicks = Math.max(0, expectedTicks);
		expectedRespawnTick = newAppliedTicks > 0 ? deathTick + newAppliedTicks : -1;
		if (windowStartTick >= 0)
		{
			int correction = newAppliedTicks - appliedRespawnTicks;
			windowStartTick += correction;
			windowEndTick += correction;
		}
		appliedRespawnTicks = newAppliedTicks;
	}

	Window getUpcomingWindow(long now, int intervalTicks)
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

		return new Window(Math.max(0, start - now), Math.max(0, end - now));
	}

	long getRespawnTicksRemaining(long now)
	{
		return expectedRespawnTick < 0 ? -1 : Math.max(0, expectedRespawnTick - now);
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
