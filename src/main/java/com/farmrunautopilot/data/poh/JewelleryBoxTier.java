package com.farmrunautopilot.data.poh;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * POH jewellery box tiers. Each tier includes everything from the tiers below it, with unlimited uses
 * (SPEC 8.3).
 */
@Getter
@RequiredArgsConstructor
public enum JewelleryBoxTier
{
	/** Ring of dueling, games necklace. */
	BASIC(81),
	/** Adds combat bracelet and skills necklace. */
	FANCY(86),
	/** Adds amulet of glory and ring of wealth. */
	ORNATE(91);

	private final int constructionLevel;

	public boolean includes(JewelleryBoxTier needed)
	{
		return ordinal() >= needed.ordinal();
	}
}
