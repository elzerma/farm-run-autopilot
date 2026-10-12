package com.farmrunautopilot.ui;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelKind;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.ChargeBudget;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.route.TravelPick;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.settings.TravelStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import lombok.Value;

/**
 * The Travel tab: defaults for every stop, the route, and how to reach each location.
 */
class TravelPage extends SetupPanel
{
	/** Remembered like an open section: the Locations list shows every location. */
	private static final String SHOW_ALL_LOCATIONS = "Show every location";

	/** Location rows the player has open, kept across redraws. */
	private final Set<Location> openRows = EnumSet.noneOf(Location.class);
	/** Auto (best)'s picks; null until shown. */
	private RunService runService;
	/** The picks the page was last drawn with, to redraw only when they change. */
	private Map<Location, TravelPick> shownPicks = Collections.emptyMap();

	TravelPage(SettingsStore settings, AccessChecker accessChecker, Runnable changed)
	{
		super(settings, accessChecker, changed);
		rebuild();
	}

	@Override
	void addSections(RunConfig config, AccountSettings account, AccessSnapshot access)
	{
		add(travelDefaultsSection(config, access));
		add(routeSection(config));
		add(travelSection(config, access));
	}

	/** Show Auto (best)'s picks on the Travel page. */
	void showAutoPicks(RunService runService)
	{
		this.runService = runService;
		rebuild();
	}

	/** Redraw the Travel page if Auto (best)'s picks changed. Call on the Swing thread. */
	void refreshAutoPicks()
	{
		if (runService != null && !runService.getAutoPicks().equals(shownPicks))
		{
			rebuild();
		}
	}

	/** Settings for every stop; each location below can override its own teleport. */
	private JComponent travelDefaultsSection(RunConfig config, AccessSnapshot access)
	{
		final CollapsibleSection s = section("Defaults for every stop");
		final List<Choice<TravelStyle>> styles = new ArrayList<>();
		for (TravelStyle style : TravelStyle.values())
		{
			styles.add(new Choice<>(style, style.getDisplayName(), true, style.getDescription()));
		}
		s.addContent(label("Auto (best) picks"));
		s.addContent(combo(styles, config.getTravelStyle(), st -> saveRun(() -> config.setTravelStyle(st))));
		s.addContent(checkBox("Keep the last charge of my rechargeable jewellery", config.isKeepLastCharge(), true,
			"Off: every charge is used. On: the last charge of your last skills necklace, glory, ring of wealth, combat "
				+ "bracelet or teleport crystal is kept back, and another way is used instead",
			on -> saveRun(() -> config.setKeepLastCharge(on))));
		s.addContent(checkBox("Walk when it's nearly as quick as teleporting", config.isPreferWalking(), true,
			"A walk up to about 20 seconds slower is used instead of a teleport, saving charges and clicks "
				+ "(e.g. one Falador Teleport for Falador Park and Taverley)",
			on -> saveRun(() -> config.setPreferWalking(on))));
		s.addContent(spinnerRow("Stamina doses to bring", spinner(config.getStaminaDoses(), 0, 40, 1,
			v -> saveRunQuietly(() -> config.setStaminaDoses(v)))));

		final List<Choice<FairyRingAccess>> ways = new ArrayList<>();
		ways.add(Choice.of(null, "Auto (best)"));
		for (FairyRingAccess way : FairyRingAccess.values())
		{
			if (way == FairyRingAccess.NEARBY)
			{
				continue;
			}
			// Locked when no item for it can be used (the quest point cape also needs every quest done)
			List<Requirement> missing = new ArrayList<>();
			for (TravelItem item : way.getItems())
			{
				missing = access.missing(item.getRequirements());
				if (missing.isEmpty())
				{
					break;
				}
			}
			final boolean locked = !missing.isEmpty();
			final String needs = "Needs: " + way.getItems().get(way.getItems().size() - 1).getDisplayName()
				+ (way.getItems().size() > 1 ? " or higher" : "");
			ways.add(new Choice<>(way, way.getDisplayName() + (locked ? " (locked)" : ""), !locked,
				locked ? AccessSnapshot.describe(missing) : needs));
		}
		final String ringHelp = "How you get to a fairy ring when the stop before has none. The ring by the stop you "
			+ "just finished is used whenever it's quicker, and your house ring is set in Account > My house.";
		s.addContent(tip(label("Way to a fairy ring"), ringHelp));
		s.addContent(tip(combo(ways, config.getFairyRingWay(), w -> saveRun(() -> config.setFairyRingWay(w))),
			ringHelp));
		return s;
	}

