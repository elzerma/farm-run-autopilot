package com.farmrunautopilot.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.Test;

public class RunConfigTest
{
	// The plugin uses RuneLite's injected Gson; a plain one behaves the same for these classes.
	private final Gson gson = new Gson();

	@Test
	public void presetCopiesMatchTheSettingsTheyWereSavedFrom()
	{
		// SettingsStore.activePreset() relies on a saved copy comparing equal to the live settings
		final RunConfig config = new RunConfig().sanitise();
		config.getEnabledTypes().remove(PatchType.TREE);
		config.getCrops().put(PatchType.HERB, Crop.TORSTOL);
		config.setOutfit(Outfit.GRACEFUL);
		final Preset preset = new Preset("Quick herbs", gson.fromJson(gson.toJson(config), RunConfig.class).sanitise());
		final Preset[] saved = gson.fromJson(gson.toJson(new Preset[]{preset}), Preset[].class);
		assertEquals(config, saved[0].getConfig().sanitise());

		config.setPlantCureDoses(config.getPlantCureDoses() + 1);
		assertFalse(config.equals(saved[0].getConfig()));
	}

	@Test
	public void defaults()
	{
		final RunConfig config = new RunConfig().sanitise();
		assertEquals(3, config.getEnabledTypes().size());
		assertTrue(config.isPayWithNotes());
		assertTrue(config.isUseGroupStorage());
		assertTrue(config.isUseSeedVault());
		assertNull(config.getStartLocation());
		assertEquals(Protection.PAY_GARDENER, config.protectionFor(Patch.TAVERLEY_TREE));
		assertEquals(Compost.ULTRACOMPOST, config.getCompost().get(PatchType.HERB));
	}

	@Test
	public void oldSavesGetStorageSwitchedOnOnce()
	{
		final RunConfig old = gson.fromJson("{\"useGroupStorage\":false,\"useSeedVault\":false}", RunConfig.class)
			.sanitise();
		assertTrue(old.isUseSeedVault());
		assertTrue(old.isUseGroupStorage());

		// Switched off again afterwards, it stays off
		old.setUseSeedVault(false);
		final RunConfig reloaded = gson.fromJson(gson.toJson(old), RunConfig.class).sanitise();
		assertFalse(reloaded.isUseSeedVault());
	}

	@Test
	public void roundTripThroughJson()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.getDisabledPatches().add(Patch.LUMBRIDGE_TREE);
		config.getCrops().put(PatchType.HERB, Crop.RANARR);
		config.getProtectionOverrides().put(Patch.FALADOR_TREE, Protection.COMPOST_ONLY);
		config.setUseProtectionOverrides(true);
		config.setUseRunesNotTabs(true);
		config.setRouteMode(RouteMode.META);

