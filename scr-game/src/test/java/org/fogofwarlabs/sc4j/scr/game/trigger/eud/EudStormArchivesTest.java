package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Checks archive regions and links against the recorded layout.
 */
@RequiresInstallation
final class EudStormArchivesTest {
    /**
     * The recorded archive allocation base.
     */
    private static final int FIRST_BASE = 0x1A173C10;
    /**
     * The regions that precede the recorded archive regions.
     */
    private static final int LEADING_REGIONS = 2;
    /**
     * The address of the Storm archive list head.
     */
    private static final int STORM_ARCHIVE_HEAD = 0x1505ADF8;

    private static final int @NotNull [] RECORDED = {
            0x1A173C10, 0x20,
            0x1A173C30, 0xB50,
            0x1A174780, 0x40000,
            0x1A1B4780, 0x144,
            0x1A1B48D0, 0x20,
            0x1A1B48F0, 0x3A30,
            0x1A1B8320, 0x10000,
            0x1A1C8320, 0x144,
            0x1A1C8470, 0x20,
            0x1A1C8490, 0xB6D0,
            0x1A1D3B60, 0x10000,
            0x1A1E3B60, 0x144,
            0x1A1E3CB0, 0x20,
            0x1A1E3CD0, 0x4E40,
            0x1A1E8B10, 0x8000,
            0x1A1F0B10, 0x144,
            0x1A1F0C60, 0x20,
            0x1A1F0C80, 0x72400,
            0x1A263080, 0x4000,
            0x1A267080, 0x144
    };

    @Test
    void storm_archive_regions_match_the_native_recording() throws IOException {
        Path executable = ExternalInput.INSTALLATION.resolveFile(GameData.EXECUTABLE_DIRECTORY, GameData.EXECUTABLE_FILE_NAME);
        Path map = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, ExternalInput.TEST_MAPS_FOLDER, "eud_com.scx").toAbsolutePath();

        NativeCodeConstants constants = new NativeCodeConstants();

        Assertions.assertTrue(constants.loadExecutable(executable));

        byte[] filePathBytes = map.toString().getBytes(StandardCharsets.ISO_8859_1);
        List<ClassicRegion> regions = ClassicStormArchives.create(constants.getStormArchives(), filePathBytes, Files.readAllBytes(map), FIRST_BASE);

        Assertions.assertEquals(LEADING_REGIONS + RECORDED.length / 2, regions.size());

        for (int index = 0; index < RECORDED.length / 2; index++) {
            ClassicRegion region = regions.get(LEADING_REGIONS + index);

            Assertions.assertEquals(RECORDED[index * 2], region.getBase(), "region " + index);
            Assertions.assertEquals(RECORDED[index * 2 + 1], region.getSize(), "region " + index);
        }

        ClassicAddressSpace space = new ClassicAddressSpace();

        space.addAll(regions);

        Assertions.assertEquals(0x15021A00, space.readDword(0x4FE544));

        Assertions.assertEquals(0x1A267080, space.readDword(ClassicStormArchives.HEAD_BASE + 4));
        Assertions.assertEquals(0x1A1B4780, space.readDword(ClassicStormArchives.HEAD_BASE + 8));
        Assertions.assertEquals(0x1A1C8320, space.readDword(0x1A1B4780 + 4));
        Assertions.assertEquals(~(STORM_ARCHIVE_HEAD + 4), space.readDword(0x1A267080 + 4));

        Assertions.assertEquals(0x1A1F0C60, space.readDword(0x1A267080 + 8 + 0x128));
        Assertions.assertEquals(0x1A1F0C80, space.readDword(0x1A267080 + 8 + 0x12C));
        Assertions.assertEquals(0x1A263080, space.readDword(0x1A267080 + 8 + 0x130));
        Assertions.assertEquals(0x1A51504D, space.readDword(0x1A1F0C60), "The map archive's MPQ header.");
    }
}
