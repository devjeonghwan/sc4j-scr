package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.WeaponsDat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that every {@link ScrWeaponTypeView} getter returns the weapons.dat column read of its id, inside the column and past it, where the empty
 * weapon {@code 130} reads.
 * <p>
 * The weapons.dat memory layout is the contract: a unit without a weapon reads the alignment gap after each column array, and an index past the
 * gap reads the next column.
 */
final class ScrWeaponTypeViewTest {
    private static final @NotNull String FILE_NAME = "weapons.dat";
    private static final int EMPTY_WEAPON = ScrWeaponIds.NONE;
    /**
     * The first index past the alignment gap of a 130-byte column: the next column starts at 144 bytes.
     */
    private static final int NEXT_BYTE_COLUMN_INDEX = 144;

    @Test
    void reads_of_weapon_130_return_the_alignment_gap_and_then_the_next_column() throws IOException {
        DatTable data = WeaponsDat.createSchema().parse(new byte[WeaponsDat.createSchema().getExpectedSize()]);
        ScrWeaponTypeView weapons = WeaponsDat.createTypeTable(data);

        data.set(WeaponsDat.ATTACK_ANGLE, EMPTY_WEAPON - 1, 16);
        data.set(WeaponsDat.LAUNCH_SPIN, 0, 24);
        data.set(WeaponsDat.MINIMUM_RANGE, EMPTY_WEAPON - 1, 64);
        data.set(WeaponsDat.MAXIMUM_RANGE, 0, 96);

        Assertions.assertEquals(0, weapons.getAttackAngle(EMPTY_WEAPON));
        Assertions.assertEquals(0, weapons.getMinimumRange(EMPTY_WEAPON));
        Assertions.assertEquals(24, weapons.getAttackAngle(NEXT_BYTE_COLUMN_INDEX));
    }

    @Test
    void getters_match_name_lookup_on_generated_content() throws Exception {
        ColumnGetterAssert.assertGeneratedTables(FILE_NAME,
                                                 WeaponsDat::createSchema, WeaponsDat::createTypeTable,
                                                 WeaponsDat.class, ScrWeaponTypeView.class);
    }

    @RequiresInstallation
    @Test
    void getters_match_name_lookup_on_installed_content() throws Exception {
        ColumnGetterAssert.assertInstalledTable(FILE_NAME,
                                                WeaponsDat::createSchema, WeaponsDat::createTypeTable,
                                                WeaponsDat.class, ScrWeaponTypeView.class);
    }
}
