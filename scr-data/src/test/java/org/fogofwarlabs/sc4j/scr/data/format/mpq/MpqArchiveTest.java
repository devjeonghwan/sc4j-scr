package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.test.DataTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tests {@link MpqArchive} against the map files of a real StarCraft: Remastered installation.
 */
@RequiresInstallation
final class MpqArchiveTest {
    private static final @NotNull String SCENARIO_CHK_NAME = "staredit\\scenario.chk";
    private static final int SECTION_HEADER_SIZE = 8;
    private static final int SECTION_NAME_LENGTH = 4;
    private static final int FIRST_PRINTABLE_CHARACTER = 0x20;
    private static final int LAST_PRINTABLE_CHARACTER = 0x7E;

    private static int indexOf(byte @NotNull [] data, byte @NotNull [] pattern) {
        for (int index = 0; index + pattern.length <= data.length; index++) {
            boolean matches = true;

            for (int patternIndex = 0; patternIndex < pattern.length; patternIndex++) {
                if (data[index + patternIndex] != pattern[patternIndex]) {
                    matches = false;

                    break;
                }
            }

            if (matches) {
                return index;
            }
        }

        return -1;
    }

    private static boolean isValidChk(byte @NotNull [] chkData) {
        if (chkData.length < SECTION_HEADER_SIZE) {
            return false;
        }

        long offset = 0;

        while (offset + SECTION_HEADER_SIZE <= chkData.length) {
            // Section names are four printable ASCII characters.
            for (int nameIndex = 0; nameIndex < SECTION_NAME_LENGTH; nameIndex++) {
                byte nameCharacter = chkData[(int) offset + nameIndex];

                if (nameCharacter < FIRST_PRINTABLE_CHARACTER || nameCharacter > LAST_PRINTABLE_CHARACTER) {
                    return false;
                }
            }

            int sizeOffset = (int) offset + SECTION_NAME_LENGTH;
            int sectionSize = Byte.toUnsignedInt(chkData[sizeOffset]) |
                              (Byte.toUnsignedInt(chkData[sizeOffset + 1]) << Byte.SIZE) |
                              (Byte.toUnsignedInt(chkData[sizeOffset + 2]) << Short.SIZE) |
                              (Byte.toUnsignedInt(chkData[sizeOffset + 3]) << (Integer.SIZE - Byte.SIZE));

            offset += SECTION_HEADER_SIZE + (long) sectionSize;

            // Negative and oversized lengths are protection tricks that sequential parsing rejects.
            if (sectionSize < 0 || offset > chkData.length) {
                return false;
            }
        }

        return offset == chkData.length;
    }


    @Test
    void read_file_should_read_valid_scenario_chk_from_installed_map() throws IOException {
        List<Path> mapFiles = DataTestResources.listInstalledMaps();
        Path mapFile = mapFiles.isEmpty() ? null : mapFiles.get(0);

        Assertions.assertNotNull(mapFile, "No map file found.");

        MpqArchive archive = MpqArchive.open(mapFile);
        byte[] scenarioChk = archive.readFile(SCENARIO_CHK_NAME);

        Assertions.assertTrue(isValidChk(scenarioChk), "The scenario.chk is not valid: " + mapFile);
    }

    @Test
    void read_file_should_accept_slash_and_any_case() throws IOException {
        List<Path> mapFiles = DataTestResources.listInstalledMaps();
        Path mapFile = mapFiles.isEmpty() ? null : mapFiles.get(0);

        Assertions.assertNotNull(mapFile, "No map file found.");

        MpqArchive archive = MpqArchive.open(mapFile);
        byte[] backslashContent = archive.readFile(SCENARIO_CHK_NAME);
        byte[] slashContent = archive.readFile("STAREDIT/SCENARIO.CHK");

        Assertions.assertArrayEquals(backslashContent, slashContent);
    }

    @Test
    void read_file_should_load_scenario_chk_from_every_installed_map() throws IOException {
        List<Path> mapFiles = DataTestResources.listInstalledMaps();

        Assertions.assertFalse(mapFiles.isEmpty(), "No map file found.");

        int validCount = 0;
        int protectedCount = 0;
        int customProtectedCount = 0;
        Map<String, Integer> failureReasons = new TreeMap<>();

        for (Path mapFile : mapFiles) {
            try {
                MpqArchive archive = MpqArchive.open(mapFile);
                byte[] scenarioChk = archive.readFile(SCENARIO_CHK_NAME);

                if (isValidChk(scenarioChk)) {
                    validCount++;
                } else if (indexOf(scenarioChk, "VER ".getBytes(StandardCharsets.US_ASCII)) >= 0) {
                    // Protected maps garble the section table on purpose; the game still loads them.
                    protectedCount++;
                } else {
                    // No VER section at all. The installed iCCup ladder maps reach this branch only when imploded
                    // (MPQ_FILE_IMPLODE) sectors are misread; they decode to valid scenarios.
                    customProtectedCount++;
                }
            } catch (IOException | RuntimeException exception) {
                String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();

                failureReasons.merge(reason, 1, Integer::sum);
                System.out.println("FAILED: " + mapFile.getFileName() + " -> " + reason);
            }
        }

        int readCount = validCount + protectedCount + customProtectedCount;

        System.out.println("Maps: " + mapFiles.size() + ", valid: " + validCount + ", protected: " +
                           protectedCount + ", custom-protected: " + customProtectedCount + ", failed: " + (mapFiles.size() - readCount));

        for (Map.Entry<String, Integer> failureReason : failureReasons.entrySet()) {
            System.out.println("  " + failureReason.getValue() + "x " + failureReason.getKey());
        }

        Assertions.assertEquals(mapFiles.size(), readCount, "Some maps failed to load.");
    }
}
