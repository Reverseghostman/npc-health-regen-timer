package com.npchealthregen;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NpcHealthRegenPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NpcHealthRegenPlugin.class);
		RuneLite.main(args);
	}
}
