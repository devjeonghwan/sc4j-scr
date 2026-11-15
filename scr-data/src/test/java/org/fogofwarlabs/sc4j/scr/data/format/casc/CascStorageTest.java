package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Tests {@link CascStorage} against a real StarCraft: Remastered installation.
 */
@RequiresInstallation
final class CascStorageTest {
    private static final @NotNull String EXPECTED_BUILD_VERSION = "1.23.10.13515";

    private static final int UNITS_DAT_SIZE = 19876;
    private static final int MINIMUM_ROOT_ENTRY_COUNT = 1000;

    /**
     * The installation storage, or {@code null} before setup succeeds.
     */
    private static @Nullable CascStorage storage;

    @BeforeAll
    static void openStorage() throws IOException {
        storage = CascStorage.open(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeStorage() {
        if (storage != null) {
            storage.close();
        }
    }

    private static void assertContentKey(@NotNull String fileName) throws IOException {
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        byte[] contentKey = activeStorage.findContentKey(fileName);

        Assertions.assertNotNull(contentKey, "The file does not exist: " + fileName);

        byte[] content = activeStorage.readFile(fileName);

        Assertions.assertEquals(HexFormat.of().formatHex(contentKey), calculateMd5Hex(content), "The content key mismatches: " + fileName);
    }

    private static @NotNull String calculateMd5Hex(byte @NotNull [] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            return Assertions.fail(exception);
        }
    }


    @Test
    void get_build_version_should_return_installed_build() {
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        Assertions.assertEquals(EXPECTED_BUILD_VERSION, activeStorage.getBuildVersion());
    }

    @Test
    void get_file_names_should_list_root_entries() {
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        List<String> fileNames = activeStorage.getFileNames();

        Assertions.assertTrue(fileNames.size() > MINIMUM_ROOT_ENTRY_COUNT, "The root file holds too few entries: " + fileNames.size());
    }

    @Test
    void read_file_should_read_units_dat_with_expected_size() throws IOException {
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        byte[] unitsDat = activeStorage.readFile("arr/units.dat");

        // The size of arr/units.dat in the 1.23.10.13515 build.
        Assertions.assertEquals(UNITS_DAT_SIZE, unitsDat.length);
    }

    @Test
    void read_file_should_accept_backslash_and_any_case() throws IOException {
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        byte[] slashContent = activeStorage.readFile("arr/units.dat");
        byte[] backslashContent = activeStorage.readFile("ARR\\UNITS.DAT");

        Assertions.assertArrayEquals(slashContent, backslashContent);
    }

    @Test
    void read_file_should_return_content_matching_content_key() throws IOException {
        assertContentKey("arr/units.dat");
        assertContentKey("arr/weapons.dat");
        assertContentKey("scripts/iscript.bin");
    }
}
