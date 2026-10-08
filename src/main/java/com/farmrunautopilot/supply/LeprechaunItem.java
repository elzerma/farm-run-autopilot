package com.farmrunautopilot.supply;

import static com.farmrunautopilot.data.DataConstants.NONE;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Items the tool leprechaun stores, read from player varbits (sent everywhere, so no need to visit).
 *
 * <p>Large counts are split over a base varbit and an "extra" varbit. The total is assumed to be
 * {@code base + (extra << bitsInBase)}: UNVERIFIED, confirm with the varbit inspector.
 */
@Getter
@RequiredArgsConstructor
public enum LeprechaunItem
{
	RAKE(ItemID.RAKE, VarbitID.FARMING_TOOLS_RAKE, VarbitID.FARMING_TOOLS_EXTRARAKES),
	SEED_DIBBER(ItemID.DIBBER, VarbitID.FARMING_TOOLS_DIBBER, VarbitID.FARMING_TOOLS_EXTRADIBBERS),
	SPADE(ItemID.SPADE, VarbitID.FARMING_TOOLS_SPADE, VarbitID.FARMING_TOOLS_EXTRASPADES),
	SECATEURS(ItemID.SECATEURS, VarbitID.FARMING_TOOLS_SECATEURS, VarbitID.FARMING_TOOLS_EXTRASECATEURS),
	/** A flag: the secateurs slot holds magic secateurs. */
	MAGIC_SECATEURS(ItemID.FAIRY_ENCHANTED_SECATEURS, VarbitID.FARMING_TOOLS_FAIRYSECATEURS, NONE),
	COMPOST(ItemID.BUCKET_COMPOST, VarbitID.FARMING_TOOLS_COMPOST, VarbitID.FARMING_TOOLS_EXTRACOMPOST),
	SUPERCOMPOST(ItemID.BUCKET_SUPERCOMPOST, VarbitID.FARMING_TOOLS_SUPERCOMPOST,
		VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST),
	ULTRACOMPOST(ItemID.BUCKET_ULTRACOMPOST, VarbitID.FARMING_TOOLS_ULTRACOMPOST, NONE),
	PLANT_CURE(ItemID.PLANT_CURE, VarbitID.FARMING_TOOLS_PLANTCURE, NONE);

	private final int itemId;
	private final int baseVarbit;
	/** Overflow varbit, or {@link com.farmrunautopilot.data.DataConstants#NONE}. */
	private final int extraVarbit;

	/**
	 * @param bitsInBase width of the base varbit
	 */
	public static int combine(int base, int extra, int bitsInBase)
	{
		return base + (extra << bitsInBase);
	}
}
