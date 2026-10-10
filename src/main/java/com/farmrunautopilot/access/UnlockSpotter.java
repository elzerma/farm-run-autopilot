package com.farmrunautopilot.access;

import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.SettingsStore;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;

/**
 * Ticks unlocks the game has no value for once the player sees them: a grown spirit tree they can travel
 * from at a planted spot, or the built Fire of Nourishment at Weiss. Driven by object spawns. Client thread.
 *
 * <p>UNVERIFIED: the region IDs, the names and the "Travel" option are from the wiki and other patches, not
 * checked in game; spotted objects are logged at debug level for checking.
 */
@Slf4j
@Singleton
public class UnlockSpotter
{
	private static final class Spot
	{
		final Unlock unlock;
		final List<Integer> regions;
		final String name;
		/** An option the object has once unlocked, e.g. "Travel"; null if the name alone is enough. */
		final String option;

		Spot(Unlock unlock, String name, String option, Integer... regions)
		{
			this.unlock = unlock;
			this.name = name;
			this.option = option;
			this.regions = Arrays.asList(regions);
		}
	}

	private static final List<Spot> SPOTS = Arrays.asList(
		new Spot(Unlock.SPIRIT_TREE_PORT_SARIM, "Spirit tree", "Travel", 12082),
		new Spot(Unlock.SPIRIT_TREE_BRIMHAVEN, "Spirit tree", "Travel", 11058, 11057),
		new Spot(Unlock.SPIRIT_TREE_FARMING_GUILD, "Spirit tree", "Travel", 4922),
		// Unbuilt, the spot offers "Build" instead
		new Spot(Unlock.FIRE_OF_NOURISHMENT, "Fire of Nourishment", null, 11325));

	private final Client client;
	private final SettingsStore settings;
	private final AccessChecker accessChecker;

	@Inject
	UnlockSpotter(Client client, SettingsStore settings, AccessChecker accessChecker)
	{
		this.client = client;
		this.settings = settings;
		this.accessChecker = accessChecker;
	}

	public void onObjectSpawned(GameObject object)
	{
		final int region = object.getWorldLocation().getRegionID();
		final AccountSettings account = settings.getAccount();
		ObjectComposition composition = null;
		for (Spot spot : SPOTS)
		{
			if (!spot.regions.contains(region) || account.getManualUnlocks().contains(spot.unlock))
			{
				continue;
			}
			// Only look the object up in a region that matters
			if (composition == null)
			{
				composition = client.getObjectDefinition(object.getId());
				if (composition != null && composition.getImpostorIds() != null)
				{
					composition = composition.getImpostor();
				}
				if (composition == null)
				{
					return;
				}
			}
			if (spot.name.equals(composition.getName()) && hasOption(composition, spot.option)
				&& !hasOption(composition, "Build"))
			{
				log.debug("Spotted {} ({} at {})", spot.unlock, object.getId(), object.getWorldLocation());
				account.getManualUnlocks().add(spot.unlock);
				settings.saveAccount(true);
				accessChecker.requestRefresh();
			}
		}
	}

	private static boolean hasOption(ObjectComposition composition, String option)
	{
		if (option == null)
		{
			return true;
		}
		final String[] actions = composition.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				if (option.equalsIgnoreCase(action))
				{
					return true;
				}
			}
		}
		return false;
	}
}
