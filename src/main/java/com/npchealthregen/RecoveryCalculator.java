package com.npchealthregen;

final class RecoveryCalculator
{
	static final class Range
	{
		private final int minimum;
		private final int maximum;

		Range(int minimum, int maximum)
		{
			this.minimum = minimum;
			this.maximum = maximum;
		}

		int getMinimum()
		{
			return minimum;
		}

		int getMaximum()
		{
			return maximum;
		}
	}

	static final class TickWindow
	{
		private final long earliest;
		private final long latest;

		TickWindow(long earliest, long latest)
		{
			this.earliest = earliest;
			this.latest = latest;
		}

		long getEarliest()
		{
			return earliest;
		}

		long getLatest()
		{
			return latest;
		}
	}

	private RecoveryCalculator()
	{
	}

	static Range healthFromRatio(int ratio, int scale, int maximumHitpoints)
	{
		if (ratio < 0 || scale <= 0 || maximumHitpoints <= 0)
		{
			return null;
		}
		if (ratio == 0)
		{
			return new Range(0, 0);
		}

		int minimum = 1;
		int maximum = maximumHitpoints;
		if (scale > 1)
		{
			if (ratio > 1)
			{
				minimum = (maximumHitpoints * (ratio - 1) + scale - 2) / (scale - 1);
			}
			maximum = (maximumHitpoints * ratio - 1) / (scale - 1);
			maximum = Math.min(maximum, maximumHitpoints);
		}

		return new Range(Math.min(minimum, maximumHitpoints), Math.max(minimum, maximum));
	}

	static TickWindow timeUntilFull(
		Range currentHitpoints,
		int maximumHitpoints,
		long sampleTick,
		long now,
		int regenInterval,
		RegenTimer timer)
	{
		if (currentHitpoints == null || maximumHitpoints <= 0 || sampleTick < 0 || regenInterval <= 0)
		{
			return null;
		}

		int minimumMissing = Math.max(0, maximumHitpoints - currentHitpoints.getMaximum());
		int maximumMissing = Math.max(0, maximumHitpoints - currentHitpoints.getMinimum());
		if (maximumMissing == 0)
		{
			return new TickWindow(0, 0);
		}

		long referenceTick = sampleTick + 1;
		RegenTimer.Window next = timer.getUpcomingWindow(referenceTick, regenInterval);
		if (next == null)
		{
			return null;
		}

		long earliestAbsolute = minimumMissing == 0
			? now
			: referenceTick + next.getEarliestTicks() + (long) (minimumMissing - 1) * regenInterval;
		long latestAbsolute = referenceTick + next.getLatestTicks()
			+ (long) (maximumMissing - 1) * regenInterval;
		return new TickWindow(
			Math.max(0, earliestAbsolute - now),
			Math.max(0, latestAbsolute - now));
	}

	static Range projectHealth(
		Range observed,
		int maximumHitpoints,
		long sampleTick,
		long now,
		int regenInterval,
		RegenTimer timer)
	{
		if (observed == null || maximumHitpoints <= 0 || sampleTick < 0
			|| now <= sampleTick || regenInterval <= 0)
		{
			return observed;
		}

		long referenceTick = sampleTick + 1;
		RegenTimer.Window next = timer.getUpcomingWindow(referenceTick, regenInterval);
		if (next == null)
		{
			return observed;
		}

		long earliestFirst = referenceTick + next.getEarliestTicks();
		long latestFirst = referenceTick + next.getLatestTicks();
		long guaranteedHeals = completedCycles(now, latestFirst, regenInterval);
		long possibleHeals = completedCycles(now, earliestFirst, regenInterval);
		return new Range(
			(int) Math.min(maximumHitpoints, observed.getMinimum() + guaranteedHeals),
			(int) Math.min(maximumHitpoints, observed.getMaximum() + possibleHeals));
	}

	static Range defenceAtFull(
		int currentDefence,
		int baseDefence,
		Range currentHitpoints,
		int maximumHitpoints)
	{
		if (currentDefence < 0 || baseDefence <= 0 || currentHitpoints == null || maximumHitpoints <= 0)
		{
			return null;
		}

		int minimumMissing = Math.max(0, maximumHitpoints - currentHitpoints.getMaximum());
		int maximumMissing = Math.max(0, maximumHitpoints - currentHitpoints.getMinimum());
		return new Range(
			Math.min(baseDefence, currentDefence + minimumMissing),
			Math.min(baseDefence, currentDefence + maximumMissing));
	}

	private static long completedCycles(long now, long firstTick, int interval)
	{
		return now < firstTick ? 0 : 1 + (now - firstTick) / interval;
	}
}
