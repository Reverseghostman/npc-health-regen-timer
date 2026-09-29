package com.npchealthregen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class NpcHealthRegenConfigTest
{
	@Test
	public void regroupingKeepsExistingSettingKeys()
	{
		// Renaming a key silently resets that setting for every existing user.
		Set<String> keys = new HashSet<>();
		for (Method method : NpcHealthRegenConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				keys.add(item.keyName());
			}
		}

		for (String key : Arrays.asList("regenTicks", "respawnTicks", "warningTicks",
			"observationDelayTicks", "learnNpcTimings", "showSeconds", "showNpcName",
			"showRegenRate", "showRespawnTime", "showTickCounter", "showObservationSource",
			"showInspectedStats", "showBaseStats", "showCurrentHealth", "showTimeUntilFullHp",
			"showDefenceAtFullHp", "showOverheadRegenCountdown", "showRespawnTile",
			"markRegenHotkey", "resetHotkey"))
		{
			assertTrue("missing setting key " + key, keys.contains(key));
		}
	}

	@Test
	public void standardOptionsStayShortAndSectionsExist() throws Exception
	{
		Set<String> sections = new HashSet<>();
		for (Field field : NpcHealthRegenConfig.class.getDeclaredFields())
		{
			if (field.getAnnotation(ConfigSection.class) != null)
			{
				sections.add((String) field.get(null));
			}
		}

		int standard = 0;
		for (Method method : NpcHealthRegenConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null)
			{
				continue;
			}
			if (item.section().isEmpty())
			{
				standard++;
			}
			else
			{
				assertTrue("unknown section " + item.section(), sections.contains(item.section()));
			}
		}
		assertTrue("too many standard options: " + standard, standard <= 5);
	}
}
