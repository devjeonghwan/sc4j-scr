package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.engine.save.SaveFormat;
import org.fogofwarlabs.sc4j.engine.save.SaveSummary;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.GameStructureView;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Describes the saved games of StarCraft: Remastered of one kind ({@link SaveGameKind}), the SC:R mod's {@link SaveFormat}: {@link SaveGameFile}
 * reads them, {@link SaveGameUtils} writes and restores them with the native bytes, and the network saved games of a LAN lobby are the files of
 * the multiplayer kinds. The SC:R mod saves no engine saved game: its saved games stay the native files.
 * <p>
 * The four kinds share one file layout, so {@link #isSave} and {@link #readSummary} accept a file of any kind; the kind names the file name
 * extension the format lists and saves.
 */
public final class ScrSaveFormat implements SaveFormat {
    /**
     * The name of the format for people.
     */
    public static final @NotNull String NAME = "StarCraft: Remastered saved game";

    private final @NotNull SaveGameKind kind;

    /**
     * Creates a {@link ScrSaveFormat} instance for the saved games of one kind.
     *
     * @param kind the kind whose file name extension the format lists and saves
     */
    public ScrSaveFormat(@NotNull SaveGameKind kind) {
        this.kind = kind;
    }

    /**
     * @return the kind whose file name extension the format lists and saves
     */
    public @NotNull SaveGameKind getKind() {
        return this.kind;
    }

    /**
     * @return the name of the format for people: {@value #NAME}
     */
    @Override
    public @NotNull String getName() {
        return NAME;
    }

    /**
     * @return the file name extension of the format's saved games, without the dot: the extension of the kind, such as {@code mlx}
     */
    @Override
    public @NotNull String getFileExtension() {
        return this.kind.getExtension().substring(1);
    }


    /**
     * Tests whether file contents start like a saved game of this format: with a saved-game banner and the format mark
     * ({@link SaveGameFile#isNativeFile}); the contents may still be damaged.
     *
     * @param contents the file contents
     * @return {@code true} if the contents start like a saved game of this format, {@code false} otherwise
     */
    @Override
    public boolean isSave(byte @NotNull [] contents) {
        return SaveGameFile.isNativeFile(contents);
    }

    /**
     * Reads what a load list shows of a saved game: the scenario title and the human players of its banner, and the frame of its game
     * structure.
     *
     * @param contents the file contents
     * @return the created {@link SaveSummary} instance
     * @throws IOException if the contents are no readable saved game
     */
    @Override
    public @NotNull SaveSummary readSummary(byte @NotNull [] contents) throws IOException {
        SaveGameFile file = SaveGameFile.parse(contents);
        SaveGameDescription description = file.getDescription();
        int frame = ByteBuffer.wrap(file.getGameStructure()).order(ByteOrder.LITTLE_ENDIAN).getInt(GameStructureView.FRAME_OFFSET);

        return new SaveSummary(description.getScenarioTitle(), Integer.toUnsignedLong(frame), description.getPlayers().size());
    }
}
