package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that every {@link ScrUnitTypeView} getter returns the {@code units.dat} column read of its id, inside the column and past it, where the empty
 * unit type {@code 228} reads, and that the getters see later writes to the table.
 * <p>
 * The {@code units.dat} memory layout is the contract: an emptied production queue slot builds a unit from the bytes that follow each column array,
 * or from zero alignment gaps, and a write to a byte of a column block (EUD) changes the column element that holds the byte.
 */
final class ScrUnitTypeViewTest {
    private static final @NotNull String FILE_NAME = "units.dat";
    private static final int EMPTY_TYPE = ScrUnitIds.NONE;
    private static final int FIRST_ADDON_TYPE = ScrUnitTypeView.FIRST_INFESTATION_TYPE;
    /**
     * The unit type of the Terran Command Center, the first type of the partial columns.
     */
    private static final int COMMAND_CENTER_TYPE = 106;

    /**
     * The shield amount written to the first unit type to find the block of the shield amount column.
     */
    private static final int SHIELD_BLOCK_MARKER = 0x5A;
    /**
     * The offset of the high byte in a little-endian WORD.
     */
    private static final int HIGH_BYTE_OFFSET = 1;
    /**
     * The unit type whose hit points the write test sets.
     */
    private static final int HIT_POINTS_UNIT_TYPE = 5;
    /**
     * The unit type whose shield amount the write test sets through the high byte of its WORD.
     */
    private static final int SHIELD_UNIT_TYPE = 7;
    /**
     * The high byte the write test stores into the shield amount.
     */
    private static final int SHIELD_HIGH_BYTE = 0x80;
    /**
     * The unit type whose requirement index the write test sets through the {@link RequirementIndexColumn}.
     */
    private static final int COLUMN_WRITE_UNIT_TYPE = 9;
    /**
     * The unit type whose requirement index the write test sets through the {@link DatTable}.
     */
    private static final int TABLE_WRITE_UNIT_TYPE = 10;

    private static int findFirstShieldBlock(@NotNull DatTable table) {
        int saved = table.get(UnitsDat.SHIELD_AMOUNT, 0);

        table.set(UnitsDat.SHIELD_AMOUNT, 0, SHIELD_BLOCK_MARKER);

        try {
            for (int block = 0; block < table.getBlockCount(); block++) {
                if (table.readBlockByte(block, 0) == SHIELD_BLOCK_MARKER) {
                    return block;
                }
            }
        } finally {
            table.set(UnitsDat.SHIELD_AMOUNT, 0, saved);
        }

        return Assertions.fail("No block holds " + UnitsDat.SHIELD_AMOUNT);
    }

    /**
     * The zero-filled {@code units.dat} table of the test, with the values the test sets.
     */
    private final @NotNull DatTable data;
    private final @NotNull ScrUnitTypeView units;

    ScrUnitTypeViewTest() throws IOException {
        this.data  = UnitsDat.createSchema().parse(new byte[UnitsDat.createSchema().getExpectedSize()]);
        this.units = UnitsDat.createTypeTable(this.data);
    }


    @Test
    void reads_past_a_column_return_the_leading_elements_of_the_next_column() {
        int[] leadingBytes = {4, 7, 5, 6};

        for (int unitType = 0; unitType < leadingBytes.length; unitType++) {
            this.data.set(UnitsDat.TARGET_ACQUISITION_RANGE, unitType, leadingBytes[unitType]);
            this.data.set(UnitsDat.ELEVATION_LEVEL, unitType, leadingBytes[unitType] + 1);
            this.data.set(UnitsDat.INITIAL_DIRECTION, unitType, leadingBytes[unitType] + 2);
        }

        this.data.set(UnitsDat.ADDON_HORIZONTAL_OFFSET, 0, 0x10);
        this.data.set(UnitsDat.ADDON_VERTICAL_OFFSET, 0, 0x20);
        this.data.set(UnitsDat.PORTRAIT, 0, 11);
        this.data.set(UnitsDat.PORTRAIT, 1, 12);
        this.data.set(UnitsDat.PORTRAIT, 2, 13);
        this.data.set(UnitsDat.PORTRAIT, 3, 14);

        Assertions.assertEquals(0x06050704, this.units.getFlags(EMPTY_TYPE));
        Assertions.assertEquals(0x07060805, this.units.getHitPoints(EMPTY_TYPE));
        Assertions.assertEquals(0x08070906, this.units.getConstructionImage(EMPTY_TYPE));
        Assertions.assertEquals(this.units.getAddonHorizontalOffset(FIRST_ADDON_TYPE), this.units.getPlacementWidth(EMPTY_TYPE));
        Assertions.assertEquals(this.units.getAddonVerticalOffset(FIRST_ADDON_TYPE), this.units.getPlacementHeight(EMPTY_TYPE));
        Assertions.assertEquals(11, this.units.getDimensionLeft(EMPTY_TYPE));
        Assertions.assertEquals(12, this.units.getDimensionUp(EMPTY_TYPE));
        Assertions.assertEquals(13, this.units.getDimensionRight(EMPTY_TYPE));
        Assertions.assertEquals(14, this.units.getDimensionDown(EMPTY_TYPE));
    }

