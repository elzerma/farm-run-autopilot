package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.RuneAmount;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelKind;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyCalculator;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.gameval.ItemID;

/**
 * What a stop's way needs carried, as keys that combine as a set across the run (docs/plans/travel-rework.md, T4): one
 * skills necklace serves every leg that uses it for one slot, and every spell needing law runes shares that rune. The
 * keys follow what {@link SupplyCalculator} puts on the supply list for a way, so the travel search can tell the planner
 * to stop using one ({@link RouteAtlas#avoiding}) and the whole run be planned and counted again without it.
 */
final class Footprint
{
	static final String COINS = "coins";
	static final String FAIRY_RING_STAFF = "staff:fairy ring";
	static final String BALLOON_LOGS = "logs:balloon";

	private Footprint()
	{
	}

	/** The keys a stop's way needs; none for a walk, a spirit tree or a way with nothing to bring. */
	static Set<String> of(RouteStop stop, RunConfig config, AccessSnapshot access, Holdings holdings)
	{
		final Departure departure = stop.getDeparture();
		if (departure == Departure.NONE)
		{
			return Collections.emptySet();
		}
		final Set<String> keys = new TreeSet<>();
		final AtlasLeg atlas = stop.getAtlas();
		final TravelMethod method = stop.getMethod();
		if (departure.isViaHouse() || atlas != null && atlas.isViaHouse())
		{
			// Teleport to House by cape if the player has one, otherwise tablet or runes
			if (holdings.countAny(TravelItem.CONSTRUCTION_CAPE.getItemIds()) > 0)
			{
				keys.add(item(TravelItem.CONSTRUCTION_CAPE));
			}
			else if (holdings.countAny(TravelItem.MAX_CAPE.getItemIds()) > 0)
			{
				keys.add(item(TravelItem.MAX_CAPE));
			}
			else
			{
				spell(keys, Spell.TELEPORT_TO_HOUSE, stop, config, access, holdings);
			}
		}
		else if (departure == Departure.DIRECT && method != null)
		{
			if (method.getCoins() > 0)
			{
				keys.add(COINS);
			}
			if (method.getSpell() != null)
			{
				spell(keys, method.getSpell(), stop, config, access, holdings);
			}
			if (method.getItem() != null)
			{
				keys.add(item(method.getItem()));
			}
			if (method.getKind() == TravelKind.BALLOON)
			{
				keys.add(BALLOON_LOGS);
			}
		}
		else if (departure.getFairyRingAccess() != null)
		{
			// The way to the ring (cloak, slayer ring...), the one the player has as the supply list picks it
			final FairyRingAccess way = departure.getFairyRingAccess();
			if (!way.getItems().isEmpty())
			{
				TravelItem item = way.getItems().get(0);
				for (TravelItem candidate : way.getItems())
				{
					if (holdings.countAny(candidate.getItemIds()) > 0)
					{
						item = candidate;
						break;
					}
				}
				keys.add(item(item));
			}
		}
		if (atlas != null)
		{
			if (atlas.getSpell() != null)
			{
				spell(keys, atlas.getSpell(), stop, config, access, holdings);
			}
			if (atlas.getItem() != null)
			{
				keys.add(item(atlas.getItem()));
			}
			for (AtlasLeg.ItemNeed need : atlas.getItems())
			{
				keys.add(need.getIds().length == 1 && need.getIds()[0] == ItemID.COINS ? COINS : "item:" + need.getIds()[0]);
			}
			if (atlas.getBalloons() > 0)
			{
				keys.add(BALLOON_LOGS);
			}
		}
		if (SupplyCalculator.usesFairyRing(stop))
		{
			keys.add(FAIRY_RING_STAFF);
		}
		return keys;
	}

	/** One of the plugin's teleport items. */
	static String item(TravelItem item)
	{
		return "item:" + item.name();
	}

	/** A spell's tablet, or each of its rune types, as the supply list would bring it. */
	private static void spell(Set<String> keys, Spell spell, RouteStop stop, RunConfig config, AccessSnapshot access,
		Holdings holdings)
	{
		if (SupplyCalculator.usesTablet(spell, stop.getLocation(), config, access, holdings))
		{
			keys.add("tablet:" + spell.name());
			return;
		}
		for (RuneAmount amount : spell.getRunes())
		{
			keys.add("rune:" + amount.getRune().name());
		}
	}
}
