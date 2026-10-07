package com.farmrunautopilot.data;

import static com.farmrunautopilot.data.PatchState.CHECK_HEALTH;
import static com.farmrunautopilot.data.PatchState.DEAD;
import static com.farmrunautopilot.data.PatchState.DISEASED;
import static com.farmrunautopilot.data.PatchState.EMPTY;
import static com.farmrunautopilot.data.PatchState.GROWING;
import static com.farmrunautopilot.data.PatchState.HARVESTABLE;
import static com.farmrunautopilot.data.PatchState.STUMP;
import static com.farmrunautopilot.data.PatchState.UNKNOWN;
import static com.farmrunautopilot.data.PatchState.WEEDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Checks every value 0-255 for each patch type against an independent copy of RuneLite core's
 * PatchImplementation ranges, written out as literal numbers (one row per RuneLite range).
 */
public class PatchStateDecoderTest
{
	private final Map<Integer, DecodedPatch> expected = new HashMap<>();

	@Test
	public void herb()
	{
		weeds();
		row(4, 7, Crop.GUAM, GROWING, 0, 3);
		row(8, 10, Crop.GUAM, HARVESTABLE, 2, 0);
		row(11, 14, Crop.MARRENTILL, GROWING, 0, 3);
		row(15, 17, Crop.MARRENTILL, HARVESTABLE, 2, 0);
		row(18, 21, Crop.TARROMIN, GROWING, 0, 3);
		row(22, 24, Crop.TARROMIN, HARVESTABLE, 2, 0);
		row(25, 28, Crop.HARRALANDER, GROWING, 0, 3);
		row(29, 31, Crop.HARRALANDER, HARVESTABLE, 2, 0);
		row(32, 35, Crop.RANARR, GROWING, 0, 3);
		row(36, 38, Crop.RANARR, HARVESTABLE, 2, 0);
		row(39, 42, Crop.TOADFLAX, GROWING, 0, 3);
		row(43, 45, Crop.TOADFLAX, HARVESTABLE, 2, 0);
		row(46, 49, Crop.IRIT, GROWING, 0, 3);
		row(50, 52, Crop.IRIT, HARVESTABLE, 2, 0);
		row(53, 56, Crop.AVANTOE, GROWING, 0, 3);
		row(57, 59, Crop.AVANTOE, HARVESTABLE, 2, 0);
		row(60, 63, Crop.HUASCA, GROWING, 0, 3);
		row(64, 66, Crop.HUASCA, HARVESTABLE, 2, 0);
		row(67, 67, null, WEEDS, 3, 3);
		row(68, 71, Crop.KWUARM, GROWING, 0, 3);
		row(72, 74, Crop.KWUARM, HARVESTABLE, 2, 0);
		row(75, 78, Crop.SNAPDRAGON, GROWING, 0, 3);
		row(79, 81, Crop.SNAPDRAGON, HARVESTABLE, 2, 0);
		row(82, 85, Crop.CADANTINE, GROWING, 0, 3);
		row(86, 88, Crop.CADANTINE, HARVESTABLE, 2, 0);
		row(89, 92, Crop.LANTADYME, GROWING, 0, 3);
		row(93, 95, Crop.LANTADYME, HARVESTABLE, 2, 0);
		row(96, 99, Crop.DWARF_WEED, GROWING, 0, 3);
		row(100, 102, Crop.DWARF_WEED, HARVESTABLE, 2, 0);
		row(103, 106, Crop.TORSTOL, GROWING, 0, 3);
		row(107, 109, Crop.TORSTOL, HARVESTABLE, 2, 0);
		row(128, 130, Crop.GUAM, DISEASED, 1, 3);
		row(131, 133, Crop.MARRENTILL, DISEASED, 1, 3);
		row(134, 136, Crop.TARROMIN, DISEASED, 1, 3);
		row(137, 139, Crop.HARRALANDER, DISEASED, 1, 3);
		row(140, 142, Crop.RANARR, DISEASED, 1, 3);
		row(143, 145, Crop.TOADFLAX, DISEASED, 1, 3);
		row(146, 148, Crop.IRIT, DISEASED, 1, 3);
		row(149, 151, Crop.AVANTOE, DISEASED, 1, 3);
		row(152, 154, Crop.KWUARM, DISEASED, 1, 3);
		row(155, 157, Crop.SNAPDRAGON, DISEASED, 1, 3);
		row(158, 160, Crop.CADANTINE, DISEASED, 1, 3);
		row(161, 163, Crop.LANTADYME, DISEASED, 1, 3);
		row(164, 166, Crop.DWARF_WEED, DISEASED, 1, 3);
		row(167, 169, Crop.TORSTOL, DISEASED, 1, 3);
		row(170, 172, null, DEAD, 1, 3);
		row(173, 175, Crop.HUASCA, DISEASED, 1, 3);
		row(176, 191, null, WEEDS, 3, 3);
		// Goutweed
		row(192, 195, null, GROWING, 0, 3);
		row(196, 197, null, HARVESTABLE, 1, 0);
		row(198, 200, null, DISEASED, 1, 3);
		row(201, 203, null, DEAD, 1, 3);
		row(204, 219, null, WEEDS, 3, 3);
		row(221, 255, null, WEEDS, 3, 3);
		// 110-127 and 220 are not recognised.

		checkAll(PatchType.HERB);
	}

