package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.SfxdataDat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/**
 * Checks that every {@link ScrSoundTypeView} getter returns the sfxdata.dat column read of its id inside the column, and throws as that read does
 * past it.
 */
final class ScrSoundTypeViewTest {
    private static final @NotNull String FILE_NAME = "sfxdata.dat";

    @Test
    void getters_match_name_lookup_on_generated_content() throws Exception {
        ColumnGetterAssert.assertGeneratedTables(FILE_NAME,
                                                 SfxdataDat::createSchema, SfxdataDat::createTypeTable,
                                                 SfxdataDat.class, ScrSoundTypeView.class);
    }

    @RequiresInstallation
    @Test
    void getters_match_name_lookup_on_installed_content() throws Exception {
        ColumnGetterAssert.assertInstalledTable(FILE_NAME,
                                                SfxdataDat::createSchema, SfxdataDat::createTypeTable,
                                                SfxdataDat.class, ScrSoundTypeView.class);
    }
}
