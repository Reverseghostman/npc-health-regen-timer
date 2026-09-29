package com.npchealthregen;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.NPCManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Builds the plugin the way RuneLite does, from an injector, so a missing or unsatisfiable
 * injection fails here instead of when the plugin is enabled in the client.
 */
public class NpcHealthRegenPluginWiringTest
{
	private final NpcHealthRegenPlugin plugin = new NpcHealthRegenPlugin();
	private OverlayManager overlayManager;
	private KeyManager keyManager;
	private WSClient wsClient;
	private ConfigManager configManager;
	private NpcHealthRegenConfig config;
	private Injector injector;

	@Before
	public void setUp()
	{
		overlayManager = mock(OverlayManager.class);
		keyManager = mock(KeyManager.class);
		wsClient = mock(WSClient.class);
		configManager = mock(ConfigManager.class);
		config = mock(NpcHealthRegenConfig.class);
		when(configManager.getConfig(NpcHealthRegenConfig.class)).thenReturn(config);

		injector = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(mock(Client.class));
				bind(ClientThread.class).toInstance(mock(ClientThread.class));
				bind(ConfigManager.class).toInstance(configManager);
				bind(NpcHealthRegenConfig.class).toInstance(config);
				bind(OverlayManager.class).toInstance(overlayManager);
				bind(KeyManager.class).toInstance(keyManager);
				bind(NPCManager.class).toInstance(mock(NPCManager.class));
				bind(PartyService.class).toInstance(mock(PartyService.class));
				bind(WSClient.class).toInstance(wsClient);
				bind(PluginManager.class).toInstance(mock(PluginManager.class));
				// RuneLite binds each plugin to its own instance, which the overlays inject.
				bind(NpcHealthRegenPlugin.class).toInstance(plugin);
			}
		});
	}

	@Test
	public void everyInjectionPointIsSatisfiedIncludingBothOverlays()
	{
		assertNotNull(injector.getInstance(NpcHealthRegenOverlay.class));
		assertNotNull(injector.getInstance(NpcHealthRegenSceneOverlay.class));
		assertNotNull(injector.getInstance(NpcTimingProfileStore.class));
		assertSame(plugin, injector.getInstance(NpcHealthRegenPlugin.class));
	}

	@Test
	public void configIsProvidedFromTheConfigManager()
	{
		assertSame(config, plugin.provideConfig(configManager));
	}

	@Test
	public void startUpRegistersAndShutDownUnregistersEverything()
	{
		plugin.startUp();

		verify(overlayManager, times(2)).add(any(Overlay.class));
		verify(keyManager).registerKeyListener(plugin);
		verify(wsClient).registerMessage(NpcHealthRegenPartyUpdate.class);
		verify(wsClient).registerMessage(NpcHealthRegenVenomUpdate.class);

		plugin.shutDown();

		verify(overlayManager, times(2)).remove(any(Overlay.class));
		verify(keyManager).unregisterKeyListener(plugin);
		verify(wsClient).unregisterMessage(NpcHealthRegenPartyUpdate.class);
		verify(wsClient).unregisterMessage(NpcHealthRegenVenomUpdate.class);
	}

	@Test
	public void everySubscriberMethodRegistersWithTheRealEventBus()
	{
		// The event bus rejects a @Subscribe method with the wrong shape when the plugin is
		// registered, which would otherwise only show up in the running client.
		EventBus eventBus = new EventBus();
		eventBus.register(plugin);
		eventBus.unregister(plugin);
	}

	@Test
	public void canBeStartedAgainAfterShutDown()
	{
		plugin.startUp();
		plugin.shutDown();
		plugin.startUp();

		verify(overlayManager, times(4)).add(any(Overlay.class));
		verify(keyManager, times(2)).registerKeyListener(plugin);
	}
}
