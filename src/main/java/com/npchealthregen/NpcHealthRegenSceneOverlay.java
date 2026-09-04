package com.npchealthregen;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

final class NpcHealthRegenSceneOverlay extends Overlay
{
	private static final Color TARGET_BORDER = new Color(80, 220, 120);
	private static final Color TARGET_FILL = new Color(80, 220, 120, 35);
	private static final Color WARNING = new Color(255, 170, 0);
	private static final Color RESPAWN_FILL = new Color(255, 170, 0, 35);
	private static final Color TEXT = Color.WHITE;

	private final Client client;
	private final NpcHealthRegenPlugin plugin;
	private final NpcHealthRegenConfig config;

	@Inject
	private NpcHealthRegenSceneOverlay(
		Client client,
		NpcHealthRegenPlugin plugin,
		NpcHealthRegenConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		NPC target = plugin.getTarget();
		if (target != null)
		{
			renderTarget(graphics, target);
		}
		else if (config.showRespawnTile()
			&& plugin.getTimer().getState() == RegenTimer.State.WAITING_FOR_RESPAWN)
		{
			renderRespawnTile(graphics);
		}
		return null;
	}

	private void renderTarget(Graphics2D graphics, NPC target)
	{
		if (config.highlightSelectedNpc())
		{
			Shape hull = target.getConvexHull();
			if (hull == null)
			{
				hull = target.getCanvasTilePoly();
			}
			renderShape(graphics, hull, TARGET_BORDER, TARGET_FILL);
		}

		if (!config.showOverheadRegenCountdown())
		{
			return;
		}

		RegenTimer.Window window = plugin.getTimer().getUpcomingWindow(
			plugin.getTick(), plugin.getActiveRegenTicks());
		if (window == null)
		{
			return;
		}

		String text = formatRegenCountdown(window);
		Point location = target.getCanvasTextLocation(
			graphics, text, target.getLogicalHeight() + 40);
		if (location != null)
		{
			Color colour = window.getEarliestTicks() <= config.warningTicks()
				? WARNING : TEXT;
			OverlayUtil.renderTextLocation(graphics, location, text, colour);
		}
	}

	private void renderRespawnTile(Graphics2D graphics)
	{
		WorldPoint worldPoint = plugin.getLastTargetPoint();
		if (worldPoint == null)
		{
			return;
		}

		LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
		if (localPoint == null)
		{
			return;
		}

		int size = Math.max(1, plugin.getLastTargetSize());
		LocalPoint centre = localPoint.plus(
			Perspective.LOCAL_TILE_SIZE * (size - 1) / 2,
			Perspective.LOCAL_TILE_SIZE * (size - 1) / 2);
		Polygon polygon = Perspective.getCanvasTileAreaPoly(client, centre, size);
		renderShape(graphics, polygon, WARNING, RESPAWN_FILL);

		long remaining = plugin.getTimer().getRespawnTicksRemaining(plugin.getTick());
		String text = remaining >= 0
			? "Respawn: " + remaining + "t"
			: "Dead: " + plugin.getTimer().getRespawnTicksElapsed(plugin.getTick()) + "t";
		Point canvasPoint = Perspective.localToCanvas(client, centre, worldPoint.getPlane());
		if (canvasPoint == null)
		{
			return;
		}

		int textWidth = graphics.getFontMetrics().stringWidth(text);
		int textHeight = graphics.getFontMetrics().getAscent();
		OverlayUtil.renderTextLocation(graphics,
			new Point(canvasPoint.getX() - textWidth / 2, canvasPoint.getY() + textHeight / 2),
			text, TEXT);
	}

	private static String formatRegenCountdown(RegenTimer.Window window)
	{
		long earliest = window.getEarliestTicks();
		long latest = window.getLatestTicks();
		if (earliest == 0)
		{
			return latest == 0 ? "Regen: NOW" : "Regen: NOW-" + latest + "t";
		}
		return earliest == latest
			? "Regen: " + earliest + "t"
			: "Regen: " + earliest + "-" + latest + "t";
	}

	private static void renderShape(
		Graphics2D graphics,
		Shape shape,
		Color border,
		Color fill)
	{
		if (shape == null)
		{
			return;
		}
		graphics.setStroke(new BasicStroke(2.0f));
		graphics.setColor(border);
		graphics.draw(shape);
		graphics.setColor(fill);
		graphics.fill(shape);
	}
}