	/** A teleport and how it's used (directly, or through the house), as a dropdown entry. */
	@Value
	private static class Way
	{
		TravelMethod method;
		Departure how;
	}

	/**
	 * One row per location: Auto (best)'s pick, or the teleport and how the player chose, and whether a spell
	 * is cast from runes instead of its tablet.
	 */
	private JComponent travelSection(RunConfig config, AccessSnapshot access)
	{
		// Stops a run can include: a ticked, unlocked patch of a ticked run type
		final Set<Location> inRuns = EnumSet.noneOf(Location.class);
		for (Patch patch : Patch.values())
		{
			if (config.getEnabledTypes().contains(patch.getType()) && config.isPatchSelected(patch)
				&& access.missingFor(patch).isEmpty())
			{
				inRuns.add(patch.getLocation());
			}
		}
		int overridden = 0;
		for (Location location : Location.values())
		{
			overridden += isOverridden(config, location) ? 1 : 0;
		}
		final boolean showAll = settings.isSectionOpen(SHOW_ALL_LOCATIONS);
		final Map<Location, TravelPick> picks = runService != null ? runService.getAutoPicks()
			: Collections.emptyMap();
		shownPicks = picks;

		final CollapsibleSection s = section("Locations", overrides(overridden));
		s.help("Auto (best) shows what it picked for each stop, by the style above. Click a stop to "
			+ "choose a teleport and "
			+ "how to use it instead; that overrides Auto for that stop. Tablets are used before runes.");
		s.addContent(checkBox("Show every location", showAll, true,
			"Off: only stops your runs can include, plus any you've changed",
			on ->
			{
				settings.setSectionOpen(SHOW_ALL_LOCATIONS, on);
				SwingUtilities.invokeLater(changed);
			}));
		for (Location location : Location.values())
		{
			if (!showAll && !inRuns.contains(location) && !isOverridden(config, location))
			{
				continue;
			}
			final TravelPick pick = picks.get(location);
			final String auto = pick != null ? pick.describe() : null;
			final List<Choice<Way>> choices = new ArrayList<>();
			choices.add(Choice.of(null, auto != null ? "Auto (best): " + auto : "Auto (best)"));
			for (TravelMethod method : TravelMethod.values())
			{
				if (method.getDestination() == location)
				{
					for (Departure how : waysToUse(method))
					{
						choices.add(wayChoice(new Way(method, how), location, access));
					}
				}
			}

			final TravelMethod chosen = config.getTravel().get(location);
			final Departure chosenHow = config.getTravelHow().get(location);
			final Way selected = chosen != null ? new Way(chosen, chosenHow != null ? chosenHow : Departure.DIRECT)
				: null;
			final ExpandableRow row = new ExpandableRow(location.getDisplayName(),
				selected != null ? wayLabel(selected, location) : auto != null ? "Auto: " + auto : "Auto",
				isOverridden(config, location), CONTROL_WIDTH, openRows.contains(location),
				open ->
				{
					if (open)
					{
						openRows.add(location);
					}
					else
					{
						openRows.remove(location);
					}
				});
			s.addContent(row);
			row.addContent(combo(choices, selected,
				w -> saveRun(() ->
				{
					if (w == null)
					{
						config.getTravel().remove(location);
						config.getTravelHow().remove(location);
					}
					else
					{
						config.getTravel().put(location, w.getMethod());
						config.getTravelHow().put(location, w.getHow());
					}
				})));

			// Tablets come first; a spell the player can cast may use runes here instead
			final Spell spell = selected != null
				? (selected.getHow() == Departure.DIRECT ? selected.getMethod().getSpell() : null)
				: pick != null && pick.getStop().getDeparture() == Departure.DIRECT && pick.getStop().getMethod() != null
				? pick.getStop().getMethod().getSpell() : null;
			final boolean runesHere = config.getRunesNotTabsAt().contains(location);
			if (runesHere || (spell != null && spell.hasTablet() && access.canCast(spell)))
			{
				row.addContent(checkBox("Use runes instead of a tablet", runesHere, true,
					"Cast this stop's teleport from runes even when you have its tablet",
					on -> saveRun(() ->
					{
						if (on)
						{
							config.getRunesNotTabsAt().add(location);
						}
						else
						{
							config.getRunesNotTabsAt().remove(location);
						}
					})));
			}
		}
		return s;
	}

