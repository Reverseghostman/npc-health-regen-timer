package com.npchealthregen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

final class MonsterInspectionReader
{
	private static final Pattern BREAK_TAG = Pattern.compile("(?i)<br\\s*/?>");
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
	private static final Pattern HITPOINTS = Pattern.compile("(?im)^\\s*Hitpoints\\s*:\\s*([0-9][0-9,]*)\\s*$");
	private static final Pattern DEFENCE = Pattern.compile("(?im)^\\s*Defence\\s*:\\s*([0-9][0-9,]*)\\s*$");

	static final class Snapshot
	{
		private final int hitpoints;
		private final int defence;

		private Snapshot(int hitpoints, int defence)
		{
			this.hitpoints = hitpoints;
			this.defence = defence;
		}

		int getHitpoints()
		{
			return hitpoints;
		}

		int getDefence()
		{
			return defence;
		}
	}

	private MonsterInspectionReader()
	{
	}

	static Snapshot find(Client client, String targetName)
	{
		if (targetName == null)
		{
			return null;
		}

		Widget[] roots = client.getWidgetRoots();
		if (roots == null)
		{
			return null;
		}

		for (Widget root : roots)
		{
			Snapshot snapshot = parse(targetName, collectVisibleText(root));
			if (snapshot != null)
			{
				return snapshot;
			}
		}
		return null;
	}

	static Snapshot parse(String targetName, Iterable<String> widgetText)
	{
		String expectedName = normalise(targetName);
		boolean nameFound = false;
		int hitpoints = -1;
		int defence = -1;

		for (String rawText : widgetText)
		{
			String text = normalise(rawText);
			for (String line : text.split("\\R"))
			{
				if (line.trim().equalsIgnoreCase(expectedName))
				{
					nameFound = true;
				}
			}

			if (hitpoints < 0)
			{
				hitpoints = readStat(HITPOINTS, text);
			}
			if (defence < 0)
			{
				defence = readStat(DEFENCE, text);
			}
		}

		return nameFound && hitpoints >= 0 ? new Snapshot(hitpoints, defence) : null;
	}

	private static List<String> collectVisibleText(Widget root)
	{
		if (root == null)
		{
			return Collections.emptyList();
		}

		List<String> text = new ArrayList<>();
		Deque<Widget> pending = new ArrayDeque<>();
		Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		pending.push(root);

		while (!pending.isEmpty())
		{
			Widget widget = pending.pop();
			if (!visited.add(widget) || widget.isHidden())
			{
				continue;
			}

			String widgetText = widget.getText();
			if (widgetText != null && !widgetText.isEmpty())
			{
				text.add(widgetText);
			}

			addChildren(pending, widget.getChildren());
			addChildren(pending, widget.getStaticChildren());
			addChildren(pending, widget.getNestedChildren());
		}
		return text;
	}

	private static void addChildren(Deque<Widget> pending, Widget[] children)
	{
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			if (child != null)
			{
				pending.push(child);
			}
		}
	}

	private static int readStat(Pattern pattern, String text)
	{
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find())
		{
			return -1;
		}

		try
		{
			return Integer.parseInt(matcher.group(1).replace(",", ""));
		}
		catch (NumberFormatException ignored)
		{
			return -1;
		}
	}

	private static String normalise(String text)
	{
		if (text == null)
		{
			return "";
		}
		return HTML_TAG.matcher(BREAK_TAG.matcher(text).replaceAll("\n"))
			.replaceAll("")
			.replace("&nbsp;", " ")
			.replace('\u00a0', ' ')
			.trim();
	}
}
