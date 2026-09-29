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
import net.runelite.client.ui.overlay.components.ProgressPieComponent;

final class NpcHealthRegenSceneOverlay extends Overlay
{
	private static final Color WARNING = new Color(255, 170, 0);
	private static final Color VENOM_GREEN = new Color(60, 200, 90);
	private static final Color LATE_RED = new Color(230, 60, 60);
	private static final int RING_DIAMETER = 24;
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
		Shape highlight = highlightShape(target, config.npcHighlight());
		if (highlight != null)
		{
			Color border = config.highlightColour();
			Color fill = new Color(border.getRed(), border.getGreen(), border.getBlue(),
				border.getAlpha() * 35 / 255);
			renderShape(graphics, highlight, border, fill);
		}

		renderVenomRing(graphics, target);

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

	private void renderVenomRing(Graphics2D graphics, NPC target)
	{
		VenomRing ring = plugin.getVenomRing();
		if (!config.showVenomRing() || ring.getStatus() == VenomRing.Status.NONE)
		{
			return;
		}

		// Above the overhead regen countdown.
		Point centre = target.getCanvasTextLocation(graphics, "", target.getLogicalHeight() + 110);
		if (centre == null)
		{
			return;
		}

		RingState state = ringState(ring, plugin.getTick(), plugin.getDynamiteDelayTicks());
		ProgressPieComponent pie = new ProgressPieComponent();
		pie.setPosition(centre);
		pie.setDiameter(RING_DIAMETER);
		pie.setProgress(state.progress);
		pie.setBorderColor(state.colour);
		pie.setFill(new Color(state.colour.getRed(), state.colour.getGreen(), state.colour.getBlue(), 110));
		pie.render(graphics);

		int textWidth = graphics.getFontMetrics().stringWidth(state.label);
		int textHeight = graphics.getFontMetrics().getAscent();
		OverlayUtil.renderTextLocation(graphics,
			new Point(centre.getX() - textWidth / 2, centre.getY() + RING_DIAMETER / 2 + textHeight + 2),
			state.label, state.colour);
	}

	static final class RingState
	{
		final double progress;
		final Color colour;
		final String label;

		RingState(double progress, Color colour, String label)
		{
			this.progress = progress;
			this.colour = colour;
			this.label = label;
		}
	}

	/** What the venom ring shows: wait, send the dynamite, too late, or the result. */
	static RingState ringState(VenomRing ring, long now, int dynamiteDelayTicks)
	{
		switch (ring.getStatus())
		{
			case PROCCED:
				return new RingState(1, VENOM_GREEN, "Venom!");
			case NO_VENOM:
				return new RingState(1, LATE_RED, "No venom");
			default:
				break;
		}

		double progress = ring.getProgress(now);
		long[] window = ring.getDynamiteWindow(dynamiteDelayTicks);
		if (window == null || window[1] < window[0])
		{
			return new RingState(progress, LATE_RED, "No window");
		}
		if (now < window[0])
		{
			return new RingState(progress, WARNING, "Wait " + (window[0] - now) + "t");
		}
		if (now <= window[1])
		{
			return new RingState(progress, VENOM_GREEN, "Send " + (window[1] - now) + "t");
		}
		return new RingState(progress, LATE_RED, "Too late");
	}

	private Shape highlightShape(NPC target, NpcHealthRegenConfig.NpcHighlight style)
	{
		switch (style)
		{
			case TILE:
				// Follows the model smoothly as it walks, covering its full size.
				return target.getCanvasTilePoly();
			case TRUE_TILE:
			{
				int size = target.getTransformedComposition() == null
					? 1 : target.getTransformedComposition().getSize();
				return tileArea(target.getWorldLocation(), size);
			}
			case HULL:
				return target.getConvexHull();
			default:
				return null;
		}
	}

	/** The on-screen area of a size x size NPC whose south-west tile is worldPoint. */
	private Polygon tileArea(WorldPoint worldPoint, int size)
	{
		if (worldPoint == null)
		{
			return null;
		}

		LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
		if (localPoint == null)
		{
			return null;
		}

		LocalPoint centre = areaCentre(localPoint, size);
		return Perspective.getCanvasTileAreaPoly(client, centre, Math.max(1, size));
	}

	private static LocalPoint areaCentre(LocalPoint southWest, int size)
	{
		int offset = Perspective.LOCAL_TILE_SIZE * (Math.max(1, size) - 1) / 2;
		return southWest.plus(offset, offset);
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
		LocalPoint centre = areaCentre(localPoint, size);
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

	static String formatRegenCountdown(RegenTimer.Window window)
	{
		long earliest = window.getEarliestTicks();
		long latest = window.getLatestTicks();
		if (earliest == 0)
		{
			return latest == 0 ? "Regen: ~NOW" : "Regen: ~NOW-" + latest + "t";
		}
		return earliest == latest
			? "Regen: ~" + earliest + "t"
			: "Regen: ~" + earliest + "-" + latest + "t";
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