	@Test
	public void fruitTree()
	{
		weeds();
		row(4, 7, null, WEEDS, 3, 3);
		fruit(Crop.APPLE, 8, 13, 14, 20, 21, 26, 27, 32, 33, 34);
		fruit(Crop.BANANA, 35, 40, 41, 47, 48, 53, 54, 59, 60, 61);
		row(62, 71, null, WEEDS, 3, 3);
		fruit(Crop.ORANGE, 72, 77, 78, 84, 85, 90, 91, 96, 97, 98);
		fruit(Crop.CURRY, 99, 104, 105, 111, 112, 117, 118, 123, 124, 125);
		row(126, 135, null, WEEDS, 3, 3);
		fruit(Crop.PINEAPPLE, 136, 141, 142, 148, 149, 154, 155, 160, 161, 162);
		fruit(Crop.PAPAYA, 163, 168, 169, 175, 176, 181, 182, 187, 188, 189);
		row(190, 199, null, WEEDS, 3, 3);
		fruit(Crop.PALM, 200, 205, 206, 212, 213, 218, 219, 224, 225, 226);
		fruit(Crop.DRAGONFRUIT, 227, 232, 233, 239, 240, 245, 246, 251, 252, 253);
		row(254, 255, null, WEEDS, 3, 3);

		checkAll(PatchType.FRUIT_TREE);
	}

	@Test
	public void tree()
	{
		weeds();
		row(4, 7, null, WEEDS, 3, 3);
		row(8, 11, Crop.OAK, GROWING, 0, 3);
		row(12, 12, Crop.OAK, CHECK_HEALTH, 4, 4);
		row(13, 13, Crop.OAK, HARVESTABLE, 0, 0);
		row(14, 14, Crop.OAK, STUMP, 0, 0);
		row(15, 20, Crop.WILLOW, GROWING, 0, 5);
		row(21, 21, Crop.WILLOW, CHECK_HEALTH, 6, 6);
		row(22, 22, Crop.WILLOW, HARVESTABLE, 0, 0);
		row(23, 23, Crop.WILLOW, STUMP, 0, 0);
		row(24, 31, Crop.MAPLE, GROWING, 0, 7);
		row(32, 32, Crop.MAPLE, CHECK_HEALTH, 8, 8);
		row(33, 33, Crop.MAPLE, HARVESTABLE, 0, 0);
		row(34, 34, Crop.MAPLE, STUMP, 0, 0);
		row(35, 44, Crop.YEW, GROWING, 0, 9);
		row(45, 45, Crop.YEW, CHECK_HEALTH, 10, 10);
		row(46, 46, Crop.YEW, HARVESTABLE, 0, 0);
		row(47, 47, Crop.YEW, STUMP, 0, 0);
		row(48, 59, Crop.MAGIC, GROWING, 0, 11);
		row(60, 60, Crop.MAGIC, CHECK_HEALTH, 12, 12);
		row(61, 61, Crop.MAGIC, HARVESTABLE, 0, 0);
		row(62, 62, Crop.MAGIC, STUMP, 0, 0);
		row(63, 72, null, WEEDS, 3, 3);
		row(73, 75, Crop.OAK, DISEASED, 1, 3);
		row(77, 77, Crop.OAK, DISEASED, 4, 4);
		row(78, 79, null, WEEDS, 3, 3);
		row(80, 84, Crop.WILLOW, DISEASED, 1, 5);
		row(86, 86, Crop.WILLOW, DISEASED, 6, 6);
		row(87, 88, null, WEEDS, 3, 3);
		row(89, 95, Crop.MAPLE, DISEASED, 1, 7);
		row(97, 97, Crop.MAPLE, DISEASED, 8, 8);
		row(98, 99, null, WEEDS, 3, 3);
		row(100, 108, Crop.YEW, DISEASED, 1, 9);
		row(110, 110, Crop.YEW, DISEASED, 10, 10);
		row(111, 112, null, WEEDS, 3, 3);
		row(113, 123, Crop.MAGIC, DISEASED, 1, 11);
		row(125, 125, Crop.MAGIC, DISEASED, 12, 12);
		row(126, 136, null, WEEDS, 3, 3);
		row(137, 139, Crop.OAK, DEAD, 1, 3);
		row(141, 141, Crop.OAK, DEAD, 4, 4);
		row(142, 143, null, WEEDS, 3, 3);
		row(144, 148, Crop.WILLOW, DEAD, 1, 5);
		row(150, 150, Crop.WILLOW, DEAD, 6, 6);
		row(151, 152, null, WEEDS, 3, 3);
		row(153, 159, Crop.MAPLE, DEAD, 1, 7);
		row(161, 161, Crop.MAPLE, DEAD, 8, 8);
		row(162, 163, null, WEEDS, 3, 3);
		row(164, 172, Crop.YEW, DEAD, 1, 9);
		row(174, 174, Crop.YEW, DEAD, 10, 10);
		row(175, 176, null, WEEDS, 3, 3);
		row(177, 187, Crop.MAGIC, DEAD, 1, 11);
		row(189, 189, Crop.MAGIC, DEAD, 12, 12);
		row(190, 191, null, WEEDS, 3, 3);
		row(192, 197, Crop.WILLOW, HARVESTABLE, 0, 0);
		row(198, 255, null, WEEDS, 3, 3);
		// 76, 85, 96, 109, 124, 140, 149, 160, 173 and 188 are not recognised.

		checkAll(PatchType.TREE);
	}

