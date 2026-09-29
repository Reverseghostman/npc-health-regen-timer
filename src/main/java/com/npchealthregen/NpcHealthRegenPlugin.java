package com.npchealthregen;

import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.NPCManager;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "NPC Health Regen Timer",
	description = "Tracks ordinary NPC health regeneration from health bars or inspection spells",
	tags = {"npc", "health", "regen", "regeneration", "respawn", "timer", "ticks", "inspect", "examine"}
)
public class NpcHealthRegenPlugin extends Plugin implements KeyListener
{
	private static final int INSPECTION_RESULT_TIMEOUT_TICKS = 16;
	// A party update can arrive in the sender's tick or the next, either side of
	// this client's own tick boundary.
	private static final int PARTY_LATENCY_TICKS = 1;
	// Beyond this many tiles from its spawn, an out-of-view NPC is dropped.
	private static final int OUT_OF_VIEW_CLEAR_DISTANCE = 50;
	// Longest plausible wait from using dynamite on the NPC to its hitsplat.
	private static final int MAX_DYNAMITE_DELAY_TICKS = 10;
	private static final Set<Integer> SERPENTINE_HELMS = Set.of(
		ItemID.SERPENTINE_HELM_CHARGED,
		ItemID.SERPENTINE_HELM_CHARGED_CYAN,
		ItemID.SERPENTINE_HELM_CHARGED_RED);
	private static final Map<Integer, VenomChance.Weapon> VENOM_WEAPONS = Map.ofEntries(
		Map.entry(ItemID.TOXIC_TOTS_CHARGED, VenomChance.Weapon.TRIDENT_OF_THE_SWAMP),
		Map.entry(ItemID.TOXIC_TOTS_I_CHARGED, VenomChance.Weapon.TRIDENT_OF_THE_SWAMP),
		Map.entry(ItemID.TOXIC_TOTS_CHARGED_ORN, VenomChance.Weapon.TRIDENT_OF_THE_SWAMP),
		Map.entry(ItemID.TOXIC_TOTS_I_CHARGED_ORN, VenomChance.Weapon.TRIDENT_OF_THE_SWAMP),
		Map.entry(ItemID.TOXIC_SOTD_CHARGED, VenomChance.Weapon.TOXIC_STAFF_OF_THE_DEAD),
		Map.entry(ItemID.TOXIC_SOTD_CHARGED_DEADMAN, VenomChance.Weapon.TOXIC_STAFF_OF_THE_DEAD),
		Map.entry(ItemID.TOXIC_BLOWPIPE_LOADED, VenomChance.Weapon.TOXIC_BLOWPIPE),
		Map.entry(ItemID.TOXIC_BLOWPIPE_LOADED_ORNAMENT, VenomChance.Weapon.TOXIC_BLOWPIPE),
		Map.entry(ItemID.NOXIOUS_HALBERD, VenomChance.Weapon.NOXIOUS_HALBERD));
	// Plugins that draw above an NPC's head, where the overhead countdown would otherwise sit.
	private static final Set<String> OVERHEAD_PLUGIN_NAMES = Set.of(
		"Poison Dynamite", "Poisoned NPCs", "Venom Timer");

	private enum ObservationSource
	{
		HEALTH_BAR("Health bar"),
		MONSTER_INSPECTION("Monster Inspect/Examine"),
		COMBINED("Health bar + Inspect/Examine");

		private final String label;

		ObservationSource(String label)
		{
			this.label = label;
		}
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private NpcHealthRegenConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NpcHealthRegenOverlay overlay;

	@Inject
	private NpcHealthRegenSceneOverlay sceneOverlay;

	@Inject
	private KeyManager keyManager;

	@Inject
	private NpcTimingProfileStore profileStore;

	@Inject
	private NPCManager npcManager;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	@Inject
	private PluginManager pluginManager;

