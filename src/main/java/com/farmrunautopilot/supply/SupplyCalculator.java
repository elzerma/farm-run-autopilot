package com.farmrunautopilot.supply;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.Rune;
import com.farmrunautopilot.data.travel.RuneAmount;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelKind;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.AtlasLeg;
import com.farmrunautopilot.route.ChargeBudget;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * Works out what to bring for the next run (SPEC 11). Pure: everything it needs is passed in, so it can
 * run anywhere and be unit tested.
 */
public final class SupplyCalculator
{
	public static final int INVENTORY_SLOTS = 28;
	/** A typical herb harvest with compost (3 to 18 is possible). */
	public static final int HERBS_PER_PATCH = 8;
	public static final int FRUIT_PER_TREE = 6;
	private static final int STAMINA_DOSES_PER_POTION = 4;
	private static final String[] ORDINALS = {"1st", "2nd", "3rd"};

	// Where each kind of line is changed, shown in its tooltip
	private static final String CROPS = "Farm > Crops";
	private static final String RUN_OPTIONS = "Farm > Extras to bring";
	private static final String TRAVEL_DEFAULTS = "Travel > Defaults for every stop";
	private static final String PROTECTION = "Farm > Protection";
	private static final String CURES = "Farm > Compost and cures";
	/** Compost lines: the setting is the compost type, not anything about a bucket. */
	private static final String COMPOST_TYPE = "Farm > Compost and cures (compost type)";
	private static final String TRAVEL = "Travel > Locations";

	private SupplyCalculator()
	{
	}

