package com.farmrunautopilot.supply;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.RunConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks how to reach each location. The player's choice wins when it's unlocked. "Auto" is a stand-in
 * until the route planner (M6) picks by time: the wiki's first choice if the player can use it right
 * now, otherwise the shortest-walk method they can use.
 */
public final class TravelChooser
{
	private TravelChooser()
	{
	}

	/**
	 * @return the method to use, or null if none is unlocked
	 */
	public static TravelMethod choose(Location location, RunConfig config, AccessSnapshot access, Holdings holdings)
	{
		final TravelMethod chosen = config.getTravel().get(location);
		if (chosen != null && access.missingFor(chosen).isEmpty())
		{
			return chosen;
		}

		final List<TravelMethod> unlocked = new ArrayList<>();
		for (TravelMethod method : TravelMethod.values())
		{
			if (method.getDestination() == location && access.missingFor(method).isEmpty())
			{
				unlocked.add(method);
			}
		}
		if (unlocked.isEmpty())
		{
			return null;
		}

		final Comparator<TravelMethod> preference = Comparator
			.comparing((TravelMethod m) -> !isPrimaryHere(m, location))
			.thenComparing(TravelMethod::getWalk);
		return unlocked.stream()
			.filter(m -> usableNow(m, access, holdings))
			.min(preference)
			.orElseGet(() -> unlocked.stream().min(preference).get());
	}

	/** Whether the player has what this method needs on hand (item, tablet, or can cast). */
	static boolean usableNow(TravelMethod method, AccessSnapshot access, Holdings holdings)
	{
		switch (method.getKind())
		{
			case SPELL:
			case HOUSE_PORTAL:
				return canTeleport(method.getSpell(), access, holdings);
			case FAIRY_RING:
			case SPIRIT_TREE:
				return true;
			default:
				return method.getItem() != null && holdings.countAny(method.getItem().getItemIds()) > 0;
		}
	}

	private static boolean canTeleport(Spell spell, AccessSnapshot access, Holdings holdings)
	{
		return (spell.hasTablet() && holdings.count(spell.getTabletItemId()) > 0) || access.canCast(spell);
	}

	private static boolean isPrimaryHere(TravelMethod method, Location location)
	{
		for (Patch patch : location.getPatches())
		{
			if (method.isPrimaryFor(patch.getType()))
			{
				return true;
			}
		}
		return false;
	}
}
