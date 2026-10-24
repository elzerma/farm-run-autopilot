package com.farmrunautopilot.route;

import com.farmrunautopilot.data.travel.atlas.AtlasAccount;
import com.farmrunautopilot.data.travel.atlas.AtlasLegs;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The travel atlas and the player as it needs them (docs/plans/travel-rework.md, sprint T3): the planner takes every leg
 * from it, and without it there's no route. Made for one round of planning (the client thread's refresh, or the travel
 * search on its own thread): every plan and question in it shares the legs worked out for the player.
 */
public final class RouteAtlas
{
	/** Legs for a plan that hasn't used anything up yet, and what they were made for; shared by every view. */
	private static final class Fresh
	{
		AtlasLegs legs;
		Object[] inputs;
	}

	private final TravelAtlas atlas;
	private final AtlasAccount account;
	private final Fresh fresh;
	/** What the route shouldn't carry ({@link Footprint} keys), from the travel search; empty for the quickest ways. */
	private final Set<String> avoid;

	public RouteAtlas(TravelAtlas atlas, AtlasAccount account)
	{
		this(atlas, account, new Fresh(), Collections.emptySet());
	}

	private RouteAtlas(TravelAtlas atlas, AtlasAccount account, Fresh fresh, Set<String> avoid)
	{
		this.atlas = atlas;
		this.account = account;
		this.fresh = fresh;
		this.avoid = avoid;
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
	 * The same atlas and legs, planning without the ways that need any of these carried wherever another way gets there
	 * (T4's search: a slot freed can save a bank visit).
	 */
	RouteAtlas avoiding(Set<String> keys)
	{
		return new RouteAtlas(atlas, account, fresh, Collections.unmodifiableSet(new TreeSet<>(keys)));
	}

	/** What the route shouldn't carry where it has another way; empty for the quickest ways. */
	Set<String> getAvoid()
	{
		return avoid;
	}

	/** A copy with legs of its own, for planning on another thread. */
	RouteAtlas copy()
	{
		return new RouteAtlas(atlas, account, new Fresh(), avoid);
	}

	/**
	 * The legs for a plan that hasn't used anything up yet, made once for the same settings, levels, items and house
	 * ({@code inputs}, compared as the same objects) and shared by every plan and question asked with them.
	 */
	AtlasLegs freshLegs(Object[] inputs, Supplier<AtlasLegs> make)
	{
		if (fresh.legs == null || !sameObjects(fresh.inputs, inputs))
		{
			fresh.legs = make.get();
			fresh.inputs = inputs.clone();
		}
		return fresh.legs;
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
