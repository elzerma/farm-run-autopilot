package com.farmrunautopilot.access;

import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.poh.PoolTier;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.SettingsStore;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ObjectID;

/**
 * Fills in My POH from house furniture as it loads (SPEC 2 "auto-detect on visit"). Driven by object
 * spawn events, never by scanning the scene.
 *
 * <p>Detection only ever adds or upgrades, because objects in another player's house look the same
 * and a partly loaded house would otherwise wipe settings. The house portal location and nexus
 * destinations can't be read this way and stay manual.
 */
@Slf4j
@Singleton
public class PohDetector
{
	private final SettingsStore settings;
	private final AccessChecker accessChecker;

	@Inject
	PohDetector(SettingsStore settings, AccessChecker accessChecker)
	{
		this.settings = settings;
		this.accessChecker = accessChecker;
	}

	/** Call for every spawned game object. Cheap for objects that aren't POH furniture. */
	public void onObjectSpawned(int objectId)
	{
		final JewelleryBoxTier box = jewelleryBoxFor(objectId);
		final PoolTier pool = poolFor(objectId);
		final boolean fairyRing = objectId == ObjectID.POH_FAIRY_RING || objectId == ObjectID.POH_SPIRIT_RING;
		final boolean spiritTree = objectId == ObjectID.POH_SPIRIT_TREE || objectId == ObjectID.POH_SPIRIT_RING;
		final PohAltar altar = altarFor(objectId);
		if (box == null && pool == null && altar == null && !fairyRing && !spiritTree)
		{
			return;
		}

		// Settings are only changed on the Swing thread.
		SwingUtilities.invokeLater(() -> apply(box, pool, altar, fairyRing, spiritTree));
	}

	private void apply(JewelleryBoxTier box, PoolTier pool, PohAltar altar, boolean fairyRing, boolean spiritTree)
	{
		final PohSetup poh = settings.getAccount().getPoh();
		boolean changed = false;
		if (box != null && (poh.getJewelleryBox() == null || box.ordinal() > poh.getJewelleryBox().ordinal()))
		{
			poh.setJewelleryBox(box);
			changed = true;
		}
		if (pool != null && (poh.getPool() == null || pool.ordinal() > poh.getPool().ordinal()))
		{
			poh.setPool(pool);
			changed = true;
		}
		// The occult altar covers every spellbook, so it beats the others
		if (altar != null && poh.getAltar() != altar && poh.getAltar() != PohAltar.OCCULT)
		{
			poh.setAltar(altar);
			changed = true;
		}
		if (fairyRing && !poh.isFairyRing())
		{
			poh.setFairyRing(true);
			changed = true;
		}
		if (spiritTree && !poh.isSpiritTree())
		{
			poh.setSpiritTree(true);
			changed = true;
		}

		poh.setLastDetected(Instant.now().getEpochSecond());
		if (changed)
		{
			log.debug("Detected POH furniture: box={} pool={} altar={} fairyRing={} spiritTree={}", box, pool, altar, fairyRing,
				spiritTree);
		}
		// Always save so "last detected" updates; refresh the tab only when something new was found.
		settings.saveAccount(changed);
		if (changed)
		{
			accessChecker.requestRefresh();
		}
	}

	static JewelleryBoxTier jewelleryBoxFor(int objectId)
	{
		switch (objectId)
		{
			case ObjectID.POH_JEWELLERY_BOX_1:
				return JewelleryBoxTier.BASIC;
			case ObjectID.POH_JEWELLERY_BOX_2:
				return JewelleryBoxTier.FANCY;
			case ObjectID.POH_JEWELLERY_BOX_3:
				return JewelleryBoxTier.ORNATE;
			default:
				return null;
		}
	}

	static PohAltar altarFor(int objectId)
	{
		switch (objectId)
		{
			case ObjectID.POH_ALTAR_ANCIENT:
				return PohAltar.ANCIENT;
			case ObjectID.POH_ALTAR_LUNAR:
				return PohAltar.LUNAR;
			case ObjectID.POH_ALTAR_DARK:
				return PohAltar.DARK;
			case ObjectID.POH_ALTAR_OCCULT:
			case ObjectID.POH_ALTAR_OCCULT_STANDARD:
			case ObjectID.POH_ALTAR_OCCULT_ANCIENT:
			case ObjectID.POH_ALTAR_OCCULT_LUNAR:
			case ObjectID.POH_ALTAR_OCCULT_ARCEUUS:
				return PohAltar.OCCULT;
			default:
				return null;
		}
	}

	static PoolTier poolFor(int objectId)
	{
		switch (objectId)
		{
			case ObjectID.POH_POOL_RESTORATION:
				return PoolTier.RESTORATION;
			case ObjectID.POH_POOL_REVITALISATION:
				return PoolTier.REVITALISATION;
			case ObjectID.POH_POOL_REJUVENATION:
				return PoolTier.REJUVENATION;
			// UNVERIFIED: "recovery" and "regeneration" are assumed to be the fancy and ornate pools.
			case ObjectID.POH_POOL_RECOVERY:
				return PoolTier.FANCY_REJUVENATION;
			case ObjectID.POH_POOL_REGENERATION:
				return PoolTier.ORNATE_REJUVENATION;
			default:
				return null;
		}
	}
}
