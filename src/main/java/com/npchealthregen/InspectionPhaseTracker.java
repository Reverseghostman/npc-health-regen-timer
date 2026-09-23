package com.npchealthregen;

import java.util.Arrays;

/** Intersects possible periodic heal phases using fresh, undamaged HP snapshots. */
final class InspectionPhaseTracker
{
	private boolean[] phases;
	private int previousHp = -1;
	private long previousEarliest;
	private long previousLatest;
	private int anchorHp = -1;
	private long anchorEarliest;
	private long anchorLatest;
	private boolean observedHeal;

	void reset()
	{
		phases = null;
		previousHp = -1;
		anchorHp = -1;
		observedHeal = false;
	}

	void invalidateSample()
	{
		previousHp = -1;
		anchorHp = -1;
	}

	// Returns absolute bounds for the most recent possible heal, or null when
	// there is not enough evidence. Assumes one HP restored per active interval.
	long[] sample(int hp, int maximumHp, long earliest, long latest, int interval)
	{
		if (interval <= 0 || interval > 10000 || earliest > latest || hp <= 0
			|| (maximumHp > 0 && hp >= maximumHp))
		{
			invalidateSample();
			return null;
		}
		if (phases == null || phases.length != interval)
		{
			reset();
			phases = new boolean[interval];
			Arrays.fill(phases, true);
		}
		if (previousHp >= 0 && hp < previousHp)
		{
			invalidateSample();
		}
		if (anchorHp < 0)
		{
			anchorHp = hp;
			anchorEarliest = earliest;
			anchorLatest = latest;
		}
		if (previousHp >= 0 && hp >= previousHp && earliest >= previousEarliest
			&& (hp > previousHp || maximumHp > hp))
		{
			int delta = hp - previousHp;
			boolean[] compatible = new boolean[interval];
			int retained = 0;
			int possible = 0;
			for (int phase = 0; phase < interval; phase++)
			{
				long minimum = Math.max(0, cycles(earliest, phase, interval)
					- cycles(previousLatest, phase, interval));
				long maximum = cycles(latest, phase, interval)
					- cycles(previousEarliest, phase, interval);
				long totalMinimum = Math.max(0, cycles(earliest, phase, interval)
					- cycles(anchorLatest, phase, interval));
				long totalMaximum = cycles(latest, phase, interval)
					- cycles(anchorEarliest, phase, interval);
				int total = hp - anchorHp;
				compatible[phase] = delta >= minimum && delta <= maximum
					&& total >= totalMinimum && total <= totalMaximum;
				if (compatible[phase])
				{
					possible++;
					if (phases[phase])
					{
						retained++;
					}
				}
			}
			if (possible == 0)
			{
				// Different healing mechanics or a wrong interval: discard the
				// contradictory history rather than report spurious precision.
				Arrays.fill(phases, true);
				observedHeal = false;
				anchorHp = hp;
				anchorEarliest = earliest;
				anchorLatest = latest;
			}
			else
			{
				for (int phase = 0; phase < interval; phase++)
				{
					phases[phase] = compatible[phase] && (retained == 0 || phases[phase]);
				}
				observedHeal |= delta > 0;
			}
		}
		previousHp = hp;
		previousEarliest = earliest;
		previousLatest = latest;
		return observedHeal ? enclosingWindow(latest, interval) : null;
	}

	private static long cycles(long tick, int phase, int interval)
	{
		return Math.floorDiv(tick - phase, interval);
	}

	private long[] enclosingWindow(long now, int interval)
	{
		// Remove the largest empty circular gap. If candidates are disjoint,
		// enclose them conservatively instead of choosing one arbitrarily.
		int first = -1;
		int previous = -1;
		int largestGap = -1;
		int endPhase = 0;
		for (int phase = 0; phase < interval; phase++)
		{
			if (!phases[phase])
			{
				continue;
			}
			if (first < 0)
			{
				first = phase;
			}
			else if (phase - previous > largestGap)
			{
				largestGap = phase - previous;
				endPhase = previous;
			}
			previous = phase;
		}
		if (first + interval - previous > largestGap)
		{
			largestGap = first + interval - previous;
			endPhase = previous;
		}
		long end = endPhase + Math.floorDiv(now - endPhase, interval) * interval;
		return new long[]{end - (interval - largestGap), end};
	}
}
