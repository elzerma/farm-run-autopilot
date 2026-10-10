package com.farmrunautopilot.access;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.RunConfig;
import java.util.List;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class UnlocksToReviewTest
{
	@Test
	public void flagsUnreadableUnlocksTheRunNeeds()
	{
		final List<Unlock> review = AccessChecker.toReview(new RunConfig().sanitise(), new AccountSettings().sanitise());
		assertTrue(review.contains(Unlock.FIRE_OF_NOURISHMENT));
		assertTrue(review.contains(Unlock.FORTIS_CHAMPION));
		// Read from the game, so never asked about
		assertFalse(review.contains(Unlock.QUETZAL_KASTORI));
		assertFalse(review.contains(Unlock.FAIRY_RINGS));
	}

	@Test
	public void skipsTickedAndSwitchedOffAndReviewed()
	{
		final RunConfig config = new RunConfig().sanitise();
		final AccountSettings account = new AccountSettings().sanitise();
		account.getManualUnlocks().add(Unlock.FORTIS_CHAMPION);
		config.getDisabledPatches().add(Patch.WEISS_HERB);
		List<Unlock> review = AccessChecker.toReview(config, account);
		assertFalse(review.contains(Unlock.FORTIS_CHAMPION));
		assertFalse(review.contains(Unlock.FIRE_OF_NOURISHMENT));

		config.getEnabledTypes().remove(PatchType.HERB);
		account.setUnlocksReviewed(true);
		assertTrue(AccessChecker.toReview(config, account).isEmpty());
	}
}
