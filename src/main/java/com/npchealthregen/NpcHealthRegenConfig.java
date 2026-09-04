package com.npchealthregen;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(NpcHealthRegenConfig.GROUP)
public interface NpcHealthRegenConfig extends Config
{
	String GROUP = "npc-health-regen-timer";

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "regenTicks",
		name = "Default regen interval",
		description = "Fallback game ticks between heals until this NPC's interval is learned"
	)
	default int regenTicks()
	{
		return 100;
	}

	@Range(min = 0, max = 10000)
	@ConfigItem(
		keyName = "respawnTicks",
		name = "Default respawn time",
		description = "Fallback respawn time in ticks until this NPC's time is learned. Set to 0 if unknown"
	)
	default int respawnTicks()
	{
		return 0;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "warningTicks",
		name = "Warning colour from",
		description = "Turn the countdown orange this many ticks before the regeneration window"
	)
	default int warningTicks()
	{
		return 8;
	}

	@Range(min = 0, max = 10)
	@ConfigItem(
		keyName = "observationDelayTicks",
		name = "Splash weapon delay",
		description = "Maximum delay between an NPC heal and seeing it during splashing. Usually the weapon's "
			+ "attack speed minus one; use 3 for a 4-tick weapon"
	)
	default int observationDelayTicks()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "learnNpcTimings",
		name = "Learn NPC timings",
		description = "Remember learned regeneration and respawn timings for each NPC type across RuneLite restarts"
	)
	default boolean learnNpcTimings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSeconds",
		name = "Show seconds",
		description = "Show approximate seconds alongside game ticks"
	)
	default boolean showSeconds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNpcName",
		name = "Show NPC name",
		description = "Show the tracked NPC's name in the overlay"
	)
	default boolean showNpcName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRegenRate",
		name = "Show regen rate",
		description = "Show the active health regeneration interval"
	)
	default boolean showRegenRate()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRespawnTime",
		name = "Show respawn rate",
		description = "Show the learned or fallback NPC respawn rate while it is alive"
	)
	default boolean showRespawnTime()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showTickCounter",
		name = "Show tick counter",
		description = "Show the plugin's running game tick counter"
	)
	default boolean showTickCounter()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showObservationSource",
		name = "Show observation source",
		description = "Show which health bar and Monster Inspect/Examine observations are available"
	)
	default boolean showObservationSource()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showInspectedStats",
		name = "Show inspected stats",
		description = "Show the latest Hitpoints and Defence values read from Monster Inspect/Examine"
	)
	default boolean showInspectedStats()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showBaseStats",
		name = "Show reference stats",
		description = "Show available maximum Hitpoints and highest inspected Defence"
	)
	default boolean showBaseStats()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showCurrentHealth",
		name = "Show current HP",
		description = "Show exact inspected Hitpoints, or the possible range calculated from the health bar"
	)
	default boolean showCurrentHealth()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showTimeUntilFullHp",
		name = "Show time until full HP",
		description = "Estimate when the NPC will reach full Hitpoints using its current HP and regeneration timing"
	)
	default boolean showTimeUntilFullHp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showDefenceAtFullHp",
		name = "Defence at full HP",
		description = "Estimate Defence when HP becomes full, assuming Defence restores by one on each HP regeneration cycle"
	)
	default boolean showDefenceAtFullHp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightSelectedNpc",
		name = "Highlight selected NPC",
		description = "Highlight the manually selected NPC in the game scene"
	)
	default boolean highlightSelectedNpc()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showOverheadRegenCountdown",
		name = "Overhead regen countdown",
		description = "Show the regeneration tick countdown above the selected NPC"
	)
	default boolean showOverheadRegenCountdown()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRespawnTile",
		name = "Show respawn tile",
		description = "Highlight the selected NPC's recorded spawn tile and count down there while it is dead"
	)
	default boolean showRespawnTile()
	{
		return false;
	}

	@ConfigItem(
		keyName = "markRegenHotkey",
		name = "Mark regeneration now",
		description = "Manually set the current game tick as the NPC's regeneration tick"
	)
	default Keybind markRegenHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "resetHotkey",
		name = "Reset timer",
		description = "Clear calibration for the current NPC and begin observing again"
	)
	default Keybind resetHotkey()
	{
		return Keybind.NOT_SET;
	}
}
