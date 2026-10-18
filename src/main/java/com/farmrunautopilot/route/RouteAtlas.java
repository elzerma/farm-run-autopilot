package com.farmrunautopilot.route;

import com.farmrunautopilot.data.travel.atlas.AtlasAccount;
import com.farmrunautopilot.data.travel.atlas.AtlasLegs;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import java.util.function.Supplier;

/**
 * The travel atlas and the player as it needs them (docs/plans/travel-rework.md, sprint T3). With it the planner takes
 * its legs from the atlas; without it (the atlas didn't load), from its old tables. Made for one round of planning
 * (the client thread's refresh): every plan and question in it shares the legs worked out for the player.
 */
public final class RouteAtlas
{
	private final TravelAtlas atlas;
	private final AtlasAccount account;
	/** Legs for a plan that hasn't used anything up yet, and what they were made for. */
	private AtlasLegs fresh;
	private Object[] freshFor;

	public RouteAtlas(TravelAtlas atlas, AtlasAccount account)
	{
		this.atlas = atlas;
		this.account = account;
	}

	public TravelAtlas getAtlas()
	{
		return atlas;
	}

	public AtlasAccount getAccount()
	{
		return account;
	}

	/**
	 * The legs for a plan that hasn't used anything up yet, made once for the same settings, levels, items and house
	 * ({@code inputs}, compared as the same objects) and shared by every plan and question asked with them.
	 */
	AtlasLegs freshLegs(Object[] inputs, Supplier<AtlasLegs> make)
	{
		if (fresh == null || !sameObjects(freshFor, inputs))
		{
			fresh = make.get();
			freshFor = inputs.clone();
		}
		return fresh;
	}

	private static boolean sameObjects(Object[] a, Object[] b)
	{
		if (a.length != b.length)
		{
			return false;
		}
		for (int i = 0; i < a.length; i++)
		{
			if (a[i] != b[i])
			{
				return false;
			}
		}
		return true;
	}
}
