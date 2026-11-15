package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.PortdataDat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

/**
 * Checks that every {@link PortraitTypeTable} getter returns the portdata.dat column read of its id, inside the column and past it.
 * <p>
 * The portdata.dat memory layout is the contract: an id past a column has no bound check, so it reads the bytes that follow the column in the DAT
 * memory.
 */
final class PortraitTypeTableTest {
    private static final @NotNull String FILE_NAME = "portdata.dat";
    /**
     * The seed of the random portdata.dat content of the idle clip test.
     */
    private static final long RANDOM_SEED = 0x5C4AL;

    @Test
    void getters_match_name_lookup_on_generated_content() throws Exception {
        ColumnGetterAssert.assertGeneratedTables(FILE_NAME,
                                                 PortdataDat::createSchema, PortdataDat::createTypeTable,
                                                 PortdataDat.class, PortraitTypeTable.class);
    }

    @RequiresInstallation
    @Test
    void getters_match_name_lookup_on_installed_content() throws Exception {
        ColumnGetterAssert.assertInstalledTable(FILE_NAME,
                                                PortdataDat::createSchema, PortdataDat::createTypeTable,
                                                PortdataDat.class, PortraitTypeTable.class);
    }

    @Test
    void get_idle_change_should_select_the_column_by_idle_clip() throws IOException {
        byte[] content = new byte[PortdataDat.createSchema().getExpectedSize()];

        new Random(RANDOM_SEED).nextBytes(content);

        DatTable portraits = PortdataDat.createSchema().parse(content);
        PortraitTypeTable portraitTypes = PortdataDat.createTypeTable(portraits);
        String[] idleChanges = {PortdataDat.FIRST_IDLE_CHANGE, PortdataDat.SECOND_IDLE_CHANGE, PortdataDat.THIRD_IDLE_CHANGE,
                                PortdataDat.FOURTH_IDLE_CHANGE};

        for (int portrait = 0; portrait < PortdataDat.PORTRAIT_COUNT; portrait++) {
            for (int clip = 0; clip < PortraitTypeTable.IDLE_CLIP_COUNT; clip++) {
                Assertions.assertEquals(portraits.get(idleChanges[clip], portrait), portraitTypes.getIdleChange(portrait, clip));
            }
        }
    }
}
