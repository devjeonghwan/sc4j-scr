package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Verifies install paths, data-directory resolution and startup native-library validation.
 */
final class InstallLayoutTest {
    private static final @NotNull String LWJGL_LIBRARY_PATH_PROPERTY = "org.lwjgl.librarypath";
    private static final @NotNull String LWJGL_EXTRACT_PROPERTY = "org.lwjgl.system.SharedLibraryExtractPath";
    private static final @NotNull String JAVACPP_CACHE_PROPERTY = "org.bytedeco.javacpp.cachedir";
    private static final @NotNull String JAVACPP_LINK_PATH_PROPERTY = "org.bytedeco.javacpp.platform.linkpath";
    private static final @NotNull String JAVACPP_PRELOAD_PATH_PROPERTY = "org.bytedeco.javacpp.platform.preloadpath";
    private static final @NotNull String JAVACPP_PATHS_FIRST_PROPERTY = "org.bytedeco.javacpp.pathsFirst";
    private static final @NotNull String JAVACPP_CACHE_LIBRARIES_PROPERTY = "org.bytedeco.javacpp.cacheLibraries";

    private static final @NotNull String @NotNull [] PROCESS_PROPERTIES = {
            LWJGL_LIBRARY_PATH_PROPERTY, LWJGL_EXTRACT_PROPERTY,
            JAVACPP_CACHE_PROPERTY, JAVACPP_LINK_PATH_PROPERTY, JAVACPP_PRELOAD_PATH_PROPERTY,
            JAVACPP_PATHS_FIRST_PROPERTY, JAVACPP_CACHE_LIBRARIES_PROPERTY
    };

    private static final @NotNull String ROOT_PROPERTY = "sc4j.root";
    private static final @NotNull String DEFAULT_SOURCE = "default";

