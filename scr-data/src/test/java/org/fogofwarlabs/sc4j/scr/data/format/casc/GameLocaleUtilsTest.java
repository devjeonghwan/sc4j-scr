package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Tests the installed locale detection and the locale selection of {@link GameLocaleUtils}.
 */
final class GameLocaleUtilsTest {
    @Test
    void read_installed_locale_should_prefer_non_english_text_tag_and_default_to_en_us() throws IOException {
        Path directory = Files.createTempDirectory("sc4j-build-info");

        try {
            Files.writeString(directory.resolve(".build.info"),
                              "Branch!STRING:0|Tags!STRING:0\nkr|Windows enUS speech?:Windows enUS text?:Windows koKR speech?:Windows koKR text?\n");

            Assertions.assertEquals("koKR", GameLocaleUtils.readInstalledLocale(directory));

            Files.writeString(directory.resolve(".build.info"), "Branch!STRING:0|Tags!STRING:0\nus|Windows enUS speech?:Windows enUS text?\n");

            Assertions.assertEquals("enUS", GameLocaleUtils.readInstalledLocale(directory));

            Files.delete(directory.resolve(".build.info"));

            Assertions.assertEquals("enUS", GameLocaleUtils.readInstalledLocale(directory));
        } finally {
            Files.deleteIfExists(directory.resolve(".build.info"));
            Files.delete(directory);
        }
    }

    @Test
    void select_installed_should_keep_an_installed_text_locale_and_fall_back_to_en_us() throws IOException {
        Path directory = Files.createTempDirectory("sc4j-build-info");

        try {
            Files.writeString(directory.resolve(".build.info"),
                              "Branch!STRING:0|Tags!STRING:0\nkr|Windows enUS speech?:Windows enUS text?:Windows koKR speech?:Windows koKR text?\n");

            Assertions.assertEquals(Set.of("enUS", "koKR"), GameLocaleUtils.readTextLocales(directory));
            Assertions.assertEquals("koKR", GameLocaleUtils.selectInstalled("kokr", directory));
            Assertions.assertEquals("enUS", GameLocaleUtils.selectInstalled("enUS", directory));
            Assertions.assertEquals("enUS", GameLocaleUtils.selectInstalled("deDE", directory));
        } finally {
            Files.deleteIfExists(directory.resolve(".build.info"));
            Files.delete(directory);
        }
    }

    @RequiresInstallation
    @Test
    void select_should_return_requested_installed_locale_and_fall_back_to_installed_one() throws IOException {
        Path directory = ExternalInput.INSTALLATION.getPath();

        try (CascStorage storage = CascStorage.open(directory)) {
            Set<String> available = GameLocaleUtils.getAvailableLocales(storage);

            Assertions.assertTrue(available.contains("enUS"), "Available: " + available);

            for (String locale : available) {
                Assertions.assertEquals(locale, GameLocaleUtils.select(locale.toLowerCase(Locale.ROOT), directory, storage));
            }

            String installed = GameLocaleUtils.select(null, directory, storage);

            Assertions.assertEquals(installed, GameLocaleUtils.select("xxYY", directory, storage));
        }
    }
}