	/** Every way a method can be used: directly, and through the house where the house has it. */
	private static List<Departure> waysToUse(TravelMethod method)
	{
		final List<Departure> ways = new ArrayList<>();
		ways.add(Departure.DIRECT);
		if (method.getNexus() != null)
		{
			ways.add(Departure.POH_NEXUS);
		}
		if (method.getJewelleryBox() != null)
		{
			ways.add(Departure.POH_JEWELLERY_BOX);
		}
		if (method.getKind() == TravelKind.FAIRY_RING)
		{
			ways.add(Departure.POH_FAIRY_RING);
		}
		if (method.getKind() == TravelKind.SPIRIT_TREE)
		{
			ways.add(Departure.POH_SPIRIT_TREE);
		}
		return ways;
	}

	/** e.g. "Camelot Teleport (tablet)", "House portal nexus: Catherby Teleport". */
	private String wayLabel(Way way, Location location)
	{
		final Spell spell = way.getMethod().getSpell();
		final boolean tablet = spell != null && way.getHow() == Departure.DIRECT && runService != null
			&& runService.usesTablet(spell, location);
		return new TravelPick(new RouteStop(location, way.getMethod(), way.getHow(), 0, false), tablet).describe();
	}

	/** A dropdown entry, greyed out with the reason when it can't be used. */
	private Choice<Way> wayChoice(Way way, Location location, AccessSnapshot access)
	{
		final TravelMethod method = way.getMethod();
		final List<Requirement> missing = access.missingFor(method);
		if (!missing.isEmpty())
		{
			return new Choice<>(way, wayLabel(way, location) + " (locked)", false, AccessSnapshot.describe(missing));
		}
		final PohSetup poh = settings.editAccount().getPoh();
		final boolean inHouse;
		switch (way.getHow())
		{
			case POH_NEXUS:
				inHouse = poh.getNexusDestinations().contains(method.getNexus());
				break;
			case POH_JEWELLERY_BOX:
				inHouse = poh.getJewelleryBox() != null && poh.getJewelleryBox().includes(method.getJewelleryBox());
				break;
			case POH_FAIRY_RING:
				inHouse = poh.isFairyRing();
				break;
			case POH_SPIRIT_TREE:
				inHouse = poh.isSpiritTree();
				break;
			default:
				inHouse = true;
				break;
		}
		if (!inHouse)
		{
			return new Choice<>(way, wayLabel(way, location) + " (not in your house)", false,
				"Set your house's furniture in Account > My house");
		}
		// A spell that can't be cast right now still works as a tablet, so say why rather than lock it
		final Spell spell = method.getSpell();
		if (way.getHow() == Departure.DIRECT && method.getKind() == TravelKind.SPELL && access.isKnown()
			&& !access.canCast(spell))
		{
			final String why = !access.isOnSpellbook(spell) ? title(spell.getSpellbook().name()) + " spellbook"
				: "needs " + spell.getMagicLevel() + " Magic";
			return new Choice<>(way, method.getDisplayName() + " (tablet only: " + why + ")", true,
				"You can't cast this right now (" + why + "). It's used if you carry its teleport tablet; "
					+ "otherwise another way is picked.");
		}
		// Uses left today (Ardougne cloak 2/3, Explorer's ring 2)
		final Integer today = way.getHow() == Departure.DIRECT ? ChargeBudget.leftToday(method, holdings.get()) : null;
		if (today != null)
		{
			return new Choice<>(way, wayLabel(way, location) + (today == 0 ? " (used up today)"
				: " (" + today + " left today)"), true, "Limited per day; resets each day");
		}
		// Charges held, pooled across every piece of the item
		final TravelItem item = way.getHow() == Departure.DIRECT ? method.getItem() : null;
		final Integer charges = item != null ? ChargeBudget.held(item, holdings.get()) : null;
		if (charges != null)
		{
			return new Choice<>(way, wayLabel(way, location) + (charges == 0 ? " (no charges)"
				: " (" + charges + (charges == 1 ? " charge)" : " charges)")), true,
				"Charges are shared by every teleport this item has, across the whole run");
		}
		return new Choice<>(way, wayLabel(way, location), true, method.getNote());
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

	/** A chosen teleport, or runes instead of a tablet at this stop. */
	private static boolean isOverridden(RunConfig config, Location location)
	{
		return config.getTravel().containsKey(location) || config.getRunesNotTabsAt().contains(location);
	}

}
