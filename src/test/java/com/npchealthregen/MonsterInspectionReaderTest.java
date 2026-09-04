package com.npchealthregen;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MonsterInspectionReaderTest
{
	@Test
	public void parsesSelectedNpcHitpointsAndDefence()
	{
		MonsterInspectionReader.Snapshot snapshot = MonsterInspectionReader.parse(
			"The Inadequacy (hard)",
			Arrays.asList(
				"<col=ff981f>The Inadequacy (hard)</col>",
				"Stats<br>Combat level: 600<br>Hitpoints: 255<br>Attack: 1128<br>Defence: 240"));

		assertEquals(255, snapshot.getHitpoints());
		assertEquals(240, snapshot.getDefence());
	}

	@Test
	public void ignoresInspectionForAnotherNpc()
	{
		MonsterInspectionReader.Snapshot snapshot = MonsterInspectionReader.parse(
			"Cow",
			Arrays.asList("The Inadequacy (hard)", "Hitpoints: 255", "Defence: 240"));

		assertNull(snapshot);
	}

	@Test
	public void supportsCommaSeparatedValuesAndMissingDefence()
	{
		MonsterInspectionReader.Snapshot snapshot = MonsterInspectionReader.parse(
			"Example monster",
			Arrays.asList("Example monster", "Hitpoints: 1,250"));

		assertEquals(1250, snapshot.getHitpoints());
		assertEquals(-1, snapshot.getDefence());
	}
}
