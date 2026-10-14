package com.farmrunautopilot.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Karamja's easy to hard values are 2 when done (the game's area_task_complete script); 1 means started. */
public class AchievementDiaryTest
{
	@Test
	public void aStartedKaramjaTierIsNotDone()
	{
		assertFalse(AchievementDiary.KARAMJA.isComplete(AchievementDiary.Tier.EASY, 1));
		assertTrue(AchievementDiary.KARAMJA.isComplete(AchievementDiary.Tier.EASY, 2));
		assertFalse(AchievementDiary.KARAMJA.isComplete(AchievementDiary.Tier.HARD, 1));
	}

	@Test
	public void otherTiersAreDoneAtOne()
	{
		assertTrue(AchievementDiary.KARAMJA.isComplete(AchievementDiary.Tier.ELITE, 1));
		assertTrue(AchievementDiary.ARDOUGNE.isComplete(AchievementDiary.Tier.EASY, 1));
		assertFalse(AchievementDiary.ARDOUGNE.isComplete(AchievementDiary.Tier.EASY, 0));
	}
}
