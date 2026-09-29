package com.npchealthregen;

/**
 * Where the selected NPC's overhead text and venom ring go, in screen pixels relative to one
 * anchor point (the NPC's canvas position just above its head).
 *
 * Other NPC plugins stack their text above the head at the same anchor: Poison Dynamite's
 * countdown ring and Poisoned NPCs' timer are centred on it, and Venom Timer draws up to four
 * lines from a little higher and down through it. Beside the NPC is left free of all of them.
 *
 * The rows are stacked in pixels, not at fixed model heights, so they never overlap each
 * other whatever the zoom: the regen text sits on the anchor, the venom label above it and the
 * venom ring above that.
 */
final class OverheadLayout
{
	enum Placement
	{
		/** Centred above the head, where other plugins also draw. */
		ABOVE,
		/** Left edge {@code sideOffset} pixels right of the anchor. */
		RIGHT,
		/** Right edge {@code sideOffset} pixels left of the anchor. */
		LEFT
	}

	static final int GAP = 2;

	/**
	 * Clear of the widest neighbour: Poisoned NPCs' timer can reach about 65px either side of
	 * the anchor and Venom Timer's lines about 60px.
	 */
	static final int DEFAULT_SIDE_OFFSET = 70;

	static final class Layout
	{
		final int regenX;
		final int regenBaselineY;
		final int ringCentreX;
		final int ringCentreY;
		final int labelX;
		final int labelBaselineY;

		Layout(int regenX, int regenBaselineY, int ringCentreX, int ringCentreY,
			int labelX, int labelBaselineY)
		{
			this.regenX = regenX;
			this.regenBaselineY = regenBaselineY;
			this.ringCentreX = ringCentreX;
			this.ringCentreY = ringCentreY;
			this.labelX = labelX;
			this.labelBaselineY = labelBaselineY;
		}
	}

	private OverheadLayout()
	{
	}

	/**
	 * @param regenWidth width of the regen text, or 0 if it is not drawn
	 * @param ringDiameter diameter of the venom ring, or 0 if it is not drawn
	 * @param labelWidth width of the venom label under the ring; ignored without a ring
	 */
	static Layout layout(int anchorX, int anchorY, Placement placement, int sideOffset,
		int ascent, int descent, int regenWidth, int ringDiameter, int labelWidth)
	{
		int rowHeight = ascent + descent;
		boolean hasRegen = regenWidth > 0;
		boolean hasRing = ringDiameter > 0;
		int side = Math.max(0, sideOffset);

		int regenBaselineY = anchorY;
		// Bottom edge of whatever is stacked next: just above the regen text's tallest glyph,
		// or, with no regen text, the anchor is the venom label's baseline.
		int cursor = hasRegen ? anchorY - ascent - GAP : anchorY + descent;
		int labelBaselineY = cursor - descent;
		int ringBottom = cursor - rowHeight - GAP;
		int ringCentreY = ringBottom - ringDiameter / 2;

		int regenX;
		int labelX;
		int ringCentreX;
		switch (placement)
		{
			case RIGHT:
			{
				int left = anchorX + side;
				regenX = left;
				labelX = left;
				ringCentreX = left + ringDiameter / 2;
				break;
			}
			case LEFT:
			{
				int right = anchorX - side;
				regenX = right - regenWidth;
				labelX = right - labelWidth;
				ringCentreX = right - ringDiameter / 2;
				break;
			}
			default:
				regenX = anchorX - regenWidth / 2;
				labelX = anchorX - labelWidth / 2;
				ringCentreX = anchorX;
				break;
		}

		return new Layout(regenX, regenBaselineY, ringCentreX, ringCentreY, labelX, labelBaselineY);
	}
}
