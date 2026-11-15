package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.TechdataDat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that every {@link ScrTechTypeView} getter returns the techdata.dat column read of its id, inside the column and past the technology count.
 * <p>
 * The techdata.dat memory layout is the contract: an id past a column has no bound check, so it reads the bytes that follow the column in the DAT
 * memory. The WORD arrays are aligned to {@code 16} bytes, the BYTE arrays to {@code 8} bytes, so the label array is followed directly by the race
 * array.
 */
final class ScrTechTypeViewTest {
    private static final @NotNull String FILE_NAME = "techdata.dat";
    /**
     * The first race index past the 4-byte alignment gap after the 44 races: the researched flags.
     */
    private static final int FIRST_RESEARCHED_FLAG_INDEX = 48;

    @Test
    void reads_past_a_byte_array_return_the_gap_and_then_the_next_array() throws IOException {
        DatTable data = TechdataDat.createSchema().parse(new byte[TechdataDat.createSchema().getExpectedSize()]);
        ScrTechTypeView technologies = TechdataDat.createTypeTable(data);

        data.set(TechdataDat.RACE, TechdataDat.TECH_COUNT - 1, 2);
        data.set(TechdataDat.RESEARCHED_FLAG, 0, 1);
        data.set(TechdataDat.RACE, 0, 3);
        data.set(TechdataDat.RACE, 1, 1);

        Assertions.assertEquals(0, technologies.getRace(TechdataDat.TECH_COUNT));
        Assertions.assertEquals(1, technologies.getRace(FIRST_RESEARCHED_FLAG_INDEX));
        Assertions.assertEquals(0x0103, technologies.getLabel(TechdataDat.TECH_COUNT));
    }

    @Test
    void getters_match_name_lookup_on_generated_content() throws Exception {
        ColumnGetterAssert.assertGeneratedTables(FILE_NAME,
                                                 TechdataDat::createSchema, TechdataDat::createTypeTable,
                                                 TechdataDat.class, ScrTechTypeView.class);
    }

    @RequiresInstallation
    @Test
    void getters_match_name_lookup_on_installed_content() throws Exception {
        ColumnGetterAssert.assertInstalledTable(FILE_NAME,
                                                TechdataDat::createSchema, TechdataDat::createTypeTable,
                                                TechdataDat.class, ScrTechTypeView.class);
    }
}