	private final RegenTimer timer = new RegenTimer();
	private final Map<NPC, WorldPoint> observedSpawnPoints = new IdentityHashMap<>();
	private NPC target;
	private int targetNpcId = -1;
	private int targetNpcIndex = -1;
	private String targetName;
	private WorldPoint lastTargetPoint;
	private int lastTargetSize = 1;
	private long tick;
	private boolean deathHandled;
	private boolean deathTickReliable;
	private int activeRegenTicks = 100;
	private int activeRespawnTicks;
	private boolean learnedRegen;
	private boolean learnedRespawn;
	private ObservationSource observationSource = ObservationSource.HEALTH_BAR;
	private long inspectionSampleTick = -1;
	private long inspectionDeadlineTick = -1;
	private boolean watchingInspection;
	private MonsterInspectionReader.Snapshot inspectionBeforeCast;
	private int lastInspectedHitpoints = -1;
	private int lastInspectedDefence = -1;
	private long lastInspectedTick = -1;
	private int lastHealthRatio = -1;
	private int lastHealthScale = -1;
	private long lastHealthSampleTick = -1;
	private int maximumHitpoints = -1;
	private int baseDefence = -1;
	private String lastSharedKey;
	private boolean shareRequested;
	private String partySourceName;
	private int targetWorld = -1;
	// Wall-clock time and tick when the client stopped receiving game ticks.
	private long awayMillis = -1;
	private long awayTick;
	private long awayTicksDuringDeath = -1;
	private final VenomRing venomRing = new VenomRing();
	private final Map<Integer, Integer> freshVenomDelays = new HashMap<>();
	private long dynamiteUseTick = -1;
	private int measuredDynamiteDelay = -1;
	private VenomChance.Setup venomSetup;
	private boolean overheadCrowded;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(sceneOverlay);
		keyManager.registerKeyListener(this);
		wsClient.registerMessage(NpcHealthRegenPartyUpdate.class);
		wsClient.registerMessage(NpcHealthRegenVenomUpdate.class);
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(NpcHealthRegenVenomUpdate.class);
		wsClient.unregisterMessage(NpcHealthRegenPartyUpdate.class);
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(overlay);
		observedSpawnPoints.clear();
		clearTarget();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;
		venomSetup = readVenomSetup();
		overheadCrowded = config.overheadPlacement() == NpcHealthRegenConfig.OverheadPlacement.AUTO
			&& isAnyOverheadPluginEnabled();
		if (target != null)
		{
			if (maximumHitpoints <= 0)
			{
				loadBaseStats();
			}
			if (target.getTransformedComposition() != null)
			{
				lastTargetSize = target.getTransformedComposition().getSize();
			}
			processHealthSample(target);
			if (inspectionSampleTick >= 0 || watchingInspection)
			{
				processInspectionResult();
			}
		}
		else if (isTargetOutOfView() && isFarFromTarget())
		{
			// Walked or teleported away: stop tracking an NPC that is not coming back.
			clearTarget();
		}
		venomRing.onTick(tick);
		shareTimer();
	}

	private boolean isFarFromTarget()
	{
		Player player = client == null ? null : client.getLocalPlayer();
		if (player == null || lastTargetPoint == null)
		{
			return false;
		}
		WorldPoint location = player.getWorldLocation();
		return location.getPlane() != lastTargetPoint.getPlane()
			|| location.distanceTo2D(lastTargetPoint) > OUT_OF_VIEW_CLEAR_DISTANCE;
	}

	/** Sends the tracked NPC's regen window to the party whenever it changes. */
	private void shareTimer()
	{
		if (!config.shareWithParty() || target == null
			|| timer.getState() != RegenTimer.State.TRACKING || !partyService.isInParty()
			|| partyService.getLocalMember() == null)
		{
			return;
		}

		long[] window = timer.getUpcomingWindowTicks(tick, activeRegenTicks);
		if (window == null || window[1] - window[0] >= activeRegenTicks)
		{
			return;
		}

		int world = client.getWorld();
		// The absolute window only moves when it is refined or rolls to the next
		// cycle, so this sends at most about once per regen cycle when idle.
		String key = world + ":" + targetNpcId + ":" + targetNpcIndex + ":" + activeRegenTicks
			+ ":" + window[0] + ":" + window[1];
		if (!shareRequested && key.equals(lastSharedKey))
		{
			return;
		}

		partyService.send(new NpcHealthRegenPartyUpdate(world, targetNpcId, targetNpcIndex,
			activeRegenTicks, learnedRegen, (int) (window[0] - tick), (int) (window[1] - tick)));
		lastSharedKey = key;
		shareRequested = false;
	}

	@Subscribe
	public void onNpcHealthRegenPartyUpdate(NpcHealthRegenPartyUpdate update)
	{
		// Party messages arrive on the websocket thread.
		clientThread.invokeLater(() -> applyPartyUpdate(update));
	}

	void applyPartyUpdate(NpcHealthRegenPartyUpdate update)
	{
		PartyMember local = partyService.getLocalMember();
		if (!config.useSharedTimers() || target == null || local == null
			|| local.getMemberId() == update.getMemberId()
			|| update.getWorld() != client.getWorld()
			|| update.getNpcId() != targetNpcId || update.getNpcIndex() != targetNpcIndex
			|| update.getRegenTicks() <= 0)
		{
			return;
		}

		if (update.getRegenTicks() != activeRegenTicks)
		{
			// Only adopt a rate the sender measured, and never over our own.
			if (learnedRegen || !update.isRegenLearned())
			{
				return;
			}
			activeRegenTicks = update.getRegenTicks();
		}

		long start = tick + update.getWindowStartOffset() - PARTY_LATENCY_TICKS;
		long end = tick + update.getWindowEndOffset() + PARTY_LATENCY_TICKS;
		if (timer.applySharedWindow(start, end, activeRegenTicks))
		{
			PartyMember sender = partyService.getMemberById(update.getMemberId());
			partySourceName = sender == null ? "Party member" : sender.getDisplayName();
		}
	}

