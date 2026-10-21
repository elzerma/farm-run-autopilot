package com.farmrunautopilot.ui;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.settings.TravelStyle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * The Travel tab: how the route travels and the route itself. Every stop's way is planned for the player from the
 * travel atlas, so there's nothing to pick per stop (elzerma, 2026-10-20).
 */
class TravelPage extends SetupPanel
{
	TravelPage(SettingsStore settings, AccessChecker accessChecker, Runnable changed)
	{
		super(settings, accessChecker, changed);
		rebuild();
	}

	@Override
	void addSections(RunConfig config, AccountSettings account, AccessSnapshot access)
	{
		add(waysToTravelSection(config));
		add(routeSection(config));
	}

	/** What every stop's way is chosen by: the travel style and the player's preferences. */
	private JComponent waysToTravelSection(RunConfig config)
	{
		final CollapsibleSection s = section("Ways to travel");
		final List<Choice<TravelStyle>> styles = new ArrayList<>();
		for (TravelStyle style : TravelStyle.values())
		{
			styles.add(new Choice<>(style, style.getDisplayName(), true, style.getDescription()));
		}
		s.addContent(label("Travel style"));
		s.addContent(combo(styles, config.getTravelStyle(), st -> saveRun(() -> config.setTravelStyle(st))));
		s.addContent(checkBox("Keep the last charge of my rechargeable jewellery", config.isKeepLastCharge(), true,
			"Off: every charge is used. On: the last charge of your last skills necklace, glory, ring of wealth, combat "
				+ "bracelet or teleport crystal is kept back, and another way is used instead",
			on -> saveRun(() -> config.setKeepLastCharge(on))));
		s.addContent(checkBox("Walk when it's nearly as quick as teleporting", config.isPreferWalking(), true,
			"A walk up to about 20 seconds slower is used instead of a teleport, saving charges and clicks "
				+ "(e.g. one Falador Teleport for Falador Park and Taverley)",
			on -> saveRun(() -> config.setPreferWalking(on))));
		s.addContent(checkBox("Cast from runes, not tablets", config.isUseRunesNotTabs(), true,
			"Off: a teleport's tablet is used when you have one. On: teleports you can cast are cast from runes, and a "
				+ "tablet is only used for one you can't",
			on -> saveRun(() -> config.setUseRunesNotTabs(on))));
		return s;
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
