package com.npchealthregen;

/**
 * Counts down from envenoming the selected NPC to its first venom damage, and
 * the window in which a poison dynamite must be sent to land on the NPC at
 * full health but before that damage.
 *
 * Poison and venom damage run on one timer per NPC that fires every 30 of the
 * NPC's active ticks. Envenoming switches the timer on without restarting it,
 * so the first hit lands (30 - ticks already counted) after the venom is
 * applied. That count is zero on an NPC that has never been poisoned, or one
 * killed by a poison or venom hit; after another killing blow it is the ticks
 * since the last poison hit before the death.
 */
final class VenomRing
{
	enum Status
	{
		NONE,
		COUNTING,
		PROCCED,
		NO_VENOM
	}

	static final int TIMER_TICKS = 30;
	private static final int RESULT_DISPLAY_TICKS = 5;
	// Allow the first venom hit a little late before calling it a failure.
	private static final int PROC_GRACE_TICKS = 2;

	private Status status = Status.NONE;
	private boolean shared;
	private long appliedTick = -1;
	private long procEarliest = -1;
	private long procLatest = -1;
	private long fullHpTick = -1;
	private long resolvedTick = -1;
	private long lastPoisonHitTick = -1;
	// Timer ticks already counted, carried into the NPC's next life.
	private int clockMin;
	private int clockMax;
	private int freshDelay = TIMER_TICKS;
	private int lastObservedDelay = -1;

	void reset()
	{
		status = Status.NONE;
		shared = false;
		lastPoisonHitTick = -1;
		clockMin = 0;
		clockMax = 0;
		lastObservedDelay = -1;
	}

	/** Uses a first-hit delay measured on an earlier NPC of the same type. */
	void setFreshDelay(int ticks)
	{
		freshDelay = ticks;
	}

	int getFreshDelay()
	{
		return freshDelay;
	}

	/** @return whether poison or venom is already ticking on the NPC */
	boolean isNpcPoisoned(long now)
	{
		return status == Status.COUNTING
			|| (lastPoisonHitTick >= 0 && now - lastPoisonHitTick <= TIMER_TICKS);
	}

	/**
	 * Starts the countdown for venom applied by this client's hit.
	 * @param fullHpTick the latest tick by which regen restores the hit's
	 * damage, or -1 if unknown
	 * @return false if the NPC was already poisoned or envenomed
	 */
	boolean start(long appliedTick, long fullHpTick)
	{
		if (isNpcPoisoned(appliedTick))
		{
			return false;
		}

		this.appliedTick = appliedTick;
		this.fullHpTick = fullHpTick;
		procEarliest = appliedTick + freshDelay - clockMax;
		procLatest = appliedTick + freshDelay - clockMin;
		status = Status.COUNTING;
		shared = false;
		return true;
	}

	/** Starts or refreshes the countdown from a party member's venom hit. */
	void startShared(long appliedTick, long procEarliest, long procLatest, long fullHpTick)
	{
		if (status == Status.COUNTING && !shared)
		{
			// This client's own hit is first-hand.
			return;
		}

		this.appliedTick = appliedTick;
		this.procEarliest = procEarliest;
		this.procLatest = procLatest;
		this.fullHpTick = fullHpTick;
		status = Status.COUNTING;
		shared = true;
	}

	void onPoisonHit(long tick, boolean venom)
	{
		if (status == Status.COUNTING && venom)
		{
			status = Status.PROCCED;
			resolvedTick = tick;
			lastObservedDelay = (int) (tick - appliedTick);
			if (!shared && clockMin == clockMax)
			{
				int delay = lastObservedDelay + clockMin;
				if (Math.abs(delay - TIMER_TICKS) <= 3)
				{
					freshDelay = delay;
				}
			}
		}
		lastPoisonHitTick = tick;
	}

	void onTick(long now)
	{
		if (status == Status.COUNTING && now > procLatest + PROC_GRACE_TICKS)
		{
			status = Status.NO_VENOM;
			resolvedTick = now;
		}
		else if ((status == Status.PROCCED || status == Status.NO_VENOM)
			&& now - resolvedTick >= RESULT_DISPLAY_TICKS)
		{
			status = Status.NONE;
		}
	}

	void onDeath(long tick)
	{
		if (lastPoisonHitTick >= 0)
		{
			long sinceHit = tick - lastPoisonHitTick;
			if (sinceHit <= 0 || sinceHit >= TIMER_TICKS)
			{
				// Killed by the timer's own hit, or the poison had worn off.
				clockMin = 0;
				clockMax = 0;
			}
			else
			{
				// It may count once more after the killing blow, like regen.
				clockMin = (int) sinceHit;
				clockMax = (int) Math.min(TIMER_TICKS - 1, sinceHit + 1);
			}
		}
		status = Status.NONE;
		lastPoisonHitTick = -1;
	}

	void cancel()
	{
		status = Status.NONE;
	}

	Status getStatus()
	{
		return status;
	}

	boolean isShared()
	{
		return shared;
	}

	long getAppliedTick()
	{
		return appliedTick;
	}

	long getProcEarliest()
	{
		return procEarliest;
	}

	long getProcLatest()
	{
		return procLatest;
	}

	long getFullHpTick()
	{
		return fullHpTick;
	}

	int getLastObservedDelay()
	{
		return lastObservedDelay;
	}

	/** @return fraction of the way from the venom hit to its latest first damage */
	double getProgress(long now)
	{
		long span = Math.max(1, procLatest - appliedTick);
		return Math.max(0, Math.min(1, (double) (now - appliedTick) / span));
	}

	/**
	 * The ticks in which to send dynamite that takes leadTicks to land so it
	 * lands at full health (regen runs before player hits in a tick) and before
	 * the earliest venom damage (which also runs first).
	 * @return {first, last} send tick, or null when not counting
	 */
	long[] getDynamiteWindow(int leadTicks)
	{
		if (status != Status.COUNTING)
		{
			return null;
		}
		long first = (fullHpTick >= 0 ? fullHpTick : appliedTick) - leadTicks;
		long last = procEarliest - 1 - leadTicks;
		return new long[]{first, last};
	}
}