	@Subscribe
	public void onNpcHealthRegenVenomUpdate(NpcHealthRegenVenomUpdate update)
	{
		clientThread.invokeLater(() -> applyVenomUpdate(update));
	}

	void applyVenomUpdate(NpcHealthRegenVenomUpdate update)
	{
		PartyMember local = partyService.getLocalMember();
		if (!config.useSharedTimers() || !config.showVenomRing() || targetNpcId < 0 || local == null
			|| local.getMemberId() == update.getMemberId()
			|| update.getWorld() != targetWorld
			|| update.getNpcId() != targetNpcId || update.getNpcIndex() != targetNpcIndex)
		{
			return;
		}

		if (update.isCancelled())
		{
			if (venomRing.isShared())
			{
				venomRing.cancel();
			}
			return;
		}

		venomRing.startShared(
			tick + update.getAppliedOffset(),
			tick + update.getProcEarliestOffset() - PARTY_LATENCY_TICKS,
			tick + update.getProcLatestOffset() + PARTY_LATENCY_TICKS,
			update.isFullHpKnown() ? tick + update.getFullHpOffset() + PARTY_LATENCY_TICKS : -1);
	}

	private void shareVenom(boolean cancelled)
	{
		if (!config.shareWithParty() || !partyService.isInParty() || partyService.getLocalMember() == null)
		{
			return;
		}

		long fullHp = venomRing.getFullHpTick();
		partyService.send(new NpcHealthRegenVenomUpdate(targetWorld, targetNpcId, targetNpcIndex, cancelled,
			(int) (venomRing.getAppliedTick() - tick),
			(int) (venomRing.getProcEarliest() - tick),
			(int) (venomRing.getProcLatest() - tick),
			fullHp >= 0, fullHp >= 0 ? (int) (fullHp - tick) : 0));
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		// Bring a newly joined member up to date on the next tick.
		shareRequested = true;
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		lastSharedKey = null;
		shareRequested = true;
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (target != null && inspectionSampleTick >= 0)
		{
			processInspectionResult();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (target == null || event.getMenuAction() != MenuAction.WIDGET_TARGET_ON_NPC)
		{
			return;
		}

		Widget selected = client.getSelectedWidget();
		if (selected != null && selected.getItemId() == ItemID.LOVAKENGJ_DYNAMITE_POISON)
		{
			if (event.getMenuEntry().getNpc() == target)
			{
				// Timed to its hitsplat to learn how long dynamite takes to land.
				dynamiteUseTick = tick;
			}
			return;
		}

		if (!isMonsterInspectionCast(event))
		{
			return;
		}

		// A same-named NPC's panel must not update the selected NPC's timer.
		watchingInspection = false;
		inspectionSampleTick = -1;
		inspectionDeadlineTick = -1;
		inspectionBeforeCast = null;
		if (event.getMenuEntry().getNpc() != target)
		{
			return;
		}
		inspectionBeforeCast = MonsterInspectionReader.find(client, targetName);

		observationSource = lastHealthRatio >= 0
			? ObservationSource.COMBINED : ObservationSource.MONSTER_INSPECTION;
		inspectionSampleTick = tick;
		inspectionDeadlineTick = tick + INSPECTION_RESULT_TIMEOUT_TICKS;
	}

	private boolean isMonsterInspectionCast(MenuOptionClicked event)
	{
		if (containsMonsterInspectionName(event.getMenuOption())
			|| containsMonsterInspectionName(event.getMenuTarget()))
		{
			return true;
		}

		// Fall back to the selected spell itself in case the menu text omits it.
		return widgetHasMonsterInspectionName(event.getWidget())
			|| widgetHasMonsterInspectionName(client.getSelectedWidget());
	}

	private static boolean widgetHasMonsterInspectionName(Widget widget)
	{
		if (widget == null)
		{
			return false;
		}
		int id = widget.getId();
		return id == InterfaceID.MagicSpellbook.MONSTER_EXAMINE
			|| id == InterfaceID.MagicSpellbook.MONSTER_INSPECT
			|| containsMonsterInspectionName(widget.getName());
	}

	private static boolean containsMonsterInspectionName(String text)
	{
		if (text == null)
		{
			return false;
		}

		String normalised = Text.removeTags(text).toLowerCase(Locale.ENGLISH);
		return normalised.contains("monster inspect") || normalised.contains("monster examine");
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (event.getMenuEntry().getType() != MenuAction.EXAMINE_NPC
			|| !client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		NPC npc = event.getMenuEntry().getNpc();
		if (npc == null)
		{
			return;
		}

		boolean isCurrentTarget = npc == target;
		if (isCurrentTarget)
		{
			client.getMenu().createMenuEntry(-1)
				.setOption("Recalibrate Regen Timer")
				.setTarget(event.getTarget())
				.setWorldViewId(event.getMenuEntry().getWorldViewId())
				.setIdentifier(event.getIdentifier())
				.setType(MenuAction.RUNELITE)
				.onClick(menuEntry ->
				{
					if (npc == target)
					{
						recalibrate();
					}
				});
		}
		client.getMenu().createMenuEntry(-1)
			.setOption(isCurrentTarget ? "Clear Regen Timer" : "Select Regen Timer")
			.setTarget(event.getTarget())
			.setWorldViewId(event.getMenuEntry().getWorldViewId())
			.setIdentifier(event.getIdentifier())
			.setType(MenuAction.RUNELITE)
			.onClick(menuEntry ->
			{
				if (isCurrentTarget != (npc == target))
				{
					return;
				}

				if (isCurrentTarget)
				{
					clearTarget();
				}
				else
				{
					selectTarget(npc);
				}
			});
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		if (target != npc)
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		if (hitsplat != null)
		{
			processVenomHitsplat(hitsplat);
		}
		if (hitsplat != null && hitsplat.getAmount() > 0)
		{
			timer.invalidateInspectionHealth();
		}
		processHealthSample(npc);
	}

	private void processVenomHitsplat(Hitsplat hitsplat)
	{
		int type = hitsplat.getHitsplatType();
		if (type == HitsplatID.POISON || type == HitsplatID.VENOM)
		{
			VenomRing.Status before = venomRing.getStatus();
			venomRing.onPoisonHit(tick, type == HitsplatID.VENOM);
			if (before == VenomRing.Status.COUNTING && venomRing.getStatus() == VenomRing.Status.PROCCED)
			{
				freshVenomDelays.put(targetNpcId, venomRing.getFreshDelay());
			}
			return;
		}

		if (!hitsplat.isMine())
		{
			return;
		}

		if (dynamiteUseTick >= 0 && tick - dynamiteUseTick <= MAX_DYNAMITE_DELAY_TICKS)
		{
			measuredDynamiteDelay = (int) (tick - dynamiteUseTick);
			dynamiteUseTick = -1;
			return;
		}

		// A hit that passes its accuracy roll always deals at least 1 damage, so a hitsplat of 0
		// is a miss or a splash, and that cannot envenom.
		if (hitsplat.getAmount() <= 0)
		{
			return;
		}

		// A charged serpentine helm with a toxic blowpipe, trident of the swamp or toxic staff of
		// the dead envenoms every landed hit. Other setups only do so some of the time, which is
		// not enough to time a dynamite by, so they start no ring.
		VenomChance.Setup setup = readVenomSetup();
		venomSetup = setup;
		if (config.showVenomRing() && setup != null && setup.isGuaranteed()
			&& venomRing.start(tick, fullHpTickAfterHit(hitsplat.getAmount())))
		{
			shareVenom(false);
		}
	}

	/** @return the latest tick by which regen undoes a hit of at least 1 landing now, or -1 */
	private long fullHpTickAfterHit(int damage)
	{
		long[] window = timer.getUpcomingWindowTicks(tick + 1, activeRegenTicks);
		if (window == null)
		{
			return -1;
		}
		// Regen runs before player hits within a tick, so a regen possibly on or
		// before this tick came first and the next is a whole cycle later.
		long latest = window[0] <= tick ? tick + activeRegenTicks : window[1];
		return latest + (long) (damage - 1) * activeRegenTicks;
	}

	/** @return the worn venom weapon and whether a charged serpentine helm is worn, or null */
	private VenomChance.Setup readVenomSetup()
	{
		ItemContainer worn = client == null ? null : client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return null;
		}
		Item weapon = worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		VenomChance.Weapon kind = weapon == null ? null : VENOM_WEAPONS.get(weapon.getId());
		if (kind == null)
		{
			return null;
		}
		Item head = worn.getItem(EquipmentInventorySlot.HEAD.getSlotIdx());
		return new VenomChance.Setup(kind, head != null && SERPENTINE_HELMS.contains(head.getId()));
	}

	/** @return whether a plugin that draws above NPCs' heads (see OVERHEAD_PLUGIN_NAMES) is enabled */
	private boolean isAnyOverheadPluginEnabled()
	{
		if (pluginManager == null)
		{
			return false;
		}
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (OVERHEAD_PLUGIN_NAMES.contains(plugin.getName()) && pluginManager.isPluginEnabled(plugin))
			{
				return true;
			}
		}
		return false;
	}

