package com.farmrunautopilot.run;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The amount option coloured in the tool leprechaun's window (elzerma, 2026-11-01). */
public class LeprechaunMenuHighlighterTest
{
	@Test
	public void theOptionForTheAmount()
	{
		// 13 of 20 buckets: "-X"; all of them: "-All"; one, five
		assertTrue(LeprechaunMenuHighlighter.forAmount("Remove-X", 13, 20));
		assertFalse(LeprechaunMenuHighlighter.forAmount("Remove-All", 13, 20));
		assertTrue(LeprechaunMenuHighlighter.forAmount("Store-All", 13, 13));
		assertTrue(LeprechaunMenuHighlighter.forAmount("Remove-1", 1, 20));
		assertTrue(LeprechaunMenuHighlighter.forAmount("Remove-5", 5, 20));
		assertFalse(LeprechaunMenuHighlighter.forAmount("Remove-5", 13, 20));
		// The words before the amount aren't checked, nor its case
		assertTrue(LeprechaunMenuHighlighter.forAmount("Withdraw-x", 13, 20));
		assertFalse(LeprechaunMenuHighlighter.forAmount("Examine", 13, 20));
	}
}
