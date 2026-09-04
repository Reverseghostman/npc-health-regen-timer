package com.npchealthregen;

import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.NPCManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "NPC Health Regen Timer",
	description = "Tracks ordinary NPC health regeneration from health bars or inspection spells",
	tags = {"npc", "health", "regen", "regeneration", "respawn", "timer", "ticks", "inspect", "examine"}
)
public class NpcHealthRegenPlugin extends Plugin implements KeyListener
{
	private static final int INSPECTION_RESULT_WAIT_TICKS = 3;
	private static final int INSPECTION_RESULT_TIMEOUT_TICKS = 8;

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
	private int activeRegenTicks = 100;
	private int activeRespawnTicks;
	private boolean learnedRegen;
	private boolean learnedRespawn;
	private ObservationSource observationSource = ObservationSource.HEALTH_BAR;
	private long inspectionSampleTick = -1;
	private long inspectionDeadlineTick = -1;
	private int lastInspectedHitpoints = -1;
	private int lastInspectedDefence = -1;
	private long lastInspectedTick = -1;
	private int lastHealthRatio = -1;
	private int lastHealthScale = -1;
	private long lastHealthSampleTick = -1;
	private int maximumHitpoints = -1;
	private int baseDefence = -1;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(sceneOverlay);
		keyManager.registerKeyListener(this);
	}

	@Override
	protected void shutDown()
	{
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
			if (inspectionSampleTick >= 0)
			{
				processInspectionResult();
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (target == null || event.getMenuAction() != MenuAction.WIDGET_TARGET_ON_NPC
			|| event.getMenuEntry().getNpc() != target)
		{
			return;
		}

		String rawMenuTarget = event.getMenuTarget();
		if (rawMenuTarget == null)
		{
			return;
		}

		String menuTarget = Text.removeTags(rawMenuTarget).toLowerCase(Locale.ENGLISH);
		if (!menuTarget.contains("monster inspect") && !menuTarget.contains("monster examine"))
		{
			return;
		}

		observationSource = lastHealthRatio >= 0
			? ObservationSource.COMBINED : ObservationSource.MONSTER_INSPECTION;
		inspectionSampleTick = tick + INSPECTION_RESULT_WAIT_TICKS;
		inspectionDeadlineTick = tick + INSPECTION_RESULT_TIMEOUT_TICKS;
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

		client.createMenuEntry(-1)
			.setOption("Select Regen Timer")
			.setTarget(event.getTarget())
			.setWorldViewId(event.getMenuEntry().getWorldViewId())
			.setIdentifier(event.getIdentifier())
			.setType(MenuAction.RUNELITE)
			.onClick(menuEntry -> selectTarget(npc));
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

		processHealthSample(npc);
	}

	private void processHealthSample(NPC npc)
	{
		int healthRatio = npc.getHealthRatio();
		int healthScale = npc.getHealthScale();
		if (healthRatio >= 0 && healthScale > 0)
		{
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
		if (inspectionSampleTick < 0 || tick < inspectionSampleTick)
		{
			return;
		}
		if (tick > inspectionDeadlineTick)
		{
			inspectionSampleTick = -1;
			inspectionDeadlineTick = -1;
			return;
		}

		MonsterInspectionReader.Snapshot snapshot = MonsterInspectionReader.find(client, targetName);
		if (snapshot == null)
		{
			return;
		}

		lastInspectedHitpoints = snapshot.getHitpoints();
		lastInspectedDefence = snapshot.getDefence();
		maximumHitpoints = Math.max(maximumHitpoints, lastInspectedHitpoints);
		if (lastInspectedDefence >= 0)
		{
			baseDefence = Math.max(baseDefence, lastInspectedDefence);
		}
		lastInspectedTick = tick;
		observationSource = lastHealthRatio >= 0
			? ObservationSource.COMBINED : ObservationSource.MONSTER_INSPECTION;
		inspectionSampleTick = -1;
		inspectionDeadlineTick = -1;
		applyDetectedInterval(timer.sampleExactHealth(
			lastInspectedHitpoints, tick, activeRegenTicks));
	}

	private void applyDetectedInterval(int detectedInterval)
	{
		if (detectedInterval > 0 && config.learnNpcTimings())
		{
			int tolerance = Math.max(1, config.observationDelayTicks());
			if (Math.abs(detectedInterval - config.regenTicks())
				<= tolerance)
			{
				detectedInterval = config.regenTicks();
			}
			else if (Math.abs(detectedInterval - activeRegenTicks)
				<= tolerance)
			{
				detectedInterval = activeRegenTicks;
			}
			activeRegenTicks = detectedInterval;
			learnedRegen = true;
			saveActiveProfile();
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == target)
		{
			handleTargetDeath();
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
			handleTargetDeath();
		}
		else
		{
			clearTarget();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		observedSpawnPoints.put(npc, npc.getWorldLocation());
		if (timer.getState() != RegenTimer.State.WAITING_FOR_RESPAWN || !isExpectedRespawn(npc))
		{
			return;
		}

		target = npc;
		targetNpcIndex = npc.getIndex();
		lastTargetPoint = observedSpawnPoints.get(npc);
		deathHandled = false;
		resetObservationSource();
		int measuredRespawnTicks = timer.onRespawn(tick);
		if (measuredRespawnTicks > 0 && config.learnNpcTimings())
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
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			observedSpawnPoints.clear();
			clearTarget();
			tick = 0;
		}
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (target == null)
		{
			return;
		}

		if (config.markRegenHotkey().matches(event))
		{
			int detectedInterval = timer.markNow(tick, activeRegenTicks);
			applyDetectedInterval(detectedInterval);
		}
		else if (config.resetHotkey().matches(event))
		{
			timer.reset();
			resetObservationSource();
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
		lastTargetPoint = observedSpawnPoints.getOrDefault(npc, npc.getWorldLocation());
		lastTargetSize = npc.getTransformedComposition() == null
			? 1 : npc.getTransformedComposition().getSize();
		deathHandled = false;
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

	private void loadActiveProfile()
	{
		NpcTimingProfileStore.Profile profile = config.learnNpcTimings()
			? profileStore.load(targetNpcId) : new NpcTimingProfileStore.Profile(0, 0);
		int storedRegenTicks = profile.getRegenTicks();
		if (storedRegenTicks > 0
			&& Math.abs(storedRegenTicks - config.regenTicks())
			<= Math.max(1, config.observationDelayTicks()))
		{
			storedRegenTicks = config.regenTicks();
		}
		learnedRegen = storedRegenTicks > 0;
		learnedRespawn = profile.getRespawnTicks() > 0;
		activeRegenTicks = learnedRegen ? storedRegenTicks : config.regenTicks();
		activeRespawnTicks = learnedRespawn ? profile.getRespawnTicks() : config.respawnTicks();
		if (learnedRegen && storedRegenTicks != profile.getRegenTicks())
		{
			saveActiveProfile();
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

	private void handleTargetDeath()
	{
		if (deathHandled)
		{
			return;
		}

		deathHandled = true;
		target = null;
		timer.onDeath(tick, activeRespawnTicks);
	}

	private boolean isExpectedRespawn(NPC npc)
	{
		if (targetNpcId != npc.getId())
		{
			return false;
		}

		return targetNpcIndex == npc.getIndex()
			|| lastTargetPoint != null && lastTargetPoint.distanceTo(npc.getWorldLocation()) <= 8;
	}

	private void clearTarget()
	{
		target = null;
		targetNpcId = -1;
		targetNpcIndex = -1;
		targetName = null;
		lastTargetPoint = null;
		lastTargetSize = 1;
		deathHandled = false;
		activeRegenTicks = config == null ? 100 : config.regenTicks();
		activeRespawnTicks = 0;
		learnedRegen = false;
		learnedRespawn = false;
		maximumHitpoints = -1;
		baseDefence = -1;
		resetObservationSource();
		timer.reset();
	}

	private void resetObservationSource()
	{
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
