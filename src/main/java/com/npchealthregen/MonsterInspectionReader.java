package com.npchealthregen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

final class MonsterInspectionReader
{
	private static final Pattern BREAK_TAG = Pattern.compile("(?i)<br\\s*/?>");
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
	// The whitespace after the colon may span a line break, so a label and its
	// value can be held in separate widgets.
	private static final Pattern HITPOINTS = Pattern.compile("(?im)^[ \\t]*Hitpoints[ \\t]*:\\s*([0-9][0-9,]*)[ \\t]*$");
	private static final Pattern DEFENCE = Pattern.compile("(?im)^[ \\t]*Defence[ \\t]*:\\s*([0-9][0-9,]*)[ \\t]*$");
	private static final Pattern COMBAT_LEVEL_SUFFIX = Pattern.compile("(?i)\\s*\\(level-\\d+\\)$");
	private static final Pattern ELLIPSIS = Pattern.compile("(\\.\\.\\.|…)$");
	private static final int MIN_TRUNCATED_NAME_LENGTH = 3;

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

		// The Monster Examine/Inspect result panel. Read it directly so the
		// result does not depend on where the panel is nested in the layout.
		Widget name = client.getWidget(InterfaceID.DreamMonsterStat.MONSTER_NAME);
		Widget stats = client.getWidget(InterfaceID.DreamMonsterStat.MONSTER_STATS);
		if (name != null && stats != null && !name.isHidden() && !stats.isHidden())
		{
			List<String> panelText = new ArrayList<>(collectVisibleText(name));
			panelText.addAll(collectVisibleText(stats));
			Snapshot snapshot = parse(targetName, panelText);
			if (snapshot != null)
			{
				return snapshot;
			}
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
		StringBuilder combined = new StringBuilder();

		for (String rawText : widgetText)
		{
			String text = normalise(rawText);
			for (String line : text.split("\\R"))
			{
				if (namesMatch(line, expectedName))
				{
					nameFound = true;
				}
			}
			combined.append(text).append('\n');
		}

		int hitpoints = readStat(HITPOINTS, combined);
		int defence = readStat(DEFENCE, combined);
		return nameFound && hitpoints >= 0 ? new Snapshot(hitpoints, defence) : null;
	}

	/**
	 * The panel title is cut to fit the side panel ("Deranged archaeologi...")
	 * and may carry a combat level suffix.
	 */
	static boolean namesMatch(String panelLine, String expectedName)
	{
		String line = stripLevel(panelLine);
		String expected = stripLevel(expectedName);
		if (expected.isEmpty())
		{
			return false;
		}
		if (line.equals(expected))
		{
			return true;
		}

		Matcher ellipsis = ELLIPSIS.matcher(line);
		if (!ellipsis.find())
		{
			return false;
		}
		String prefix = line.substring(0, ellipsis.start()).trim();
		return prefix.length() >= Math.min(MIN_TRUNCATED_NAME_LENGTH, expected.length())
			&& expected.startsWith(prefix);
	}

	private static String stripLevel(String name)
	{
		return COMBAT_LEVEL_SUFFIX.matcher(name.trim()).replaceAll("").trim().toLowerCase(Locale.ENGLISH);
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

			// Pushed last-first so the stack yields children top-down, keeping a
			// label ahead of a value held in a following widget.
			addChildren(pending, widget.getNestedChildren());
			addChildren(pending, widget.getDynamicChildren());
			addChildren(pending, widget.getStaticChildren());
		}
		return text;
	}

	private static void addChildren(Deque<Widget> pending, Widget[] children)
	{
		if (children == null)
		{
			return;
		}

		for (int i = children.length - 1; i >= 0; i--)
		{
			if (children[i] != null)
			{
				pending.push(children[i]);
			}
		}
	}

	private static int readStat(Pattern pattern, CharSequence text)
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
			.replace(' ', ' ')
			.trim();
	}
}
