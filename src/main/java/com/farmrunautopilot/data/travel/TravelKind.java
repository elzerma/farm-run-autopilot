package com.farmrunautopilot.data.travel;

public enum TravelKind
{
	/** A teleport spell; its tablet can be used instead. */
	SPELL,
	/** A redirected house tablet. */
	TABLET,
	JEWELLERY,
	CAPE,
	DIARY_ITEM,
	/** Any other teleport item (talisman, basalt, crystal, ...). */
	ITEM,
	/** Needs access to a fairy ring (POH ring, or one nearby). */
	FAIRY_RING,
	/** Needs access to a spirit tree (POH tree, Farming Guild tree, or one nearby). */
	SPIRIT_TREE,
	/** Teleport to House with the house portal at the destination. */
	HOUSE_PORTAL,
	QUETZAL
}
