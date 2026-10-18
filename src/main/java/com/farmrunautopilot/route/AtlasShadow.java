package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.travel.atlas.AtlasAccount;
import com.farmrunautopilot.data.travel.atlas.AtlasHouse;
import com.farmrunautopilot.data.travel.atlas.AtlasLegs;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.data.travel.TravelTimes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sprint T2 of docs/plans/travel-rework.md, shadow mode: for a planned route, each leg the planner chose against the
 * atlas's quickest leg for the same player, for the dev client's log. Nothing here changes the route.
 */
final class AtlasShadow
{
	private AtlasShadow()
	{
	}

	/**
	 * One line per leg, e.g. "3. Catherby: plugin 29 s (Camelot Teleport) | atlas 26 s (Camelot Teleport > walk 58
	 * tiles), 3 s quicker", and the totals.
	 */
	static String compare(TravelAtlas atlas, AtlasAccount account, AtlasHouse house, Route route, List<Patch> patches)
	{
		final AtlasLegs legs = new AtlasLegs(atlas, account, house);
		final Map<Location, List<Integer>> places = new LinkedHashMap<>();
		for (Patch patch : patches)
		{
			final TravelAtlas.Place place = atlas.patchPlace(patch.name());
			if (place != null)
			{
				final List<Integer> list = places.computeIfAbsent(patch.getLocation(), k -> new ArrayList<>());
				if (!list.contains(place.getId()))
				{
					list.add(place.getId());
				}
			}
		}
		final StringBuilder s = new StringBuilder();
		s.append(legs.usableTeleports()).append(" teleports usable, ")
			.append(house == null ? "no house" : house.getExits().size() + " ways out of the house").append('\n');
		double plugin = 0;
		double atlasTotal = 0;
		int from = -1;
		final List<RouteStop> stops = route.getStops();
		for (int i = 0; i < stops.size(); i++)
		{
			final RouteStop stop = stops.get(i);
			final List<Integer> to = places.getOrDefault(stop.getLocation(), List.of());
			final AtlasLegs.Leg leg = to.isEmpty() ? null : legs.quickest(from, to);
			final double atlasSeconds = leg == null ? Double.NaN
				: leg.getSeconds() + (leg.isWalking() ? TravelTimes.WALK_OVERHEAD : TravelTimes.LEG_OVERHEAD);
			s.append(i + 1).append(". ").append(stop.getLocation().getDisplayName()).append(": plugin ")
				.append(Math.round(stop.getLegSeconds())).append(" s (").append(stop.describeTravel()).append(") | atlas ");
			if (leg == null)
			{
				s.append(to.isEmpty() ? "no patch places" : "no way").append('\n');
			}
			else
			{
				final double gap = stop.getLegSeconds() - atlasSeconds;
				s.append(Math.round(atlasSeconds)).append(" s (").append(leg.getHow()).append(")")
					.append(Math.abs(gap) >= 3 ? String.format(Locale.ROOT, ", atlas %.0f s %s", Math.abs(gap),
						gap > 0 ? "quicker" : "slower") : "").append('\n');
				plugin += stop.getLegSeconds();
				atlasTotal += atlasSeconds;
			}
			from = to.isEmpty() ? -1 : to.get(to.size() - 1);
		}
		return s.append(String.format(Locale.ROOT, "Legs with an atlas answer: plugin %.0f s, atlas %.0f s", plugin,
			atlasTotal)).toString();
	}
}
