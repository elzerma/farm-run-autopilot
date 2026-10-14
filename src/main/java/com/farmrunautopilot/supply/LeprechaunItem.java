package com.farmrunautopilot.supply;

import static com.farmrunautopilot.data.DataConstants.NONE;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Items the tool leprechaun stores, read from player varbits (sent everywhere, so no need to visit).
 *
 * <p>Large counts are split over a base varbit and an "extra" varbit: the total is {@code base + extra * 2^shift},
 * with the shift the game's own farming_tools_getstored script uses (1 for tools, 8 for compost and supercompost).
 */
@Getter
@RequiredArgsConstructor
public enum LeprechaunItem
{
	RAKE(ItemID.RAKE, VarbitID.FARMING_TOOLS_RAKE, VarbitID.FARMING_TOOLS_EXTRARAKES, 1),
	SEED_DIBBER(ItemID.DIBBER, VarbitID.FARMING_TOOLS_DIBBER, VarbitID.FARMING_TOOLS_EXTRADIBBERS, 1),
	SPADE(ItemID.SPADE, VarbitID.FARMING_TOOLS_SPADE, VarbitID.FARMING_TOOLS_EXTRASPADES, 1),
	SECATEURS(ItemID.SECATEURS, VarbitID.FARMING_TOOLS_SECATEURS, VarbitID.FARMING_TOOLS_EXTRASECATEURS, 1),
	/** A flag: the secateurs slot holds magic secateurs. */
	MAGIC_SECATEURS(ItemID.FAIRY_ENCHANTED_SECATEURS, VarbitID.FARMING_TOOLS_FAIRYSECATEURS, NONE, 0),
	COMPOST(ItemID.BUCKET_COMPOST, VarbitID.FARMING_TOOLS_COMPOST, VarbitID.FARMING_TOOLS_EXTRACOMPOST, 8),
	SUPERCOMPOST(ItemID.BUCKET_SUPERCOMPOST, VarbitID.FARMING_TOOLS_SUPERCOMPOST,
		VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST, 8),
	ULTRACOMPOST(ItemID.BUCKET_ULTRACOMPOST, VarbitID.FARMING_TOOLS_ULTRACOMPOST, NONE, 0),
	PLANT_CURE(ItemID.PLANT_CURE, VarbitID.FARMING_TOOLS_PLANTCURE, NONE, 0);

	private final int itemId;
	private final int baseVarbit;
	/** Overflow varbit, or {@link com.farmrunautopilot.data.DataConstants#NONE}. */
	private final int extraVarbit;
	/** How far the extra varbit is shifted (the game's script multiplies it by 2^shift). */
	private final int extraShift;

	/** The total stored, as the game's script works it out. */
	public int total(int base, int extra)
	{
		return base + (extra << extraShift);
	}
}
