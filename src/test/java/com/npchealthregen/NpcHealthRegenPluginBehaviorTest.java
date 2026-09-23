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
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcSpawned;
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
		verify(newEntry).setOption(option.capture());
		lastMenuOption = option.getValue();

		ArgumentCaptor<Consumer<MenuEntry>> click = ArgumentCaptor.forClass(Consumer.class);
		verify(newEntry).onClick(click.capture());
		return click.getValue();
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
}