    /**
     * Restores a system property to its value before a test.
     *
     * @param name     the property name
     * @param previous the value before the test, or {@code null} if the property was not set
     */
    private static void restoreProperty(@NotNull String name, @Nullable String previous) {
        if (previous == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previous);
        }
    }

    @Test
    void from_code_location_should_detect_install_root_or_use_working_directory() {
        Path root = Path.of("install").toAbsolutePath();
        Path workingDirectory = Path.of("").toAbsolutePath();

        Assertions.assertEquals(root, InstallLayout.fromCodeLocation(root.resolve("lib").resolve("scr-client-0.1.0-SNAPSHOT.jar")).getRoot());
        Assertions.assertEquals(workingDirectory, InstallLayout.fromCodeLocation(Path.of("build", "classes", "java", "main")).getRoot());
        Assertions.assertEquals(workingDirectory, InstallLayout.fromCodeLocation(root.resolve("other").resolve("client.jar")).getRoot());
    }

    @Test
    void install_layout_names_every_path_under_root() {
        Path root = Path.of("install").toAbsolutePath();
        InstallLayout layout = InstallLayout.of(root);

        Assertions.assertEquals(root.resolve("sc4j-settings.json"), layout.getSettingsFile());
        Assertions.assertEquals(root.resolve("data"), layout.getDefaultDataDirectory());
        Assertions.assertEquals(root.resolve("maps"), layout.getMapDirectory());
        Assertions.assertEquals(root.resolve("replays"), layout.getReplayDirectory());
        Assertions.assertEquals(root.resolve("logs"), layout.getLogDirectory());
        Assertions.assertEquals(root.resolve(".cache"), layout.getCacheDirectory());
        Assertions.assertEquals(root.resolve("lib").resolve("natives"), layout.getNativeLibraryDirectory());
        Assertions.assertEquals(root.resolve(".cache").resolve("natives"), layout.getNativeExtractDirectory());
        Assertions.assertEquals(root.resolve(".cache").resolve("tmp"), layout.getTemporaryDirectory());
        Assertions.assertEquals(root.resolve(".cache").resolve("javacpp"), layout.getJavacppCacheDirectory());
    }

    @Test
    void resolve_data_directory_should_prefer_argument_then_property_then_default() {
        Path root = Path.of("install").toAbsolutePath();
        InstallLayout layout = InstallLayout.of(root);

        Assertions.assertEquals(root.resolve("games").resolve("SC"), layout.resolveDataDirectory("games/SC").getPath());

        Path absolute = Path.of("elsewhere", "StarCraft").toAbsolutePath();

        Assertions.assertEquals(absolute, layout.resolveDataDirectory(absolute.toString()).getPath());

        String previous = System.getProperty(InstallLayout.DATA_PROPERTY);

        try {
            System.clearProperty(InstallLayout.DATA_PROPERTY);

            InstallLayout.DataDirectory fallback = layout.resolveDataDirectory(null);

            Assertions.assertEquals(root.resolve("data"), fallback.getPath());
            Assertions.assertEquals(DEFAULT_SOURCE, fallback.getSource());

            System.setProperty(InstallLayout.DATA_PROPERTY, absolute.toString());

            Assertions.assertEquals(absolute, layout.resolveDataDirectory(null).getPath());
            Assertions.assertEquals(root.resolve("games").resolve("SC"), layout.resolveDataDirectory("games/SC").getPath());
        } finally {
            restoreProperty(InstallLayout.DATA_PROPERTY, previous);
        }
    }

    @Test
    void data_directory_recognizes_data_pack_and_describes_missing_one(@TempDir @NotNull Path root) throws IOException {
        Path data = root.resolve("data");
        InstallLayout.DataDirectory directory = new InstallLayout.DataDirectory(data, DEFAULT_SOURCE);

        Assertions.assertFalse(directory.isPresent());

        String message = directory.describeMissing();

        Assertions.assertTrue(message.contains(data.toAbsolutePath().toString()), message);
        Assertions.assertTrue(message.contains("--data") && message.contains("paths") && message.contains("sc4j-settings.json"), message);

        Files.createDirectories(data.resolve("Data").resolve("data"));
        Files.writeString(data.resolve(".build.info"), "Branch!STRING:0\n");

        Assertions.assertTrue(directory.isPresent());
        Assertions.assertTrue(InstallLayout.isDataDirectory(data.resolve("Data")));
    }

    @Test
    void apply_process_properties_should_validate_required_files_before_configuring_libraries(@TempDir @NotNull Path root) throws IOException {
        InstallLayout layout = InstallLayout.of(root);
        Path nativeDirectory = layout.getNativeLibraryDirectory();
        Path manifest = nativeDirectory.resolve("libraries.list");
        String[] previousProperties = new String[PROCESS_PROPERTIES.length];
        String previousExtractDirectory = System.getProperty(LWJGL_EXTRACT_PROPERTY);
        boolean previousUseCache = ImageIO.getUseCache();

        for (int propertyIndex = 0; propertyIndex < PROCESS_PROPERTIES.length; propertyIndex++) {
            previousProperties[propertyIndex] = System.getProperty(PROCESS_PROPERTIES[propertyIndex]);
        }

        Files.createDirectories(nativeDirectory);

        try {
            System.setProperty(LWJGL_LIBRARY_PATH_PROPERTY, nativeDirectory.toString());

            Files.writeString(manifest, "missing.dll\n");

            Assertions.assertThrows(IOException.class, layout::applyProcessProperties);

            Files.delete(manifest);

            Assertions.assertThrows(IOException.class, layout::applyProcessProperties);

            for (String entry : List.of("", "../outside.dll\n", "nested/../library.dll\n", "C:/outside.dll\n",
                                        "wrong\u0000name.dll\n", "missing.dll\n")) {
                Files.writeString(manifest, entry);

                Assertions.assertThrows(IOException.class, layout::applyProcessProperties, entry);
                Assertions.assertEquals(previousExtractDirectory, System.getProperty(LWJGL_EXTRACT_PROPERTY));
                Assertions.assertEquals(previousUseCache, ImageIO.getUseCache());
            }

            Path library = nativeDirectory.resolve("library.dll");

            Files.write(library, new byte[0]);
            Files.writeString(manifest, library.getFileName() + "\n");
            layout.applyProcessProperties();

            Assertions.assertEquals(nativeDirectory.toString(), System.getProperty(JAVACPP_PRELOAD_PATH_PROPERTY));
            Assertions.assertFalse(ImageIO.getUseCache());
        } finally {
            ImageIO.setUseCache(previousUseCache);

            for (int propertyIndex = PROCESS_PROPERTIES.length - 1; propertyIndex >= 0; propertyIndex--) {
                restoreProperty(PROCESS_PROPERTIES[propertyIndex], previousProperties[propertyIndex]);
            }
        }
    }

    @Test
    void detect_should_prefer_explicit_root_and_normalize_relative_root() {
        String previous = System.getProperty(ROOT_PROPERTY);

        try {
            Path root = Path.of("chosen root", "parent", "..", "실행").toAbsolutePath().normalize();

            System.setProperty(ROOT_PROPERTY, "chosen root/parent/../실행");

            Assertions.assertEquals(root, InstallLayout.detect().getRoot());

            System.setProperty(ROOT_PROPERTY, root.toString());

            Assertions.assertEquals(root, InstallLayout.detect().getRoot());
        } finally {
            restoreProperty(ROOT_PROPERTY, previous);
        }
    }

    @Test
    void detect_should_reject_invalid_explicit_root() {
        String previous = System.getProperty(ROOT_PROPERTY);

        try {
            System.setProperty(ROOT_PROPERTY, "invalid\u0000root");

            Assertions.assertThrows(IllegalArgumentException.class, InstallLayout::detect);
        } finally {
            restoreProperty(ROOT_PROPERTY, previous);
        }
    }

    @Test
    void resolve_data_directory_should_resolve_relative_property_under_root() {
        InstallLayout layout = InstallLayout.of(Path.of("chosen root"));
        String previous = System.getProperty(InstallLayout.DATA_PROPERTY);

        try {
            System.setProperty(InstallLayout.DATA_PROPERTY, "game data/../StarCraft");

            Assertions.assertEquals(layout.getRoot().resolve("StarCraft"), layout.resolveDataDirectory(null).getPath());
        } finally {
            restoreProperty(InstallLayout.DATA_PROPERTY, previous);
        }
    }

    @Test
    void detect_should_keep_working_directory_fallback_when_explicit_root_is_absent_or_blank() {
        String previous = System.getProperty(ROOT_PROPERTY);

        try {
            System.clearProperty(ROOT_PROPERTY);

            Assertions.assertEquals(Path.of("").toAbsolutePath(), InstallLayout.detect().getRoot());

            System.setProperty(ROOT_PROPERTY, "  ");

            Assertions.assertEquals(Path.of("").toAbsolutePath(), InstallLayout.detect().getRoot());
        } finally {
            restoreProperty(ROOT_PROPERTY, previous);
        }
    }

    @Test
    void resolve_user_path_should_normalize_relative_input_and_preserve_absolute_input() {
        InstallLayout layout = InstallLayout.of(Path.of("chosen root"));
        Path absolute = Path.of("outside", "..", "chosen file.rep").toAbsolutePath();

        Assertions.assertEquals(layout.getRoot().resolve("game.rep"), layout.resolveUserPath(Path.of("replays", "..", "game.rep")));
        Assertions.assertEquals(absolute, layout.resolveUserPath(absolute));
    }
}
