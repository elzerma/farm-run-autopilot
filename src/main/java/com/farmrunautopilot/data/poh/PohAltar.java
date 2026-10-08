package com.farmrunautopilot.data.poh;

import com.farmrunautopilot.data.travel.Spellbook;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;

/**
 * POH altars that switch spellbook, so a run can use spells from another book (SPEC 8.1).
 */
@Getter
public enum PohAltar
{
	ANCIENT("Ancient altar", EnumSet.of(Spellbook.STANDARD, Spellbook.ANCIENT)),
	LUNAR("Lunar altar", EnumSet.of(Spellbook.STANDARD, Spellbook.LUNAR)),
	DARK("Dark altar (Arceuus)", EnumSet.of(Spellbook.STANDARD, Spellbook.ARCEUUS)),
	OCCULT("Occult altar (all)", EnumSet.allOf(Spellbook.class));

	private final String displayName;
	/** Spellbooks the altar can switch to (and back to standard). */
	private final Set<Spellbook> spellbooks;

	PohAltar(String displayName, Set<Spellbook> spellbooks)
	{
		this.displayName = displayName;
		this.spellbooks = Collections.unmodifiableSet(spellbooks);
	}
}