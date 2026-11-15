package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.FlingyDat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/**
 * Checks that every {@link ScrFlingyTypeView} getter returns the flingy.dat column read of its id, inside the column and past it.
 * <p>
 * The flingy.dat memory layout is the contract: an id past a column has no bound check, so it reads the bytes that follow the column in the DAT
 * memory.
 */
final class ScrFlingyTypeViewTest {
    private static final @NotNull String FILE_NAME = "flingy.dat";

    @Test
    void getters_match_name_lookup_on_generated_content() throws Exception {
        ColumnGetterAssert.assertGeneratedTables(FILE_NAME,
                                                 FlingyDat::createSchema, FlingyDat::createTypeTable,
                                                 FlingyDat.class, ScrFlingyTypeView.class);
    }

    @RequiresInstallation
    @Test
    void getters_match_name_lookup_on_installed_content() throws Exception {
        ColumnGetterAssert.assertInstalledTable(FILE_NAME,
                                                FlingyDat::createSchema, FlingyDat::createTypeTable,
                                                FlingyDat.class, ScrFlingyTypeView.class);
    }
}