	/**
	 * @param selection the patches in this run
	 * @param route how each stop is reached
	 * @param predictions current prediction per patch (null for never-seen patches)
	 * @param itemName display name for an item ID
	 * @param price Grand Exchange price of an item ID, used to rank herbs for disease-free patches
	 */
	public static SupplyPlan calculate(RunConfig config, AccessSnapshot access, Holdings holdings,
		RunSelection selection, Route route, Function<Patch, PatchPrediction> predictions,
		IntFunction<String> itemName, IntToLongFunction price)
	{
		final List<SupplyLine> lines = new ArrayList<>();
		final List<String> notDue = new ArrayList<>(selection.getNotDue());
		final List<String> travelPlan = new ArrayList<>();
		final List<String> warnings = new ArrayList<>();
		final Map<PatchType, Integer> patchCounts = new EnumMap<>(PatchType.class);

		final List<Patch> patches = selection.getPatches();
		for (Patch patch : patches)
		{
			patchCounts.merge(patch.getType(), 1, Integer::sum);
		}
		if (patches.isEmpty())
		{
			return new SupplyPlan(lines, patchCounts, notDue, travelPlan, warnings, 0, "", 0, Collections.emptyMap());
		}
		final Holdings carried = holdings.carriedOnly();
		final int farming = access.isKnown() ? access.level(Skill.FARMING) : 99;
		// Before access is known, assume no diary: better to bring one payment too many.
		final boolean faladorElite = access.isKnown()
			&& access.isMet(Requirement.diary(AchievementDiary.FALADOR, AchievementDiary.Tier.ELITE));
		final Map<Rune, Integer> runeNeed = new EnumMap<>(Rune.class);
		int coins = 0;
		boolean needAxe = false;
		boolean anyWeeds = false;
		final Map<Integer, Integer> payments = new LinkedHashMap<>();
		final Map<Compost, Integer> compost = new EnumMap<>(Compost.class);

		// Which crop goes where (1st choice, or backups when stock runs short)
		final Map<Patch, Crop> plantings = new LinkedHashMap<>();
		final List<Crop> reservedHerbs = diseaseFreeHerbs(config, farming, price);
		for (PatchType type : PatchType.values())
		{
			final List<Patch> ofType = new ArrayList<>();
			for (Patch patch : patches)
			{
				// Flowers kept for protecting allotments get the flower that suits them, below
				if (patch.getType() == type && !FlowerProtection.isProtecting(patch, config, access))
				{
					ofType.add(patch);
				}
			}
			if (!ofType.isEmpty())
			{
				plantings.putAll(CropAllocator.allocate(ofType, config.cropChoices(type, farming),
					crop -> holdings.count(crop.getPlantItemId()),
					patch -> isSafe(patch, config, access, faladorElite),
					type == PatchType.HERB ? reservedHerbs : Collections.emptyList()));
			}
		}
		plantings.putAll(protectingFlowers(patches, plantings, config, access, farming, holdings, predictions));

		// How each patch is looked after (elzerma, 2026-10-18): its type's choices first; when the gardener's payment or
		// that compost can't be had, down Pay -> ultracompost -> supercompost -> compost to the best the player has. Only
		// once the bank's contents are known, so nothing is given up on before it's been seen.
		final Map<Integer, Integer> paymentsLeft = new HashMap<>();
		final Map<Compost, Integer> compostLeft = compostAvailable(holdings);
		final boolean bucketUnknown = holdings.count(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED) > 0
			&& holdings.getBucketCompost() == null;
		final Map<Patch, PatchCare> care = new LinkedHashMap<>();
		final List<String> fellBack = new ArrayList<>();
		for (Patch patch : patches)
		{
			final PatchType type = patch.getType();
			final PatchPrediction prediction = predictions.apply(patch);
			final PatchState state = prediction != null ? prediction.getState() : PatchState.UNKNOWN;
			anyWeeds |= state == PatchState.WEEDS;

			if (type.isProtectable())
			{
				// Only trees are cleared; a grown allotment or hops patch is just picked
				if (type.isHealthChecked() && needsClearing(state))
				{
					if (config.getPayToClear().contains(type))
					{
						coins += Crop.CLEAR_PATCH_COINS;
					}
					else if (state != PatchState.STUMP)
					{
						needAxe = true;
					}
				}
			}

			final Crop crop = plantings.get(patch);
			final boolean pays = type.isProtectable() && crop != null && crop.hasPayment()
				&& FlowerProtection.effective(patch, config, access, plantings, predictions) == Protection.PAY_GARDENER;
			final boolean freeProtection = pays && patch == Patch.FALADOR_TREE && faladorElite;
			boolean paid = false;
			boolean lostPayment = false;
			if (pays && !freeProtection)
			{
				final int id = crop.getPaymentItemId();
				final int left = paymentsLeft.computeIfAbsent(id, holdings::count);
				// With no compost to fall back on either, the supply list asks for the payment, as chosen
				if (!holdings.isBankKnown() || left >= crop.getPaymentQuantity()
					|| !bucketUnknown && compostLeft.values().stream().noneMatch(n -> n > 0))
				{
					paymentsLeft.put(id, left - crop.getPaymentQuantity());
					payments.merge(id, crop.getPaymentQuantity(), Integer::sum);
					paid = true;
				}
				else
				{
					lostPayment = true;
				}
			}

			// Protected patches skip compost unless it raises the harvest; one whose payment can't be had gets the best
			// compost the player has instead
			Compost c = config.getCompost().getOrDefault(type, Compost.NONE);
			if ((paid || freeProtection) && !type.isCompostRaisesYield())
			{
				c = Compost.NONE;
			}
			else if (lostPayment)
			{
				c = fallBack(compostLeft, Compost.ULTRACOMPOST, bucketUnknown);
				fellBack.add(patch.getDisplayName() + " gets " + c.getDisplayName().toLowerCase(Locale.ROOT) + " (no "
					+ crop.getPaymentQuantity() + " " + itemName.apply(crop.getPaymentItemId()) + " to pay the gardener)");
			}
			else if (c != Compost.NONE && holdings.isBankKnown())
			{
				c = fallBack(compostLeft, c, bucketUnknown);
			}
			if (c != Compost.NONE)
			{
				compost.merge(c, 1, Integer::sum);
				compostLeft.computeIfPresent(c, (k, n) -> Math.max(0, n - 1));
			}
			care.put(patch, new PatchCare(paid, c));
		}
		if (!fellBack.isEmpty())
		{
			warnings.add("Protected with compost instead: " + String.join("; ", fellBack));
		}

		final Set<Crop> protectors = EnumSet.noneOf(Crop.class);
		plantings.forEach((patch, crop) ->
		{
			if (FlowerProtection.isProtecting(patch, config, access))
			{
				protectors.add(crop);
			}
		});
		// Seeds and saplings, one line per crop
		final Map<Crop, List<String>> plantedAt = new LinkedHashMap<>();
		plantings.forEach((patch, crop) -> plantedAt.computeIfAbsent(crop, k -> new ArrayList<>())
			.add(patch.getLocation().getDisplayName()));
		plantedAt.forEach((crop, places) ->
		{
			final int id = crop.getPlantItemId();
			final int n = places.size() * crop.getSeedsPerPatch();
			// Seeds stack; saplings and scarecrows don't.
			final int slots = crop.isSeed() && crop != Crop.SCARECROW ? 1 : n;
			final String role = protectors.contains(crop) ? "protects allotments" : cropRole(crop, config, farming, reservedHerbs);
			lines.add(line(SupplyLine.Group.SEEDS, itemName.apply(id) + (role.isEmpty() ? "" : " - " + role), n, holdings,
				carried, "Planting at: " + placesText(places), slots, id).usedUp());
		});

		// Payments
		payments.forEach((id, qty) ->
		{
			final String name = itemName.apply(id) + (config.isPayWithNotes() ? " (noted)" : "");
			lines.add(line(SupplyLine.Group.PAYMENTS, name, qty, holdings, carried,
				"Gardener protection payment", config.isPayWithNotes() ? 1 : qty, id).usedUp());
		});

		// Travel, following the route
		final Map<TravelItem, Integer> travelItems = new LinkedHashMap<>();
		final Map<Integer, Integer> tablets = new LinkedHashMap<>();
		boolean fairyRing = false;
		// Hot air balloon flights: one normal log each
		int balloonFlights = 0;
		// What the travel atlas's ways need that the plugin doesn't plan itself, by the first of their item ids
		final Map<Integer, AtlasLeg.ItemNeed> wayItems = new LinkedHashMap<>();
		final Map<Integer, Integer> wayCounts = new LinkedHashMap<>();
		// What each leg casts and whether it needs the fairy ring staff, for swapping staves (issue #7)
		final List<StaffPlanner.Leg> legs = new ArrayList<>();
		for (RouteStop stop : route.getStops())
		{
			final Location location = stop.getLocation();
			final TravelMethod method = stop.getMethod();
			final Departure departure = stop.getDeparture();
			if (departure == Departure.NONE)
			{
				warnings.add("No unlocked way to reach " + location.getDisplayName());
				continue;
			}
			final Map<Rune, Integer> runesBefore = new EnumMap<>(runeNeed);
			// A teleport the player picked that can't be used right now: say what's used instead
			final TravelMethod picked = config.getTravel().get(location);
			final Departure pickedHow = config.getTravelHow().get(location);
			if (picked != null && departure != Departure.WALK
				&& (method != picked || (pickedHow != null && departure != pickedHow)))
			{
				warnings.add("Your choice for " + location.getDisplayName() + " can't be used right now, so this run "
					+ "uses " + stop.describeTravel());
			}

			String extra = "";
			final AtlasLeg atlas = stop.getAtlas();
			if (departure.isViaHouse() || atlas != null && atlas.isViaHouse())
			{
				// Teleport to House by cape if the player has one, otherwise tablet or runes
				if (holdings.countAny(TravelItem.CONSTRUCTION_CAPE.getItemIds()) > 0)
				{
					travelItems.merge(TravelItem.CONSTRUCTION_CAPE, 1, Integer::sum);
				}
				else if (holdings.countAny(TravelItem.MAX_CAPE.getItemIds()) > 0)
				{
					travelItems.merge(TravelItem.MAX_CAPE, 1, Integer::sum);
				}
				else
				{
					extra = addSpell(Spell.TELEPORT_TO_HOUSE, location, config, access, holdings, tablets, runeNeed,
						warnings);
				}
				fairyRing |= departure == Departure.POH_FAIRY_RING;
			}
			else if (departure == Departure.DIRECT)
			{
				coins += method.getCoins();
				if (method.getSpell() != null)
				{
					extra = addSpell(method.getSpell(), location, config, access, holdings, tablets, runeNeed,
						warnings);
				}
				if (method.getItem() != null)
				{
					travelItems.merge(method.getItem(), 1, Integer::sum);
					if (ChargeBudget.chargesUnknown(method.getItem(), holdings))
					{
						warnings.add("Check your " + method.getItem().getDisplayName().toLowerCase()
							+ " so its charges are known");
					}
				}
				fairyRing |= method.getKind() == TravelKind.FAIRY_RING;
				balloonFlights += method.getKind() == TravelKind.BALLOON ? 1 : 0;
			}
			else if (departure.getFairyRingAccess() != null)
			{
				// The way to the ring (cloak, slayer ring, ...); walking to a nearby ring needs nothing
				final FairyRingAccess way = departure.getFairyRingAccess();
				if (!way.getItems().isEmpty())
				{
					// The one the player has (cloak 2+ shares a line with the Ardougne farm teleport)
					TravelItem item = way.getItems().get(0);
					for (TravelItem candidate : way.getItems())
					{
						if (holdings.countAny(candidate.getItemIds()) > 0)
						{
							item = candidate;
							break;
						}
					}
					travelItems.merge(item, 1, Integer::sum);
				}
				fairyRing = true;
			}
			if (atlas != null)
			{
				// A way from the travel atlas: its spell or teleport item, and whatever else the way needs
				if (atlas.getSpell() != null)
				{
					extra = addSpell(atlas.getSpell(), location, config, access, holdings, tablets, runeNeed, warnings);
				}
				if (atlas.getItem() != null)
				{
					travelItems.merge(atlas.getItem(), 1, Integer::sum);
					if (ChargeBudget.chargesUnknown(atlas.getItem(), holdings))
					{
						warnings.add("Check your " + atlas.getItem().getDisplayName().toLowerCase()
							+ " so its charges are known");
					}
				}
				for (AtlasLeg.ItemNeed need : atlas.getItems())
				{
					if (need.getIds().length == 1 && need.getIds()[0] == ItemID.COINS)
					{
						coins += need.getQuantity();
						continue;
					}
					final int key = need.getIds()[0];
					wayItems.putIfAbsent(key, need);
					// Used up each time (a tablet), or one carried for every leg that needs it
					wayCounts.merge(key, need.getQuantity(), need.isUsedUp() ? Integer::sum : Math::max);
				}
				balloonFlights += atlas.getBalloons();
			}
			final boolean legFairy = usesFairyRing(stop);
			// Into the house by a spirit tree, then its fairy ring, needs the staff too
			fairyRing |= legFairy;
			final Map<Rune, Integer> legRunes = new EnumMap<>(Rune.class);
			runeNeed.forEach((rune, n) ->
			{
				final int cast = n - runesBefore.getOrDefault(rune, 0);
				if (cast > 0)
				{
					legRunes.put(rune, cast);
				}
			});
			legs.add(new StaffPlanner.Leg(location, legRunes, new EnumMap<>(Rune.class), legFairy));
			final boolean auto = (method != null || atlas != null) && config.getTravel().get(location) != method;
			travelPlan.add(location.getDisplayName() + ": " + stop.describeTravel() + extra + (auto ? " (auto)" : ""));
		}
		tablets.forEach((id, n) -> lines.add(line(SupplyLine.Group.TRAVEL, itemName.apply(id), n, holdings, carried,
			null, 1, id).usedUp()));
		// Fairy rings need a staff wielded, which takes the weapon slot
		final boolean fairyStaff = fairyRing && !(access.isKnown()
			&& access.isMet(Requirement.diary(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.ELITE)));

		// Spells used on patches
		final int herbs = patchCounts.getOrDefault(PatchType.HERB, 0);
		// Magic secateurs also add to allotment and hops yields (wiki: Magic secateurs)
		final int boosted = herbs + patchCounts.getOrDefault(PatchType.ALLOTMENT, 0) + patchCounts.getOrDefault(PatchType.HOPS, 0);
		final boolean seeds = plantings.values().stream().anyMatch(crop -> crop.isSeed() && crop != Crop.SCARECROW);
		if (config.isUseCurePlant() && herbs > 0)
		{
			addRunes(runeNeed, Spell.CURE_PLANT);
			// Cast, if at all, at a herb patch, with the staff that was wielded on the way there
			for (StaffPlanner.Leg leg : legs)
			{
				if (patches.stream().anyMatch(p -> p.getType() == PatchType.HERB && p.getLocation() == leg.location))
				{
					addRunes(leg.backup, Spell.CURE_PLANT);
				}
			}
		}

		// Who gets each equipment slot, now that the runes are known (issue #7). Put on at the bank; only staves are
		// swapped on the way, below.
		final boolean secateursWanted = config.isRecommendEquipmentBoosts() && boosted > 0;
		final List<Wearable> extras = new ArrayList<>();
		if (fairyStaff)
		{
			extras.add(new Wearable(Wearable.FAIRY_RING_STAFF, TravelItem.WearSlot.WEAPON, Wearable.NEEDED, 0,
				wornAny(holdings, SupplyItems.FAIRY_RING_STAFFS)));
		}
		extras.addAll(runeItems(runeNeed, holdings));
		if (secateursWanted)
		{
			// Their bonus works from the inventory, so they come last
			extras.add(new Wearable(Wearable.MAGIC_SECATEURS, TravelItem.WearSlot.WEAPON, 1, 1,
				wornAny(holdings, SupplyItems.MAGIC_SECATEURS)));
		}
		final Map<TravelItem.WearSlot, Object> wear = toWear(travelItems.keySet(), extras, config.getOutfit(), holdings);
		// More than one staff, swapped on the way, when that frees an inventory slot (issue #7)
		final Map<TravelItem.WearSlot, Object> noStaff = new EnumMap<>(wear);
		noStaff.put(TravelItem.WearSlot.WEAPON, Wearable.MAGIC_SECATEURS);
		final Set<Rune> alwaysFree = infiniteRunes(noStaff, holdings);
		final List<Object> swappable = swappableStaves(extras, runeNeed, alwaysFree, wear.get(TravelItem.WearSlot.WEAPON));
		if (fairyStaff)
		{
			swappable.add(0, Wearable.FAIRY_RING_STAFF);
		}
		final List<StaffPlanner.Leg> staffLegs = new ArrayList<>();
		for (StaffPlanner.Leg leg : legs)
		{
			// With the Elite Lumbridge & Draynor diary a fairy ring needs no staff
			staffLegs.add(new StaffPlanner.Leg(leg.location, leg.runes, leg.backup, leg.fairyRing && fairyStaff));
		}
		final StaffPlanner.Plan swaps = StaffPlanner.plan(staffLegs, runeNeed, swappable, wear.get(TravelItem.WearSlot.WEAPON),
			alwaysFree, StaffPlanner.pouchTypes(holdings));
		if (swaps != null)
		{
			wear.put(TravelItem.WearSlot.WEAPON, swaps.start());
		}
		travelItems.forEach((item, stops) ->
		{
			final boolean worn = wear.containsValue(item);
			final String used = stops > 1 ? "Used at " + stops + " stops" : "Used once";
			final SupplyLine line = line(SupplyLine.Group.TRAVEL, item.getDisplayName(), 1, holdings, carried,
				worn ? used + ". Wear it: no inventory slot" : used, worn ? 0 : 1, item.getItemIds());
			lines.add(worn ? line.worn() : line);
		});
		wayItems.forEach((key, need) ->
		{
			final SupplyLine line = line(SupplyLine.Group.TRAVEL, itemName.apply(key), wayCounts.get(key), holdings, carried,
				need.isTeleport() ? "The teleport on the way" : "Needed on the way", 1, need.getIds());
			lines.add(need.isUsedUp() ? line.usedUp() : line);
		});
		if (balloonFlights > 0)
		{
			lines.add(line(SupplyLine.Group.TRAVEL, "Logs", balloonFlights, holdings, carried,
				"The hot air balloon takes one normal log a flight", 1, ItemID.LOGS).usedUp());
		}
		if (fairyStaff)
		{
			final String why = "Needed for fairy rings until the Elite Lumbridge & Draynor diary. ";
			if (swaps == null)
			{
				lines.add(line(SupplyLine.Group.TRAVEL, "Dramen or lunar staff", 1, holdings, carried,
					why + "Wield it: fairy rings need it wielded", 0, SupplyItems.FAIRY_RING_STAFFS).worn());
			}
			else
			{
				lines.add(swapLine(SupplyLine.Group.TRAVEL, "Dramen or lunar staff", why, Wearable.FAIRY_RING_STAFF, swaps,
					holdings, carried, SupplyItems.FAIRY_RING_STAFFS));
			}
		}

		// Runes, with the staff or tome the plan puts on
		if (swaps != null)
		{
			for (Object staff : swaps.staves)
			{
				if (staff instanceof Integer)
				{
					final int id = (Integer) staff;
					lines.add(swapLine(SupplyLine.Group.RUNES, itemName.apply(id), "Covers the "
						+ coveredText(id, runeNeed) + " runes when wielded. ", staff, swaps, holdings, carried, id));
				}
			}
		}
		for (Map.Entry<TravelItem.WearSlot, Object> e : wear.entrySet())
		{
			final Object item = e.getValue();
			if (item instanceof Integer && (swaps == null || e.getKey() != TravelItem.WearSlot.WEAPON))
			{
				final int id = (Integer) item;
				lines.add(line(SupplyLine.Group.RUNES, itemName.apply(id), 1, holdings, carried, "Covers the "
					+ coveredText(id, runeNeed) + " runes. " + (SupplyItems.RUNE_TOMES.contains(id) ? "Wear" : "Wield")
					+ " it: no runes to carry", 0, id).worn());
			}
		}
		final RuneResult runes;
		if (swaps == null)
		{
			runes = runes(runeNeed, infiniteRunes(wear, holdings), holdings, carried, itemName);
		}
		else
		{
			// Only the casts made with a staff that doesn't cover a rune need it carried
			final Map<Rune, Integer> toCarry = new EnumMap<>(runeNeed);
			swaps.runes.forEach(toCarry::put);
			final Set<Rune> free = EnumSet.noneOf(Rune.class);
			free.addAll(alwaysFree);
			free.addAll(swaps.free);
			runes = runes(toCarry, free, holdings, carried, itemName);
		}
		lines.addAll(runes.lines);

		// Coins
		if (coins > 0)
		{
			lines.add(line(SupplyLine.Group.PAYMENTS, "Coins", coins, holdings, carried,
				"200 per tree cleared by the gardener, plus travel fares", 1, ItemID.COINS).usedUp());
		}

		// Tools and compost
		lines.add(line(SupplyLine.Group.TOOLS, "Spade", 1, holdings, carried, null, 1, ItemID.SPADE));
		if (!holdings.isAutoweedOn() || anyWeeds)
		{
			lines.add(line(SupplyLine.Group.TOOLS, "Rake", 1, holdings, carried,
				holdings.isAutoweedOn() ? "Some patches have weeds" : "Not needed once Tithe Farm Auto-weed is on",
				1, ItemID.RAKE));
		}
		// Barbarian seed planting plants by hand (issue #6)
		if (seeds && !(access.isKnown() && access.isMet(Requirement.unlock(Unlock.BARBARIAN_PLANTING))))
		{
			lines.add(line(SupplyLine.Group.TOOLS, "Seed dibber", 1, holdings, carried, null, 1, ItemID.DIBBER));
		}
		if (needAxe)
		{
			lines.add(changeIn(line(SupplyLine.Group.TOOLS, "Axe", 1, holdings, carried,
				"To chop grown trees (pay-to-clear is off)", 1, SupplyItems.AXES), PROTECTION));
		}
		lines.addAll(compostLines(compost, holdings, carried, warnings));

		// Optional
		if (secateursWanted)
		{
			// The bonus works from the inventory too. Worn when nothing else wants the weapon slot; otherwise they go
			// in the inventory (elzerma)
			final Object weapon = wear.get(TravelItem.WearSlot.WEAPON);
			final boolean wearSecateurs = weapon == Wearable.MAGIC_SECATEURS;
			final String takenBy = swaps != null ? "staves you swap between"
				: weapon instanceof Integer ? itemName.apply((Integer) weapon).toLowerCase() : "staff for fairy rings";
			final SupplyLine secateurs = changeIn(line(SupplyLine.Group.OPTIONAL, "Magic secateurs", 1, holdings,
				carried, wearSecateurs ? (herbs == boosted ? "+10% herb yield" : "+10% yield") + ". Wear it: no inventory slot"
				: (herbs == boosted ? "+10% herb yield" : "+10% yield") + "; in the inventory, as the " + takenBy + " takes the weapon slot",
				wearSecateurs ? 0 : 1, SupplyItems.MAGIC_SECATEURS), RUN_OPTIONS);
			lines.add(wearSecateurs ? secateurs.worn() : secateurs);
		}
		if (config.getOutfit() != Outfit.NONE)
		{
			lines.add(outfitLine(config.getOutfit(), holdings, carried).worn());
		}
		// Only when a patch on the run can catch disease: the Civitas herbs with the Champion rank, Trollheim and the
		// like never need it (the Fortis Champion test could never pass)
		if (config.getPlantCureDoses() > 0
			&& patches.stream().anyMatch(patch -> !isSafe(patch, config, access, faladorElite)))
		{
			lines.add(changeIn(line(SupplyLine.Group.OPTIONAL, "Plant cure", config.getPlantCureDoses(), holdings, carried,
				"Backup for diseased patches", config.getPlantCureDoses(), ItemID.PLANT_CURE).usedUp(), CURES));
		}
		if (config.getStaminaDoses() > 0)
		{
			int doses = 0;
			int carriedDoses = 0;
			for (Map.Entry<Integer, Integer> e : SupplyItems.STAMINA_DOSES.entrySet())
			{
				doses += holdings.count(e.getKey()) * e.getValue();
				carriedDoses += carried.count(e.getKey()) * e.getValue();
			}
			final int[] staminaIds = SupplyItems.STAMINA_DOSES.keySet().stream().mapToInt(Integer::intValue).toArray();
			lines.add(new SupplyLine(SupplyLine.Group.OPTIONAL, "Stamina doses", config.getStaminaDoses(), doses,
				carriedDoses, holdings.where(staminaIds), null,
				(config.getStaminaDoses() + STAMINA_DOSES_PER_POTION - 1) / STAMINA_DOSES_PER_POTION, false, TRAVEL_DEFAULTS,
				staminaIds, true));
		}

		int slots = 0;
		for (SupplyLine line : lines)
		{
			slots += line.getSlots();
		}
		if (!holdings.isBankKnown())
		{
			warnings.add("Open your bank once so its contents can be counted.");
		}
		if (slots > INVENTORY_SLOTS)
		{
			warnings.add("Needs about " + slots + " inventory slots, more than " + INVENTORY_SLOTS + ".");
		}
		// Filling up mid-run is said at the patch when it happens ("Inventory full: note produce..."), not here

		lines.sort((a, b) -> a.getGroup().compareTo(b.getGroup()));
		return new SupplyPlan(Collections.unmodifiableList(lines), patchCounts, notDue, travelPlan, warnings, coins,
			runes.summary, slots, Collections.unmodifiableMap(plantings), staffSwaps(swaps, holdings, itemName),
			Collections.unmodifiableMap(care));
	}

