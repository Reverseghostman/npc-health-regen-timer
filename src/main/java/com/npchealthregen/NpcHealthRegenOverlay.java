package com.npchealthregen;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class NpcHealthRegenOverlay extends OverlayPanel
{
	private static final Color WARNING = new Color(255, 170, 0);
	private static final Color WINDOW_NOW = new Color(255, 80, 80);
	private static final Color LEARNED = new Color(80, 220, 120);

	private final NpcHealthRegenPlugin plugin;
	private final NpcHealthRegenConfig config;

	@Inject
	private NpcHealthRegenOverlay(NpcHealthRegenPlugin plugin, NpcHealthRegenConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin.getTargetName() == null)
		{
			return null;
		}

		RegenTimer timer = plugin.getTimer();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("NPC Health Regen")
			.build());

		if (config.showNpcName())
		{
			addLine("Target", plugin.getTargetName(), Color.WHITE);
		}
		if (config.showTickCounter())
		{
			addLine("Game tick", Long.toString(plugin.getTick()), Color.WHITE);
		}
		if (config.showObservationSource())
		{
			addLine("Observation", plugin.getObservationSourceLabel(), Color.WHITE);
		}
		if (config.showInspectedStats() && plugin.getLastInspectedHitpoints() >= 0)
		{
			addLine("Inspected HP", Integer.toString(plugin.getLastInspectedHitpoints()), Color.WHITE);
			if (plugin.getLastInspectedDefence() >= 0)
			{
				addLine("Inspected Defence", Integer.toString(plugin.getLastInspectedDefence()), Color.WHITE);
			}
		}
		if (config.showBaseStats())
		{
			if (plugin.getMaximumHitpoints() > 0)
			{
				addLine("Maximum HP", Integer.toString(plugin.getMaximumHitpoints()), Color.WHITE);
			}
			if (plugin.getBaseDefence() > 0)
			{
				addLine("Highest inspected Defence",
					Integer.toString(plugin.getBaseDefence()), Color.WHITE);
			}
		}

		RecoveryCalculator.Range currentHitpoints = plugin.getCurrentHitpoints();
		if (config.showCurrentHealth() && currentHitpoints != null)
		{
			String estimate = plugin.isCurrentHitpointsExact() ? "" : "~";
			addLine("Current HP", estimate + formatRange(currentHitpoints), Color.WHITE);
		}
		if (config.showDefenceAtFullHp())
		{
			RecoveryCalculator.Range defence = plugin.getDefenceAtFullHitpoints();
			if (defence != null)
			{
				addLine("Defence at full HP", "~" + formatRange(defence), Color.WHITE);
			}
		}
		if (config.showRegenRate())
		{
			String source = plugin.isLearnedRegen() ? " (learned)" : " (default)";
			addLine("Regen rate", formatRate(plugin.getActiveRegenTicks()) + source,
				plugin.isLearnedRegen() ? LEARNED : Color.WHITE);
		}

		if (timer.getState() == RegenTimer.State.WAITING_FOR_RESPAWN)
		{
			addRespawnCountdown(timer);
		}
		if (config.showRespawnTime())
		{
			addRespawnRate(timer);
		}

		RegenTimer.Window window = timer.getUpcomingWindow(
			plugin.getTick(), plugin.getActiveRegenTicks());
		if (window == null)
		{
			if (config.showTimeUntilFullHp() && currentHitpoints != null)
			{
				boolean full = currentHitpoints.getMinimum() >= plugin.getMaximumHitpoints();
				addLine("Full HP in", full ? "0.0s" : "Need first heal",
					full ? LEARNED : WARNING);
			}
			String status = timer.getState() == RegenTimer.State.WAITING_FOR_RESPAWN
				? "Waiting for respawn"
				: plugin.isUsingMonsterInspection()
					? "Cast Inspect/Examine again"
					: "Keep splashing to find regen";
			addLine("Status", status, Color.WHITE);
			return super.render(graphics);
		}

		long earliest = window.getEarliestTicks();
		long latest = window.getLatestTicks();
		Color colour = earliest == 0 ? WINDOW_NOW
			: earliest <= config.warningTicks() ? WARNING : Color.WHITE;
		String label = earliest == 0 ? "Regen window" : "Next regen";
		String value = earliest == 0
			? (latest == 0 ? "NOW" : "NOW - " + formatTicks(latest))
			: formatWindow(earliest, latest);
		addLine(label, value, colour);

		if (config.showTimeUntilFullHp())
		{
			RecoveryCalculator.TickWindow fullHp = plugin.getTimeUntilFullHitpoints();
			if (fullHp != null)
			{
				String estimate = fullHp.getLatest() == 0 ? "" : "~";
				addLine("Full HP in", estimate + formatDurationWindow(fullHp),
					fullHp.getEarliest() == 0 ? LEARNED : Color.WHITE);
			}
		}

		return super.render(graphics);
	}

	private void addRespawnCountdown(RegenTimer timer)
	{
		long remaining = timer.getRespawnTicksRemaining(plugin.getTick());
		if (remaining >= 0)
		{
			addLine("Respawn in", formatTicks(remaining), remaining == 0 ? WINDOW_NOW : WARNING);
		}
		else
		{
			addLine("Dead for", formatTicks(timer.getRespawnTicksElapsed(plugin.getTick())), WARNING);
		}
	}

	private void addRespawnRate(RegenTimer timer)
	{
		if (plugin.getActiveRespawnTicks() > 0)
		{
			String source = plugin.isLearnedRespawn() ? " (learned)" : " (default)";
			addLine("Respawn rate", formatRate(plugin.getActiveRespawnTicks()) + source,
				plugin.isLearnedRespawn() ? LEARNED : Color.WHITE);
		}
		else if (timer.getState() != RegenTimer.State.WAITING_FOR_RESPAWN)
		{
			addLine("Respawn rate", "Not learned yet", Color.WHITE);
		}
	}

	private void addLine(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.rightColor(rightColor)
			.build());
	}

	private String formatWindow(long earliest, long latest)
	{
		if (earliest == latest)
		{
			return formatTicks(earliest);
		}

		String ticks = earliest + "-" + latest + " ticks";
		if (!config.showSeconds())
		{
			return ticks;
		}
		return ticks + " (" + formatSeconds(earliest) + "-" + formatSeconds(latest) + "s)";
	}

	private String formatTicks(long ticks)
	{
		String value = ticks + (ticks == 1 ? " tick" : " ticks");
		return config.showSeconds() ? value + " (" + formatSeconds(ticks) + "s)" : value;
	}

	private String formatRate(int ticks)
	{
		return config.showSeconds()
			? ticks + "t / " + formatSeconds(ticks) + "s"
			: ticks + " ticks";
	}

	private static String formatRange(RecoveryCalculator.Range range)
	{
		return range.getMinimum() == range.getMaximum()
			? Integer.toString(range.getMinimum())
			: range.getMinimum() + "-" + range.getMaximum();
	}

	private static String formatDurationWindow(RecoveryCalculator.TickWindow window)
	{
		if (window.getEarliest() == window.getLatest())
		{
			return formatDuration(window.getEarliest());
		}
		return formatDuration(window.getEarliest()) + "-" + formatDuration(window.getLatest());
	}

	private static String formatDuration(long ticks)
	{
		long tenths = Math.max(0, ticks) * 6L;
		long minutes = tenths / 600L;
		long seconds = (tenths % 600L) / 10L;
		long decimal = tenths % 10L;
		return minutes > 0
			? String.format(Locale.ENGLISH, "%dm %d.%ds", minutes, seconds, decimal)
			: String.format(Locale.ENGLISH, "%d.%ds", seconds, decimal);
	}

	private static String formatSeconds(long ticks)
	{
		return String.format(Locale.ENGLISH, "%.1f", ticks * 0.6d);
	}
}
