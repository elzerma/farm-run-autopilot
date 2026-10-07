package com.farmrunautopilot.data;

import static org.junit.Assert.assertEquals;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class MetaOrderTest
{
	@Test
	public void eachOrderVisitsExactlyTheLocationsWithThatPatchType()
	{
		for (PatchType type : PatchType.values())
		{
			final List<Location> order = MetaOrder.forType(type);
			final Set<Location> unique = EnumSet.noneOf(Location.class);
			unique.addAll(order);
			assertEquals(type + " order has duplicates", order.size(), unique.size());

			final Set<Location> withType = EnumSet.noneOf(Location.class);
			for (Patch patch : Patch.values())
			{
				if (patch.getType() == type)
				{
					withType.add(patch.getLocation());
				}
			}
			assertEquals(type.name(), withType, unique);
		}
	}
}