		final RunConfig loaded = gson.fromJson(gson.toJson(config), RunConfig.class).sanitise();
		assertEquals(config, loaded);
		assertFalse(loaded.isPatchSelected(Patch.LUMBRIDGE_TREE));
		assertEquals(Protection.COMPOST_ONLY, loaded.protectionFor(Patch.FALADOR_TREE));
	}

	@Test
	public void perPatchProtectionIsBehindItsSwitch()
	{
		// Saved before the switch existed, with an override: switched on, so nothing changes
		final RunConfig old = new RunConfig();
		old.getProtectionOverrides().put(Patch.FALADOR_TREE, Protection.COMPOST_ONLY);
		old.setUseProtectionOverrides(null);
		final RunConfig loaded = gson.fromJson(gson.toJson(old), RunConfig.class).sanitise();
		assertEquals(Protection.COMPOST_ONLY, loaded.protectionFor(Patch.FALADOR_TREE));

		// Switched off: the override is kept but ignored
		loaded.setUseProtectionOverrides(false);
		assertEquals(Protection.PAY_GARDENER, loaded.protectionFor(Patch.FALADOR_TREE));
		assertEquals(Protection.COMPOST_ONLY, loaded.getProtectionOverrides().get(Patch.FALADOR_TREE));
	}

	@Test
	public void sanitiseRepairsBadSaves()
	{
		// Unknown enum names, wrong crop for a type, a method for another location, nulls, settings that no longer
		// exist (energyThreshold, the Travel tab's per-location picks).
		final String json = "{\"enabledTypes\":[\"TREE\",\"NOT_A_TYPE\"],"
			+ "\"crops\":{\"HERB\":\"MAGIC\",\"TREE\":\"YEW\"},"
			+ "\"travel\":{\"CATHERBY\":\"FARMING_CAPE\",\"LLETYA\":\"TELEPORT_CRYSTAL_LLETYA\"},"
			+ "\"testTravel\":{\"CATHERBY\":\"FARMING_CAPE\",\"LLETYA\":\"TELEPORT_CRYSTAL_LLETYA\"},"
			+ "\"protection\":null,\"routeMode\":\"GONE\",\"energyThreshold\":500,\"customOrder\":null}";
		final RunConfig config = gson.fromJson(json, RunConfig.class).sanitise();

		assertEquals(1, config.getEnabledTypes().size());
		assertEquals(Crop.YEW, config.getCrops().get(PatchType.TREE));
		assertFalse(config.getCrops().containsKey(PatchType.HERB));
		assertFalse(config.getTestTravel().containsKey(Location.CATHERBY));
		assertEquals(TravelMethod.TELEPORT_CRYSTAL_LLETYA, config.getTestTravel().get(Location.LLETYA));
		assertEquals(Protection.PAY_GARDENER, config.getProtection().get(PatchType.TREE));
		assertEquals(RouteMode.AUTOPILOT, config.getRouteMode());
		assertNotNull(config.getCustomOrder());
	}

	@Test
	public void cropDefaultsToHighestPlantable()
	{
		final RunConfig config = new RunConfig();
		assertEquals(Crop.MAGIC, config.cropFor(PatchType.TREE, 99));
		assertEquals(Crop.MAPLE, config.cropFor(PatchType.TREE, 59));
		assertEquals(Crop.OAK, config.cropFor(PatchType.TREE, 1));
		assertEquals(Crop.HUASCA, config.cropFor(PatchType.HERB, 66));
		config.getCrops().put(PatchType.TREE, Crop.WILLOW);
		assertEquals(Crop.WILLOW, config.cropFor(PatchType.TREE, 99));
	}

	@Test
	public void backupCropChoices()
	{
		final String json = "{\"crops\":{\"HERB\":\"RANARR\"},\"useBackupCrops\":true,"
			+ "\"backupCrops\":{\"HERB\":[\"RANARR\",\"TORSTOL\",\"TOADFLAX\",\"MAGIC\",\"GUAM\"]}}";
		final RunConfig config = gson.fromJson(json, RunConfig.class).sanitise();
		// Duplicates of the same crop and other run types' crops are dropped; at most two backups are kept.
		assertEquals(java.util.Arrays.asList(Crop.RANARR, Crop.TORSTOL), config.getBackupCrops().get(PatchType.HERB));
		// Torstol needs 85 Farming, so at 50 it is skipped.
		assertEquals(java.util.Arrays.asList(Crop.RANARR), config.cropChoices(PatchType.HERB, 50));
		assertEquals(java.util.Arrays.asList(Crop.RANARR, Crop.TORSTOL), config.cropChoices(PatchType.HERB, 99));

		config.setUseBackupCrops(false);
		assertEquals(java.util.Arrays.asList(Crop.RANARR), config.cropChoices(PatchType.HERB, 99));
	}

	@Test
	public void runesInsteadOfTabletsIsOneChoice()
	{
		// Saved when it was chosen per stop: runes at any stop means runes at every stop
		final RunConfig perStop = new Gson().fromJson("{\"runesNotTabsAt\":[\"CATHERBY\"]}", RunConfig.class)
			.sanitise();
		assertTrue(perStop.isUseRunesNotTabs());
		assertNull(perStop.getRunesNotTabsAt());
		assertFalse(new Gson().fromJson("{\"runesNotTabsAt\":[]}", RunConfig.class).sanitise().isUseRunesNotTabs());
		assertFalse(new RunConfig().sanitise().isUseRunesNotTabs());
		// The released version moves the one choice into every stop and back: still on
		final RunConfig released = new Gson().fromJson("{\"useRunesNotTabs\":false,"
			+ "\"runesNotTabsAt\":[\"CATHERBY\",\"LUMBRIDGE\",\"NOT_A_PLACE\"]}", RunConfig.class).sanitise();
		assertTrue(released.isUseRunesNotTabs());
	}

	@Test
	public void accountSettingsRoundTrip()
	{
		final AccountSettings account = new AccountSettings();
		account.getManualUnlocks().add(Unlock.FIRE_OF_NOURISHMENT);
		account.getPoh().setPortal(HousePortal.TAVERLEY);
		account.getPoh().getNexusDestinations().add(PortalNexus.Destination.WEISS);

		final AccountSettings loaded = gson.fromJson(gson.toJson(account), AccountSettings.class).sanitise();
		assertEquals(account, loaded);

		final AccountSettings repaired = gson.fromJson("{\"poh\":null,\"manualUnlocks\":null}", AccountSettings.class)
			.sanitise();
		assertNotNull(repaired.getPoh());
		assertTrue(repaired.getManualUnlocks().isEmpty());
	}

	@Test
	public void runesEverywhereStaysOn()
	{
		final RunConfig config = new Gson().fromJson("{\"useRunesNotTabs\":true}", RunConfig.class).sanitise();
		assertTrue(config.isUseRunesNotTabs());
	}

	@Test
	public void oldTravelPicksAreDropped()
	{
		// Picked per stop on the Travel tab before 2026-10-20: the save still loads, and the picks aren't saved again
		final RunConfig config = gson.fromJson("{\"travel\":{\"CATHERBY\":\"CATHERBY_TELEPORT\"},"
			+ "\"travelHow\":{\"CATHERBY\":\"POH_NEXUS\"},\"fairyRingWay\":\"ARDOUGNE_CLOAK\",\"routeMode\":\"META\"}",
			RunConfig.class).sanitise();
		assertEquals(RouteMode.META, config.getRouteMode());
		assertTrue(config.getTestTravel().isEmpty());
		final String saved = gson.toJson(config);
		assertFalse(saved, saved.contains("\"travel\"") || saved.contains("travelHow") || saved.contains("fairyRingWay"));
	}

	@Test
	public void waysToTravelStartWithEverythingButCooldownTeleports()
	{
		// Saves from before the Travel tab's families (sprint T7) load with the defaults (elzerma, 2026-10-17)
		final RunConfig old = gson.fromJson("{\"routeMode\":\"META\"}", RunConfig.class).sanitise();
		for (TravelFamily family : TravelFamily.values())
		{
			assertEquals(family.name(), family != TravelFamily.COOLDOWN, old.isOn(family));
		}
		assertTrue(old.isAvoidWilderness());
		assertTrue(old.isSwitchSpellbooks());
		assertTrue(old.getWaysOff().isEmpty());
		assertEquals(EnumSet.of(TravelFamily.COOLDOWN), old.familiesOff());
	}

	@Test
	public void waysToTravelSurviveASave()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setOn(TravelFamily.FAIRY_RINGS, false);
		config.setOn(TravelFamily.COOLDOWN, true);
		config.getWaysOff().add("TELEPORTATION_ITEM|Ectophial");
		config.getWaysOff().add("SPIRIT_TREE|Gnome Stronghold");
		config.setAvoidWilderness(false);
		config.setSwitchSpellbooks(false);
		final RunConfig loaded = gson.fromJson(gson.toJson(config), RunConfig.class).sanitise();
		assertEquals(config, loaded);
		assertFalse(loaded.isSwitchSpellbooks());
		assertFalse(loaded.isOn(TravelFamily.FAIRY_RINGS));
		assertTrue(loaded.isOn(TravelFamily.COOLDOWN));
		assertEquals(EnumSet.of(TravelFamily.FAIRY_RINGS), loaded.familiesOff());
		assertTrue(loaded.getWaysOff().contains("SPIRIT_TREE|Gnome Stronghold"));
		assertFalse(loaded.isAvoidWilderness());
	}

	@Test
	public void brokenWaysToTravelAreRepaired()
	{
		final RunConfig config = gson.fromJson("{\"travelFamilies\":null,\"waysOff\":[\"\",null,"
			+ "\"FAIRY_RING|CKR\"]}", RunConfig.class).sanitise();
		assertEquals(EnumSet.of(TravelFamily.COOLDOWN), config.familiesOff());
		assertEquals(Collections.singleton("FAIRY_RING|CKR"), config.getWaysOff());
	}
}
