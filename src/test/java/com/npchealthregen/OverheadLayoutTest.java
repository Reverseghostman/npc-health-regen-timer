package com.npchealthregen;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OverheadLayoutTest
{
	private static final int ASCENT = 11;
	private static final int DESCENT = 3;
	private static final int RING = 24;
	private static final int REGEN_WIDTH = 82;
	private static final int LABEL_WIDTH = 50;
	private static final int SIDE = OverheadLayout.DEFAULT_SIDE_OFFSET;

	private static OverheadLayout.Layout layout(OverheadLayout.Placement placement, boolean regen, boolean ring)
	{
		return OverheadLayout.layout(0, 0, placement, SIDE, ASCENT, DESCENT,
			regen ? REGEN_WIDTH : 0, ring ? RING : 0, LABEL_WIDTH);
	}

	private static Rectangle text(int x, int baseline, int width)
	{
		return new Rectangle(x, baseline - ASCENT, width, ASCENT + DESCENT);
	}

	/** The rectangles our overhead items cover. */
	private static List<Rectangle> ours(OverheadLayout.Layout l, boolean regen, boolean ring)
	{
		List<Rectangle> rectangles = new ArrayList<>();
		if (regen)
		{
			rectangles.add(text(l.regenX, l.regenBaselineY, REGEN_WIDTH));
		}
		if (ring)
		{
			rectangles.add(text(l.labelX, l.labelBaselineY, LABEL_WIDTH));
			rectangles.add(new Rectangle(l.ringCentreX - RING / 2, l.ringCentreY - RING / 2, RING, RING));
		}
		return rectangles;
	}

	@Test
	public void ourOwnRowsNeverOverlapInAnyPlacement()
	{
		for (OverheadLayout.Placement placement : OverheadLayout.Placement.values())
		{
			OverheadLayout.Layout l = layout(placement, true, true);
			List<Rectangle> rows = ours(l, true, true);
			for (int i = 0; i < rows.size(); i++)
			{
				for (int j = i + 1; j < rows.size(); j++)
				{
					assertFalse(placement + " rows " + i + " and " + j + " overlap",
						rows.get(i).intersects(rows.get(j)));
				}
			}
		}
	}

	@Test
	public void regenTextStaysOnTheAnchorAsBefore()
	{
		OverheadLayout.Layout l = layout(OverheadLayout.Placement.ABOVE, true, false);

		// Centred on the anchor with its baseline on it, as the single overhead line was.
		assertEquals(-REGEN_WIDTH / 2, l.regenX);
		assertEquals(0, l.regenBaselineY);
	}

	@Test
	public void venomLabelIsOnTheAnchorWhenThereIsNoRegenText()
	{
		OverheadLayout.Layout l = layout(OverheadLayout.Placement.ABOVE, false, true);

		assertEquals(0, l.labelBaselineY);
		assertEquals(0, l.ringCentreX);
		assertTrue(l.ringCentreY < l.labelBaselineY - ASCENT);
	}

	@Test
	public void sidePlacementsKeepTheNearEdgeTheConfiguredDistanceFromTheAnchor()
	{
		OverheadLayout.Layout right = layout(OverheadLayout.Placement.RIGHT, true, true);
		assertEquals(SIDE, right.regenX);
		assertEquals(SIDE, right.labelX);
		assertEquals(SIDE, right.ringCentreX - RING / 2);

		OverheadLayout.Layout left = layout(OverheadLayout.Placement.LEFT, true, true);
		assertEquals(-SIDE, left.regenX + REGEN_WIDTH);
		assertEquals(-SIDE, left.labelX + LABEL_WIDTH);
		assertEquals(-SIDE, left.ringCentreX + RING / 2);
	}

	@Test
	public void negativeSideOffsetIsTreatedAsZero()
	{
		OverheadLayout.Layout l = OverheadLayout.layout(0, 0, OverheadLayout.Placement.RIGHT, -5,
			ASCENT, DESCENT, REGEN_WIDTH, 0, 0);

		assertEquals(0, l.regenX);
	}

	/**
	 * What the other plugins draw around the same anchor, from their source:
	 * - Poison Dynamite: a 30px ring centred on the anchor (getCanvasTextLocation at
	 *   logicalHeight + 40, RING_DIAMETER = 30).
	 * - Poisoned NPCs: centred text with its baseline on the anchor (logicalHeight + 40), up to
	 *   about 130px wide with next damage and total damage shown.
	 * - Venom Timer: up to four ~120px lines, 15px apart, starting at logicalHeight + 100 and
	 *   going down. That is 60 model units above the anchor, so pixelsPerUnit decides how far.
	 */
	private static List<Rectangle> neighbours(double pixelsPerUnit)
	{
		List<Rectangle> rectangles = new ArrayList<>();
		rectangles.add(new Rectangle(-15, -15, 30, 30));
		rectangles.add(new Rectangle(-65, -ASCENT, 130, ASCENT + DESCENT));
		int venomTimerFirstBaseline = -(int) Math.round(60 * pixelsPerUnit);
		for (int line = 0; line < 4; line++)
		{
			rectangles.add(new Rectangle(-60, venomTimerFirstBaseline + 15 * line - ASCENT,
				120, ASCENT + DESCENT));
		}
		return rectangles;
	}

	private static boolean touchesAny(List<Rectangle> ours, List<Rectangle> others)
	{
		for (Rectangle a : ours)
		{
			for (Rectangle b : others)
			{
				if (a.intersects(b))
				{
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void besideTheNpcClearsTheOtherPluginsAtEveryZoom()
	{
		double[] zooms = {0.1, 0.15, 0.28, 0.5, 0.8, 1.2};
		for (double zoom : zooms)
		{
			for (OverheadLayout.Placement placement : new OverheadLayout.Placement[]{
				OverheadLayout.Placement.RIGHT, OverheadLayout.Placement.LEFT})
			{
				OverheadLayout.Layout l = layout(placement, true, true);
				assertFalse(placement + " overlaps another plugin at " + zoom + " px per unit",
					touchesAny(ours(l, true, true), neighbours(zoom)));
			}
		}
	}

	@Test
	public void centredAboveTheHeadDoesCollideWhichIsWhyAutoMovesBeside()
	{
		// Control: the same check fails for the centred placement, so the test above is not vacuous.
		OverheadLayout.Layout l = layout(OverheadLayout.Placement.ABOVE, true, true);

		assertTrue(touchesAny(ours(l, true, true), neighbours(0.28)));
	}
}
