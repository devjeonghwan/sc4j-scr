package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.engine.replay.ReplayFormat;
import org.fogofwarlabs.sc4j.engine.replay.ReplaySummary;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeader;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Describes the {@code .rep} replay files of StarCraft: Remastered, the SC:R mod's {@link ReplayFormat}: {@link ReplayFile} reads them,
 * {@link org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayWriter} writes them (the live game's replay,
 * {@link org.fogofwarlabs.sc4j.scr.game.session.LiveGame#writeReplay}) with the native bytes, and the replay runtime plays them back with the
 * native command dispatcher. The SC:R mod records no engine replay: its replays stay the native files.
 */
public final class ScrReplayFormat implements ReplayFormat {
    /**
     * The name of the format for people.
     */
    public static final @NotNull String NAME = "StarCraft: Remastered replay";
    /**
     * The file name extension of the format's replay files, lower case without the dot.
     */
    public static final @NotNull String FILE_EXTENSION = "rep";

    /**
     * Creates a {@link ScrReplayFormat} instance.
     */
    public ScrReplayFormat() {
    }

    /**
     * @return the name of the format for people: {@value #NAME}
     */
    @Override
    public @NotNull String getName() {
        return NAME;
    }

    /**
     * @return the file name extension of the format's replay files, without the dot: {@value #FILE_EXTENSION}
     */
    @Override
    public @NotNull String getFileExtension() {
        return FILE_EXTENSION;
    }


    /**
     * Tests whether file contents start like a replay of this format: with the replay ID section of a {@code .rep} file
     * ({@link ReplayFile#isReplay}); the contents may still be damaged.
     *
     * @param contents the file contents
     * @return {@code true} if the contents start like a replay of this format, {@code false} otherwise
     */
    @Override
    public boolean isReplay(byte @NotNull [] contents) {
        return ReplayFile.isReplay(contents);
    }

    /**
     * Reads what a replay list shows of a replay: the map title, the length in game frames and the players with a name of its header.
     *
     * @param contents the file contents
     * @return the created {@link ReplaySummary} instance
     * @throws IOException if the contents are no readable {@code .rep} file
     */
    @Override
    public @NotNull ReplaySummary readSummary(byte @NotNull [] contents) throws IOException {
        ReplayHeader header = ReplayFile.parse(contents).getHeader();
        int playerCount = 0;

        for (ReplayHeader.ReplayPlayer player : header.getPlayers()) {
            if (player.getType() != PlayerTypes.NONE && !player.getName().isEmpty()) {
                playerCount++;
            }
        }

        return new ReplaySummary(header.getMap(), header.getFrames(), playerCount);
    }
}
