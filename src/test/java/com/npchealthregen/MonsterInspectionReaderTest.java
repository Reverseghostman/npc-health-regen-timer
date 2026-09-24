package com.npchealthregen;

import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

	@Test
	public void acceptsTruncatedPanelTitle()
	{
		MonsterInspectionReader.Snapshot snapshot = MonsterInspectionReader.parse(
			"Deranged archaeologist",
			Arrays.asList("<col=ff981f>Deranged archaeologi...</col>", "Stats", "Combat level: 276",
				"Hitpoints: 197", "Attack: 266", "Defence: 48"));

		assertEquals(197, snapshot.getHitpoints());
		assertEquals(48, snapshot.getDefence());
	}

	@Test
	public void rejectsTruncatedTitleOfAnotherNpc()
	{
		assertNull(MonsterInspectionReader.parse(
			"Deranged archaeologist",
			Arrays.asList("Crazy archaeologi...", "Hitpoints: 197")));
	}

	@Test
	public void titleMatchingIgnoresCombatLevelAndCase()
	{
		assertTrue(MonsterInspectionReader.namesMatch("GOBLIN (level-2)", "Goblin"));
		assertTrue(MonsterInspectionReader.namesMatch("Deranged archaeologi…", "Deranged archaeologist"));
		assertFalse(MonsterInspectionReader.namesMatch("Goblin", "Goblin chief"));
	}

	@Test
	public void readsLabelAndValueFromSeparateWidgets()
	{
		MonsterInspectionReader.Snapshot snapshot = MonsterInspectionReader.parse(
			"Goblin",
			Arrays.asList("Goblin", "Hitpoints:", "5", "Defence:", "1"));

		assertEquals(5, snapshot.getHitpoints());
		assertEquals(1, snapshot.getDefence());
	}

	@Test
	public void readsResultPanelDirectlyInWidgetOrder()
	{
		Client client = mock(Client.class);
		Widget title = mock(Widget.class);
		Widget stats = mock(Widget.class);
		Widget label = mock(Widget.class);
		Widget value = mock(Widget.class);
		when(title.getText()).thenReturn("Deranged archaeologi...");
		when(label.getText()).thenReturn("Hitpoints:");
		when(value.getText()).thenReturn("197");
		when(stats.getDynamicChildren()).thenReturn(new Widget[]{label, null, value});
		when(client.getWidget(InterfaceID.DreamMonsterStat.MONSTER_NAME)).thenReturn(title);
		when(client.getWidget(InterfaceID.DreamMonsterStat.MONSTER_STATS)).thenReturn(stats);

		MonsterInspectionReader.Snapshot snapshot = MonsterInspectionReader.find(
			client, "Deranged archaeologist");

		assertEquals(197, snapshot.getHitpoints());
	}

	@Test
	public void hiddenResultPanelIsIgnored()
	{
		Client client = mock(Client.class);
		Widget title = mock(Widget.class);
		Widget stats = mock(Widget.class);
		when(title.getText()).thenReturn("Goblin");
		when(stats.getText()).thenReturn("Hitpoints: 5");
		when(stats.isHidden()).thenReturn(true);
		when(client.getWidget(InterfaceID.DreamMonsterStat.MONSTER_NAME)).thenReturn(title);
		when(client.getWidget(InterfaceID.DreamMonsterStat.MONSTER_STATS)).thenReturn(stats);

		assertNull(MonsterInspectionReader.find(client, "Goblin"));
	}

	@Test
	public void readsStatsFromDynamicWidgetChildren()
	{
		Client client = mock(Client.class);
		Widget root = mock(Widget.class);
		Widget title = mock(Widget.class);
		Widget stats = mock(Widget.class);
		when(client.getWidgetRoots()).thenReturn(new Widget[]{root});
		when(root.getDynamicChildren()).thenReturn(new Widget[]{title, stats});
		when(title.getText()).thenReturn("Deranged archaeologist");
		when(stats.getText()).thenReturn("Hitpoints: 197<br>Defence: 48");

		MonsterInspectionReader.Snapshot snapshot = MonsterInspectionReader.find(
			client, "Deranged archaeologist");

		assertEquals(197, snapshot.getHitpoints());
		assertEquals(48, snapshot.getDefence());
	}
}