	@Test
	public void outOfRangeValuesAreUnknown()
	{
		for (PatchType type : PatchType.values())
		{
			assertEquals(UNKNOWN, PatchStateDecoder.decode(type, -1).getState());
			assertEquals(UNKNOWN, PatchStateDecoder.decode(type, 256).getState());
		}
	}

	@Test
	public void unrecognisedValueHasNoCrop()
	{
		final DecodedPatch decoded = PatchStateDecoder.decode(PatchType.HERB, 220);
		assertEquals(UNKNOWN, decoded.getState());
		assertNull(decoded.getCrop());
	}

	/** Values 0-3: fully overgrown (0) down to raked clear (3). */
	private void weeds()
	{
		row(0, 0, null, WEEDS, 3, 3);
		row(1, 1, null, WEEDS, 2, 2);
		row(2, 2, null, WEEDS, 1, 1);
		row(3, 3, null, EMPTY, 0, 0);
	}

	private void fruit(Crop crop, int growLo, int growHi, int harvestLo, int harvestHi, int diseasedLo, int diseasedHi,
		int deadLo, int deadHi, int stump, int checkHealth)
	{
		row(growLo, growHi, crop, GROWING, 0, 5);
		row(harvestLo, harvestHi, crop, HARVESTABLE, 0, 6);
		row(diseasedLo, diseasedHi, crop, DISEASED, 1, 6);
		row(deadLo, deadHi, crop, DEAD, 1, 6);
		row(stump, stump, crop, STUMP, 0, 0);
		row(checkHealth, checkHealth, crop, CHECK_HEALTH, 6, 6);
	}

	/** Stage moves by one per value from stageAtLo to stageAtHi. */
	private void row(int lo, int hi, Crop crop, PatchState state, int stageAtLo, int stageAtHi)
	{
		final int step = Integer.signum(stageAtHi - stageAtLo);
		assertEquals("bad test row " + lo + "-" + hi, Math.abs(stageAtHi - stageAtLo), step == 0 ? 0 : hi - lo);
		for (int v = lo; v <= hi; v++)
		{
			final DecodedPatch previous = expected.put(v, new DecodedPatch(crop, state, stageAtLo + step * (v - lo)));
			assertNull("test rows overlap at " + v, previous);
		}
	}

	private void checkAll(PatchType type)
	{
		for (int v = 0; v <= 255; v++)
		{
			final DecodedPatch want = expected.getOrDefault(v, new DecodedPatch(null, UNKNOWN, 0));
			assertEquals(type + " value " + v, want, PatchStateDecoder.decode(type, v));
		}
	}
}
