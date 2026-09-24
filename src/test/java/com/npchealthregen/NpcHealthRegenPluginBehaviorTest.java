package com.npchealthregen;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.NPCManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NpcHealthRegenPluginBehaviorTest
{
	private NpcHealthRegenPlugin plugin;
	private NpcHealthRegenConfig config;
	private NpcTimingProfileStore profiles;
	private ClientThread clientThread;
	private NPC target;

	@Before
	public void setUp() throws Exception
	{
		plugin = new NpcHealthRegenPlugin();
		config = mock(NpcHealthRegenConfig.class);
		when(config.regenTicks()).thenReturn(100);
		when(config.observationDelayTicks()).thenReturn(3);
		when(config.learnNpcTimings()).thenReturn(true);
		when(config.markRegenHotkey()).thenReturn(new Keybind(KeyEvent.VK_F6, 0));
		when(config.resetHotkey()).thenReturn(new Keybind(KeyEvent.VK_F7, 0));
		profiles = mock(NpcTimingProfileStore.class);
		when(profiles.load(1)).thenReturn(new NpcTimingProfileStore.Profile(0, 0));
		clientThread = mock(ClientThread.class);
		set("config", config);
		set("profileStore", profiles);
		set("clientThread", clientThread);
		set("npcManager", mock(NPCManager.class));
		target = npc(1, 42);
		select(target);
	}

	@Test
	public void nearbyNpcDoesNotReplaceDeadTarget() throws Exception
	{
		kill();
		set("tick", 20L);
		plugin.onNpcSpawned(new NpcSpawned(npc(1, 43)));
		assertNull(plugin.getTarget());
		assertEquals(RegenTimer.State.WAITING_FOR_RESPAWN, plugin.getTimer().getState());
		verify(profiles, never()).save(anyInt(), anyInt(), anyInt());
		NPC respawn = npc(1, 42);
		plugin.onNpcSpawned(new NpcSpawned(respawn));
		assertSame(respawn, plugin.getTarget());
		assertEquals(20, plugin.getActiveRespawnTicks());
	}

	@Test
	public void reusedIndexWithDifferentNpcTypeIsIgnored()
	{
		kill();
		plugin.onNpcSpawned(new NpcSpawned(npc(2, 42)));
		assertNull(plugin.getTarget());
	}

	@Test
	public void hotkeyQueuesMutationAndPreservesExactInterval() throws Exception
	{
		plugin.getTimer().markNow(10);
		set("tick", 107L);
		plugin.keyPressed(key(KeyEvent.VK_F6));
		assertEquals(1, plugin.getTimer().getObservedRegens());
		runQueuedHotkey();
		assertEquals(97, plugin.getActiveRegenTicks());
		verify(profiles).save(1, 97, 0);
	}

	@Test
	public void queuedHotkeyCannotMarkDeadTarget()
	{
		plugin.keyPressed(key(KeyEvent.VK_F6));
		kill();
		runQueuedHotkey();
		assertEquals(RegenTimer.State.WAITING_FOR_RESPAWN, plugin.getTimer().getState());
		assertEquals(0, plugin.getTimer().getObservedRegens());
	}

	@Test
	public void resetHotkeyRunsOnClientThread()
	{
		plugin.getTimer().markNow(10);
		plugin.keyPressed(key(KeyEvent.VK_F7));
		assertEquals(1, plugin.getTimer().getObservedRegens());
		runQueuedHotkey();
		assertEquals(RegenTimer.State.OBSERVING, plugin.getTimer().getState());
	}

	@Test
	public void storedExactIntervalIsNotRoundedToDefault() throws Exception
	{
		when(profiles.load(1)).thenReturn(new NpcTimingProfileStore.Profile(103, 20));
		select(target);
		assertEquals(103, plugin.getActiveRegenTicks());
		verify(profiles, never()).save(anyInt(), anyInt(), anyInt());
	}

	@Test
	public void disablingLearningUpdatesPendingRespawn() throws Exception
	{
		when(profiles.load(1)).thenReturn(new NpcTimingProfileStore.Profile(100, 20));
		select(target);
		kill();
		assertEquals(20, plugin.getTimer().getRespawnTicksRemaining(0));
		when(config.learnNpcTimings()).thenReturn(false);
		ConfigChanged event = new ConfigChanged();
		event.setGroup(NpcHealthRegenConfig.GROUP);
		event.setKey("learnNpcTimings");
		plugin.onConfigChanged(event);
		assertEquals(-1, plugin.getTimer().getRespawnTicksRemaining(0));
	}

	@Test
	public void deathClearsLivingHealthEstimates() throws Exception
	{
		set("lastInspectedHitpoints", 50);
		set("lastInspectedDefence", 20);
		set("lastInspectedTick", 0L);
		kill();
		assertNull(plugin.getCurrentHitpoints());
		assertNull(plugin.getDefenceAtFullHitpoints());
		assertEquals(-1, plugin.getLastInspectedHitpoints());
	}

	@Test
	public void inspectionDoesNotInventMaximumHealth() throws Exception
	{
		Client client = mock(Client.class);
		Widget root = mock(Widget.class);
		when(root.getText()).thenReturn("Goblin<br>Hitpoints: 5<br>Defence: 3");
		when(client.getWidgetRoots()).thenReturn(new Widget[]{root});
		set("client", client);
		set("inspectionSampleTick", 0L);
		set("inspectionDeadlineTick", 8L);
		invoke("processInspectionResult");
		assertEquals(5, plugin.getLastInspectedHitpoints());
		assertEquals(-1, plugin.getMaximumHitpoints());
		assertNull(plugin.getTimeUntilFullHitpoints());
	}

	@Test
	public void castOnCurrentNpcIsRecognisedFromSelectedSpellWidget() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getWidgetRoots()).thenReturn(new Widget[0]);
		set("client", client);

		castInspection(target);

		assertTrue(plugin.isUsingMonsterInspection());
	}

	@Test
	public void castIsRecognisedFromLiveMenuTargetWithoutSelectedWidget() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getWidgetRoots()).thenReturn(new Widget[0]);
		set("client", client);

		castWithMenu(target, "Cast",
			"<col=00ff00>Monster Inspect</col><col=ffffff> -> <col=ffff00>Goblin<col=ff00>  (level-2)", null);

		assertTrue(plugin.isUsingMonsterInspection());
	}

	@Test
	public void castIsRecognisedFromSpellbookWidgetId() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getWidgetRoots()).thenReturn(new Widget[0]);
		set("client", client);
		Widget spell = mock(Widget.class);
		when(spell.getId()).thenReturn(InterfaceID.MagicSpellbook.MONSTER_INSPECT);

		castWithMenu(target, "Cast", "<col=ffff00>Goblin", spell);

		assertTrue(plugin.isUsingMonsterInspection());
	}

	@Test
	public void truncatedResultPanelTitleIsRead() throws Exception
	{
		// Reproduces the live panel: the title is cut to "Deranged archaeologi..."
		// and each stat line is its own widget under the stats container.
		Client client = mock(Client.class);
		set("client", client);
		NPC archaeologist = npc(1, 42);
		when(archaeologist.getName()).thenReturn("Deranged archaeologist");
		when(archaeologist.getHealthRatio()).thenReturn(-1);
		select(archaeologist);
		castWithMenu(archaeologist, "Cast",
			"<col=00ff00>Monster Inspect</col><col=ffffff> -> <col=ffff00>Deranged archaeologist", null);

		Widget title = mock(Widget.class);
		when(title.getText()).thenReturn("Deranged archaeologi...");
		String[] text = {"Stats", "Combat level: 276", "Hitpoints: 197", "Attack: 266", "Defence: 48",
			"Strength: 152", "Magic: 1", "Ranged: 320"};
		Widget[] lines = new Widget[text.length];
		for (int i = 0; i < lines.length; i++)
		{
			lines[i] = mock(Widget.class);
			when(lines[i].getText()).thenReturn(text[i]);
		}
		Widget stats = mock(Widget.class);
		when(stats.getDynamicChildren()).thenReturn(lines);
		when(client.getWidget(InterfaceID.DreamMonsterStat.MONSTER_NAME)).thenReturn(title);
		when(client.getWidget(InterfaceID.DreamMonsterStat.MONSTER_STATS)).thenReturn(stats);
		plugin.onClientTick(new ClientTick());

		assertEquals(197, plugin.getLastInspectedHitpoints());
		assertEquals(48, plugin.getLastInspectedDefence());
		assertFalse(plugin.isInspectionPending());
	}

	@Test
	public void unrelatedTargetedSpellDoesNotStartInspection() throws Exception
	{
		Client client = mock(Client.class);
		set("client", client);

		castSpellOn(target, "Cast Fire Strike");

		assertFalse(plugin.isUsingMonsterInspection());
	}

	@Test
	public void shiftRightClickOnCurrentTargetOffersClearInsteadOfSelect() throws Exception
	{
		Consumer<MenuEntry> onClick = captureMenuClick(target);
		assertEquals("Clear Regen Timer", lastMenuOption);

		onClick.accept(mock(MenuEntry.class));
		assertNull(plugin.getTarget());
	}

	@Test
	public void shiftRightClickOnAnotherNpcOffersSelect() throws Exception
	{
		NPC other = npc(1, 99);
		Consumer<MenuEntry> onClick = captureMenuClick(other);
		assertEquals("Select Regen Timer", lastMenuOption);

		onClick.accept(mock(MenuEntry.class));
		assertSame(other, plugin.getTarget());
	}

	@Test
	public void staleClearEntryDoesNotClearReplacementTarget() throws Exception
	{
		Consumer<MenuEntry> onClick = captureMenuClick(target);
		NPC replacement = npc(1, 99);
		select(replacement);

		onClick.accept(mock(MenuEntry.class));
		assertSame(replacement, plugin.getTarget());
	}

	@Test
	public void staleSelectEntryDoesNotResetCurrentTarget() throws Exception
	{
		NPC other = npc(1, 99);
		Consumer<MenuEntry> onClick = captureMenuClick(other);
		select(other);
		plugin.getTimer().markNow(10);

		onClick.accept(mock(MenuEntry.class));
		assertSame(other, plugin.getTarget());
		assertEquals(1, plugin.getTimer().getObservedRegens());
	}

	private String lastMenuOption;

	private Consumer<MenuEntry> captureMenuClick(NPC clickedNpc) throws Exception
	{
		Client client = mock(Client.class);
		when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
		Menu menu = mock(Menu.class);
		when(client.getMenu()).thenReturn(menu);
		MenuEntry newEntry = mock(MenuEntry.class, RETURNS_SELF);
		when(menu.createMenuEntry(-1)).thenReturn(newEntry);
		set("client", client);

		MenuEntry sourceEntry = mock(MenuEntry.class);
		when(sourceEntry.getType()).thenReturn(MenuAction.EXAMINE_NPC);
		when(sourceEntry.getNpc()).thenReturn(clickedNpc);
		when(sourceEntry.getWorldViewId()).thenReturn(-1);
		MenuEntryAdded event = new MenuEntryAdded(sourceEntry);

		ArgumentCaptor<String> option = ArgumentCaptor.forClass(String.class);
		plugin.onMenuEntryAdded(event);
		verify(newEntry, times(clickedNpc == plugin.getTarget() ? 2 : 1)).setOption(option.capture());
		lastMenuOption = option.getValue();

		ArgumentCaptor<Consumer<MenuEntry>> click = ArgumentCaptor.forClass(Consumer.class);
		verify(newEntry, times(clickedNpc == plugin.getTarget() ? 2 : 1)).onClick(click.capture());
		return click.getValue();
	}

	@Test
	public void rapidRecastsCaptureUnchangedBaselineThenHeal() throws Exception
	{
		Client client = mock(Client.class);
		Widget root = mock(Widget.class);
		set("client", client);
		when(target.getHealthRatio()).thenReturn(-1);
		when(client.getWidgetRoots()).thenReturn(new Widget[0]);
		castInspection(target);
		when(root.getText()).thenReturn("Goblin<br>Hitpoints: 5<br>Defence: 3");
		when(client.getWidgetRoots()).thenReturn(new Widget[]{root});
		plugin.onGameTick(new GameTick());
		assertEquals(5, plugin.getLastInspectedHitpoints());

		// Close and immediately recast: the next unchanged reading is evidence too.
		set("tick", 30L);
		when(client.getWidgetRoots()).thenReturn(new Widget[0]);
		castInspection(target);
		when(client.getWidgetRoots()).thenReturn(new Widget[]{root});
		plugin.onGameTick(new GameTick());
		when(client.getWidgetRoots()).thenReturn(new Widget[0]);
		castInspection(target);
		when(root.getText()).thenReturn("Goblin<br>Hitpoints: 6<br>Defence: 4");
		when(client.getWidgetRoots()).thenReturn(new Widget[]{root});
		plugin.onGameTick(new GameTick());
		assertEquals(6, plugin.getLastInspectedHitpoints());
		assertEquals(4, plugin.getLastInspectedDefence());
		assertEquals(1, plugin.getTimer().getObservedRegens());
		RegenTimer.Window window = plugin.getTimer().getUpcomingWindow(33, 100);
		assertEquals(98, window.getEarliestTicks());
		assertEquals(99, window.getLatestTicks());
	}

	@Test
	public void oldPanelCannotConsumeNewCastAndStaticPanelIsNotResampled() throws Exception
	{
		Client client = mock(Client.class);
		Widget root = mock(Widget.class);
		set("client", client);
		when(target.getHealthRatio()).thenReturn(-1);
		when(client.getWidgetRoots()).thenReturn(new Widget[0]);
		castInspection(target);
		when(root.getText()).thenReturn("Goblin<br>Hitpoints: 5<br>Defence: 3");
		when(client.getWidgetRoots()).thenReturn(new Widget[]{root});
		plugin.onGameTick(new GameTick());
		set("tick", 20L);
		castInspection(target);
		plugin.onGameTick(new GameTick());
		when(root.getText()).thenReturn("Goblin<br>Hitpoints: 6<br>Defence: 3");
		plugin.onGameTick(new GameTick());
		assertEquals(6, plugin.getLastInspectedHitpoints());
		plugin.onGameTick(new GameTick());
		assertFalse(plugin.isCurrentHitpointsExact());
		assertEquals(1, plugin.getTimer().getObservedRegens());
		castInspection(npc(1, 43));
		when(root.getText()).thenReturn("Goblin<br>Hitpoints: 9<br>Defence: 3");
		plugin.onGameTick(new GameTick());
		assertEquals(6, plugin.getLastInspectedHitpoints());
	}

	private void castInspection(NPC npc) throws Exception
	{
		castSpellOn(npc, "Cast Monster Examine");
	}

	private void castSpellOn(NPC npc, String spellName) throws Exception
	{
		Widget selectedSpell = mock(Widget.class);
		when(selectedSpell.getName()).thenReturn(spellName);
		castWithMenu(npc, "Cast", "<col=00ff00>Goblin</col>", selectedSpell);
	}

	private void castWithMenu(NPC npc, String option, String menuTarget, Widget selectedSpell) throws Exception
	{
		Client client = (Client) get("client");
		when(client.getSelectedWidget()).thenReturn(selectedSpell);

		MenuOptionClicked event = mock(MenuOptionClicked.class);
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getNpc()).thenReturn(npc);
		when(event.getMenuEntry()).thenReturn(entry);
		when(event.getMenuAction()).thenReturn(MenuAction.WIDGET_TARGET_ON_NPC);
		when(event.getWidget()).thenReturn(selectedSpell);
		when(event.getMenuOption()).thenReturn(option);
		when(event.getMenuTarget()).thenReturn(menuTarget);
		plugin.onMenuOptionClicked(event);
	}

	@Test
	public void clientTickCapturesResultBeforeNextGameTick() throws Exception
	{
		Client client = mock(Client.class);
		Widget root = mock(Widget.class);
		set("client", client);
		when(client.getWidgetRoots()).thenReturn(new Widget[0]);
		castInspection(target);
		when(root.getText()).thenReturn("Goblin<br>Hitpoints: 5<br>Defence: 3");
		when(client.getWidgetRoots()).thenReturn(new Widget[]{root});
		plugin.onClientTick(mock(ClientTick.class));
		assertEquals(5, plugin.getLastInspectedHitpoints());
		assertEquals(0, plugin.getTick());
		when(root.getText()).thenReturn("Goblin<br>Hitpoints: 6<br>Defence: 3");
		// No new cast: client frames do not repeatedly resample the old panel.
		plugin.onClientTick(mock(ClientTick.class));
		assertEquals(5, plugin.getLastInspectedHitpoints());
	}

	@Test
	public void recalibrationClearsBadSavedRegenButPreservesRespawn() throws Exception
	{
		when(profiles.load(1)).thenReturn(new NpcTimingProfileStore.Profile(112, 20));
		select(target);
		plugin.getTimer().markNow(1);
		plugin.keyPressed(key(KeyEvent.VK_F7));
		runQueuedHotkey();
		assertEquals(100, plugin.getActiveRegenTicks());
		assertFalse(plugin.isLearnedRegen());
		assertEquals(20, plugin.getActiveRespawnTicks());
		assertSame(target, plugin.getTarget());
		verify(profiles).save(1, 0, 20);
	}

	private void kill()
	{
		plugin.onActorDeath(new ActorDeath(target));
	}

	private NPC npc(int id, int index)
	{
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(id);
		when(npc.getIndex()).thenReturn(index);
		when(npc.getName()).thenReturn("Goblin");
		when(npc.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
		return npc;
	}

	private KeyEvent key(int code)
	{
		KeyEvent event = mock(KeyEvent.class);
		when(event.getID()).thenReturn(KeyEvent.KEY_PRESSED);
		when(event.getExtendedKeyCode()).thenReturn(code);
		return event;
	}

	private void runQueuedHotkey()
	{
		ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread).invokeLater(action.capture());
		action.getValue().run();
	}

	private void select(NPC npc) throws Exception
	{
		Method method = NpcHealthRegenPlugin.class.getDeclaredMethod("selectTarget", NPC.class);
		method.setAccessible(true);
		method.invoke(plugin, npc);
	}

	private void invoke(String name) throws Exception
	{
		Method method = NpcHealthRegenPlugin.class.getDeclaredMethod(name);
		method.setAccessible(true);
		method.invoke(plugin);
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = NpcHealthRegenPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	private Object get(String name) throws Exception
	{
		Field field = NpcHealthRegenPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(plugin);
	}
}
