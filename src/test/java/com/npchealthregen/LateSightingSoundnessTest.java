package com.npchealthregen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.NPCManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A respawn seen late (out of range, on another plane) leaves the regen phase in doubt. Whatever the
 * NPC's real respawn time, this kill's difference from the learned one, and how long the watch was
 * lost for, the window that results must still contain the real phase. A window as wide as the
 * regen interval says nothing about it, which is sound but not useful, so those are only counted.
 */
public class LateSightingSoundnessTest
{
	private static final WorldPoint SPAWN = new WorldPoint(3200, 3200, 0);

	private static void set(Object o, String name, Object value) throws Exception
	{
		Field f = NpcHealthRegenPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, value);
	}

	private static NPC npc()
	{
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(1);
		when(npc.getIndex()).thenReturn(42);
		when(npc.getName()).thenReturn("Goblin");
		when(npc.getWorldLocation()).thenReturn(SPAWN);
		when(npc.getHealthRatio()).thenReturn(-1);
		return npc;
	}

	@Test
	public void lateSightingWindowAlwaysContainsTheTruePhase() throws Exception
	{
		Random random = new Random(12345);
		int unsound = 0;
		int unsoundKnown = 0;
		int narrow = 0;
		for (int i = 0; i < 2500; i++)
		{
			boolean known = random.nextBoolean();
			int respawn = 8 + random.nextInt(150);
			int knownError = known ? random.nextInt(5) - 2 : 0;
			int adj = random.nextInt(3);
			long anchor = 2000 + random.nextInt(100);
			long death = anchor + 1 + random.nextInt(95);
			// The watch is lost some time before the respawn; it is seen again well after it.
			long lost = death + 1 + random.nextInt(respawn);
			long sighting = death + respawn + random.nextInt(300);

			NpcHealthRegenPlugin plugin = new NpcHealthRegenPlugin();
			NpcHealthRegenConfig config = mock(NpcHealthRegenConfig.class);
			when(config.regenTicks()).thenReturn(100);
			when(config.learnNpcTimings()).thenReturn(true);
			NpcTimingProfileStore profiles = mock(NpcTimingProfileStore.class);
			when(profiles.load(1)).thenReturn(new NpcTimingProfileStore.Profile(0,
				known ? Math.max(1, respawn + knownError) : 0));
			Client client = mock(Client.class);
			Player player = mock(Player.class);
			when(client.getLocalPlayer()).thenReturn(player);
			when(player.getWorldLocation()).thenReturn(SPAWN);
			set(plugin, "config", config);
			set(plugin, "profileStore", profiles);
			set(plugin, "clientThread", mock(ClientThread.class));
			set(plugin, "npcManager", mock(NPCManager.class));
			set(plugin, "partyService", mock(PartyService.class));
			set(plugin, "wsClient", mock(WSClient.class));
			set(plugin, "client", client);

			NPC target = npc();
			Method select = NpcHealthRegenPlugin.class.getDeclaredMethod("selectTarget", NPC.class);
			select.setAccessible(true);
			select.invoke(plugin, target);
			plugin.getTimer().markNow(anchor, 100);
			set(plugin, "tick", death);
			plugin.onActorDeath(new ActorDeath(target));

			set(plugin, "tick", lost - 1);
			when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3240, 0));
			plugin.onGameTick(new GameTick());
			set(plugin, "tick", sighting);
			when(player.getWorldLocation()).thenReturn(SPAWN);
			plugin.onNpcSpawned(new NpcSpawned(npc()));

			long truth = anchor + respawn - adj;
			long[] window = plugin.getTimer().getUpcomingWindowTicks(sighting, 100);
			if (window[1] - window[0] >= 100)
			{
				continue;
			}
			narrow++;
			boolean covers = false;
			for (long k = -10; k <= 10 && !covers; k++)
			{
				covers = window[0] <= truth + 100 * k && truth + 100 * k <= window[1];
			}
			if (!covers)
			{
				if (known)
				{
					unsoundKnown++;
				}
				else
				{
					unsound++;
				}
			}
		}
		// The check is only worth something if many of the windows were informative.
		assertTrue("too few informative windows: " + narrow, narrow > 500);
		assertTrue("unsound with unknown respawn: " + unsound, unsound == 0);
		assertTrue("unsound with known respawn: " + unsoundKnown, unsoundKnown == 0);
	}
}
