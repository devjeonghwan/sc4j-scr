package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/**
 * Checks which source file paths the game context lists as a Storm archive.
 */
final class GameContextTest {
    /**
     * The most path bytes the game context keeps, one less than its path buffer.
     */
    private static final int KEPT_PATH_BYTES = GameContext.MAP_PATH_SIZE - 1;
    /**
     * A character of three UTF-8 bytes.
     */
    private static final @NotNull String HANGUL = "\ud55c";
    private static final int HANGUL_BYTES = 3;
    private static final @NotNull String DRIVE = "C:\\";
    private static final byte @NotNull [] FILE = {1};

    /**
     * Creates a {@link GameContext} instance on a one-tile map without game data.
     *
     * @return the created {@link GameContext} instance
     */
    private static @NotNull GameContext createContext() {
        return new GameContext(null, new GameMap(1, 1, 0, new int[1]));
    }

    @Test
    void has_map_archive_file_should_refuse_a_path_whose_cut_splits_a_character() {
        GameContext context = createContext();

        // The cut keeps the first byte of the last character.
        byte[] split = (DRIVE + "x".repeat(KEPT_PATH_BYTES - DRIVE.length() - 1) + HANGUL).getBytes(StandardCharsets.UTF_8);
        byte[] whole = (DRIVE + "x".repeat(KEPT_PATH_BYTES - DRIVE.length() - HANGUL_BYTES) + HANGUL + "yy").getBytes(StandardCharsets.UTF_8);

        context.setMapArchive(split, FILE);

        Assertions.assertFalse(context.hasMapArchiveFile());

        context.setMapArchive(whole, FILE);

        Assertions.assertTrue(context.hasMapArchiveFile(), "A cut between characters keeps a file name.");
    }

    @Test
    void has_map_archive_file_should_need_a_path_and_the_read_file() {
        GameContext context = createContext();
        byte[] path = "C:\\maps\\a.scx".getBytes(StandardCharsets.UTF_8);

        Assertions.assertFalse(context.hasMapArchiveFile());

        context.setMapArchive(path, null);

        Assertions.assertFalse(context.hasMapArchiveFile());

        context.setMapArchive(new byte[0], FILE);

        Assertions.assertFalse(context.hasMapArchiveFile());

        context.setMapArchive(path, FILE);

        Assertions.assertTrue(context.hasMapArchiveFile());
    }
}
