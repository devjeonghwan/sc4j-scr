package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.game.state.GameContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The file a game is loaded from: the replay file of replay playback, the map file of a live game.
 * <p>
 * The game start keeps its path in the game context ({@link GameContext#setMapArchive}), from which the EUD emulation
 * builds its Storm archive list. The path is absolute and normalized, in UTF-8, the encoding the game's file layer converts
 * its paths from.
 */
public final class GameSourceFile {
    /**
     * Creates a {@link GameSourceFile} instance by reading the replay or map file a game is loaded from.
     *
     * @param file the replay or map file
     * @return the created {@link GameSourceFile} instance
     * @throws IOException if the file cannot be read
     */
    public static @NotNull GameSourceFile read(@NotNull Path file) throws IOException {
        return create(file, Files.readAllBytes(file));
    }

    /**
     * Creates a {@link GameSourceFile} instance from a file whose contents were already read.
     *
     * @param file     the replay or map file
     * @param contents the file bytes, retained
     * @return the created {@link GameSourceFile} instance
     */
    public static @NotNull GameSourceFile create(@NotNull Path file, byte @NotNull [] contents) {
        return new GameSourceFile(file.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8), contents);
    }


    private final byte @NotNull [] path;
    private final byte @NotNull [] contents;

    private GameSourceFile(byte @NotNull [] path, byte @NotNull [] contents) {
        this.path     = path;
        this.contents = contents;
    }

    /**
     * @return the path bytes, retained
     */
    public byte @NotNull [] getPath() {
        return this.path;
    }

    /**
     * @return the file bytes, retained
     */
    public byte @NotNull [] getContents() {
        return this.contents;
    }
}