    @Test
    void reads_past_a_column_before_an_alignment_gap_return_zero() {
        this.data.set(UnitsDat.MINERAL_COST, EMPTY_TYPE - 1, 400);
        this.data.set(UnitsDat.GAS_COST, 0, 300);
        this.data.set(UnitsDat.SUPPLY_REQUIRED, EMPTY_TYPE - 1, 8);
        this.data.set(UnitsDat.SPACE_REQUIRED, 0, 2);
        this.data.set(UnitsDat.FLINGY_TYPE, EMPTY_TYPE - 1, 9);
        this.data.set(UnitsDat.FIRST_SUBUNIT, 0, 3);

        Assertions.assertEquals(0, this.units.getMineralCost(EMPTY_TYPE));
        Assertions.assertEquals(0, this.units.getSupplyRequired(EMPTY_TYPE));
        Assertions.assertEquals(0, this.units.getFlingyType(EMPTY_TYPE));
    }

    @Test
    void get_build_time_should_read_the_live_requirement_index_of_type_0_for_type_228() {
        this.data.set(UnitsDat.BUILD_TIME, EMPTY_TYPE - 1, 900);
        this.units.getRequirementIndices().set(0, 2);

        Assertions.assertEquals(2, this.units.getBuildTime(EMPTY_TYPE));
        Assertions.assertEquals(900, this.units.getBuildTime(EMPTY_TYPE - 1));
        Assertions.assertEquals(0, this.units.getBuildTimeEntry(EMPTY_TYPE));
    }

    @Test
    void reads_past_the_units_dat_memory_throw() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> this.units.getAvailabilityFlags(EMPTY_TYPE));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> this.units.getFlingyType(-1));
    }

    @Test
    void getters_match_name_lookup_on_generated_content() throws Exception {
        ColumnGetterAssert.assertGeneratedTables(FILE_NAME, UnitsDat::createSchema, UnitsDat::createTypeTable, UnitsDat.class, ScrUnitTypeView.class);
    }

    @RequiresInstallation
    @Test
    void getters_match_name_lookup_on_installed_content() throws Exception {
        ColumnGetterAssert.assertInstalledTable(FILE_NAME, UnitsDat::createSchema, UnitsDat::createTypeTable, UnitsDat.class, ScrUnitTypeView.class);
    }

    @Test
    void partial_columns_start_at_the_first_infestation_type() {
        Assertions.assertEquals(COMMAND_CENTER_TYPE, ScrUnitTypeView.FIRST_INFESTATION_TYPE);
    }

    @Test
    void getters_see_writes_to_the_underlying_table() {
        RequirementIndexColumn requirementIndices = this.units.getRequirementIndices();

        this.data.set(UnitsDat.HIT_POINTS, HIT_POINTS_UNIT_TYPE, -256);
        this.data.writeBlockByte(findFirstShieldBlock(this.data), Short.BYTES * SHIELD_UNIT_TYPE + HIGH_BYTE_OFFSET, SHIELD_HIGH_BYTE);
        requirementIndices.set(COLUMN_WRITE_UNIT_TYPE, 0x12345);
        this.data.set(UnitsDat.REQUIREMENT_INDEX, TABLE_WRITE_UNIT_TYPE, 0x12345);

        Assertions.assertEquals(-256, this.units.getHitPoints(HIT_POINTS_UNIT_TYPE));
        Assertions.assertEquals(SHIELD_HIGH_BYTE << Byte.SIZE, this.units.getShieldAmount(SHIELD_UNIT_TYPE));
        Assertions.assertEquals(this.data.get(UnitsDat.REQUIREMENT_INDEX, COLUMN_WRITE_UNIT_TYPE), requirementIndices.get(COLUMN_WRITE_UNIT_TYPE));
        Assertions.assertEquals(this.data.get(UnitsDat.REQUIREMENT_INDEX, TABLE_WRITE_UNIT_TYPE),
                                this.units.getRequirementIndex(TABLE_WRITE_UNIT_TYPE));
        Assertions.assertEquals(this.units.getRequirementIndex(COLUMN_WRITE_UNIT_TYPE), this.units.getRequirementIndex(TABLE_WRITE_UNIT_TYPE));
        Assertions.assertEquals(this.data.getCount(UnitsDat.REQUIREMENT_INDEX), requirementIndices.getCount());
    }
}
