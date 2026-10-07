package com.farmrunautopilot.data.travel;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum Rune
{
	AIR(ItemID.AIRRUNE),
	WATER(ItemID.WATERRUNE),
	EARTH(ItemID.EARTHRUNE),
	FIRE(ItemID.FIRERUNE),
	LAW(ItemID.LAWRUNE),
	NATURE(ItemID.NATURERUNE),
	ASTRAL(ItemID.ASTRALRUNE),
	BLOOD(ItemID.BLOODRUNE),
	SOUL(ItemID.SOULRUNE);

	private final int itemId;
}
