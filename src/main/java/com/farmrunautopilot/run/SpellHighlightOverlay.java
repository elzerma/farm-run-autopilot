package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.data.travel.Spell;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Outlines the spell to cast next in the spellbook (SPEC 13.4): the teleport while travelling, Teleport to
 * House for routes through the house, or Cure Plant on a diseased patch. Only drawn while the spellbook is
 * open and the spell is on it.
 */
public class SpellHighlightOverlay extends Overlay
{
	/** Each spell's button in the spellbook. */
	private static final Map<Spell, Integer> BUTTONS = new EnumMap<>(Spell.class);

	static
	{
		BUTTONS.put(Spell.LUMBRIDGE_HOME_TELEPORT, InterfaceID.MagicSpellbook.TELEPORT_HOME_STANDARD);
		BUTTONS.put(Spell.VARROCK_TELEPORT, InterfaceID.MagicSpellbook.VARROCK_TELEPORT);
		BUTTONS.put(Spell.LUMBRIDGE_TELEPORT, InterfaceID.MagicSpellbook.LUMBRIDGE_TELEPORT);
		BUTTONS.put(Spell.FALADOR_TELEPORT, InterfaceID.MagicSpellbook.FALADOR_TELEPORT);
		BUTTONS.put(Spell.TELEPORT_TO_HOUSE, InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE);
		BUTTONS.put(Spell.CAMELOT_TELEPORT, InterfaceID.MagicSpellbook.CAMELOT_TELEPORT);
		BUTTONS.put(Spell.ARDOUGNE_TELEPORT, InterfaceID.MagicSpellbook.ARDOUGNE_TELEPORT);
		BUTTONS.put(Spell.CIVITAS_ILLA_FORTIS_TELEPORT, InterfaceID.MagicSpellbook.FORTIS_TELEPORT);
		BUTTONS.put(Spell.TROLLHEIM_TELEPORT, InterfaceID.MagicSpellbook.TROLLHEIM_TELEPORT);
		BUTTONS.put(Spell.DRAYNOR_MANOR_TELEPORT, InterfaceID.MagicSpellbook.TELEPORT_DRAYNOR_MANOR);
		BUTTONS.put(Spell.BATTLEFRONT_TELEPORT, InterfaceID.MagicSpellbook.TELEPORT_BATTLEFRONT);
		BUTTONS.put(Spell.FENKENSTRAINS_CASTLE_TELEPORT, InterfaceID.MagicSpellbook.TELEPORT_FENKENSTRAIN_CASTLE);
		BUTTONS.put(Spell.HARMONY_ISLAND_TELEPORT, InterfaceID.MagicSpellbook.TELEPORT_HARMONY_ISLAND);
		BUTTONS.put(Spell.RESURRECT_CROPS, InterfaceID.MagicSpellbook.RESURRECT_CROPS);
		// UNVERIFIED: Kharyrll is assumed to be the third Ancient teleport button (Paddewwa, Senntisten, Kharyrll)
		BUTTONS.put(Spell.KHARYRLL_TELEPORT, InterfaceID.MagicSpellbook.ZAROSTELEPORT3);
		BUTTONS.put(Spell.CURE_PLANT, InterfaceID.MagicSpellbook.CURE_PLANT);
		BUTTONS.put(Spell.FERTILE_SOIL, InterfaceID.MagicSpellbook.FERTILE_SOIL);
		BUTTONS.put(Spell.FISHING_GUILD_TELEPORT, InterfaceID.MagicSpellbook.TELE_FISH);
		BUTTONS.put(Spell.CATHERBY_TELEPORT, InterfaceID.MagicSpellbook.TELE_CATHER);
	}

	private final Client client;
	private final RunSession session;
	private final FarmRunAutopilotConfig config;

	@Inject
	SpellHighlightOverlay(Client client, RunSession session, FarmRunAutopilotConfig config)
	{
		this.client = client;
		this.session = session;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/** The spellbook button for a spell, or null if there isn't one. */
	static Integer buttonFor(Spell spell)
	{
		return BUTTONS.get(spell);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Spell spell = session.getView().getHighlights().getSpell();
		if (spell == null || !config.highlightSpell())
		{
			return null;
		}
		final Integer button = BUTTONS.get(spell);
		final Widget widget = button != null ? client.getWidget(button) : null;
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		final Rectangle bounds = widget.getBounds();
		graphics.setColor(config.itemColour());
		graphics.setStroke(new BasicStroke(2));
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
		return null;
	}
}
