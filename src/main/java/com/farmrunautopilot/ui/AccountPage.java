package com.farmrunautopilot.ui;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.access.PohDetector;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.poh.PoolTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import java.awt.Rectangle;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.util.LinkBrowser;

/**
 * The Account tab: what you have that the plugin can't read by itself (Sean: Account is what you have): your
 * house, unlocks and where you store things. Display, debug and testing are on the {@link SettingsPage}.
 */
class AccountPage extends SetupPanel
{
	/** The Unlocks section's title, which is also how the Run tab opens it. */
	static final String UNLOCKS = "Unlocks";
	/** The Unlocks section as last drawn, to scroll to it. */
	private JComponent unlocksSectionShown;

	AccountPage(SettingsStore settings, AccessChecker accessChecker, Runnable changed)
	{
		super(settings, accessChecker, changed);
		rebuild();
	}

	@Override
	void addSections(RunConfig config, AccountSettings account, AccessSnapshot access)
	{
		add(pohSection(account));
		unlocksSectionShown = unlocksSection(account);
		add(unlocksSectionShown);
		add(storageSection(config));
	}

	/** Open the Unlocks section and scroll to it (Account page). Swing thread. */
	void revealUnlocks()
	{
		settings.setSectionOpen(UNLOCKS, true);
		rebuild();
		SwingUtilities.invokeLater(() ->
		{
			final JComponent section = unlocksSectionShown;
			if (section != null)
			{
				section.scrollRectToVisible(new Rectangle(0, 0, section.getWidth(), section.getHeight()));
			}
		});
	}

	private JComponent pohSection(AccountSettings account)
	{
		final PohSetup poh = account.getPoh();
		final CollapsibleSection s = section("My house");
		s.addContent(checkBox("Detect furniture when I enter my house (beta)", account.isAutoDetectHouse(), true,
			"Fills in the jewellery box, pool, altar, fairy ring and spirit tree below from your house. Beta: it skips "
				+ "other players' houses by how you got in, which can't be fully checked yet; if it gets your house wrong, "
				+ "please report it.",
			on -> saveAccount(() -> account.setAutoDetectHouse(on), false)));
		if (account.isAutoDetectHouse())
		{
			final JButton report = smallButton("Report a problem");
			report.setToolTipText("Opens the plugin's GitHub issues page");
			report.addActionListener(e -> LinkBrowser.browse(HelpWanted.ISSUES_URL));
			s.addContent(report);
			s.addContent(note(poh.getLastDetected() > 0
				? "Last scanned " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
				.format(new Date(poh.getLastDetected() * 1000))
				: "Enter your house to scan it"));
			final JButton rescan = smallButton("Rescan house");
			rescan.setToolTipText("<html>Detection only adds furniture. If you removed or downgraded something, "
				+ "rescan:<br>this clears the detected furniture and your next house visit fills it in again.<br>"
				+ "The portal location and nexus destinations are kept.</html>");
			rescan.addActionListener(e ->
			{
				PohDetector.clearDetected(poh);
				settings.saveAccount(true);
				accessChecker.requestRefresh();
			});
			s.addContent(rescan);
		}
		s.help("Set the portal location and nexus destinations by hand.");

		final List<Choice<HousePortal>> portals = new ArrayList<>();
		portals.add(Choice.of(null, "Unknown / no house"));
		for (HousePortal portal : HousePortal.values())
		{
			portals.add(Choice.of(portal, portal.getDisplayName() + " (" + portal.getConstructionLevel() + " Con)"));
		}
		s.addContent(label("House portal location"));
		s.addContent(combo(portals, poh.getPortal(), p -> saveAccount(() -> poh.setPortal(p), true)));
		s.addContent(checkBox("House set to teleport outside", poh.isTeleportOutside(), true,
			"Teleport to House lands outside the portal instead of inside",
			on -> saveAccount(() -> poh.setTeleportOutside(on), false)));

		final List<Choice<JewelleryBoxTier>> boxes = new ArrayList<>();
		boxes.add(Choice.of(null, "None"));
		for (JewelleryBoxTier tier : JewelleryBoxTier.values())
		{
			boxes.add(Choice.of(tier, title(tier.name()) + " (" + tier.getConstructionLevel() + " Con)"));
		}
		s.addContent(label("Jewellery box"));
		s.addContent(combo(boxes, poh.getJewelleryBox(), b -> saveAccount(() -> poh.setJewelleryBox(b), false)));

		final List<Choice<PoolTier>> pools = new ArrayList<>();
		pools.add(Choice.of(null, "None"));
		for (PoolTier tier : PoolTier.values())
		{
			pools.add(Choice.of(tier, title(tier.name()) + (tier.isRestoresRunEnergy() ? "" : " (no run energy)")));
		}
		s.addContent(label("Pool"));
		s.addContent(combo(pools, poh.getPool(), p -> saveAccount(() -> poh.setPool(p), false)));

		final List<Choice<PohAltar>> altars = new ArrayList<>();
		altars.add(Choice.of(null, "None"));
		for (PohAltar altar : PohAltar.values())
		{
			altars.add(Choice.of(altar, altar.getDisplayName()));
		}
		s.addContent(label("Spellbook altar"));
		s.addContent(combo(altars, poh.getAltar(), a -> saveAccount(() -> poh.setAltar(a), true)));
		s.addContent(checkBox("Fairy ring", poh.isFairyRing(), true, null,
			on -> saveAccount(() -> poh.setFairyRing(on), false)));
		s.addContent(checkBox("Spirit tree", poh.isSpiritTree(), true, null,
			on -> saveAccount(() -> poh.setSpiritTree(on), false)));

		s.addContent(subheader("Portal nexus destinations"));
		for (PortalNexus.Destination destination : PortalNexus.Destination.values())
		{
			s.addContent(checkBox(destination.getDisplayName(), poh.getNexusDestinations().contains(destination), true, null,
				on -> saveAccount(() ->
				{
					if (on)
					{
						poh.getNexusDestinations().add(destination);
					}
					else
					{
						poh.getNexusDestinations().remove(destination);
					}
				}, false)));
		}
		return s;
	}

