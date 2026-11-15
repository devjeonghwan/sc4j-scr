package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Tests the target acquisition range initialization of the {@code arr/units.dat} table and the schema against the real file of a StarCraft:
 * Remastered installation.
 */
final class UnitsDatTest {
    private static final int MARINE = ScrUnitIds.TERRAN_MARINE;
    private static final int HYDRALISK = ScrUnitIds.ZERG_HYDRALISK;
    /**
     * The number of unit types, the length of the target acquisition range column.
     */
    private static final int UNIT_COUNT = 228;
    /**
     * The file offset of the interleaved placement box block.
     */
    private static final int PLACEMENT_BLOCK_OFFSET = 11284;
    /**
     * The file offset of the interleaved addon offset block.
     */
    private static final int ADDON_BLOCK_OFFSET = 12196;
    /**
     * The file offset of the interleaved unit dimension block.
     */
    private static final int DIMENSION_BLOCK_OFFSET = 12580;
    /**
     * The size of one slot of two interleaved WORDs.
     */
    private static final int WORD_PAIR_SIZE = 2 * Short.BYTES;
    /**
     * The size of one slot of the four interleaved dimension WORDs.
     */
    private static final int DIMENSION_SLOT_SIZE = 4 * Short.BYTES;

    private static @Nullable DatTable unitsTable;
    private static byte @Nullable [] unitsData;

    private static void loadUnitsDat() throws IOException {
        if (unitsTable != null) {
            return;
        }

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            unitsData = storage.readFile("arr/units.dat");
        }

