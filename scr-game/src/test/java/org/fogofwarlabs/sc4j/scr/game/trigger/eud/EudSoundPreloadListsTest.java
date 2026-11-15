package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicStorageRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks race preload-list sizes, order and shared source storage.
 */
@RequiresInstallation
final class EudSoundPreloadListsTest {
    /**
     * The mask of the low WORD.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The mask of the low BYTE.
     */
    private static final int BYTE_MASK = 0xFF;

    private static final int PRELOAD_LIST = 0x63FF78;

    @Test
    void sound_preload_lists_have_the_classic_region_sizes_and_are_shared() throws IOException {
        try (GameData installed = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            GameData game = installed.createGameCopy();
            int[] sizes = {0x290, 0x33C, 0x31A};

            for (int race = 0; race < sizes.length; race++) {
                byte[] list = game.getSoundPreloadList(race);

                Assertions.assertEquals(sizes[race], list.length, "race " + race);

                ClassicAddressSpace space = new ClassicAddressSpace();

                space.add(new ClassicStorageRegion(PRELOAD_LIST, list.clone()));

                Assertions.assertEquals(1, space.readDword(PRELOAD_LIST) & WORD_MASK);
                Assertions.assertEquals(2, space.readDword(PRELOAD_LIST) >>> 16);
                Assertions.assertEquals(0, space.readDword(PRELOAD_LIST + list.length - 4), "two trailing zero WORDs");
                Assertions.assertTrue((list[list.length - 6] & BYTE_MASK | (list[list.length - 5] & BYTE_MASK) << 8) > 113);
            }

            byte[] terran = game.getSoundPreloadList(ScrRaceIds.TERRAN);

            Assertions.assertEquals(1 + 113 + 51 + 9, terran[113 * 2] & BYTE_MASK | (terran[113 * 2 + 1] & BYTE_MASK) << 8);
            Assertions.assertSame(installed.getSoundPreloadList(ScrRaceIds.TERRAN), terran,
                                  "Built once and shared, as native builds them once per process.");
        }
    }
}