	/** Buckets of each compost the player has anywhere, and the bottomless bucket's uses of the compost it holds. */
	static Map<Compost, Integer> compostAvailable(Holdings holdings)
	{
		final Map<Compost, Integer> left = new EnumMap<>(Compost.class);
		for (Compost c : Compost.values())
		{
			if (c != Compost.NONE)
			{
				left.put(c, holdings.count(c.getItemId()));
			}
		}
		final Compost bucket = holdings.getBucketCompost();
		if (bucket != null && holdings.count(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED) > 0)
		{
			// Uses not known yet (not checked): enough for any run
			left.merge(bucket, holdings.getBucketUses() < 0 ? Integer.MAX_VALUE / 2 : holdings.getBucketUses(),
				Integer::sum);
		}
		return left;
	}

	/**
	 * The best compost from {@code from} down (ultracompost, supercompost, compost) that the player still has some of;
	 * {@code from} itself if they have none (so the supply list asks for it), or if a bottomless bucket's compost isn't
	 * known yet.
	 */
	static Compost fallBack(Map<Compost, Integer> left, Compost from, boolean bucketUnknown)
	{
		if (bucketUnknown)
		{
			return from;
		}
		final Compost[] tiers = {Compost.ULTRACOMPOST, Compost.SUPERCOMPOST, Compost.COMPOST};
		boolean reached = false;
		for (Compost c : tiers)
		{
			reached |= c == from;
			if (reached && left.getOrDefault(c, 0) > 0)
			{
				return c;
			}
		}
		return from;
	}

