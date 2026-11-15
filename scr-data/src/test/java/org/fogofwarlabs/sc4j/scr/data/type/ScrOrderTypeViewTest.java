package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.OrdersDat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/**
 * Checks that every {@link ScrOrderTypeView} getter returns the orders.dat column read of its id, inside the column and past it.
 * <p>
 * The orders.dat memory layout is the contract: an id past a column has no bound check, so it reads the bytes that follow the column in the DAT
 * memory.
 */
final class ScrOrderTypeViewTest {
    private static final @NotNull String FILE_NAME = "orders.dat";

    @Test
    void getters_match_name_lookup_on_generated_content() throws Exception {
        ColumnGetterAssert.assertGeneratedTables(FILE_NAME,
                                                 OrdersDat::createSchema, OrdersDat::createTypeTable,
                                                 OrdersDat.class, ScrOrderTypeView.class);
    }

    @RequiresInstallation
    @Test
    void getters_match_name_lookup_on_installed_content() throws Exception {
        ColumnGetterAssert.assertInstalledTable(FILE_NAME,
                                                OrdersDat::createSchema, OrdersDat::createTypeTable,
                                                OrdersDat.class, ScrOrderTypeView.class);
    }
}