        unitsTable = UnitsDat.createSchema().parse(unitsData);
    }


    private static void fillColumn(@NotNull DatTable table, @NotNull String field, int value) {
        for (int element = 0; element < table.getCount(field); element++) {
            table.set(field, element, value);
        }
    }

    private static void writeColumn(@NotNull DatTable table, @NotNull String field, int @NotNull [] values) {
        for (int element = 0; element < values.length; element++) {
            table.set(field, element, values[element]);
        }
    }

    @Test
    void acquisition_ranges_use_unsigned_weapon_ranges_byte_truncation_and_in_place_subunits() throws IOException {
        DatTable units = UnitsDat.createSchema().parse(new byte[UnitsDat.createSchema().getExpectedSize()]);
        DatTable weapons = WeaponsDat.createSchema().parse(new byte[WeaponsDat.createSchema().getExpectedSize()]);

        fillColumn(units, UnitsDat.FIRST_SUBUNIT, ScrUnitIds.NONE);
        fillColumn(units, UnitsDat.GROUND_WEAPON, ScrWeaponIds.NONE);
        fillColumn(units, UnitsDat.AIR_WEAPON, ScrWeaponIds.NONE);

        int[] subunits = {228, 0, 3, 228, 228, 228, 4, 228, 8, 228, 11, 12, 228, 11};
        int[] groundWeapons = {0, 130, 130, 130, 2, 130, 130, 3, 130, 130, 130, 130, 130, 130};
        int[] airWeapons = {130, 130, 130, 1, 130, 255, 130, 130, 130, 130, 130, 130, 130, 130};
        int[] acquisitionRanges = {5, 0, 0, 2, 220, 7, 0, 0, 9, 0, 0, 1, 4, 2};
        int[] maximumRanges = {-1, 383, 8192, 31};

        writeColumn(units, "Subunit1", subunits);
        writeColumn(units, "Ground Weapon", groundWeapons);
        writeColumn(units, "Air Weapon", airWeapons);
        writeColumn(units, "Target Acquisition Range", acquisitionRanges);
        writeColumn(weapons, "Maximum Range", maximumRanges);

        UnitsDat.initializeTargetAcquisitionRanges(units, weapons);

        // Independent expected output of acquisition range initialization.
        int[] expected = new int[UNIT_COUNT];
        int[] expectedPrefix = {255, 255, 11, 11, 0, 7, 0, 0, 9, 0, 1, 4, 4, 4};

        System.arraycopy(expectedPrefix, 0, expected, 0, expectedPrefix.length);

        Assertions.assertArrayEquals(expected, units.copyColumn("Target Acquisition Range"));
        Assertions.assertArrayEquals(subunits, Arrays.copyOf(units.copyColumn("Subunit1"), subunits.length));
    }

    @Test
    void initialize_target_acquisition_ranges_should_reject_subunit_ids_beyond_unit_table() throws IOException {
        DatTable units = UnitsDat.createSchema().parse(new byte[UnitsDat.createSchema().getExpectedSize()]);
        DatTable weapons = WeaponsDat.createSchema().parse(new byte[WeaponsDat.createSchema().getExpectedSize()]);

        units.set(UnitsDat.FIRST_SUBUNIT, 0, ScrUnitIds.NONE + 1);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> UnitsDat.initializeTargetAcquisitionRanges(units, weapons));
    }

    @RequiresInstallation
    @Test
    void get_expected_size_should_match_units_dat_file_size() {
        Assertions.assertEquals(19876, UnitsDat.createSchema().getExpectedSize());
    }

    @RequiresInstallation
    @Test
    void parse_should_read_marine_vitals() throws IOException {
        loadUnitsDat();

        DatTable activeUnitsTable = unitsTable;

        Assertions.assertNotNull(activeUnitsTable);

        // Terran Marine: 40 hit points in 1/256 units, 50 minerals, no vespene.
        Assertions.assertEquals(40 * 256, activeUnitsTable.get("Hit Points", MARINE));
        Assertions.assertEquals(50, activeUnitsTable.get("Mineral Cost", MARINE));
        Assertions.assertEquals(0, activeUnitsTable.get("Vespene Cost", MARINE));
    }

    @RequiresInstallation
    @Test
    void parse_should_read_marine_combat_values() throws IOException {
        loadUnitsDat();

        DatTable activeUnitsTable = unitsTable;

        Assertions.assertNotNull(activeUnitsTable);

        // Terran Marine: ground weapon 0 (Gauss Rifle), no air weapon, 0 armor, sight range 7.
        Assertions.assertEquals(0, activeUnitsTable.get("Ground Weapon", MARINE));
        Assertions.assertEquals(0, activeUnitsTable.get("Air Weapon", MARINE));
        Assertions.assertEquals(0, activeUnitsTable.get("Armor", MARINE));
        Assertions.assertEquals(7, activeUnitsTable.get("Sight Range", MARINE));
    }

    @RequiresInstallation
    @Test
    void parse_should_read_same_ground_and_air_weapon_for_hydralisk() throws IOException {
        loadUnitsDat();

        DatTable activeUnitsTable = unitsTable;

        Assertions.assertNotNull(activeUnitsTable);

        // Zerg Hydralisk: ground and air weapon are both Needle Spines.
        Assertions.assertEquals(activeUnitsTable.get("Ground Weapon", HYDRALISK), activeUnitsTable.get("Air Weapon", HYDRALISK));
    }

    @RequiresInstallation
    @Test
    void parse_should_read_marine_production_values() throws IOException {
        loadUnitsDat();

        DatTable activeUnitsTable = unitsTable;

        Assertions.assertNotNull(activeUnitsTable);

        // Terran Marine: build time 360 frames, 1 supply (stored doubled), no shields.
        Assertions.assertEquals(360, activeUnitsTable.get("Build Time", MARINE));
        Assertions.assertEquals(2, activeUnitsTable.get("Supply Required", MARINE));
        Assertions.assertEquals(0, activeUnitsTable.get("Shield Enable", MARINE));
    }

    @RequiresInstallation
    @Test
    void parse_should_read_interleaved_geometry_from_file_offsets() throws IOException {
        loadUnitsDat();

        byte[] activeUnitsData = unitsData;

        Assertions.assertNotNull(activeUnitsData);

        ByteBuffer data = ByteBuffer.wrap(activeUnitsData).order(ByteOrder.LITTLE_ENDIAN);
        String[] dimensions = {"Unit Size Left", "Unit Size Up", "Unit Size Right", "Unit Size Down"};

        DatTable activeUnitsTable = unitsTable;

        Assertions.assertNotNull(activeUnitsTable);

        // The native units.dat descriptor and its consumers establish these blocks.
        for (int unitIndex = 0; unitIndex < UnitsDat.UNIT_COUNT; unitIndex++) {
            int placementOffset = PLACEMENT_BLOCK_OFFSET + unitIndex * WORD_PAIR_SIZE;
            int width = Short.toUnsignedInt(data.getShort(placementOffset));
            int height = Short.toUnsignedInt(data.getShort(placementOffset + Short.BYTES));

            Assertions.assertEquals(width, activeUnitsTable.get("Placement Box Width", unitIndex));
            Assertions.assertEquals(height, activeUnitsTable.get("Placement Box Height", unitIndex));

            for (int dimensionIndex = 0; dimensionIndex < dimensions.length; dimensionIndex++) {
                String dimension = dimensions[dimensionIndex];
                int dimensionOffset = DIMENSION_BLOCK_OFFSET + unitIndex * DIMENSION_SLOT_SIZE + dimensionIndex * Short.BYTES;
                int expected = Short.toUnsignedInt(data.getShort(dimensionOffset));

                Assertions.assertEquals(expected, activeUnitsTable.get(dimension, unitIndex), dimension + ": " + unitIndex);
            }
        }
    }

    @RequiresInstallation
    @Test
    void parse_should_read_interleaved_addon_offsets() throws IOException {
        loadUnitsDat();

        byte[] activeUnitsData = unitsData;

        Assertions.assertNotNull(activeUnitsData);

        ByteBuffer data = ByteBuffer.wrap(activeUnitsData).order(ByteOrder.LITTLE_ENDIAN);

        DatTable activeUnitsTable = unitsTable;

        Assertions.assertNotNull(activeUnitsTable);

        // The native add-on placement reads index adjacent WORDs with (unitType - 106) * 4.
        for (int addonIndex = 0; addonIndex < UnitsDat.INFESTATION_UNIT_COUNT; addonIndex++) {
            int addonOffset = ADDON_BLOCK_OFFSET + addonIndex * WORD_PAIR_SIZE;
            int horizontal = Short.toUnsignedInt(data.getShort(addonOffset));
            int vertical = Short.toUnsignedInt(data.getShort(addonOffset + Short.BYTES));

            Assertions.assertEquals(horizontal, activeUnitsTable.get("Addon Horizontal", addonIndex));
            Assertions.assertEquals(vertical, activeUnitsTable.get("Addon Vertical", addonIndex));
        }
    }
}