	/** Setting off for this stop needs the fairy ring staff wielded (the ring itself, not just a way to it). */
	public static boolean usesFairyRing(RouteStop stop)
	{
		final Departure departure = stop.getDeparture();
		return departure == Departure.POH_FAIRY_RING || departure == Departure.TREE_HOUSE_FAIRY_RING
			|| departure.getFairyRingAccess() != null
			|| departure == Departure.DIRECT && stop.getMethod() != null && stop.getMethod().getKind() == TravelKind.FAIRY_RING
			|| stop.getAtlas() != null && stop.getAtlas().isFairyRing();
	}

	/**
	 * The weapon-slot staves that could be swapped between: of the rune staves the player owns, one for each set of
	 * this run's runes covered (the one kept on now first, then one already worn), leaving out runes a tome covers.
	 */
	static List<Object> swappableStaves(List<Wearable> extras, Map<Rune, Integer> need, Set<Rune> alwaysFree,
		Object current)
	{
		final List<Wearable> ordered = new ArrayList<>();
		for (Wearable wearable : extras)
		{
			if (wearable.item instanceof Integer && wearable.slot == TravelItem.WearSlot.WEAPON)
			{
				ordered.add(wearable);
			}
		}
		ordered.sort(java.util.Comparator.comparingInt(w -> w.item.equals(current) ? 0 : w.worn ? 1 : 2));
		final List<Object> staves = new ArrayList<>();
		final Set<Set<Rune>> seen = new java.util.HashSet<>();
		for (Wearable wearable : ordered)
		{
			final Set<Rune> covers = EnumSet.noneOf(Rune.class);
			for (Rune rune : StaffPlanner.covers(wearable.item))
			{
				if (need.containsKey(rune) && !alwaysFree.contains(rune))
				{
					covers.add(rune);
				}
			}
			if (!covers.isEmpty() && seen.add(covers))
			{
				staves.add(wearable.item);
			}
		}
		return staves;
	}

