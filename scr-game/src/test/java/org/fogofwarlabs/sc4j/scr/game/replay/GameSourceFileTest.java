package org.fogofwarlabs.sc4j.scr.game.replay;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Checks the path bytes the file a game is loaded from gives the game context.
 */
final class GameSourceFileTest {
    /**
     * A folder name of a Hangul character.
     */
    private static final @NotNull String FOLDER = "\ub9f5";
    /**
     * A map file name of a Hangul character.
     */
    private static final @NotNull String FILE_NAME = "\ud55c.scx";
    private static final @NotNull String PARENT = "..";

    @Test
    void create_should_hold_the_absolute_normalized_path_in_utf8() {
        GameSourceFile source = GameSourceFile.create(Path.of(FOLDER, PARENT, FOLDER, FILE_NAME), new byte[0]);

        Assertions.assertArrayEquals(Path.of(FOLDER, FILE_NAME).toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8), source.getPath());
    }
}
