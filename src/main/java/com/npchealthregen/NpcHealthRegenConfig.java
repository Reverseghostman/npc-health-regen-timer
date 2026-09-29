package com.npchealthregen;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(NpcHealthRegenConfig.GROUP)
public interface NpcHealthRegenConfig extends Config
{
	String GROUP = "npc-health-regen-timer";

	enum NpcHighlight
	{
		OFF("Off"),
		TILE("Tile"),
		TRUE_TILE("True tile"),
		HULL("Hull");

		private final String name;

		NpcHighlight(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	// Standard options stay at the top; everything else is in closed sections.

	@ConfigSection(
		name = "Overlay details",
		description = "Choose which extra rows the information panel shows",
		position = 10,
		closedByDefault = true
	)
	String OVERLAY_SECTION = "overlayDetails";

	@ConfigSection(
		name = "Hotkeys",
		description = "Manual marking and reset hotkeys",
		position = 20,
		closedByDefault = true
	)
	String HOTKEY_SECTION = "hotkeys";

	@ConfigSection(
		name = "Venom & dynamite",
		description = "Venom ring and poison dynamite timing",
		position = 15,
		closedByDefault = true
	)
	String VENOM_SECTION = "venom";

	@ConfigSection(
		name = "Party",
		description = "Share regen timers with RuneLite party members tracking the same NPC",
		position = 25,
		closedByDefault = true
	)
	String PARTY_SECTION = "party";

	@ConfigSection(
		name = "Timing (advanced)",
		description = "Fallback timings and how observations are interpreted",
		position = 30,
		closedByDefault = true
	)
	String TIMING_SECTION = "timing";

	@ConfigItem(
		keyName = "npcHighlight",
		name = "Highlight selected NPC",
		description = "Mark the selected NPC's tile (smoothly following its model or on its true server tile), "
			+ "outline its hull, or turn the highlight off",
		position = 0
	)
	default NpcHighlight npcHighlight()
	{
		return NpcHighlight.TILE;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightColour",
		name = "Highlight colour",
		description = "Border colour of the selected NPC's highlight. Its fill uses the same colour, mostly transparent",
		position = 1
	)
	default Color highlightColour()
	{
		return new Color(80, 220, 120);
	}

	@ConfigItem(
		keyName = "showOverheadRegenCountdown",
		name = "Overhead regen countdown",
		description = "Show the regeneration tick countdown above the selected NPC",
		position = 2
	)
	default boolean showOverheadRegenCountdown()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRespawnTile",
		name = "Show respawn tile",
		description = "Highlight the selected NPC's recorded spawn tile and count down there while it is dead",
		position = 3
	)
	default boolean showRespawnTile()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showSeconds",
		name = "Show seconds",
		description = "Show approximate seconds alongside game ticks",
		position = 4
	)
	default boolean showSeconds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNpcName",
		name = "NPC name",
		description = "Show the tracked NPC's name in the overlay",
		position = 0,
		section = OVERLAY_SECTION
	)
	default boolean showNpcName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRegenRate",
		name = "Regen rate",
		description = "Show the active health regeneration interval",
		position = 1,
		section = OVERLAY_SECTION
	)
	default boolean showRegenRate()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInspectedStats",
		name = "Inspected stats",
		description = "Show the latest Hitpoints and Defence values read from Monster Inspect/Examine",
		position = 2,
		section = OVERLAY_SECTION
	)
	default boolean showInspectedStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRespawnTime",
		name = "Respawn rate",
		description = "Show the learned or fallback NPC respawn rate while it is alive",
		position = 3,
		section = OVERLAY_SECTION
	)
	default boolean showRespawnTime()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showCurrentHealth",
		name = "Current HP",
		description = "Show exact inspected Hitpoints, or the possible range calculated from the health bar",
		position = 4,
		section = OVERLAY_SECTION
	)
	default boolean showCurrentHealth()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showTimeUntilFullHp",
		name = "Time until full HP",
		description = "Estimate when the NPC will reach full Hitpoints using its current HP and regeneration timing",
		position = 5,
		section = OVERLAY_SECTION
	)
	default boolean showTimeUntilFullHp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showDefenceAtFullHp",
		name = "Defence at full HP",
		description = "Estimate Defence when HP becomes full, assuming Defence restores by one on each HP regeneration cycle",
		position = 6,
		section = OVERLAY_SECTION
	)
	default boolean showDefenceAtFullHp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showBaseStats",
		name = "Reference stats",
		description = "Show available maximum Hitpoints and highest inspected Defence",
		position = 7,
		section = OVERLAY_SECTION
	)
	default boolean showBaseStats()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showObservationSource",
		name = "Observation source",
		description = "Show which health bar and Monster Inspect/Examine observations are available",
		position = 8,
		section = OVERLAY_SECTION
	)
	default boolean showObservationSource()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showTickCounter",
		name = "Tick counter",
		description = "Show the plugin's running game tick counter",
		position = 9,
		section = OVERLAY_SECTION
	)
	default boolean showTickCounter()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "warningTicks",
		name = "Warning colour from",
		description = "Turn the countdown orange this many ticks before the regeneration window",
		position = 10,
		section = OVERLAY_SECTION
	)
	default int warningTicks()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "markRegenHotkey",
		name = "Mark regeneration now",
		description = "Manually set the current game tick as the NPC's regeneration tick",
		position = 0,
		section = HOTKEY_SECTION
	)
	default Keybind markRegenHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "resetHotkey",
		name = "Reset timer",
		description = "Clear the current NPC's saved regen rate and phase, restore the default interval and observe again",
		position = 1,
		section = HOTKEY_SECTION
	)
	default Keybind resetHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "showVenomRing",
		name = "Venom ring",
		description = "After you envenom the selected NPC (a successful hit wearing a serpentine helm with a venom "
			+ "weapon), count down to its first venom damage and show when to send poison dynamite. Party "
			+ "members tracking the same NPC see it too",
		position = 0,
		section = VENOM_SECTION
	)
	default boolean showVenomRing()
	{
		return true;
	}

	@Range(min = 0, max = 10)
	@ConfigItem(
		keyName = "dynamiteDelayTicks",
		name = "Dynamite delay",
		description = "Ticks from using poison dynamite on the NPC to its hit. Measured automatically the first "
			+ "time you use Dynamite(p) on the selected NPC",
		position = 1,
		section = VENOM_SECTION
	)
	default int dynamiteDelayTicks()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "shareWithParty",
		name = "Share my timer",
		description = "Send your selected NPC's regen window to your RuneLite party. Only party members on the "
			+ "same world tracking the same NPC use it",
		position = 0,
		section = PARTY_SECTION
	)
	default boolean shareWithParty()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useSharedTimers",
		name = "Use party timers",
		description = "Refine or fill in your timer from party members tracking the same NPC. Your own "
			+ "observations take priority when they disagree",
		position = 1,
		section = PARTY_SECTION
	)
	default boolean useSharedTimers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPartySource",
		name = "Show who shared it",
		description = "Show the party member whose timer you are using, until your own observations take over",
		position = 2,
		section = PARTY_SECTION
	)
	default boolean showPartySource()
	{
		return true;
	}

	@ConfigItem(
		keyName = "learnNpcTimings",
		name = "Learn NPC timings",
		description = "Remember learned regeneration and respawn timings for each NPC type across RuneLite restarts",
		position = 0,
		section = TIMING_SECTION
	)
	default boolean learnNpcTimings()
	{
		return true;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "regenTicks",
		name = "Default regen interval",
		description = "Fallback game ticks between heals until this NPC's interval is learned",
		position = 1,
		section = TIMING_SECTION
	)
	default int regenTicks()
	{
		return 100;
	}

	@Range(min = 0, max = 10000)
	@ConfigItem(
		keyName = "respawnTicks",
		name = "Default respawn time",
		description = "Fallback respawn time in ticks until this NPC's time is learned. Set to 0 if unknown",
		position = 2,
		section = TIMING_SECTION
	)
	default int respawnTicks()
	{
		return 0;
	}

	@Range(min = 0, max = 10)
	@ConfigItem(
		keyName = "observationDelayTicks",
		name = "Splash weapon delay",
		description = "Maximum delay between an NPC heal and seeing it during splashing. Usually the weapon's "
			+ "attack speed minus one; use 3 for a 4-tick weapon",
		position = 3,
		section = TIMING_SECTION
	)
	default int observationDelayTicks()
	{
		return 3;
	}
}