	/** "earth and fire": the runes this run needs that a staff or tome covers. */
	private static String coveredText(int id, Map<Rune, Integer> need)
	{
		final List<String> covered = new ArrayList<>();
		for (Rune rune : SupplyItems.INFINITE_RUNE_ITEMS.get(id))
		{
			if (need.containsKey(rune))
			{
				covered.add(rune.name().toLowerCase());
			}
		}
		return String.join(" and ", covered);
	}

	/**
	 * A staff the plan swaps to and from: put on at the bank if it's the first one wielded, otherwise carried (a slot),
	 * with the stops it's wielded for.
	 */
	private static SupplyLine swapLine(SupplyLine.Group group, String name, String why, Object staff,
		StaffPlanner.Plan swaps, Holdings holdings, Holdings carried, int... itemIds)
	{
		final List<String> before = new ArrayList<>();
		swaps.wield.forEach((location, wielded) ->
		{
			if (wielded.equals(staff))
			{
				before.add(location.getDisplayName());
			}
		});
		final String when = "for " + String.join(", ", before);
		if (staff.equals(swaps.start()))
		{
			return line(group, name, 1, holdings, carried, why + "Wield it at the bank (" + when
				+ "); the guide says when to swap staves", 0, itemIds).worn();
		}
		return line(group, name, 1, holdings, carried, why + "Carry it, and wield it when the guide says (" + when
			+ "): fewer slots than the runes", 1, itemIds);
	}

	/** The swaps for the guide, by stop: the staff's name and item IDs. */
	private static StaffSwaps staffSwaps(StaffPlanner.Plan plan, Holdings holdings, IntFunction<String> itemName)
	{
		if (plan == null)
		{
			return StaffSwaps.NONE;
		}
		final Map<Object, StaffSwaps.Swap> byStaff = new LinkedHashMap<>();
		final Set<Integer> ids = new java.util.HashSet<>();
		for (Object staff : plan.staves)
		{
			int[] staffIds;
			if (staff instanceof Integer)
			{
				staffIds = new int[]{(Integer) staff};
			}
			else
			{
				// The dramen or lunar staff the player has, or either
				staffIds = java.util.Arrays.stream(SupplyItems.FAIRY_RING_STAFFS).filter(id -> holdings.count(id) > 0)
					.toArray();
				if (staffIds.length == 0)
				{
					staffIds = SupplyItems.FAIRY_RING_STAFFS.clone();
				}
			}
			final String name = staffIds.length == 1 ? itemName.apply(staffIds[0]) : "dramen staff";
			byStaff.put(staff, new StaffSwaps.Swap(Character.toLowerCase(name.charAt(0)) + name.substring(1), staffIds));
			for (int id : staffIds)
			{
				ids.add(id);
			}
		}
		final Map<Location, StaffSwaps.Swap> wield = new LinkedHashMap<>();
		plan.wield.forEach((location, staff) -> wield.put(location, byStaff.get(staff)));
		return new StaffSwaps(Collections.unmodifiableMap(wield), byStaff.get(Wearable.FAIRY_RING_STAFF),
			Collections.unmodifiableSet(ids));
	}

	/** Something that could go in an equipment slot; one per slot is put on at the bank (issue #7). */
	static final class Wearable
	{
		/** The value of something that only works worn: it always gets its slot. */
		static final int NEEDED = Integer.MAX_VALUE;
		/** The dramen or lunar staff, wielded for fairy rings. */
		static final Object FAIRY_RING_STAFF = "fairy ring staff";
		static final Object MAGIC_SECATEURS = "magic secateurs";
		/**
		 * One inventory slot in {@link #value}: twelfths, so a rune pouch's share of a slot (1/2, 1/3, 1/4) is whole.
		 * Magic secateurs are worth 1, below any staff (elzerma: secateurs last).
		 */
		static final int SLOT = 12;

		/** A {@link TravelItem}, a rune item ID from {@link SupplyItems#INFINITE_RUNE_ITEMS}, or a marker above. */
		final Object item;
		final TravelItem.WearSlot slot;
		/** What wearing it saves, in twelfths of a slot ({@link #SLOT}), or {@link #NEEDED}. Higher wins. */
		final int value;
		/** Runes it saves this run, which breaks ties in value (20 fire beat 2 water). */
		final int runes;
		/** Breaks ties in value, lower first (magic secateurs come after a staff). */
		final int rank;
		/** Already worn, which breaks remaining ties. */
		final boolean worn;

		Wearable(Object item, TravelItem.WearSlot slot, int value, int rank, boolean worn)
		{
			this(item, slot, value, 0, rank, worn);
		}

		Wearable(Object item, TravelItem.WearSlot slot, int value, int runes, int rank, boolean worn)
		{
			this.item = item;
			this.slot = slot;
			this.value = value;
			this.runes = runes;
			this.rank = rank;
			this.worn = worn;
		}

		boolean beats(Wearable other)
		{
			if (value != other.value)
			{
				return value > other.value;
			}
			if (runes != other.runes)
			{
				return runes > other.runes;
			}
			if (rank != other.rank)
			{
				return rank < other.rank;
			}
			return worn && !other.worn;
		}
	}

	/** The teleport items to wear rather than carry, with nothing else competing for their slots. */
	static Set<TravelItem> toWear(Set<TravelItem> items, Outfit outfit, Holdings holdings)
	{
		final Set<TravelItem> result = EnumSet.noneOf(TravelItem.class);
		for (Object item : toWear(items, Collections.emptyList(), outfit, holdings).values())
		{
			result.add((TravelItem) item);
		}
		return result;
	}

