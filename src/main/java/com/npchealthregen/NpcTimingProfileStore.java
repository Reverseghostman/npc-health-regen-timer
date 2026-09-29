package com.npchealthregen;

import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;

final class NpcTimingProfileStore
{
	static final class Profile
	{
		private final int regenTicks;
		private final int respawnTicks;

		Profile(int regenTicks, int respawnTicks)
		{
			this.regenTicks = Math.max(0, regenTicks);
			this.respawnTicks = Math.max(0, respawnTicks);
		}

		int getRegenTicks()
		{
			return regenTicks;
		}

		int getRespawnTicks()
		{
			return respawnTicks;
		}
	}

	private static final String KEY_PREFIX = "learnedNpc_";
	// Kept separate from KEY_PREFIX so the existing "regen,respawn" format is unchanged.
	private static final String DEATH_PAUSE_KEY_PREFIX = "learnedDeathPause_";
	private final ConfigManager configManager;

	@Inject
	private NpcTimingProfileStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	Profile load(int npcId)
	{
		String stored = configManager.getConfiguration(
			NpcHealthRegenConfig.GROUP, KEY_PREFIX + npcId);
		return decode(stored);
	}

	void save(int npcId, int regenTicks, int respawnTicks)
	{
		configManager.setConfiguration(
			NpcHealthRegenConfig.GROUP,
			KEY_PREFIX + npcId,
			Math.max(0, regenTicks) + "," + Math.max(0, respawnTicks));
	}

	/** @return the learned {minimum, maximum} death-pause adjustment, or null if not learned */
	int[] loadDeathPause(int npcId)
	{
		return decodeDeathPause(configManager.getConfiguration(
			NpcHealthRegenConfig.GROUP, DEATH_PAUSE_KEY_PREFIX + npcId));
	}

	void saveDeathPause(int npcId, int minimum, int maximum)
	{
		configManager.setConfiguration(
			NpcHealthRegenConfig.GROUP,
			DEATH_PAUSE_KEY_PREFIX + npcId,
			minimum + "," + maximum);
	}

	static int[] decodeDeathPause(String stored)
	{
		if (stored == null || stored.isEmpty())
		{
			return null;
		}

		String[] parts = stored.split(",", -1);
		if (parts.length != 2)
		{
			return null;
		}

		try
		{
			int minimum = Integer.parseInt(parts[0]);
			int maximum = Integer.parseInt(parts[1]);
			return minimum <= maximum ? new int[]{minimum, maximum} : null;
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	static Profile decode(String stored)
	{
		if (stored == null || stored.isEmpty())
		{
			return new Profile(0, 0);
		}

		String[] parts = stored.split(",", -1);
		if (parts.length != 2)
		{
			return new Profile(0, 0);
		}

		try
		{
			return new Profile(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
		}
		catch (NumberFormatException ignored)
		{
			return new Profile(0, 0);
		}
	}
}
