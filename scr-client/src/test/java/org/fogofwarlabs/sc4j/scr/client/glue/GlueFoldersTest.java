package org.fogofwarlabs.sc4j.scr.client.glue;

import org.fogofwarlabs.sc4j.engine.net.lobby.MapFileMatch;
import org.fogofwarlabs.sc4j.engine.net.lobby.MapFolders;
import org.fogofwarlabs.sc4j.scr.client.configuration.InstallLayout;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.net.lan.LanMapTransfer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Verifies the map folders a LAN client gets from the glue folders: only the install root's {@code maps} folder, with
 * downloads in its {@code Download} subfolder, and never the {@code Maps} folder of a StarCraft: Remastered installation.
 */
final class GlueFoldersTest {
    private static final @NotNull Path TEST_OUTPUT = Path.of("build", "test-output", "glue-folders");
    private static final int MAP_SIZE = 1000;
    private static final long MAP_SEED = 5;
    private static final @NotNull String MAP_FOLDER = "maps";
    private static final @NotNull String DOWNLOAD_FOLDER = "Download";
    private static final @NotNull String MAP_FILE = "(2)Map.scm";

    private static void deleteFolder(@NotNull Path folder) throws IOException {
        List<Path> paths;

        try (Stream<Path> walk = Files.walk(folder)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }

        for (Path path : paths) {
            Files.delete(path);
        }
    }


    @Test
    void get_lan_map_folders_should_search_only_the_install_root_maps_folder() throws IOException {
        Path root = Files.createTempDirectory(Files.createDirectories(TEST_OUTPUT), "root").toAbsolutePath();

        try {
            InstallLayout layout = InstallLayout.of(root);
            GlueFolders folders = new GlueFolders(layout.getCharacterDirectory(),
                                                  layout.getMapDirectory(),
                                                  layout.getReplayDirectory(),
                                                  layout.getSaveDirectory(),
                                                  layout.getRecentMapsFile());
            MapFolders lanFolders = folders.getLanMapFolders();

            Assertions.assertEquals(List.of(root.resolve(MAP_FOLDER)), lanFolders.getReadFolders());
            Assertions.assertEquals(root.resolve(MAP_FOLDER).resolve(DOWNLOAD_FOLDER), lanFolders.getSaveFolder());

            byte[] contents = new byte[MAP_SIZE];

            new Random(MAP_SEED).nextBytes(contents);

            MapFileMatch match = LanMapTransfer.createFileMatch(contents.length, LanMapTransfer.calculateChecksum(contents, contents.length));
            Path installation = layout.getDefaultDataDirectory();

            Files.createDirectories(installation.resolve(ExternalInput.MAPS_FOLDER));
            Files.writeString(installation.resolve(".build.info"), "Branch!STRING:0\n");
            Files.write(installation.resolve(ExternalInput.MAPS_FOLDER).resolve(MAP_FILE), contents);

            Assertions.assertNull(lanFolders.findFile(match), "the installation's Maps folder is not searched");

            Path copy = Files.write(Files.createDirectories(root.resolve(MAP_FOLDER).resolve(DOWNLOAD_FOLDER)).resolve(MAP_FILE), contents);

            Assertions.assertEquals(copy, lanFolders.findFile(match));
        } finally {
            deleteFolder(root);
        }
    }
}