	/**
	 * What goes in each equipment slot: the teleport items (each saves an inventory slot) and {@code extras}, ranked
	 * by {@link Wearable#beats}; among equals the first listed keeps the slot, teleport items in the order they're
	 * used. Every teleport item here works from the inventory too (all Ardougne cloak tiers, per the wiki), so
	 * whatever loses its slot is carried. Slots the outfit takes (Graceful's cape and gloves) are left out.
	 */
	static Map<TravelItem.WearSlot, Object> toWear(Set<TravelItem> items, List<Wearable> extras, Outfit outfit,
		Holdings holdings)
	{
		final Set<TravelItem.WearSlot> taken = outfit == Outfit.GRACEFUL
			? EnumSet.of(TravelItem.WearSlot.CAPE, TravelItem.WearSlot.HANDS) : EnumSet.noneOf(TravelItem.WearSlot.class);
		final List<Wearable> candidates = new ArrayList<>();
		for (TravelItem item : items)
		{
			// Kharedst's memoirs can't be worn; the Book of the dead (its upgrade) can
			if (item.getWearSlot() != null
				&& (item != TravelItem.KHAREDSTS_MEMOIRS || holdings.count(ItemID.BOOK_OF_THE_DEAD) > 0))
			{
				candidates.add(new Wearable(item, item.getWearSlot(), Wearable.SLOT, 0,
					wornAny(holdings, item.getItemIds())));
			}
		}
		candidates.addAll(extras);
		final Map<TravelItem.WearSlot, Wearable> best = new EnumMap<>(TravelItem.WearSlot.class);
		for (Wearable candidate : candidates)
		{
			final Wearable current = best.get(candidate.slot);
			if (!taken.contains(candidate.slot) && (current == null || candidate.beats(current)))
			{
				best.put(candidate.slot, candidate);
			}
		}
		final Map<TravelItem.WearSlot, Object> wear = new EnumMap<>(TravelItem.WearSlot.class);
		best.forEach((slot, wearable) -> wear.put(slot, wearable.item));
		return wear;
	}

	/**
	 * The staves and tomes the player owns that supply runes this run needs, each worth the slots its rune types take
	 * (with a rune pouch, each type's share of it: StaffPlanner.runeSlots), then the runes it saves. Lowest item ID
	 * first, so the pick doesn't depend on map order.
	 */
	static List<Wearable> runeItems(Map<Rune, Integer> need, Holdings holdings)
	{
		final List<Wearable> items = new ArrayList<>();
		final List<Integer> ids = new ArrayList<>(SupplyItems.INFINITE_RUNE_ITEMS.keySet());
		Collections.sort(ids);
		// What one rune type takes on average, in twelfths of a slot: a whole slot without a pouch, 1/3 in a full one
		final int perType = need.isEmpty() ? 0
			: Wearable.SLOT * StaffPlanner.runeSlots(need.size(), StaffPlanner.pouchTypes(holdings)) / need.size();
		for (int id : ids)
		{
			if (holdings.count(id) == 0)
			{
				continue;
			}
			int covers = 0;
			int runes = 0;
			for (Rune rune : SupplyItems.INFINITE_RUNE_ITEMS.get(id))
			{
				covers += need.containsKey(rune) ? 1 : 0;
				runes += need.getOrDefault(rune, 0);
			}
			if (covers > 0)
			{
				items.add(new Wearable(id, runeItemSlot(id), covers * perType, runes, 0,
					holdings.in(Holdings.Source.WORN).getOrDefault(id, 0) > 0));
			}
		}
		return items;
	}

	/**
	 * Runes supplied without limit once the plan's equipment is on: what's worn now, less the staves and tomes that
	 * give up their slot, plus the ones put on.
	 */
	static Set<Rune> infiniteRunes(Map<TravelItem.WearSlot, Object> wear, Holdings holdings)
	{
		final Set<Rune> infinite = EnumSet.noneOf(Rune.class);
		infinite.addAll(holdings.getInfiniteRunes());
		final Set<Rune> kept = EnumSet.noneOf(Rune.class);
		for (Integer id : holdings.in(Holdings.Source.WORN).keySet())
		{
			final Set<Rune> runes = SupplyItems.INFINITE_RUNE_ITEMS.get(id);
			if (runes == null)
			{
				continue;
			}
			final Object replacement = wear.get(runeItemSlot(id));
			if (replacement == null || id.equals(replacement))
			{
				kept.addAll(runes);
			}
			else
			{
				infinite.removeAll(runes);
			}
		}
		infinite.addAll(kept);
		for (Object item : wear.values())
		{
			if (item instanceof Integer)
			{
				infinite.addAll(SupplyItems.INFINITE_RUNE_ITEMS.get(item));
			}
		}
		return infinite;
	}

	private static TravelItem.WearSlot runeItemSlot(int id)
	{
		return SupplyItems.RUNE_TOMES.contains(id) ? TravelItem.WearSlot.SHIELD : TravelItem.WearSlot.WEAPON;
	}

	private static boolean wornAny(Holdings holdings, int[] ids)
	{
		final Map<Integer, Integer> worn = holdings.in(Holdings.Source.WORN);
		for (int id : ids)
		{
			if (worn.getOrDefault(id, 0) > 0)
			{
				return true;
			}
		}
		return false;
	}

	/** One line for an outfit: pieces held (any variant of each piece) out of the full set. */
	static SupplyLine outfitLine(Outfit outfit, Holdings holdings, Holdings carried)
	{
		final int[][] pieces = outfit.getPieces();
		int have = 0;
		int worn = 0;
		final List<Integer> ids = new ArrayList<>();
		for (int[] variants : pieces)
		{
			have += holdings.countAny(variants) > 0 ? 1 : 0;
			worn += carried.countAny(variants) > 0 ? 1 : 0;
			for (int id : variants)
			{
				ids.add(id);
			}
		}
		final int[] all = ids.stream().mapToInt(Integer::intValue).toArray();
		return new SupplyLine(SupplyLine.Group.OPTIONAL, outfit.getDisplayName(), pieces.length, have, worn,
			holdings.where(all), "Pieces held, any colour. Wear it for the run.", 0, false, RUN_OPTIONS, all);
	}

	/**
	 * How a crop got on the list, e.g. "1st choice" or "disease-free only". Empty with a single choice and no
	 * disease-free herbs, where there's nothing to explain.
	 */
	/**
	 * The flower for each flower patch in the run that's kept for protecting its allotments: one that protects what the
	 * allotments get this run (or hold now), at the player's level, one they hold first. Where none protects them all,
	 * the patch gets the player's own flower choice and the allotments' gardener is paid instead.
	 */
	static Map<Patch, Crop> protectingFlowers(List<Patch> patches, Map<Patch, Crop> plantings, RunConfig config,
		AccessSnapshot access, int farming, Holdings holdings, Function<Patch, PatchPrediction> predictions)
	{
		final Map<Patch, Crop> flowers = new LinkedHashMap<>();
		final Map<Crop, Integer> used = new EnumMap<>(Crop.class);
		for (Patch patch : patches)
		{
			if (!FlowerProtection.isProtecting(patch, config, access))
			{
				continue;
			}
			final List<Crop> allotmentCrops = new ArrayList<>();
			for (Patch mate : patch.getSetMates())
			{
				if (mate.getType() != PatchType.ALLOTMENT || !config.isPatchSelected(mate)
					|| config.protectionFor(mate) != Protection.FLOWER)
				{
					continue;
				}
				Crop crop = plantings.get(mate);
				if (crop == null)
				{
					final PatchPrediction now = predictions.apply(mate);
					crop = now != null && now.getState() == PatchState.GROWING ? now.getCrop() : null;
				}
				allotmentCrops.add(crop != null ? crop : config.cropFor(PatchType.ALLOTMENT, farming));
			}
			Crop flower = FlowerProtection.flowerFor(allotmentCrops, farming,
				crop -> holdings.count(crop.getPlantItemId()) - used.getOrDefault(crop, 0));
			if (flower == null)
			{
				flower = config.cropFor(PatchType.FLOWER, farming);
			}
			used.merge(flower, 1, Integer::sum);
			flowers.put(patch, flower);
		}
		return flowers;
	}

