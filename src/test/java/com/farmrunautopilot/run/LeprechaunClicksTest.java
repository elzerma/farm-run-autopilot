package com.farmrunautopilot.run;

import com.farmrunautopilot.run.LeprechaunClicks.Next;
import com.farmrunautopilot.run.LeprechaunClicks.Quantity;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The guided clicks in the tool leprechaun's window (elzerma, 2026-11-01: "13 would change to 5, 2x click bucket remove,
 * change to 1, 3x click bucket remove. change back to 5").
 */
public class LeprechaunClicksTest
{
	private static final Map<Integer, Integer> NONE = Collections.emptyMap();

	private static Map<Integer, Integer> buckets(int count)
	{
		return Collections.singletonMap(ItemID.BUCKET_EMPTY, count);
	}

	@Test
	public void thirteenIsTwoFivesThenThreeOnesThenBackToFive()
	{
		// At 1: the 5 button first
		Next next = LeprechaunClicks.next(buckets(13), NONE, Quantity.ONE);
		assertEquals(Quantity.FIVE, next.target);
		assertTrue(next.switchFirst);
		// At 5: the buckets, twice
		next = LeprechaunClicks.next(buckets(13), NONE, Quantity.FIVE);
		assertFalse(next.switchFirst);
		assertEquals(buckets(2), next.take);
		// 3 left at 5: the 1 button
		next = LeprechaunClicks.next(buckets(3), NONE, Quantity.FIVE);
		assertEquals(Quantity.ONE, next.target);
		assertTrue(next.switchFirst);
		// At 1: the buckets, three times
		next = LeprechaunClicks.next(buckets(3), NONE, Quantity.ONE);
		assertFalse(next.switchFirst);
		assertEquals(buckets(3), next.take);
		// Done at 1: back to 5; done at 5: nothing
		next = LeprechaunClicks.next(NONE, NONE, Quantity.ONE);
		assertEquals(Quantity.FIVE, next.target);
		assertTrue(next.switchFirst);
		next = LeprechaunClicks.next(buckets(0), NONE, Quantity.FIVE);
		assertFalse(next.switchFirst);
		assertTrue(next.take.isEmpty());
	}

	@Test
	public void fivesForEveryItemBeforeAnyOnes()
	{
		final Map<Integer, Integer> store = Collections.singletonMap(ItemID.BUCKET_ULTRACOMPOST, 13);
		final Next next = LeprechaunClicks.next(buckets(2), store, Quantity.FIVE);
		// The buckets' 2 wait for the ones, after the ultracompost's fives
		assertEquals(Quantity.FIVE, next.target);
		assertTrue(next.take.isEmpty());
		assertEquals(Collections.singletonMap(ItemID.BUCKET_ULTRACOMPOST, 2), next.store);
		final Map<Integer, Integer> both = new HashMap<>();
		both.put(ItemID.BUCKET_EMPTY, 2);
		assertEquals(both, LeprechaunClicks.next(buckets(2), Collections.singletonMap(ItemID.BUCKET_ULTRACOMPOST, 3),
			Quantity.ONE).take);
	}

	@Test
	public void theCountDropsOnTheClick()
	{
		final java.util.List<LeprechaunClicks.Click> pending = new java.util.ArrayList<>();
		final LeprechaunClicks.Side take = LeprechaunClicks.Side.TAKE;
		final int bucket = ItemID.BUCKET_EMPTY;
		// Two quick clicks of 5 at 13, before the game takes any: 8, then 3, at once
		pending.add(new LeprechaunClicks.Click(take, bucket, 5, 13, 100));
		assertEquals(8, LeprechaunClicks.left(pending, take, bucket, 13, 100));
		pending.add(new LeprechaunClicks.Click(take, bucket, 5, 8, 100));
		assertEquals(3, LeprechaunClicks.left(pending, take, bucket, 13, 100));
		// The game takes the first: still 3, the second still pending
		assertEquals(3, LeprechaunClicks.left(pending, take, bucket, 8, 101));
		assertEquals(1, pending.size());
		// And the second
		assertEquals(3, LeprechaunClicks.left(pending, take, bucket, 3, 102));
		assertTrue(pending.isEmpty());
		// A click the game never takes is dropped after a few ticks
		pending.add(new LeprechaunClicks.Click(take, bucket, 1, 3, 200));
		assertEquals(2, LeprechaunClicks.left(pending, take, bucket, 3, 201));
		assertEquals(3, LeprechaunClicks.left(pending, take, bucket, 3, 200 + LeprechaunClicks.PENDING_TICKS + 1));
		// Other items' clicks don't count
		pending.add(new LeprechaunClicks.Click(LeprechaunClicks.Side.STORE, ItemID.BUCKET_ULTRACOMPOST, 5, 13, 300));
		assertEquals(3, LeprechaunClicks.left(pending, take, bucket, 3, 300));
	}

	@Test
	public void theQuantityAsTheGameKeepsIt()
	{
		assertEquals(Quantity.ONE, Quantity.fromVarbit(0));
		assertEquals(Quantity.FIVE, Quantity.fromVarbit(1));
		assertEquals(Quantity.ALL, Quantity.fromVarbit(2));
		assertEquals(Quantity.X, Quantity.fromVarbit(3));
		// All or X: the button for fives or ones first
		assertTrue(LeprechaunClicks.next(buckets(13), NONE, Quantity.ALL).switchFirst);
	}

	@Test
	public void theOptionsAndLabels()
	{
		assertEquals(5, LeprechaunClicks.moves("Remove-5"));
		assertEquals(1, LeprechaunClicks.moves("<col=ff9040>Store-1</col>"));
		assertEquals(0, LeprechaunClicks.moves("Remove-X"));
		assertEquals(0, LeprechaunClicks.moves("Remove-All"));
		assertEquals("Click 3x", LeprechaunClicks.clickLabel(3));
		assertEquals("Click", LeprechaunClicks.clickLabel(1));
		assertTrue(LeprechaunMenuHighlighter.forTarget("Remove-5", Quantity.FIVE));
		assertTrue(LeprechaunMenuHighlighter.forTarget("Store-1", Quantity.ONE));
		assertFalse(LeprechaunMenuHighlighter.forTarget("Remove-X", Quantity.FIVE));
	}
}
