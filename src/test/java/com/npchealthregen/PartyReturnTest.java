package com.npchealthregen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.NPCManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two accounts in one RuneLite party watching the same NPC, one of which loses sight of it and
 * comes back. Party messages are relayed between two real plugin instances a tick after they are
 * sent, so this covers the whole path: what the account that stayed sends, and whether the account
 * that returned takes it.
 */
public class PartyReturnTest
{
	private static final int WORLD = 301;
	private static final int NPC_ID = 1;
	private static final int NPC_INDEX = 42;
	private static final int REGEN = 100;
	private static final WorldPoint SPAWN = new WorldPoint(3200, 3200, 0);

	private final List<Member> members = new ArrayList<>();

	/** One client: a plugin with its own tick counter, party connection and view of the NPC. */
	private static final class Member
	{
		final long id;
		final NpcHealthRegenPlugin plugin = new NpcHealthRegenPlugin();
		final Client client = mock(Client.class);
		final Player player = mock(Player.class);
		final List<Object> outbox = new ArrayList<>();
		NPC npc;

		Member(long id, long firstTick) throws Exception
		{
			this.id = id;
			NpcHealthRegenConfig config = mock(NpcHealthRegenConfig.class);
			when(config.regenTicks()).thenReturn(REGEN);
			when(config.observationDelayTicks()).thenReturn(3);
			when(config.learnNpcTimings()).thenReturn(true);
			when(config.shareWithParty()).thenReturn(true);
			when(config.useSharedTimers()).thenReturn(true);
			NpcTimingProfileStore profiles = mock(NpcTimingProfileStore.class);
			when(profiles.load(NPC_ID)).thenReturn(new NpcTimingProfileStore.Profile(0, 0));
			PartyService party = mock(PartyService.class);
			when(party.isInParty()).thenReturn(true);
			when(party.getLocalMember()).thenReturn(new PartyMember(id));
			doAnswer(call ->
			{
				outbox.add(call.getArgument(0));
				return null;
			}).when(party).send(any());
			when(client.getWorld()).thenReturn(WORLD);
			when(client.getLocalPlayer()).thenReturn(player);
			when(player.getWorldLocation()).thenReturn(SPAWN);

			set("config", config);
			set("profileStore", profiles);
			set("clientThread", mock(ClientThread.class));
			set("npcManager", mock(NPCManager.class));
			set("partyService", party);
			set("wsClient", mock(WSClient.class));
			set("client", client);
			set("tick", firstTick);
		}

		void set(String name, Object value) throws Exception
		{
			Field field = NpcHealthRegenPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(plugin, value);
		}

		/** A fresh in-game NPC with no health bar showing. */
		NPC newNpc()
		{
			NPC npc = mock(NPC.class);
			when(npc.getId()).thenReturn(NPC_ID);
			when(npc.getIndex()).thenReturn(NPC_INDEX);
			when(npc.getName()).thenReturn("Goblin");
			when(npc.getWorldLocation()).thenReturn(SPAWN);
			when(npc.getHealthRatio()).thenReturn(-1);
			when(npc.getHealthScale()).thenReturn(-1);
			return npc;
		}

		void select() throws Exception
		{
			npc = newNpc();
			plugin.onNpcSpawned(new NpcSpawned(npc));
			Method method = NpcHealthRegenPlugin.class.getDeclaredMethod("selectTarget", NPC.class);
			method.setAccessible(true);
			method.invoke(plugin, npc);
		}

		/** The NPC leaves this client's view without dying. */
		void loseSight()
		{
			plugin.onNpcDespawned(new NpcDespawned(npc));
		}