	/** "Catherby, Falador farm (south) x2": each stop once, with how many patches there. */
	static String placesText(List<String> places)
	{
		final Map<String, Integer> counts = new LinkedHashMap<>();
		for (String place : places)
		{
			counts.merge(place, 1, Integer::sum);
		}
		final List<String> parts = new ArrayList<>();
		counts.forEach((place, n) -> parts.add(n > 1 ? place + " x" + n : place));
		return String.join(", ", parts);
	}

	static String cropRole(Crop crop, RunConfig config, int farming, List<Crop> reservedHerbs)
	{
		final List<Crop> choices = config.cropChoices(crop.getType(), farming);
		final List<String> parts = new ArrayList<>();
		final int index = choices.indexOf(crop);
		if (index >= 0 && (choices.size() > 1 || !reservedHerbs.isEmpty()))
		{
			parts.add(ORDINALS[Math.min(index, ORDINALS.length - 1)] + " choice");
		}
		if (reservedHerbs.contains(crop))
		{
			parts.add("disease-free only");
		}
		return String.join(", ", parts);
	}

	/** The player's herbs for disease-free patches that they can plant, most valuable seed first. */
	static List<Crop> diseaseFreeHerbs(RunConfig config, int farming, IntToLongFunction price)
	{
		final List<Crop> herbs = new ArrayList<>();
		if (!config.isPrioritiseDiseaseFreeHerbs())
		{
			return herbs;
		}
		for (Crop crop : config.getDiseaseFreeHerbs())
		{
			if (crop.getFarmingLevel() <= farming)
			{
				herbs.add(crop);
			}
		}
		herbs.sort((a, b) -> Long.compare(price.applyAsLong(b.getPlantItemId()), price.applyAsLong(a.getPlantItemId())));
		return herbs;
	}

	/**
	 * Disease-free or protected: always disease-free, a disease-free unlock the player has, or paid for.
	 * Unknown access counts as not having the unlock.
	 */
	static boolean isSafe(Patch patch, RunConfig config, AccessSnapshot access, boolean faladorElite)
	{
		if (patch.isAlwaysDiseaseFree())
		{
			return true;
		}
		final Requirement diseaseFree = patch.getDiseaseFreeRequirement();
		if (diseaseFree != null && access.isKnown() && access.isMet(diseaseFree))
		{
			return true;
		}
		final Protection protection = config.protectionFor(patch);
		// A flower-protected allotment counts as safe when its set has a flower patch the player uses (the flower is
		// picked to suit the crop, so it's known only after the crops are)
		if (protection == Protection.FLOWER)
		{
			final Patch flowerPatch = FlowerProtection.flowerPatchOf(patch);
			return flowerPatch != null && FlowerProtection.isProtecting(flowerPatch, config, access);
		}
		return patch.getType().isProtectable() && patch.hasGardener()
			&& (protection == Protection.PAY_GARDENER || (patch == Patch.FALADOR_TREE && faladorElite));
	}

	/**
	 * Adds a spell's tablet (preferred, if owned or the spell can't be cast) or runes.
	 *
	 * @return how it will be done, e.g. " (tablet)"
	 */
	/**
	 * Tablet first: a spell is taken from its tablet when the player has one (unless this stop is set to use
	 * runes), and always when it can't be cast.
	 */
	public static boolean usesTablet(Spell spell, Location location, RunConfig config, AccessSnapshot access,
		Holdings holdings)
	{
		return spell.hasTablet() && (!access.canCast(spell)
			|| (!config.useRunesAt(location) && holdings.count(spell.getTabletItemId()) > 0));
	}

	private static String addSpell(Spell spell, Location location, RunConfig config, AccessSnapshot access,
		Holdings holdings, Map<Integer, Integer> tablets, Map<Rune, Integer> runeNeed, List<String> warnings)
	{
		// A spell the player can't cast (wrong spellbook with no altar, Magic level, quest) needs its tablet, even
		// when runes are preferred (GitHub #2)
		final boolean castable = access.canCast(spell);
		if (usesTablet(spell, location, config, access, holdings))
		{
			tablets.merge(spell.getTabletItemId(), 1, Integer::sum);
			return " (tablet)";
		}
		addRunes(runeNeed, spell);
		final String book = title(spell.getSpellbook().name()) + " spellbook";
		if (!castable)
		{
			return " (runes, " + book + "; you can't cast it yet)";
		}
		if (access.isOnSpellbook(spell))
		{
			return " (runes, " + book + ")";
		}
		// Off the current book: one Spellbook Swap cast on Lunar, otherwise a trip to the house altar
		if (access.needsLunarSwap(spell))
		{
			addRunes(runeNeed, Spell.SPELLBOOK_SWAP);
			warnings.add(location.getDisplayName() + ": cast Spellbook Swap (to " + book + ") just before "
				+ spell.getDisplayName());
			return " (runes, " + book + "; Spellbook Swap first)";
		}
		warnings.add(location.getDisplayName() + ": " + spell.getDisplayName() + " is on the " + book
			+ "; switch at your house altar first, and back again after");
		return " (runes, " + book + "; switch at your house altar)";
	}
	/** A grown tree or its stump must be cleared before replanting; unknown patches are assumed grown. */
	static boolean needsClearing(PatchState state)
	{
		switch (state)
		{
			case CHECK_HEALTH:
			case HARVESTABLE:
			case STUMP:
			case UNKNOWN:
				return true;
			default:
				return false;
		}
	}

	static final class RuneResult
	{
		final List<SupplyLine> lines = new ArrayList<>();
		String summary = "";
	}

	/** Runes held after free sources, and the combination runes spent to get there. */
	private static final class RuneAllocation
	{
		final Map<Rune, Integer> have = new EnumMap<>(Rune.class);
		final Map<Integer, Integer> comboUsed = new LinkedHashMap<>();
	}

	/** Rune lines, with the staves and tomes worn now. */
	static RuneResult runes(Map<Rune, Integer> need, Holdings holdings, Holdings carried, IntFunction<String> itemName)
	{
		return runes(need, holdings.getInfiniteRunes(), holdings, carried, itemName);
	}

	/**
	 * Rune lines after free sources: the staves and tomes the plan has on cover their runes ({@code infinite})
	 * completely, then combination runes are spent on the biggest shortfalls first (SPEC 11.3). Worked out twice: for
	 * everything held, and for what is carried.
	 */
	static RuneResult runes(Map<Rune, Integer> need, Set<Rune> infinite, Holdings holdings, Holdings carried,
		IntFunction<String> itemName)
	{
		final RuneResult result = new RuneResult();
		if (need.isEmpty())
		{
			return result;
		}
		final RuneAllocation all = allocateRunes(need, infinite, holdings);
		final RuneAllocation onYou = allocateRunes(need, infinite, carried);

		final List<String> bring = new ArrayList<>();
		final List<String> free = new ArrayList<>();
		// With a rune pouch, it holds the first types (those in it already first): one slot shared between them
		// (elzerma: 1/3 slot each when full); the rest take a slot each (StaffPlanner.runeSlots)
		final List<Rune> carriedTypes = new ArrayList<>();
		need.keySet().stream().filter(rune -> !infinite.contains(rune) && inPouch(holdings, rune)).forEach(carriedTypes::add);
		need.keySet().stream().filter(rune -> !infinite.contains(rune) && !inPouch(holdings, rune)).forEach(carriedTypes::add);
		final int pouchTypes = StaffPlanner.pouchTypes(holdings);
		final List<Rune> pouched = carriedTypes.subList(0, Math.min(pouchTypes, carriedTypes.size()));
		need.forEach((rune, n) ->
		{
			// Runes a staff or tome covers get no line of their own: the staff's line says so, and a ticked "earth
			// rune" line read as something to bring (elzerma)
			if (infinite.contains(rune))
			{
				free.add("staff covers " + title(rune.name()));
				return;
			}
			final String name = itemName.apply(rune.getItemId());
			bring.add(n + " " + rune.name().toLowerCase());
			String note = null;
			int slots = 1;
			if (pouched.contains(rune))
			{
				final int inPouch = holdings.getRunePouch().getOrDefault(rune.getItemId(), 0);
				note = "In your rune pouch: one slot for its " + pouched.size() + (pouched.size() == 1 ? " rune type"
					: " rune types") + (inPouch > 0 ? "; it has " + inPouch : "");
				slots = rune == pouched.get(0) ? 1 : 0;
			}
			result.lines.add(new SupplyLine(SupplyLine.Group.RUNES, name, n, all.have.get(rune), onYou.have.get(rune),
				holdings.where(rune.getItemId()), note, slots, false,
				TRAVEL + " (teleport, or runes instead of a tablet)", new int[]{rune.getItemId()}, true));
		});
		all.comboUsed.forEach((id, n) -> free.add(n + " " + itemName.apply(id).toLowerCase()));
		result.summary = String.join(", ", bring) + (free.isEmpty() ? "" : " (" + String.join("; ", free) + ")");
		return result;
	}