	private JComponent unlocksSection(AccountSettings account)
	{
		final AccessSnapshot access = accessChecker.getSnapshot();
		final List<Unlock> review = AccessChecker.toReview(settings.editRunConfig(), account);
		final int ticked = account.getManualUnlocks().size();
		final CollapsibleSection s = section(UNLOCKS, ticked == 0 ? null : ticked + " ticked");
		s.help("Ticked for you where the game shows it, or once you pass a planted spirit tree or the Weiss "
			+ "fire. Tick the rest that you have; the plugin can't "
			+ "see them until you do.");
		for (Unlock unlock : Unlock.values())
		{
			if (AccessChecker.isDetected(unlock))
			{
				// Shown in Detected
				continue;
			}
			if (unlock.hasVarbit() && access.getUnlocks().contains(unlock) && !account.getManualUnlocks().contains(unlock))
			{
				s.addContent(checkBox(unlock.getDescription() + " (detected)", true, false,
					"Read from the game", on ->
					{
					}));
				continue;
			}
			final boolean flagged = review.contains(unlock);
			s.addContent(checkBox(unlock.getDescription() + (flagged ? " (check)" : ""),
				account.getManualUnlocks().contains(unlock), true,
				flagged ? "Your run has a patch or a way to one that needs this" : null,
				on -> saveAccount(() ->
				{
					if (on)
					{
						account.getManualUnlocks().add(unlock);
					}
					else
					{
						account.getManualUnlocks().remove(unlock);
					}
				}, true)));
		}
		if (!account.isUnlocksReviewed())
		{
			final JButton done = smallButton("These are right");
			done.setToolTipText("Stop asking on the Run tab; you can still change these any time");
			done.addActionListener(e -> saveAccount(() -> account.setUnlocksReviewed(true), true));
			s.addContent(done);
		}
		return s;
	}

	private JComponent storageSection(RunConfig config)
	{
		final CollapsibleSection s = section("Storage sources");
		s.help("Bank, inventory, equipment and the tool leprechaun are always counted.");
		s.addContent(checkBox("Group storage (GIM)", config.isUseGroupStorage(), true, null,
			on -> saveRun(() -> config.setUseGroupStorage(on))));
		s.addContent(checkBox("Seed vault", config.isUseSeedVault(), true, null,
			on -> saveRun(() -> config.setUseSeedVault(on))));
		return s;
	}

}