		void walkAwayFromSpawn()
		{
			when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3230, 0));
		}

		void walkBackToSpawn()
		{
			when(player.getWorldLocation()).thenReturn(SPAWN);
		}

		void seeDeath()
		{
			plugin.onActorDeath(new ActorDeath(npc));
		}

		void seeAlive()
		{
			npc = newNpc();
			plugin.onNpcSpawned(new NpcSpawned(npc));
		}

		void markHeal()
		{
			plugin.getTimer().markNow(plugin.getTick(), REGEN);
		}

		RegenTimer.Window window()
		{
			return plugin.getTimer().getUpcomingWindow(plugin.getTick(), REGEN);
		}

		/** @return where in the regen cycle the earliest tick of the window falls */
		long phase()
		{
			long[] window = plugin.getTimer().getUpcomingWindowTicks(plugin.getTick(), REGEN);
			return Math.floorMod(window[0], REGEN);
		}
	}

	@Before
	public void setUp()
	{
		members.clear();
	}

	private Member join(long id, long firstTick) throws Exception
	{
		Member member = new Member(id, firstTick);
		members.add(member);
		return member;
	}

	/** Runs {@code ticks} game ticks on every client, relaying party messages after each. */
	private void run(int ticks)
	{
		for (int i = 0; i < ticks; i++)
		{
			for (Member member : members)
			{
				member.plugin.onGameTick(new GameTick());
			}
			for (Member from : members)
			{
				for (Object message : from.outbox)
				{
					if (message instanceof NpcHealthRegenPartyUpdate)
					{
						NpcHealthRegenPartyUpdate update = (NpcHealthRegenPartyUpdate) message;
						update.setMemberId(from.id);
						for (Member to : members)
						{
							if (to != from)
							{
								to.plugin.applyPartyUpdate(update);
							}
						}
					}
				}
				from.outbox.clear();
			}
		}
	}

	private static void assertAgree(Member expected, Member actual)
	{
		RegenTimer.Window want = expected.window();
		RegenTimer.Window got = actual.window();
		assertNotNull("the account that stayed has no window", want);
		assertNotNull("the returning account has no window", got);
		// Each hop across the party adds a tick either side for delivery, so allow a few.
		assertTrue("returning account " + got.getEarliestTicks() + "-" + got.getLatestTicks()
				+ " vs " + want.getEarliestTicks() + "-" + want.getLatestTicks(),
			Math.abs(got.getEarliestTicks() - want.getEarliestTicks()) <= 4
				&& Math.abs(got.getLatestTicks() - want.getLatestTicks()) <= 4);
	}

	/** Both accounts select the NPC; the first sees a heal and the second picks it up. */
	private void syncedOnAHeal(Member a, Member b)
	{
		run(50);
		a.markHeal();
		run(5);
		assertAgree(a, b);
	}

	@Test
	public void sharingWorksWhileBothStayInView() throws Exception
	{
		Member a = join(1, 0);
		Member b = join(2, 7);
		a.select();
		b.select();

		syncedOnAHeal(a, b);
		run(230);
		assertAgree(a, b);
	}

	@Test
	public void aKillTheReturningAccountMissedDoesNotLeaveItFiftyTicksOut() throws Exception
	{
		Member a = join(1, 0);
		Member b = join(2, 7);
		a.select();
		b.select();
		syncedOnAHeal(a, b);

		b.loseSight();
		run(40);
		// While b is away the other account kills the NPC, which comes back 50 ticks later.
		a.seeDeath();
		run(50);
		a.seeAlive();
		run(20);
		b.seeAlive();

		// b now believes the old phase; a full regen cycle later the two must agree again.
		run(REGEN + 10);
		assertAgree(a, b);
	}

	@Test
	public void aRespawnSeenLateDoesNotLeaveTheReturningAccountFiftyTicksOut() throws Exception
	{
		Member a = join(1, 0);
		Member b = join(2, 7);
		a.select();
		b.select();
		syncedOnAHeal(a, b);

		// Both watch it die; b walks off before the respawn 50 ticks later.
		a.seeDeath();
		b.seeDeath();
		run(10);
		b.walkAwayFromSpawn();
		run(40);
		a.seeAlive();
		run(50);
		b.walkBackToSpawn();
		b.seeAlive();

		run(REGEN + 10);
		assertAgree(a, b);
	}

	@Test
	public void theAccountThatStaysIsNotDraggedByTheReturningOnesOldWindow() throws Exception
	{
		Member a = join(1, 0);
		Member b = join(2, 7);
		a.select();
		b.select();
		syncedOnAHeal(a, b);
		long phase = a.phase();

		b.loseSight();
		run(60);
		b.seeAlive();
		run(REGEN + 10);

		assertEquals(phase, a.phase());
		assertAgree(a, b);
	}

	@Test
	public void theWindowIsCorrectedWhileTheAccountIsStillAwayNotOnlyOnReturn() throws Exception
	{
		Member a = join(1, 0);
		Member b = join(2, 7);
		a.select();
		b.select();
		syncedOnAHeal(a, b);

		b.loseSight();
		run(40);
		a.seeDeath();
		run(50);
		a.seeAlive();
		// The next share from the account that watched arrives while b is still out of view.
		run(REGEN + 10);

		assertAgree(a, b);
	}

	@Test
	public void anAccountThatWatchedTheWholeTimeIsNotOverriddenByOneReturningWithAStaleWindow() throws Exception
	{
		Member a = join(1, 0);
		Member b = join(2, 7);
		a.select();
		b.select();
		syncedOnAHeal(a, b);

		// a is away while b sees the kill and respawn, so a comes back with the old phase.
		a.loseSight();
		run(40);
		b.seeDeath();
		run(50);
		b.seeAlive();
		long phase = b.phase();
		run(20);
		a.seeAlive();
		run(REGEN + 10);

		assertEquals(phase, b.phase());
		assertAgree(b, a);
	}

	@Test
	public void twoAccountsThatBothWereAwayDoNotSwapWindowsBackAndForth() throws Exception
	{
		Member a = join(1, 0);
		Member b = join(2, 7);
		a.select();
		b.select();
		run(50);
		a.markHeal();
		run(30);
		// b then sees a heal of its own that disagrees with the phase it was given, and keeps it.
		b.markHeal();
		run(10);
		long phaseA = a.phase();
		long phaseB = b.phase();
		assertTrue(phaseA != phaseB);

		a.loseSight();
		b.loseSight();
		run(30);
		a.seeAlive();
		b.seeAlive();
		run(3 * REGEN);

		// Neither is surer than the other, so each keeps what it had rather than trading.
		assertEquals(phaseA, a.phase());
		assertEquals(phaseB, b.phase());
	}
}
