package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

/**
 * Checks that every {@link ScrImageTypeView} getter returns the images.dat column read of its id, inside the column and past the image count.
 * <p>
 * The images.dat memory layout is the contract: an id past a column has no bound check, so it reads the bytes that follow the column in the DAT
 * memory. The BYTE and DWORD arrays are followed by alignment gaps, and the arrays of references to loaded data (the graphics and the overlays) are
 * not represented.
 */
final class ScrImageTypeViewTest {
    private static final @NotNull String FILE_NAME = "images.dat";
    /**
     * The first draw function index past the 9-byte alignment gap after the 999 draw functions: the color shifts.
     */
    private static final int FIRST_COLOR_SHIFT_INDEX = 1008;
    /**
     * The images.dat index read as the construction image of the empty unit type {@code 228}: its four Unit Direction bytes of {@code 32}.
     */
    private static final int EMPTY_TYPE_CONSTRUCTION_IMAGE = 0x20202020;
    /**
     * The seed of the random images.dat content of the overlay test.
     */
    private static final long RANDOM_SEED = 0x5C4AL;

    @Test
    void reads_past_an_array_return_the_gap_and_then_the_next_array() throws IOException {
        DatTable data = ImagesDat.createSchema().parse(new byte[ImagesDat.createSchema().getExpectedSize()]);
        ScrImageTypeView images = ImagesDat.createTypeTable(data);

        data.set(ImagesDat.DRAW_FUNCTION, ImagesDat.IMAGE_COUNT - 1, 9);
        data.set(ImagesDat.COLOR_SHIFT, 0, 7);
        data.set(ImagesDat.ISCRIPT, ImagesDat.IMAGE_COUNT - 1, 5);

        Assertions.assertEquals(0, images.getDrawFunction(ImagesDat.IMAGE_COUNT));
        Assertions.assertEquals(7, images.getDrawFunction(FIRST_COLOR_SHIFT_INDEX));
        Assertions.assertEquals(5, images.getIscript(ImagesDat.IMAGE_COUNT - 1));
        Assertions.assertEquals(0, images.getIscript(ImagesDat.IMAGE_COUNT));
    }

    @Test
    void reads_of_references_or_past_the_images_dat_memory_throw() throws IOException {
        ScrImageTypeView images = ImagesDat.createTypeTable(ImagesDat.createSchema().parse(new byte[ImagesDat.createSchema().getExpectedSize()]));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> images.getIscript(ImagesDat.IMAGE_COUNT + 1));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> images.getGraphicsFile(ImagesDat.IMAGE_COUNT));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> images.getDrawFunction(EMPTY_TYPE_CONSTRUCTION_IMAGE));
    }

    @Test
    void getters_match_name_lookup_on_generated_content() throws Exception {
        ColumnGetterAssert.assertGeneratedTables(FILE_NAME,
                                                 ImagesDat::createSchema, ImagesDat::createTypeTable,
                                                 ImagesDat.class, ScrImageTypeView.class);
    }

    @RequiresInstallation
    @Test
    void getters_match_name_lookup_on_installed_content() throws Exception {
        ColumnGetterAssert.assertInstalledTable(FILE_NAME,
                                                ImagesDat::createSchema, ImagesDat::createTypeTable,
                                                ImagesDat.class, ScrImageTypeView.class);
    }

    @Test
    void get_overlay_should_select_the_column_by_overlay_table() throws IOException {
        byte[] content = new byte[ImagesDat.createSchema().getExpectedSize()];

        new Random(RANDOM_SEED).nextBytes(content);

        DatTable images = ImagesDat.createSchema().parse(content);
        ScrImageTypeView imageTypes = ImagesDat.createTypeTable(images);
        String[] overlays = {ImagesDat.ATTACK_OVERLAY, ImagesDat.DAMAGE_OVERLAY, ImagesDat.SPECIAL_OVERLAY, ImagesDat.LANDING_DUST_OVERLAY,
                             ImagesDat.LIFTOFF_DUST_OVERLAY, ImagesDat.SHIELD_OVERLAY};

        for (int image = 0; image < ImagesDat.IMAGE_COUNT; image++) {
            for (int overlay = ScrImageTypeView.OVERLAY_ATTACK; overlay <= ScrImageTypeView.OVERLAY_SHIELD; overlay++) {
                Assertions.assertEquals(images.get(overlays[overlay], image), imageTypes.getOverlay(image, overlay));
            }
        }
    }
}
