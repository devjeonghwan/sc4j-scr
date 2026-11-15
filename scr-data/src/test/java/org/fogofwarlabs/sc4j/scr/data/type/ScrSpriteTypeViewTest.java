package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.SpritesDat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that every {@link ScrSpriteTypeView} getter returns the {@code sprites.dat} column read of its id, inside the column and past it.
 * <p>
 * The {@code sprites.dat} memory layout is the contract: an id past a column has no bound check, so it reads the bytes that follow the column in the
 * DAT memory.
 */
final class ScrSpriteTypeViewTest {
    private static final @NotNull String FILE_NAME = "sprites.dat";
    /**
     * The first sprite with a health bar and a selection circle, where the partial {@code sprites.dat} columns start.
     */
    private static final int FIRST_SELECTABLE_SPRITE = 130;

    @Test
    void getters_match_name_lookup_on_generated_content() throws Exception {
        ColumnGetterAssert.assertGeneratedTables(FILE_NAME,
                                                 SpritesDat::createSchema, SpritesDat::createTypeTable,
                                                 SpritesDat.class, ScrSpriteTypeView.class);
    }

    @RequiresInstallation
    @Test
    void getters_match_name_lookup_on_installed_content() throws Exception {
        ColumnGetterAssert.assertInstalledTable(FILE_NAME,
                                                SpritesDat::createSchema, SpritesDat::createTypeTable,
                                                SpritesDat.class, ScrSpriteTypeView.class);
    }

    @Test
    void partial_columns_start_at_the_first_selectable_sprite() {
        Assertions.assertEquals(FIRST_SELECTABLE_SPRITE, ScrSpriteTypeView.FIRST_SELECTABLE_SPRITE);
    }
}