	private void processHealthSample(NPC npc)
	{
		int healthRatio = npc.getHealthRatio();
		int healthScale = npc.getHealthScale();
		if (healthRatio >= 0 && healthScale > 0)
		{
			if (healthScale == lastHealthScale && healthRatio < lastHealthRatio)
			{
				timer.invalidateInspectionHealth();
			}
			if (healthRatio != lastHealthRatio || healthScale != lastHealthScale
				|| lastHealthSampleTick < 0)
			{
				lastHealthSampleTick = tick;
			}
			lastHealthRatio = healthRatio;
			lastHealthScale = healthScale;
			if (lastInspectedHitpoints >= 0)
			{
				observationSource = ObservationSource.COMBINED;
			}
		}
		int detectedInterval = timer.sampleHealth(
			healthRatio, healthScale, tick, activeRegenTicks,
			config.observationDelayTicks());
		applyDetectedInterval(detectedInterval);
	}

	private void processInspectionResult()
	{
		if (inspectionSampleTick >= 0 && tick < inspectionSampleTick)
		{
			return;
		}
		if (inspectionSampleTick >= 0 && tick > inspectionDeadlineTick)
		{
			inspectionSampleTick = -1;
			inspectionDeadlineTick = -1;
			inspectionBeforeCast = null;
			return;
		}
		if (inspectionSampleTick < 0 && !watchingInspection)
		{
			return;
		}

		MonsterInspectionReader.Snapshot snapshot = MonsterInspectionReader.find(client, targetName);
		if (snapshot == null)
		{
			watchingInspection = false;
			inspectionBeforeCast = null;
			return;
		}
		if (inspectionBeforeCast != null
			&& snapshot.getHitpoints() == inspectionBeforeCast.getHitpoints()
			&& snapshot.getDefence() == inspectionBeforeCast.getDefence())
		{
			return;
		}
		if (inspectionSampleTick < 0 && snapshot.getHitpoints() == lastInspectedHitpoints
			&& snapshot.getDefence() == lastInspectedDefence)
		{
			// A static panel is not a new server observation every tick.
			return;
		}
		watchingInspection = true;
		inspectionBeforeCast = null;

		lastInspectedHitpoints = snapshot.getHitpoints();
		lastInspectedDefence = snapshot.getDefence();
		if (lastInspectedDefence >= 0)
		{
			baseDefence = Math.max(baseDefence, lastInspectedDefence);
		}
		lastInspectedTick = tick;
		observationSource = lastHealthRatio >= 0
			? ObservationSource.COMBINED : ObservationSource.MONSTER_INSPECTION;
		long earliestSampleTick = inspectionSampleTick >= 0 ? inspectionSampleTick : tick;
		inspectionSampleTick = -1;
		inspectionDeadlineTick = -1;
		applyDetectedInterval(timer.sampleInspectionStats(
			lastInspectedHitpoints, lastInspectedDefence, maximumHitpoints,
			earliestSampleTick, tick, activeRegenTicks));
	}

