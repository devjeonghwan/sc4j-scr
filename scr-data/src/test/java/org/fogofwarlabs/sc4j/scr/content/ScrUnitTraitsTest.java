package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTraitFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the unit type sets of the unit trait column.
 */
final class ScrUnitTraitsTest {
    /**
     * The unit types that produce units and keep a rally point.
     */
    private static final int @NotNull [] RALLY_PRODUCER_TYPES = {106, 111, 113, 114, 130, 131, 132, 133, 154, 155, 160, 167};

    @Test
    void create_values_should_mark_exactly_the_construction_set_types_as_rally_producers() {
        int[] traits = ScrUnitTraits.createValues(ScrUnitTypeView.TraitColumn.TRAITS);

        for (int type = 0; type < ScrUnitTraits.TYPE_COUNT; type++) {
            boolean expected = false;

            for (int value : RALLY_PRODUCER_TYPES) {
                expected |= value == type;
            }

            Assertions.assertEquals(expected, (traits[type] & UnitTraitFlags.RALLY_PRODUCER) != 0, "type " + type);
        }
    }
}
