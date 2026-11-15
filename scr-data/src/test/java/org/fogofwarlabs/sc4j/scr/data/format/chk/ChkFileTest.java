package org.fogofwarlabs.sc4j.scr.data.format.chk;

import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.test.DataTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests {@link ChkFile} against the map files of a real StarCraft: Remastered installation.
 */
@RequiresInstallation
final class ChkFileTest {
    private static final @NotNull String SCENARIO_CHK_NAME = "staredit\\scenario.chk";

    // The shipped installation holds 278 sequentially parseable maps (including the five imploded iCCup ladder maps)
    // and 45 protected maps; users may add maps (for example Maps/zergling.scm or a protected EUD map), so both are
    // lower bounds.
    private static final int MINIMUM_VALID_COUNT = 278;

    private static final int MINIMUM_INVALID_COUNT = 45;

    private static final int MAXIMUM_TILESET = 7;

    private static void assertMapInvariants(@NotNull ChkFile chkFile, @NotNull Path mapFile) {
        int width = chkFile.getWidth();
        int height = chkFile.getHeight();

        Assertions.assertTrue(width > 0 && height > 0, "Invalid dimensions: " + mapFile);
        Assertions.assertEquals(width * height, chkFile.getTileIds().length, "The MTXM size mismatches: " + mapFile);
        Assertions.assertTrue(chkFile.getTileset() <= MAXIMUM_TILESET, "Invalid tileset: " + mapFile);
        Assertions.assertTrue(chkFile.hasSection("STR ") || chkFile.hasSection("STRx"), "The string section misses: " + mapFile);
    }


    @Test
    void parse_should_read_valid_installed_maps_and_reject_protected_maps() throws IOException {
        List<Path> mapFiles = DataTestResources.listInstalledMaps();

        Assertions.assertFalse(mapFiles.isEmpty(), "No map file found.");

        int validCount = 0;
        int invalidCount = 0;

        for (Path mapFile : mapFiles) {
            MpqArchive archive = MpqArchive.open(mapFile);
            byte[] scenarioChk = archive.readFile(SCENARIO_CHK_NAME);

            try {
                ChkFile chkFile = ChkFile.parse(scenarioChk);

                assertMapInvariants(chkFile, mapFile);
                validCount++;
            } catch (IOException | IllegalStateException exception) {
                // Protected and custom-protected maps do not parse sequentially.
                invalidCount++;
            } catch (AssertionError error) {
                System.out.println("INVARIANT FAILED: " + mapFile.getFileName() + " -> " + error.getMessage());

                try {
                    System.out.println("sections: " + ChkFile.parse(scenarioChk).getSectionNames());
                } catch (IOException parseException) {
                    System.out.println("sections: unparseable");
                }

                throw error;
            }
        }

        System.out.println("Valid: " + validCount + ", invalid: " + invalidCount);

        Assertions.assertTrue(validCount >= MINIMUM_VALID_COUNT, "Valid maps: " + validCount);
        Assertions.assertTrue(invalidCount >= MINIMUM_INVALID_COUNT, "Protected maps: " + invalidCount);
    }
}
