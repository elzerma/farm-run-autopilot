package com.farmrunautopilot.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.travel.TravelItem;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.Collections;
import org.junit.Test;

public class NewerSavesTest
{
	/** Run settings as a newer version saves them: two patch types this one hasn't heard of. */
	private static final String NEWER_RUN = "{\"payWithNotes\":false,\"protection\":{\"TREE\":\"COMPOST_ONLY\","
		+ "\"NEWER_TYPE\":\"PAY_GARDENER\",\"ANOTHER_NEWER_TYPE\":\"PAY_GARDENER\"}}";

	// The plugin derives it from RuneLite's injected Gson; a plain one behaves the same for these classes.
	private final Gson gson = NewerSaves.tolerant(new Gson());

	@Test(expected = JsonSyntaxException.class)
	public void plainGsonLosesEverySettingOverTwoUnknownKeys()
	{
		// "duplicate key: null": the released plugin reading a dev build's settings (elzerma, 2026-10-19)
		new Gson().fromJson(NEWER_RUN, RunConfig.class);
	}

	@Test
	public void runSettingsFromANewerVersionKeepWhatThisOneKnows()
	{
		final RunConfig config = gson.fromJson(NEWER_RUN, RunConfig.class).sanitise();
		assertFalse(config.isPayWithNotes());
		assertEquals(Protection.COMPOST_ONLY, config.getProtection().get(PatchType.TREE));
	}

	@Test
	public void accountSettingsFromANewerVersionKeepWhatThisOneKnows()
	{
		final AccountSettings account = gson.fromJson("{\"itemCharges\":{\"NEWER_ITEM\":1,\"SKILLS_NECKLACE\":4,"
			+ "\"ANOTHER_NEWER_ITEM\":2}}", AccountSettings.class).sanitise();
		assertEquals(Collections.singletonMap(TravelItem.SKILLS_NECKLACE, 4), account.getItemCharges());
	}

	@Test
	public void settingsThisVersionSavedReadAsBefore()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.getCrops().put(PatchType.HERB, Crop.TORSTOL);
		config.getProtection().put(PatchType.FRUIT_TREE, Protection.COMPOST_ONLY);
		assertEquals(config, gson.fromJson(gson.toJson(config), RunConfig.class).sanitise());
	}
}
