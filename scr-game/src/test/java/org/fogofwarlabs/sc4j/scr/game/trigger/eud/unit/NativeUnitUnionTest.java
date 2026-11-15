package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitTraits;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.ArrayDatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Checks resource and powerup union members of types beyond units.dat while keeping workers bounded.
 */
final class NativeUnitUnionTest {
    private static final int RESOURCE_AMOUNT_OFFSET = 0xD0;
    private static final int LINK_OFFSET = 0xD4;
    private static final int RESOURCE_AREA_OFFSET = 0xD8;
    private static final int RESOURCE_AMOUNT = 1234;
    private static final int RESOURCE_ANIMATION = 5;
    private static final int RESOURCE_BUSY_BYTE = 7;

    @ParameterizedTest
    @CsvSource({"POWERUP, POWERUP_ORIGIN, POWERUP_CARRIER", "RESOURCE_CONTAINER, RESOURCE_AMOUNT, RESOURCE_FIRST_GATHERER"})
    void get_member_should_serve_resource_and_powerup_types_past_units_dat(@NotNull String flagName,
                                                                           @NotNull NativeUnitUnion.Member scalarMember,
                                                                           @NotNull NativeUnitUnion.Member pointerMember) {
        int flag = flagName.equals("POWERUP") ? UnitTypeFlags.POWERUP : UnitTypeFlags.RESOURCE_CONTAINER;
        int[] flags = new int[UnitsDat.UNIT_COUNT];
        ScrUnitTypeView unitTypes = new ScrUnitTypeView(column -> new ArrayDatColumn(flags), ScrUnitTraits.createColumns(), UnitsDat.REQUIREMENT_INDEX,
                                                        (column, index) -> flag | UnitTypeFlags.WORKER | UnitTypeFlags.BUILDING);
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setUnitId(ScrUnitIds.NONE);

        Assertions.assertNull(NativeUnitUnion.getMember(unitTypes, unit, NativeUnitUnion.FIRST_OFFSET));
        Assertions.assertSame(scalarMember, NativeUnitUnion.getMember(unitTypes, unit, RESOURCE_AMOUNT_OFFSET));
        Assertions.assertSame(pointerMember, NativeUnitUnion.getMember(unitTypes, unit, LINK_OFFSET));

        if (flag == UnitTypeFlags.RESOURCE_CONTAINER) {
            Assertions.assertSame(NativeUnitUnion.Member.RESOURCE_AREA, NativeUnitUnion.getMember(unitTypes, unit, RESOURCE_AREA_OFFSET));

            int value = RESOURCE_AMOUNT | RESOURCE_ANIMATION << Short.SIZE | RESOURCE_BUSY_BYTE << (Short.SIZE + Byte.SIZE);

            NativeUnitUnion.writeValue(unit, scalarMember, value);

            Assertions.assertEquals(RESOURCE_AMOUNT, unit.getResources());
            Assertions.assertEquals(RESOURCE_ANIMATION, unit.getResourceAnimation());
            Assertions.assertEquals(RESOURCE_BUSY_BYTE, unit.getResourceBeingGatheredByte());
            Assertions.assertEquals(value, NativeUnitUnion.readValue(unit, scalarMember));
        }
    }
}
