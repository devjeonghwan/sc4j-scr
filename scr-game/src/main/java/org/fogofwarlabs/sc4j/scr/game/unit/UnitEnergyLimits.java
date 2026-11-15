package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.jetbrains.annotations.NotNull;

/**
 * Reads the energy limit of a unit: what the status panel and the energy bars show. The unit initialization is {@link UnitInitializer}'s.
 */
public interface UnitEnergyLimits {
    /**
     * Returns the maximum energy of a unit, in {@code 1/256} units.
     *
     * @param unit  the unit
     * @param units the unit type table
     * @return {@code 0xFA00} for heroes and upgraded casters, otherwise {@code 0xC800}
     * @throws IllegalStateException if the unit type has an energy upgrade and the player upgrade tables are not loaded
     */
    int getMaximumEnergy(@NotNull Unit unit, @NotNull ScrUnitTypeView units);
}