	private static RuneAllocation allocateRunes(Map<Rune, Integer> need, Set<Rune> infinite, Holdings holdings)
	{
		final RuneAllocation result = new RuneAllocation();
		final Map<Rune, Integer> shortfall = new EnumMap<>(Rune.class);
		need.forEach((rune, n) ->
		{
			final int held = infinite.contains(rune) ? n : holdings.count(rune.getItemId());
			result.have.put(rune, held);
			shortfall.put(rune, Math.max(0, n - held));
		});

		final List<Map.Entry<Integer, Set<Rune>>> combos = new ArrayList<>(SupplyItems.COMBINATION_RUNES.entrySet());
		combos.sort((a, b) -> Integer.compare(covers(b.getValue(), shortfall), covers(a.getValue(), shortfall)));
		for (Map.Entry<Integer, Set<Rune>> combo : combos)
		{
			int largest = 0;
			for (Rune rune : combo.getValue())
			{
				largest = Math.max(largest, shortfall.getOrDefault(rune, 0));
			}
			final int use = Math.min(holdings.count(combo.getKey()), largest);
			if (use <= 0)
			{
				continue;
			}
			result.comboUsed.put(combo.getKey(), use);
			for (Rune rune : combo.getValue())
			{
				if (shortfall.containsKey(rune))
				{
					final int covered = Math.min(use, shortfall.get(rune));
					shortfall.merge(rune, -covered, Integer::sum);
					result.have.merge(rune, covered, Integer::sum);
				}
			}
		}
		return result;
	}
	private static boolean inPouch(Holdings holdings, Rune rune)
	{
		return holdings.getRunePouch().getOrDefault(rune.getItemId(), 0) > 0;
	}

	private static int covers(Set<Rune> runes, Map<Rune, Integer> shortfall)
	{
		int total = 0;
		for (Rune rune : runes)
		{
			total += shortfall.getOrDefault(rune, 0);
		}
		return total;
	}

	private static void addRunes(Map<Rune, Integer> need, Spell spell)
	{
		for (RuneAmount amount : spell.getRunes())
		{
			need.merge(amount.getRune(), amount.getQuantity(), Integer::sum);
		}
	}

	private static SupplyLine line(SupplyLine.Group group, String name, int need, Holdings holdings, Holdings carried,
		String note, int slots, int... itemIds)
	{
		return new SupplyLine(group, name, need, holdings.countAny(itemIds), carried.countAny(itemIds),
			holdings.where(itemIds), note, slots, false, defaultChangeIn(group), itemIds.clone());
	}

	private static String defaultChangeIn(SupplyLine.Group group)
	{
		switch (group)
		{
			case TRAVEL:
				return TRAVEL;
			case SEEDS:
				return CROPS;
			case PAYMENTS:
				return PROTECTION;
			case OPTIONAL:
				return RUN_OPTIONS;
			default:
				return null;
		}
	}

	/**
	 * Buckets of each compost type, or one line for a filled bottomless compost bucket (GitHub #3). The bucket
	 * holds one type: it replaces the buckets of that type (or all of them while its type isn't known), and its
	 * uses are checked against the patches when known. Warnings go to {@code warnings}.
	 */
	static List<SupplyLine> compostLines(Map<Compost, Integer> compost, Holdings holdings, Holdings carried,
		List<String> warnings)
	{
		final List<SupplyLine> lines = new ArrayList<>();
		if (compost.isEmpty())
		{
			return lines;
		}
		final Map<Compost, Integer> buckets = new EnumMap<>(compost);
		if (holdings.count(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED) > 0)
		{
			final Compost holds = holdings.getBucketCompost();
			int usesNeeded = 0;
			final List<String> replaced = new ArrayList<>();
			for (Map.Entry<Compost, Integer> e : compost.entrySet())
			{
				if (holds == null || holds == e.getKey())
				{
					usesNeeded += e.getValue();
					replaced.add(e.getKey().getDisplayName().toLowerCase());
					buckets.remove(e.getKey());
				}
			}

			final int uses = holdings.getBucketUses();
			String note = "Used instead of buckets of " + String.join(" and ", replaced) + "; this run needs "
				+ usesNeeded + (usesNeeded == 1 ? " use" : " uses");
			if (uses < 0)
			{
				note += ". Uses left aren't known yet";
				warnings.add("Right-click your bottomless compost bucket and choose Check, so its uses are known");
			}
			else if (uses < usesNeeded)
			{
				warnings.add("Your bottomless compost bucket has " + uses + (uses == 1 ? " use" : " uses")
					+ " left; this run needs " + usesNeeded);
			}
			if (holds != null && replaced.isEmpty())
			{
				warnings.add("Your bottomless compost bucket holds " + holds.getDisplayName().toLowerCase()
					+ ", which this run doesn't use (Farm > Compost and cures)");
			}
			if (!replaced.isEmpty())
			{
				final String name = "Bottomless compost bucket" + (uses >= 0 ? " (" + uses + (uses == 1 ? " use)" : " uses)") : "");
				lines.add(changeIn(line(SupplyLine.Group.TOOLS, name, 1, holdings, carried, note, 1,
					ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED), COMPOST_TYPE));
			}
		}
		final boolean emptyBucket = holdings.count(ItemID.BOTTOMLESS_COMPOST_BUCKET) > 0;
		buckets.forEach((c, n) -> lines.add(changeIn(line(SupplyLine.Group.TOOLS, c.getDisplayName(), n, holdings,
			carried, emptyBucket ? "Fill your bottomless compost bucket with " + c.getDisplayName().toLowerCase()
				+ " to use it instead" : null, n, c.getItemId()).usedUp(), COMPOST_TYPE)));
		return lines;
	}

	private static SupplyLine changeIn(SupplyLine line, String where)
	{
		return new SupplyLine(line.getGroup(), line.getName(), line.getNeed(), line.getHave(), line.getCarried(),
			line.getWhere(), line.getNote(), line.getSlots(), line.isCoveredOtherwise(), where, line.getItemIds(),
			line.isConsumed(), line.isWear());
	}
	private static String title(String enumName)
	{
		final String lower = enumName.toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
