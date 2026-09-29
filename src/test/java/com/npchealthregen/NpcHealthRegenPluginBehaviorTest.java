package com.npchealthregen;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.NPCManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
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
	private PartyService partyService;
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
		partyService = mock(PartyService.class);
		set("partyService", partyService);
		set("wsClient", mock(WSClient.class));
		target = npc(1, 42);
		select(target);
	}

	@Test
	public void sharesTheWindowWithThePartyOnlyWhenItChanges() throws Exception
	{
		joinParty(301);
		when(config.shareWithParty()).thenReturn(true);
		plugin.getTimer().markNow(50, 100);
		set("tick", 60L);

		plugin.onGameTick(new GameTick());

		ArgumentCaptor<NpcHealthRegenPartyUpdate> sent = ArgumentCaptor.forClass(NpcHealthRegenPartyUpdate.class);
		verify(partyService).send(sent.capture());
		NpcHealthRegenPartyUpdate update = sent.getValue();
		assertEquals(301, update.getWorld());
		assertEquals(1, update.getNpcId());
		assertEquals(42, update.getNpcIndex());
		assertEquals(100, update.getRegenTicks());
		// Next heal at 150, sent from tick 61.
		assertEquals(89, update.getWindowStartOffset());
		assertEquals(89, update.getWindowEndOffset());

		plugin.onGameTick(new GameTick());
		verify(partyService, times(1)).send(any());

		plugin.onUserJoin(null);
		plugin.onGameTick(new GameTick());
		verify(partyService, times(2)).send(any());
	}

	@Test
	public void doesNotShareWhenSharingIsOff() throws Exception
	{
		joinParty(301);
		plugin.getTimer().markNow(50, 100);

		plugin.onGameTick(new GameTick());

		verify(partyService, never()).send(any());
	}

	@Test
	public void partyMembersWindowFillsInTheSameNpc() throws Exception
	{
		joinParty(301);
		when(config.useSharedTimers()).thenReturn(true);
		set("tick", 100L);

		plugin.applyPartyUpdate(partyUpdate(2L, 301, 42, 30, 32));

		// Widened by a tick either side for delivery timing.
		RegenTimer.Window window = plugin.getTimer().getUpcomingWindow(100, 100);
		assertEquals(29, window.getEarliestTicks());
		assertEquals(33, window.getLatestTicks());
		assertEquals("Alice", plugin.getPartySourceName());

		// The first own observation takes over from the shared phase.
		plugin.getTimer().markNow(130, 100);
		assertNull(plugin.getPartySourceName());
	}

	@Test
	public void ignoresOwnOtherNpcAndOtherWorldUpdates() throws Exception
	{
		joinParty(301);
		when(config.useSharedTimers()).thenReturn(true);

		plugin.applyPartyUpdate(partyUpdate(1L, 301, 42, 30, 32));
		plugin.applyPartyUpdate(partyUpdate(2L, 301, 43, 30, 32));
		plugin.applyPartyUpdate(partyUpdate(2L, 302, 42, 30, 32));

		assertNull(plugin.getTimer().getUpcomingWindow(0, 100));
	}

	@Test
	public void ignoresPartyTimersWhenTurnedOff() throws Exception
	{
		joinParty(301);

		plugin.applyPartyUpdate(partyUpdate(2L, 301, 42, 30, 32));

		assertNull(plugin.getTimer().getUpcomingWindow(0, 100));
	}

	@Test
	public void partyUpdatesAreHandledOnTheClientThread()
	{
		plugin.onNpcHealthRegenPartyUpdate(partyUpdate(2L, 301, 42, 30, 32));

		verify(clientThread).invokeLater(any(Runnable.class));
	}

	@Test
	public void venomHitWithSerpentineHelmAndTridentStartsTheRingAndSharesIt() throws Exception
	{
		Client client = joinParty(301);
		when(config.showVenomRing()).thenReturn(true);
		when(config.shareWithParty()).thenReturn(true);
		wearVenomSetup(client);
		select(target);
		plugin.getTimer().markNow(90, 100);
		set("tick", 185L);

		// Snare for 1 just before the regen at 190.
		hit(target, HitsplatID.DAMAGE_ME, 1, true);

		VenomRing ring = plugin.getVenomRing();
		assertEquals(VenomRing.Status.COUNTING, ring.getStatus());
		assertEquals(215, ring.getProcLatest());
		assertEquals(190, ring.getFullHpTick());
		ArgumentCaptor<NpcHealthRegenVenomUpdate> sent = ArgumentCaptor.forClass(NpcHealthRegenVenomUpdate.class);
		verify(partyService).send(sent.capture());
		assertEquals(30, sent.getValue().getProcLatestOffset());
		assertEquals(5, sent.getValue().getFullHpOffset());

		hit(target, HitsplatID.VENOM, 6, false);
		assertEquals(VenomRing.Status.PROCCED, ring.getStatus());
	}

	@Test
	public void hitWithoutTheVenomSetupStartsNoRing() throws Exception
	{
		Client client = mock(Client.class);
		set("client", client);
		when(config.showVenomRing()).thenReturn(true);

		hit(target, HitsplatID.DAMAGE_ME, 1, true);

		assertEquals(VenomRing.Status.NONE, plugin.getVenomRing().getStatus());
	}

	@Test
	public void dynamiteDelayIsMeasuredFromUseToHit() throws Exception
	{
		Client client = mock(Client.class);
		set("client", client);
		Widget dynamite = mock(Widget.class);
		when(dynamite.getItemId()).thenReturn(ItemID.LOVAKENGJ_DYNAMITE_POISON);
		when(client.getSelectedWidget()).thenReturn(dynamite);
		MenuOptionClicked use = mock(MenuOptionClicked.class);
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getNpc()).thenReturn(target);
		when(use.getMenuEntry()).thenReturn(entry);
		when(use.getMenuAction()).thenReturn(MenuAction.WIDGET_TARGET_ON_NPC);
		set("tick", 100L);
		plugin.onMenuOptionClicked(use);

		set("tick", 104L);
		hit(target, HitsplatID.DAMAGE_ME, 3, true);

		assertEquals(4, plugin.getDynamiteDelayTicks());
		assertFalse(plugin.isInspectionPending());
	}

	@Test
	public void partyVenomHitShowsTheRingOnTheDynamiteAccount() throws Exception
	{
		joinParty(301);
		select(target);
		when(config.useSharedTimers()).thenReturn(true);
		when(config.showVenomRing()).thenReturn(true);
		set("tick", 100L);

		plugin.applyVenomUpdate(venomUpdate(false));

		VenomRing ring = plugin.getVenomRing();
		assertTrue(ring.isShared());
		assertEquals(129, ring.getProcEarliest());
		assertEquals(131, ring.getProcLatest());
		assertEquals(106, ring.getFullHpTick());

		// The venom account logged out before the venom procced.
		plugin.applyVenomUpdate(venomUpdate(true));
		assertEquals(VenomRing.Status.NONE, ring.getStatus());
	}

	@Test
	public void loggingOutKeepsTheNpcAndCarriesTheTimerOn() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getWorld()).thenReturn(301);
		set("client", client);
		select(target);
		plugin.getTimer().markNow(50, 100);
		set("tick", 100L);

		plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		assertNull(plugin.getTarget());
		assertEquals("Goblin", plugin.getTargetName());

		// Back a minute (100 ticks) later.
		set("awayMillis", System.currentTimeMillis() - 60_000L);
		plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
		assertEquals(200, plugin.getTick());
		// The heal at 50 recurs at 250, widened a tick each side for the gap.
		RegenTimer.Window window = plugin.getTimer().getUpcomingWindow(200, 100);
		assertEquals(49, window.getEarliestTicks());
		assertEquals(51, window.getLatestTicks());
		// An unseen kill in that time would have moved it, so it is no longer certain.
		assertTrue(plugin.getTimer().isUnverified());

		NPC again = npc(1, 42);
		plugin.onNpcSpawned(new NpcSpawned(again));
		assertSame(again, plugin.getTarget());
	}

	@Test
	public void loggingIntoAnotherWorldDropsTheNpc() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getWorld()).thenReturn(301);
		set("client", client);
		select(target);
		plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));

		when(client.getWorld()).thenReturn(302);
		plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));

		assertNull(plugin.getTargetName());
	}

	@Test
	public void npcOutOfViewKeepsItsTimerUntilYouWalkAway() throws Exception
	{
		Client client = mock(Client.class);
		set("client", client);
		plugin.getTimer().markNow(50, 100);
		when(target.getHealthRatio()).thenReturn(-1);

		plugin.onNpcDespawned(new NpcDespawned(target));

		assertTrue(plugin.isTargetOutOfView());
		assertNotNull(plugin.getTimer().getUpcomingWindow(60, 100));
		assertTrue(plugin.getTimer().isUnverified());

		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3210, 3200, 0));
		plugin.onGameTick(new GameTick());
		assertEquals("Goblin", plugin.getTargetName());

		when(player.getWorldLocation()).thenReturn(new WorldPoint(3300, 3200, 0));
		plugin.onGameTick(new GameTick());
		assertNull(plugin.getTargetName());
	}

	@Test
	public void sharesFlagAPhaseCarriedThroughTimeOutOfView() throws Exception
	{
		joinParty(301);
		when(config.shareWithParty()).thenReturn(true);
		plugin.getTimer().markNow(50, 100);
		set("tick", 60L);
		when(target.getHealthRatio()).thenReturn(-1);

		plugin.onNpcDespawned(new NpcDespawned(target));
		plugin.onNpcSpawned(new NpcSpawned(npc(1, 42)));
		plugin.onGameTick(new GameTick());

		ArgumentCaptor<NpcHealthRegenPartyUpdate> sent = ArgumentCaptor.forClass(NpcHealthRegenPartyUpdate.class);
		verify(partyService).send(sent.capture());
		assertTrue(sent.getValue().isUnverified());
	}

	@Test
	public void sharesDoNotFlagAPhaseThatWasObserved() throws Exception
	{
		joinParty(301);
		when(config.shareWithParty()).thenReturn(true);
		plugin.getTimer().markNow(50, 100);
		set("tick", 60L);

		plugin.onGameTick(new GameTick());

		ArgumentCaptor<NpcHealthRegenPartyUpdate> sent = ArgumentCaptor.forClass(NpcHealthRegenPartyUpdate.class);
		verify(partyService).send(sent.capture());
		assertFalse(sent.getValue().isUnverified());
	}

	@Test
	public void partyWindowIsTakenWhileTheNpcIsOutOfView() throws Exception
	{
		joinParty(301);
		when(config.useSharedTimers()).thenReturn(true);
		plugin.getTimer().markNow(50, 100);
		when(target.getHealthRatio()).thenReturn(-1);
		set("tick", 100L);
		plugin.onNpcDespawned(new NpcDespawned(target));
		assertNull(plugin.getTarget());

		// A member who kept watching sends a window that disagrees with the one carried on.
		plugin.applyPartyUpdate(partyUpdate(2L, 301, 42, 30, 32));

		RegenTimer.Window window = plugin.getTimer().getUpcomingWindow(100, 100);
		assertEquals(29, window.getEarliestTicks());
		assertEquals(33, window.getLatestTicks());
		assertEquals("Alice", plugin.getPartySourceName());
	}

	@Test
	public void partyWindowIsNotTakenWhileLoggedOutBecauseNoTicksArriveToPlaceIt() throws Exception
	{
		Client client = joinParty(301);
		when(config.useSharedTimers()).thenReturn(true);
		select(target);
		plugin.getTimer().markNow(50, 100);
		set("tick", 100L);
		plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));

		plugin.applyPartyUpdate(partyUpdate(2L, 301, 42, 30, 32));

		assertNull(plugin.getPartySourceName());
		assertEquals(50, plugin.getTimer().getUpcomingWindow(100, 100).getEarliestTicks());

		// Once back, the next window is taken.
		set("awayMillis", System.currentTimeMillis() - 30_000L);
		plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
		plugin.applyPartyUpdate(partyUpdate(2L, 301, 42, 30, 32));
		assertEquals("Alice", plugin.getPartySourceName());
	}

	@Test
	public void partyWindowIsNotTakenOnceNoNpcIsSelected() throws Exception
	{
		joinParty(301);
		when(config.useSharedTimers()).thenReturn(true);
		invoke("clearTarget");

		plugin.applyPartyUpdate(partyUpdate(2L, 301, 42, 30, 32));

		assertNull(plugin.getTimer().getUpcomingWindow(0, 100));
	}

	/**
	 * The target dies at tick 100 with the player at its spawn, the player stands {@code tilesAway}
	 * tiles off from the next tick, and it is seen alive again at tick 200.
	 */
	private void deathThenRespawnSeenFrom(int tilesAway, int plane) throws Exception
	{
		Client client = mock(Client.class);
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
		set("client", client);
		plugin.getTimer().markNow(50, 100);
		set("tick", 100L);
		kill();

		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200 + tilesAway, plane));
		plugin.onGameTick(new GameTick());
		set("tick", 200L);
		plugin.onNpcSpawned(new NpcSpawned(npc(1, 42)));
	}

	@Test
	public void respawnSeenAfterWalkingOutOfRangeIsLateAndNotLearned() throws Exception
	{
		deathThenRespawnSeenFrom(30, 0);

		assertEquals(0, plugin.getActiveRespawnTicks());
		verify(profiles, never()).save(anyInt(), anyInt(), anyInt());
		assertTrue(plugin.getTimer().isUnverified());
		// Nothing says when it came back, so the phase admits that instead of being tens of ticks out.
		assertTrue(plugin.getTimer().getPhaseUncertaintyTicks() >= 100);
	}

	@Test
	public void aKnownRespawnTimeBoundsALateSightingToATickOrTwo() throws Exception
	{
		when(profiles.load(1)).thenReturn(new NpcTimingProfileStore.Profile(0, 50));
		select(target);

		deathThenRespawnSeenFrom(30, 0);

		// It came back at 150, whenever it was seen: the heal at 50 moves by the 50 dead ticks (less
		// the 0-2 the countdown keeps running), give or take the tick or two this kill can differ by.
		long[] window = plugin.getTimer().getUpcomingWindowTicks(200, 100);
		assertEquals(196, window[0]);
		assertEquals(202, window[1]);
		assertEquals(50, plugin.getActiveRespawnTicks());
		verify(profiles, never()).save(anyInt(), anyInt(), anyInt());
		assertTrue(plugin.getTimer().isUnverified());
	}

	@Test
	public void respawnSeenFromFifteenTilesIsOnTimeAndLearned() throws Exception
	{
		deathThenRespawnSeenFrom(15, 0);

		assertEquals(100, plugin.getActiveRespawnTicks());
		verify(profiles).save(1, 0, 100);
		assertFalse(plugin.getTimer().isUnverified());
	}

	@Test
	public void respawnSeenFromSixteenTilesIsLate() throws Exception
	{
		deathThenRespawnSeenFrom(16, 0);

		assertTrue(plugin.getTimer().isUnverified());
		verify(profiles, never()).save(anyInt(), anyInt(), anyInt());
	}

	@Test
	public void respawnSeenFromAnotherPlaneIsLate() throws Exception
	{
		deathThenRespawnSeenFrom(0, 1);

		assertTrue(plugin.getTimer().isUnverified());
		verify(profiles, never()).save(anyInt(), anyInt(), anyInt());
	}

	@Test
	public void respawnSeenAfterALogoutIsLateAndUnverified() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getWorld()).thenReturn(301);
		set("client", client);
		select(target);
		plugin.getTimer().markNow(50, 100);
		set("tick", 100L);
		kill();

		plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		set("awayMillis", System.currentTimeMillis() - 60_000L);
		plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
		plugin.onNpcSpawned(new NpcSpawned(npc(1, 42)));

		assertTrue(plugin.getTimer().isUnverified());
		assertEquals(0, plugin.getActiveRespawnTicks());
		verify(profiles, never()).save(anyInt(), anyInt(), anyInt());
	}

	private static NpcHealthRegenVenomUpdate venomUpdate(boolean cancelled)
	{
		NpcHealthRegenVenomUpdate update = new NpcHealthRegenVenomUpdate(
			301, 1, 42, cancelled, 0, 30, 30, true, 5);
		update.setMemberId(2L);
		return update;
	}

	private static void wearVenomSetup(Client client)
	{
		wear(client, ItemID.SERPENTINE_HELM_CHARGED, ItemID.TOXIC_TOTS_CHARGED);
	}

	/** @param helm the worn head item, or null for none */
	private static void wear(Client client, Integer helm, int weapon)
	{
		ItemContainer worn = mock(ItemContainer.class);
		when(worn.getItem(EquipmentInventorySlot.HEAD.getSlotIdx()))
			.thenReturn(helm == null ? null : new Item(helm, 1));
		when(worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx()))
			.thenReturn(new Item(weapon, 1));
		when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
	}

	@Test
	public void aMissOrSplashOfZeroStartsNoRingButALandedHitDoes() throws Exception
	{
		Client client = joinParty(301);
		when(config.showVenomRing()).thenReturn(true);
		when(config.shareWithParty()).thenReturn(true);
		wearVenomSetup(client);
		select(target);
		plugin.getTimer().markNow(90, 100);
		set("tick", 185L);

		// A failed accuracy roll shows 0; only a landed hit (always at least 1) can envenom.
		hit(target, HitsplatID.BLOCK_ME, 0, true);

		assertEquals(VenomRing.Status.NONE, plugin.getVenomRing().getStatus());
		verify(partyService, never()).send(any());

		hit(target, HitsplatID.DAMAGE_ME, 1, true);

		assertEquals(VenomRing.Status.COUNTING, plugin.getVenomRing().getStatus());
	}

	@Test
	public void toxicBlowpipeAndToxicStaffWithTheHelmAlsoStartTheRing() throws Exception
	{
		Client client = joinParty(301);
		when(config.showVenomRing()).thenReturn(true);
		plugin.getTimer().markNow(90, 100);

		wear(client, ItemID.SERPENTINE_HELM_CHARGED_RED, ItemID.TOXIC_BLOWPIPE_LOADED);
		set("tick", 185L);
		hit(target, HitsplatID.DAMAGE_ME, 3, true);
		assertEquals(VenomRing.Status.COUNTING, plugin.getVenomRing().getStatus());

		select(target);
		plugin.getTimer().markNow(90, 100);
		wear(client, ItemID.SERPENTINE_HELM_CHARGED_CYAN, ItemID.TOXIC_SOTD_CHARGED);
		set("tick", 185L);
		hit(target, HitsplatID.DAMAGE_ME, 3, true);
		assertEquals(VenomRing.Status.COUNTING, plugin.getVenomRing().getStatus());
	}

	@Test
	public void noxiousHalberdIsOnlyFiftyPercentEvenWithTheHelmSoStartsNoRing() throws Exception
	{
		Client client = joinParty(301);
		when(config.showVenomRing()).thenReturn(true);
		wear(client, ItemID.SERPENTINE_HELM_CHARGED, ItemID.NOXIOUS_HALBERD);
		plugin.getTimer().markNow(90, 100);
		set("tick", 185L);

		hit(target, HitsplatID.DAMAGE_ME, 5, true);

		assertEquals(VenomRing.Status.NONE, plugin.getVenomRing().getStatus());
		assertEquals(50, plugin.getVenomSetup().getPercent());
		assertFalse(plugin.getVenomSetup().isGuaranteed());
	}

	@Test
	public void toxicWeaponWithoutTheHelmIsATwentyFivePercentChanceAndStartsNoRing() throws Exception
	{
		Client client = joinParty(301);
		when(config.showVenomRing()).thenReturn(true);
		wear(client, null, ItemID.TOXIC_TOTS_CHARGED);
		plugin.getTimer().markNow(90, 100);
		set("tick", 185L);

		hit(target, HitsplatID.DAMAGE_ME, 2, true);

		assertEquals(VenomRing.Status.NONE, plugin.getVenomRing().getStatus());
		assertEquals("25% (no helm)", plugin.getVenomSetup().describe());
	}

	@Test
	public void venomChanceFollowsTheWornGearEachTick() throws Exception
	{
		Client client = mock(Client.class);
		set("client", client);

		wear(client, null, ItemID.TOXIC_BLOWPIPE_LOADED);
		plugin.onGameTick(new GameTick());
		assertEquals(25, plugin.getVenomSetup().getPercent());

		wear(client, ItemID.SERPENTINE_HELM_CHARGED_CYAN, ItemID.TOXIC_BLOWPIPE_LOADED);
		plugin.onGameTick(new GameTick());
		assertEquals(100, plugin.getVenomSetup().getPercent());
		assertTrue(plugin.getVenomSetup().isGuaranteed());

		// An abyssal whip is not a venom weapon.
		wear(client, ItemID.SERPENTINE_HELM_CHARGED, 4151);
		plugin.onGameTick(new GameTick());
		assertNull(plugin.getVenomSetup());
	}

	@Test
	public void automaticPlacementMovesBesideTheNpcWhileAnotherOverheadPluginIsEnabled() throws Exception
	{
		PluginManager manager = mock(PluginManager.class);
		Plugin dynamite = mock(Plugin.class);
		when(dynamite.getName()).thenReturn("Poison Dynamite");
		Plugin unrelated = mock(Plugin.class);
		when(unrelated.getName()).thenReturn("Some Other Plugin");
		when(manager.getPlugins()).thenReturn(Arrays.asList(unrelated, dynamite));
		set("pluginManager", manager);
		when(config.overheadPlacement()).thenReturn(NpcHealthRegenConfig.OverheadPlacement.AUTO);

		when(manager.isPluginEnabled(unrelated)).thenReturn(true);
		when(manager.isPluginEnabled(dynamite)).thenReturn(false);
		plugin.onGameTick(new GameTick());
		assertEquals(OverheadLayout.Placement.ABOVE, plugin.getOverheadPlacement());

		when(manager.isPluginEnabled(dynamite)).thenReturn(true);
		plugin.onGameTick(new GameTick());
		assertEquals(OverheadLayout.Placement.RIGHT, plugin.getOverheadPlacement());

		when(manager.isPluginEnabled(dynamite)).thenReturn(false);
		plugin.onGameTick(new GameTick());
		assertEquals(OverheadLayout.Placement.ABOVE, plugin.getOverheadPlacement());
	}

	@Test
	public void eachKnownOverheadPluginTriggersAutomaticPlacement() throws Exception
	{
		when(config.overheadPlacement()).thenReturn(NpcHealthRegenConfig.OverheadPlacement.AUTO);
		for (String name : new String[]{"Poison Dynamite", "Poisoned NPCs", "Venom Timer"})
		{
			PluginManager manager = mock(PluginManager.class);
			Plugin other = mock(Plugin.class);
			when(other.getName()).thenReturn(name);
			when(manager.getPlugins()).thenReturn(Arrays.asList(other));
			when(manager.isPluginEnabled(other)).thenReturn(true);
			set("pluginManager", manager);

			plugin.onGameTick(new GameTick());

			assertEquals(name, OverheadLayout.Placement.RIGHT, plugin.getOverheadPlacement());
		}
	}

	@Test
	public void explicitPlacementIgnoresOtherPluginsAndSkipsTheirLookup() throws Exception
	{
		PluginManager manager = mock(PluginManager.class);
		Plugin dynamite = mock(Plugin.class);
		when(dynamite.getName()).thenReturn("Poison Dynamite");
		when(manager.getPlugins()).thenReturn(Arrays.asList(dynamite));
		when(manager.isPluginEnabled(dynamite)).thenReturn(true);
		set("pluginManager", manager);

		when(config.overheadPlacement()).thenReturn(NpcHealthRegenConfig.OverheadPlacement.ABOVE);
		plugin.onGameTick(new GameTick());
		assertEquals(OverheadLayout.Placement.ABOVE, plugin.getOverheadPlacement());
		verify(manager, never()).getPlugins();

		when(config.overheadPlacement()).thenReturn(NpcHealthRegenConfig.OverheadPlacement.LEFT);
		assertEquals(OverheadLayout.Placement.LEFT, plugin.getOverheadPlacement());
		when(config.overheadPlacement()).thenReturn(NpcHealthRegenConfig.OverheadPlacement.RIGHT);
		assertEquals(OverheadLayout.Placement.RIGHT, plugin.getOverheadPlacement());
	}

	@Test
	public void measuredDynamiteDelayDoesNotOutliveTheTargetOrAHandSetDelay() throws Exception
	{
		Client client = mock(Client.class);
		set("client", client);
		when(config.dynamiteDelayTicks()).thenReturn(5);
		measureDynamiteDelay(client, 4);
		assertEquals(4, plugin.getDynamiteDelayTicks());

		// A different NPC is usually a different distance away.
		select(npc(1, 99));
		assertEquals(5, plugin.getDynamiteDelayTicks());

		// Setting the delay by hand replaces what was measured.
		measureDynamiteDelay(client, 3);
		assertEquals(3, plugin.getDynamiteDelayTicks());
		ConfigChanged changed = new ConfigChanged();
		changed.setGroup(NpcHealthRegenConfig.GROUP);
		changed.setKey("dynamiteDelayTicks");
		plugin.onConfigChanged(changed);
		assertEquals(5, plugin.getDynamiteDelayTicks());
	}

	/** Uses poison dynamite on the target and has it land {@code delay} ticks later. */
	private void measureDynamiteDelay(Client client, int delay) throws Exception
	{
		Widget dynamite = mock(Widget.class);
		when(dynamite.getItemId()).thenReturn(ItemID.LOVAKENGJ_DYNAMITE_POISON);
		when(client.getSelectedWidget()).thenReturn(dynamite);
		MenuOptionClicked use = mock(MenuOptionClicked.class);
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getNpc()).thenReturn(plugin.getTarget());
		when(use.getMenuEntry()).thenReturn(entry);
		when(use.getMenuAction()).thenReturn(MenuAction.WIDGET_TARGET_ON_NPC);
		set("tick", 100L);
		plugin.onMenuOptionClicked(use);
		set("tick", 100L + delay);
		hit(plugin.getTarget(), HitsplatID.DAMAGE_ME, 3, true);
	}

	private void hit(NPC npc, int type, int amount, boolean mine)
	{
		Hitsplat hitsplat = mock(Hitsplat.class);
		when(hitsplat.getHitsplatType()).thenReturn(type);
		when(hitsplat.getAmount()).thenReturn(amount);
		when(hitsplat.isMine()).thenReturn(mine);
		HitsplatApplied event = new HitsplatApplied();
		event.setActor(npc);
		event.setHitsplat(hitsplat);
		plugin.onHitsplatApplied(event);
	}

	private static GameStateChanged gameState(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}

	private Client joinParty(int world) throws Exception
	{
		Client client = mock(Client.class);
		when(client.getWorld()).thenReturn(world);
		set("client", client);
		when(partyService.isInParty()).thenReturn(true);
		when(partyService.getLocalMember()).thenReturn(new PartyMember(1L));
		PartyMember alice = new PartyMember(2L);
		alice.setDisplayName("Alice");
		when(partyService.getMemberById(2L)).thenReturn(alice);
		return client;
	}

	private static NpcHealthRegenPartyUpdate partyUpdate(long memberId, int world, int npcIndex,
		int startOffset, int endOffset)
	{
		NpcHealthRegenPartyUpdate update = new NpcHealthRegenPartyUpdate(
			world, 1, npcIndex, 100, true, startOffset, endOffset, false);
		update.setMemberId(memberId);
		return update;
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
	public void longerMeasuredDeadTimeIsUsedAsMeasured() throws Exception
	{
		// The death sequence waits for the NPC to stop moving, so the time from
		// the killing hit to the respawn legitimately varies by a tick or two.
		kill();
		set("tick", 20L);
		NPC first = npc(1, 42);
		plugin.onNpcSpawned(new NpcSpawned(first));
		assertEquals(20, plugin.getActiveRespawnTicks());

		set("tick", 100L);
		plugin.onActorDeath(new ActorDeath(first));
		set("tick", 122L);
		plugin.onNpcSpawned(new NpcSpawned(npc(1, 42)));
		assertEquals(22, plugin.getActiveRespawnTicks());
	}

	@Test
	public void firstHealAfterRespawnLearnsAndSavesDeathPause() throws Exception
	{
		plugin.getTimer().markNow(50, 100);
		set("tick", 80L);
		kill();
		set("tick", 100L);
		plugin.onNpcSpawned(new NpcSpawned(npc(1, 42)));

		// The countdown only paused for 18 of the 20 measured dead ticks.
		set("tick", 168L);
		plugin.keyPressed(key(KeyEvent.VK_F6));
		runQueuedHotkey();

		assertEquals(2, plugin.getTimer().getDeathPauseAdjustMin());
		assertEquals(2, plugin.getTimer().getDeathPauseAdjustMax());
		verify(profiles).saveDeathPause(1, 2, 2);
	}

	@Test
	public void savedDeathPauseIsLoadedForTheNpcType() throws Exception
	{
		when(profiles.loadDeathPause(1)).thenReturn(new int[]{1, 1});
		select(target);

		assertEquals(1, plugin.getTimer().getDeathPauseAdjustMin());
		assertEquals(1, plugin.getTimer().getDeathPauseAdjustMax());
		assertTrue(plugin.getTimer().isDeathPauseLearned());
	}

	@Test
	public void unlearnedDeathPauseUsesDefaultRange()
	{
		assertEquals(RegenTimer.DEFAULT_DEATH_PAUSE_ADJUST_MIN, plugin.getTimer().getDeathPauseAdjustMin());
		assertEquals(RegenTimer.DEFAULT_DEATH_PAUSE_ADJUST_MAX, plugin.getTimer().getDeathPauseAdjustMax());
		assertFalse(plugin.getTimer().isDeathPauseLearned());
	}

	@Test
	public void deathOnlySeenAtDespawnIsNotLearnedAsRespawn() throws Exception
	{
		when(target.getHealthRatio()).thenReturn(0);
		set("tick", 5L);
		plugin.onNpcDespawned(new NpcDespawned(target));
		set("tick", 20L);
		plugin.onNpcSpawned(new NpcSpawned(npc(1, 42)));

		verify(profiles, never()).save(anyInt(), anyInt(), eq(15));
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
