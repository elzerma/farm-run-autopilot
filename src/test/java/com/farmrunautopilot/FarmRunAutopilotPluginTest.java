package com.farmrunautopilot;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FarmRunAutopilotPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FarmRunAutopilotPlugin.class);
		RuneLite.main(args);
	}
}
