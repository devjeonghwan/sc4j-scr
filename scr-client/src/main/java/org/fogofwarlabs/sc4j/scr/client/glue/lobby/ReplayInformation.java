package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeader;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads replay header metadata and named players for the replay information panel.
 */
public final class ReplayInformation {
    /**
     * Stores the name and race of one replay player.
     */
    public static final class Player {
        private final @NotNull String name;
        private final int race;

        /**
         * Creates a {@link Player} instance from replay header metadata.
         *
         * @param name the player name
         * @param race the replay race byte
         */
        private Player(@NotNull String name, int race) {
            this.name = name;
            this.race = race;
        }

        /**
         * @return the player name
         */
        public @NotNull String getName() {
            return this.name;
        }

        /**
         * @return the race byte ({@code 0} Zerg, {@code 1} Terran, {@code 2} Protoss, {@code 6} Random)
         */
        public int getRace() {
            return this.race;
        }
    }

    /**
     * Creates a {@link ReplayInformation} instance from a replay file's header.
     *
     * @param file the {@code .rep} file
     * @return the created {@link ReplayInformation} instance
     * @throws IOException if the file is not a replay
     */
    public static @NotNull ReplayInformation read(@NotNull Path file) throws IOException {
        ReplayHeader header = ReplayFile.parse(Files.readAllBytes(file)).getHeader();
        List<Player> players = new ArrayList<>();

        for (ReplayHeader.ReplayPlayer player : header.getPlayers()) {
            if (player.getType() != PlayerTypes.NONE && !player.getName().isEmpty()) {
                players.add(new Player(player.getName(), player.getRace()));
            }
        }

        String name = file.getFileName().toString();

        return new ReplayInformation(name, header, players);
    }

    private final @NotNull String fileName;
    private final @NotNull ReplayHeader header;
    private final @NotNull List<Player> players;

    /**
     * Creates a {@link ReplayInformation} instance with an immutable player list.
     *
     * @param fileName the displayed file name
     * @param header   the parsed replay header
     * @param players  the named players
     */
    private ReplayInformation(@NotNull String fileName, @NotNull ReplayHeader header, @NotNull List<Player> players) {
        this.fileName = fileName;
        this.header   = header;
        this.players  = List.copyOf(players);
    }

    /**
     * @return the game name of the header
     */
    public @NotNull String getGameName() {
        return this.header.getTitle();
    }

    /**
     * @return the map title of the header
     */
    public @NotNull String getMapTitle() {
        return this.header.getMap();
    }

    /**
     * @return the replay game type identifier
     */
    public int getGameType() {
        return this.header.getType();
    }

    /**
     * @return the game creation time in seconds since the Unix epoch
     */
    public long getCreationTime() {
        return this.header.getRandomSeed();
    }

    /**
     * @return the length in game frames
     */
    public long getFrames() {
        return this.header.getFrames();
    }

    /**
     * @return the file name
     */
    public @NotNull String getFileName() {
        return this.fileName;
    }

    /**
     * @return the players with a name
     */
    public @NotNull List<Player> getPlayers() {
        return this.players;
    }
}
