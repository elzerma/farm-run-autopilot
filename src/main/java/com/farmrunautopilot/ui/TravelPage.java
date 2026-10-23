package com.farmrunautopilot.ui;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.data.travel.atlas.AtlasWays;
import com.farmrunautopilot.route.TravelWays;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.settings.TravelStyle;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Travel tab: how the route travels and the route itself. Every stop's way is planned for the player from the
 * travel atlas, so there's nothing to pick per stop (elzerma, 2026-10-20). Ways to travel says what the route may use
 * (sprint T7): each family on or off, and opened, each of its ways with how it stands for the player and its own on and
 * off ("don't use this way"), kept for the account whatever the preset.
 */
class TravelPage extends SetupPanel
{
	/** Room for a family's or a way's line inside the section. */
	private static final int ROW_WIDTH = CONTROL_WIDTH - 4;
	/** A blank as wide as a row's chevron, so a family with nothing to open lines up with those that have. */
	private static final Icon NO_CHEVRON = new Icon()
	{
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
		}

		@Override
		public int getIconWidth()
		{
			return 10;
		}

		@Override
		public int getIconHeight()
		{
			return 10;
		}
	};

	/** The families opened, kept while the client runs; their ways are only drawn while open. */
	private final Set<TravelFamily> open = EnumSet.noneOf(TravelFamily.class);
	/** The families and ways as last worked out with a plan (RunService). */
	private TravelWays ways = TravelWays.EMPTY;

	TravelPage(SettingsStore settings, AccessChecker accessChecker, Runnable changed)
	{
		super(settings, accessChecker, changed);
		rebuild();
	}

	/** The families and ways worked out with the latest plan; redrawn only when something in them changed. */
	void showWays(TravelWays latest)
	{
		if (!latest.equals(ways))
		{
			ways = latest;
			rebuild();
		}
	}

	@Override
	void addSections(RunConfig config, AccountSettings account, AccessSnapshot access)
	{
		add(waysToTravelSection(config));
		add(routeSection(config));
	}

	/** What the route may use, family by family, and how it chooses between ways. */
	private JComponent waysToTravelSection(RunConfig config)
	{
		final CollapsibleSection s = section("Ways to travel").help("Every stop's way is the quickest the route can "
			+ "use. Untick a family, or open one and untick a way you can't or won't use: the route is planned again "
			+ "without it. Kept for your account, whichever preset is in use.");
		for (TravelFamily family : TravelFamily.values())
		{
			s.addContent(familyRow(config, family));
		}
		s.addContent(checkBox("Avoid the Wilderness", config.isAvoidWilderness(), true,
			"On: no teleport, hop or walk into the Wilderness (the Ferox Enclave is safe). Off: the route may go through "
				+ "it, where other players can attack you",
			on -> saveRun(() -> config.setAvoidWilderness(on))));
		s.addContent(checkBox("Switch spellbook to cast", config.isSwitchSpellbooks(), true,
			"On: a spell from another spellbook can be cast after Spellbook Swap (on Lunar) or a trip to your house "
				+ "altar, when that's quicker. Off: only spells on the spellbook you're on are cast",
			on -> saveRun(() -> config.setSwitchSpellbooks(on))));
		final List<Choice<TravelStyle>> styles = new ArrayList<>();
		for (TravelStyle style : TravelStyle.values())
		{
			styles.add(new Choice<>(style, style.getDisplayName(), true, style.getDescription()));
		}
		s.addContent(label("Travel style"));
		s.addContent(combo(styles, config.getTravelStyle(), st -> saveRun(() -> config.setTravelStyle(st))));
		s.addContent(checkBox("Walk when it's nearly as quick as teleporting", config.isPreferWalking(), true,
			"A walk up to about 20 seconds slower is used instead of a teleport, saving charges and clicks "
				+ "(e.g. one Falador Teleport for Falador Park and Taverley)",
			on -> saveRun(() -> config.setPreferWalking(on))));
		return s;
	}

	/** A family's line: its tick box, its name and how many of its ways are ready; opened, each of its ways. */
	private JComponent familyRow(RunConfig config, TravelFamily family)
	{
		if (!family.isListed())
		{
			// Nothing to open: the house is set up on the Account tab, shortcuts go by Agility level
			final JCheckBox tick = leadBox(config.isOn(family), family.getDescription(),
				on -> saveRun(() -> config.setOn(family, on)));
			return plainRow(tick, family.getDisplayName(), family == TravelFamily.HOUSE ? "set in Account > My house"
				: "by your level", family.getDescription());
		}
		final TravelWays.Family listed = ways.get(family);
		final JCheckBox tick = familyBox(config, family, listed);
		final ExpandableRow row = new ExpandableRow(tick, family.getDisplayName(),
			listed != null ? listed.summary() : "", family.getDescription(), false, ROW_WIDTH, open.contains(family),
			opened ->
			{
				if (opened)
				{
					open.add(family);
				}
				else
				{
					open.remove(family);
				}
				SwingUtilities.invokeLater(this::rebuild);
			});
		if (!open.contains(family))
		{
			return row;
		}
		if (family == TravelFamily.TELEPORT_ITEMS)
		{
			row.addContent(checkBox("Keep the last charge of my jewellery", config.isKeepLastCharge(), true,
				"Off: every charge is used. On: the last charge of your last skills necklace, glory, ring of wealth, "
					+ "combat bracelet or teleport crystal is kept back, and another way is used instead",
				on -> saveRun(() -> config.setKeepLastCharge(on))));
		}
		if (family == TravelFamily.TABLETS)
		{
			row.addContent(checkBox("Cast from runes, not tablets", config.isUseRunesNotTabs(), true,
				"Off: a teleport's tablet is used when you have one. On: teleports you can cast are cast from runes, "
					+ "and a tablet is only used for one you can't",
				on -> saveRun(() -> config.setUseRunesNotTabs(on))));
		}
		if (listed == null)
		{
			row.addContent(note("Your ways show once you're logged in."));
			return row;
		}
		if (listed.getWays().isEmpty())
		{
			row.addContent(note(family == TravelFamily.TABLETS ? "You don't own a teleport tablet or scroll."
				: "You don't own a teleport item."));
		}
		for (TravelWays.Way way : listed.getWays())
		{
			row.addContent(wayRow(config, family, listed, way));
		}
		return row;
	}

	/** Off while the family is turned off, whatever its ways are set to; partly on with a usable way turned off. */
	private static GroupTick familyTick(RunConfig config, TravelFamily family, TravelWays.Family listed)
	{
		if (!config.isOn(family))
		{
			return GroupTick.OFF;
		}
		if (listed != null)
		{
			for (TravelWays.Way way : listed.getWays())
			{
				if (way.getState() == TravelWays.State.READY && config.getWaysOff().contains(way.getKey()))
				{
					return GroupTick.PARTLY;
				}
			}
		}
		return GroupTick.ON;
	}

	/** A family's tick box: clicked when ticked, the family goes off; otherwise it goes on with every way on. */
	private JCheckBox familyBox(RunConfig config, TravelFamily family, TravelWays.Family listed)
	{
		return groupBox(familyTick(config, family, listed), family.getDescription(), on -> saveRun(() ->
		{
			config.setOn(family, on);
			if (on)
			{
				config.getWaysOff().removeIf(key -> AtlasWays.familyOf(key) == family);
			}
		}));
	}

	/**
	 * One way: ticked while the route may use it, unticked once the player turns it off ("don't use this way") or its
	 * family is off; greyed out and not tickable while it can't be used, ticking itself once it can (elzerma,
	 * 2026-10-20). Why it can't is on hover. Ticking one in a family that's off turns the family on for that way alone.
	 */
	private JComponent wayRow(RunConfig config, TravelFamily family, TravelWays.Family listed, TravelWays.Way way)
	{
		final boolean usable = way.getState() == TravelWays.State.READY;
		final boolean familyOn = config.isOn(family);
		final boolean on = familyOn && !config.getWaysOff().contains(way.getKey());
		return checkBox(way.getName(), usable && on, usable, UiText.tooltip(way.getDetail(), 250), ticked -> saveRun(() ->
		{
			if (!ticked)
			{
				config.getWaysOff().add(way.getKey());
				return;
			}
			if (!familyOn)
			{
				config.setOn(family, true);
				for (TravelWays.Way other : listed.getWays())
				{
					config.getWaysOff().add(other.getKey());
				}
			}
			config.getWaysOff().remove(way.getKey());
		}));
	}

	/** A family with nothing to open: its tick box, name and a short value on one line, like the others. */
	private static JComponent plainRow(JCheckBox tick, String name, String value, String tooltip)
	{
		final Card card = new Card(new BorderLayout(8, 0));
		tick.setOpaque(false);
		card.add(tick, BorderLayout.WEST);
		final JLabel label = new JLabel(name, NO_CHEVRON, JLabel.LEFT);
		label.setIconTextGap(6);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);
		card.add(label, BorderLayout.CENTER);
		final JLabel right = new JLabel(value);
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(Color.WHITE);
		card.add(right, BorderLayout.EAST);
		card.setToolTipText(UiText.tooltip(tooltip, 250));
		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(0, 0, 3, 0));
		wrapper.add(card, BorderLayout.CENTER);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapper.getPreferredSize().height));
		return wrapper;
	}

	private JComponent routeSection(RunConfig config)
	{
		final CollapsibleSection s = section("Route");
		final String orderHelp = "Autopilot finds the quickest order; Meta follows the wiki's order; Off keeps your "
			+ "own: drag the stops in the route list on the Run tab.";
		s.addContent(tip(label("Order"), orderHelp));
		s.addContent(tip(combo(enumChoices(RouteMode.values()), config.getRouteMode(),
			m -> saveRun(() -> config.setRouteMode(m))), orderHelp));
		final List<Choice<Location>> starts = new ArrayList<>();
		starts.add(Choice.of(null, "Anywhere (quickest)"));
		for (Location location : Location.values())
		{
			starts.add(Choice.of(location, location.getDisplayName()));
		}
		final String startHelp = "Where the run starts. Anywhere lets the plugin choose; if you're standing at or near "
			+ "one of the run's stops, the run starts there either way.";
		s.addContent(tip(label("Start at"), startHelp));
		s.addContent(tip(combo(starts, config.getStartLocation(), l -> saveRun(() -> config.setStartLocation(l))),
			startHelp));
		s.addContent(checkBox("Finish near a bank", config.isEndNearBank(), true, null,
			on -> saveRun(() -> config.setEndNearBank(on))));
		return s;
	}
}