	private void applyDetectedInterval(int detectedInterval)
	{
		if (detectedInterval > 0 && config.learnNpcTimings())
		{
			// RegenTimer already retains the preferred interval when the measured
			// uncertainty permits it. Do not round away an exact observation here.
			activeRegenTicks = detectedInterval;
			learnedRegen = true;
			saveActiveProfile();
		}
		if (timer.consumeDeathPauseUpdate() && config.learnNpcTimings() && targetNpcId >= 0)
		{
			profileStore.saveDeathPause(targetNpcId,
				timer.getDeathPauseAdjustMin(), timer.getDeathPauseAdjustMax());
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == target)
		{
			handleTargetDeath(true);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		observedSpawnPoints.remove(event.getNpc());
		if (event.getNpc() != target)
		{
			return;
		}

		if (event.getNpc().getHealthRatio() == 0)
		{
			handleTargetDeath(false);
		}
		else
		{
			// Out of view, or the client is logging out: keep its timer and pick
			// it up again when it reappears. Health seen then is a new baseline.
			target = null;
			resetObservationSource();
			timer.sampleHealth(-1, 0, tick);
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		observedSpawnPoints.put(npc, npc.getWorldLocation());
		if (target != null || targetNpcId < 0 || !isExpectedRespawn(npc))
		{
			return;
		}

		target = npc;
		if (timer.getState() != RegenTimer.State.WAITING_FOR_RESPAWN)
		{
			// Back in view, or logged back in: the timer carries on.
			return;
		}

		lastTargetPoint = observedSpawnPoints.get(npc);
		deathHandled = false;
		resetObservationSource();
		if (awayTicksDuringDeath >= 0)
		{
			// It respawned while logged out: the sighting is late by an unknown
			// amount, so assume the expected respawn if it fits.
			long expected = timer.getExpectedRespawnTick();
			boolean expectedFits = expected >= 0 && expected <= tick;
			timer.onRespawn(expectedFits ? expected : tick);
			if (!expectedFits)
			{
				timer.widenPhase(awayTicksDuringDeath);
			}
			awayTicksDuringDeath = -1;
			return;
		}
		// The measurement is used as-is: it can legitimately vary by a tick or two
		// between kills, because the death sequence waits for the NPC to stop moving.
		int measuredRespawnTicks = timer.onRespawn(tick);
		// A death only noticed at despawn was recorded late, so its time is too short.
		if (measuredRespawnTicks > 0 && deathTickReliable && config.learnNpcTimings())
		{
			activeRespawnTicks = measuredRespawnTicks;
			learnedRespawn = true;
			saveActiveProfile();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!NpcHealthRegenConfig.GROUP.equals(event.getGroup()) || targetNpcId < 0)
		{
			return;
		}

		if ("regenTicks".equals(event.getKey()) && !learnedRegen)
		{
			activeRegenTicks = config.regenTicks();
		}
		else if ("respawnTicks".equals(event.getKey()) && !learnedRespawn)
		{
			activeRespawnTicks = config.respawnTicks();
			timer.updateExpectedRespawnTicks(activeRespawnTicks);
		}
		else if ("learnNpcTimings".equals(event.getKey()))
		{
			loadActiveProfile();
		}
		else if ("dynamiteDelayTicks".equals(event.getKey()))
		{
			// A delay set by hand replaces the one measured.
			measuredDynamiteDelay = -1;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.HOPPING || (state == GameState.LOGIN_SCREEN && targetNpcId < 0))
		{
			observedSpawnPoints.clear();
			clearTarget();
			tick = 0;
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.CONNECTION_LOST)
		{
			// Keep the selected NPC and its timer through a logout on the same world.
			observedSpawnPoints.clear();
			if (awayMillis < 0)
			{
				awayMillis = System.currentTimeMillis();
				awayTick = tick;
			}
			if (venomRing.getStatus() == VenomRing.Status.COUNTING && !venomRing.isShared())
			{
				// Logging out takes the NPC out of combat, which stops the venom.
				shareVenom(true);
			}
			venomRing.cancel();
			target = null;
			resetObservationSource();
			timer.sampleHealth(-1, 0, tick);
		}
		else if (state == GameState.LOGGED_IN && awayMillis >= 0)
		{
			long awayTicks = Math.round((System.currentTimeMillis() - awayMillis) / 600.0);
			awayMillis = -1;
			if (client.getWorld() != targetWorld)
			{
				observedSpawnPoints.clear();
				clearTarget();
				return;
			}
			// No game ticks arrive while away; carry the count on by the time that
			// passed. Server ticks are not exactly 600 ms, so allow a tick plus one
			// per five minutes away.
			tick = awayTick + awayTicks;
			timer.widenPhase(1 + awayTicks / 500);
			if (timer.getState() == RegenTimer.State.WAITING_FOR_RESPAWN)
			{
				awayTicksDuringDeath = awayTicks;
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (config.markRegenHotkey().matches(event))
		{
			clientThread.invokeLater(() ->
			{
				if (target != null)
				{
					applyDetectedInterval(timer.markNow(tick, activeRegenTicks));
				}
			});
		}
		else if (config.resetHotkey().matches(event))
		{
			clientThread.invokeLater(() ->
			{
				if (target != null)
				{
					recalibrate();
				}
			});
		}
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
	}

	private void selectTarget(NPC npc)
	{
		target = npc;
		targetNpcId = npc.getId();
		targetNpcIndex = npc.getIndex();
		targetName = npc.getName() == null ? "NPC " + npc.getId() : Text.removeTags(npc.getName());
		targetWorld = client == null ? -1 : client.getWorld();
		lastTargetPoint = observedSpawnPoints.getOrDefault(npc, npc.getWorldLocation());
		lastTargetSize = npc.getTransformedComposition() == null
			? 1 : npc.getTransformedComposition().getSize();
		deathHandled = false;
		awayMillis = -1;
		awayTicksDuringDeath = -1;
		dynamiteUseTick = -1;
		// How long dynamite takes to land depends on the distance to this NPC.
		measuredDynamiteDelay = -1;
		venomRing.reset();
		venomRing.setFreshDelay(freshVenomDelays.getOrDefault(npc.getId(), VenomRing.TIMER_TICKS));
		timer.reset();
		resetObservationSource();
		maximumHitpoints = -1;
		baseDefence = -1;
		loadBaseStats();
		loadActiveProfile();
	}

	private void loadBaseStats()
	{
		Integer health = npcManager.getHealth(targetNpcId);
		if (health != null && health > 0)
		{
			maximumHitpoints = Math.max(maximumHitpoints, health);
		}
	}

	private void recalibrate()
	{
		// The death-pause adjustment is kept, like the respawn time: it does not
		// depend on the regen rate being recalibrated.
		timer.reset();
		resetObservationSource();
		activeRegenTicks = config.regenTicks();
		learnedRegen = false;
		// Clear only this NPC type's regen rate; retain its respawn measurement.
		profileStore.save(targetNpcId, 0, learnedRespawn ? activeRespawnTicks : 0);
	}

	private void loadActiveProfile()
	{
		NpcTimingProfileStore.Profile profile = config.learnNpcTimings()
			? profileStore.load(targetNpcId) : new NpcTimingProfileStore.Profile(0, 0);
		int storedRegenTicks = profile.getRegenTicks();
		learnedRegen = storedRegenTicks > 0;
		learnedRespawn = profile.getRespawnTicks() > 0;
		activeRegenTicks = learnedRegen ? storedRegenTicks : config.regenTicks();
		activeRespawnTicks = learnedRespawn ? profile.getRespawnTicks() : config.respawnTicks();
		timer.updateExpectedRespawnTicks(activeRespawnTicks);

		int[] deathPause = config.learnNpcTimings() ? profileStore.loadDeathPause(targetNpcId) : null;
		if (deathPause != null)
		{
			timer.setDeathPauseAdjustment(deathPause[0], deathPause[1], true);
		}
		else
		{
			timer.setDeathPauseAdjustment(RegenTimer.DEFAULT_DEATH_PAUSE_ADJUST_MIN,
				RegenTimer.DEFAULT_DEATH_PAUSE_ADJUST_MAX, false);
		}
	}

	private void saveActiveProfile()
	{
		if (targetNpcId >= 0)
		{
			profileStore.save(
				targetNpcId,
				learnedRegen ? activeRegenTicks : 0,
				learnedRespawn ? activeRespawnTicks : 0);
		}
	}

	private void handleTargetDeath(boolean deathTickReliable)
	{
		if (deathHandled)
		{
			return;
		}

		deathHandled = true;
		this.deathTickReliable = deathTickReliable;
		target = null;
		resetObservationSource();
		timer.onDeath(tick, activeRespawnTicks, deathTickReliable);
		venomRing.onDeath(tick);
	}

	private boolean isExpectedRespawn(NPC npc)
	{
		if (targetNpcId != npc.getId())
		{
			return false;
		}

		return targetNpcIndex == npc.getIndex();
	}

	private void clearTarget()
	{
		target = null;
		targetNpcId = -1;
		targetNpcIndex = -1;
		targetName = null;
		targetWorld = -1;
		lastTargetPoint = null;
		lastTargetSize = 1;
		deathHandled = false;
		awayMillis = -1;
		awayTicksDuringDeath = -1;
		dynamiteUseTick = -1;
		measuredDynamiteDelay = -1;
		venomRing.reset();
		activeRegenTicks = config == null ? 100 : config.regenTicks();
		activeRespawnTicks = 0;
		learnedRegen = false;
		learnedRespawn = false;
		maximumHitpoints = -1;
		baseDefence = -1;
		resetObservationSource();
		timer.reset();
	}

	VenomRing getVenomRing()
	{
		return venomRing;
	}

	/** @return the worn venom weapon and helm as of the last tick, or null if no venom weapon is worn */
	VenomChance.Setup getVenomSetup()
	{
		return venomSetup;
	}

	/** @return where the overhead countdown and venom ring go, with Automatic resolved */
	OverheadLayout.Placement getOverheadPlacement()
	{
		NpcHealthRegenConfig.OverheadPlacement choice = config.overheadPlacement();
		if (choice == null)
		{
			choice = NpcHealthRegenConfig.OverheadPlacement.AUTO;
		}
		switch (choice)
		{
			case ABOVE:
				return OverheadLayout.Placement.ABOVE;
			case RIGHT:
				return OverheadLayout.Placement.RIGHT;
			case LEFT:
				return OverheadLayout.Placement.LEFT;
			default:
				// Beside the NPC while another plugin is using the space above its head.
				return overheadCrowded ? OverheadLayout.Placement.RIGHT : OverheadLayout.Placement.ABOVE;
		}
	}

	/** @return ticks from using dynamite on the NPC to its hit, measured or configured */
	int getDynamiteDelayTicks()
	{
		return measuredDynamiteDelay > 0 ? measuredDynamiteDelay : config.dynamiteDelayTicks();
	}

	/** @return whether the selected NPC is alive but out of view (or the client is away) */
	boolean isTargetOutOfView()
	{
		return target == null && targetNpcId >= 0
			&& timer.getState() != RegenTimer.State.WAITING_FOR_RESPAWN;
	}

	/** @return who shared the timer, while it still rests on their phase */
	String getPartySourceName()
	{
		return timer.isPhaseFromParty() ? partySourceName : null;
	}

	private void resetObservationSource()
	{
		watchingInspection = false;
		inspectionBeforeCast = null;
		observationSource = ObservationSource.HEALTH_BAR;
		inspectionSampleTick = -1;
		inspectionDeadlineTick = -1;
		lastInspectedHitpoints = -1;
		lastInspectedDefence = -1;
		lastInspectedTick = -1;
		lastHealthRatio = -1;
		lastHealthScale = -1;
		lastHealthSampleTick = -1;
	}

	RecoveryCalculator.Range getCurrentHitpoints()
	{
		if (isInspectionHealthCurrent())
		{
			return RecoveryCalculator.projectHealth(
				new RecoveryCalculator.Range(lastInspectedHitpoints, lastInspectedHitpoints),
				maximumHitpoints, lastInspectedTick, tick, activeRegenTicks, timer);
		}
		RecoveryCalculator.Range observed = RecoveryCalculator.healthFromRatio(
			lastHealthRatio, lastHealthScale, maximumHitpoints);
		return RecoveryCalculator.projectHealth(
			observed, maximumHitpoints, lastHealthSampleTick, tick, activeRegenTicks, timer);
	}

	long getCurrentHitpointsSampleTick()
	{
		return tick;
	}

	RecoveryCalculator.TickWindow getTimeUntilFullHitpoints()
	{
		if (timer.getState() == RegenTimer.State.WAITING_FOR_RESPAWN)
		{
			return null;
		}
		return RecoveryCalculator.timeUntilFull(
			getCurrentHitpoints(), maximumHitpoints, getCurrentHitpointsSampleTick(),
			tick, activeRegenTicks, timer);
	}

	boolean isCurrentHitpointsExact()
	{
		return isInspectionHealthCurrent() && lastInspectedTick == tick;
	}

	private boolean isInspectionHealthCurrent()
	{
		return lastInspectedHitpoints >= 0
			&& (lastHealthSampleTick < 0 || lastInspectedTick >= lastHealthSampleTick);
	}

	RecoveryCalculator.Range getDefenceAtFullHitpoints()
	{
		return RecoveryCalculator.defenceAtFull(
			lastInspectedDefence, baseDefence,
			lastInspectedHitpoints < 0 ? null
				: new RecoveryCalculator.Range(lastInspectedHitpoints, lastInspectedHitpoints),
			maximumHitpoints);
	}

	int getMaximumHitpoints()
	{
		return maximumHitpoints;
	}

	int getBaseDefence()
	{
		return baseDefence;
	}

	String getTargetName()
	{
		return targetName;
	}

	long getTick()
	{
		return tick;
	}

	RegenTimer getTimer()
	{
		return timer;
	}

	int getActiveRegenTicks()
	{
		return activeRegenTicks;
	}

	int getActiveRespawnTicks()
	{
		return activeRespawnTicks;
	}

	boolean isLearnedRegen()
	{
		return learnedRegen;
	}

	boolean isLearnedRespawn()
	{
		return learnedRespawn;
	}

	String getObservationSourceLabel()
	{
		return observationSource.label;
	}

	int getLastInspectedHitpoints()
	{
		return lastInspectedHitpoints;
	}

	int getLastInspectedDefence()
	{
		return lastInspectedDefence;
	}

	boolean isUsingMonsterInspection()
	{
		return lastInspectedHitpoints >= 0 || inspectionSampleTick >= 0;
	}

	boolean isInspectionPending()
	{
		return inspectionSampleTick >= 0;
	}

	NPC getTarget()
	{
		return target;
	}

	WorldPoint getLastTargetPoint()
	{
		return lastTargetPoint;
	}

	int getLastTargetSize()
	{
		return lastTargetSize;
	}

	@Provides
	NpcHealthRegenConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NpcHealthRegenConfig.class);
	}
}
