package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.engine.save.SaveFormat;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameFile;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameInformation;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameSession;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A multiplayer saved game that a LAN lobby loads: the file, its identity ({@link SaveGameInformation}) and the lobby the
 * saved game started from, resolved again with its seed.
 * <p>
 * The host creates the lobby from its file (the save identifier of the chosen file opens it, the game information, the
 * records and the game structure are pre-loaded and turned into the lobby, {@link LanLobby.Setup#createSaved}); a joiner
 * finds its own file by the advertised save identifier ({@link #find}) and brings its identity to the joined-game command.
 * Every machine then starts the game from its own file.
 */
public final class LanSavedGame {
    private static final @NotNull Logger LOGGER = Logger.of(LanSavedGame.class);

    /**
     * Creates a {@link LanSavedGame} instance from a multiplayer saved game file.
     *
     * @param file the saved game file
     * @return the created {@link LanSavedGame} instance
     * @throws IOException if the file cannot be parsed as a multiplayer saved game
     */
    public static @NotNull LanSavedGame read(@NotNull Path file) throws IOException {
        SaveGameFile saved = SaveGameFile.parse(Files.readAllBytes(file));
        SaveGameInformation information = SaveGameInformation.read(saved);
        SaveGameSession session = SaveGameSession.read(saved);
        MeleeGameSetup setup = session.getSetup();

        if (!setup.isNetwork()) {
            throw Exceptions.newIOException("The saved game {0} is not a network game.", file.getFileName());
        }

        MeleeGameSetup.PlayerRecord[] records = setup.resolve(session.getSeed(), new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]);

        return new LanSavedGame(file, information, setup, records);
    }

    /**
     * Finds the saved game of a save identifier among a folder's files of a save format (the files of its file name extension, each header
     * read and its save identifier compared), in name order.
     *
     * @param directory          the saved game folder
     * @param format             the multiplayer save format of the client's product ({@code mlx} or {@code mlt} files)
     * @param saveGameIdentifier the save identifier
     * @return the saved game, or {@code null} when no readable file has the save identifier
     */
    public static @Nullable LanSavedGame find(@NotNull Path directory, @NotNull SaveFormat format, int saveGameIdentifier) {
        for (Path candidate : list(directory, format)) {
            try {
                SaveGameInformation information = SaveGameInformation.read(Files.readAllBytes(candidate));

                if (information.getSaveGameId() == saveGameIdentifier) {
                    return read(candidate);
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.info("LAN SAVED GAME SKIPPED: " + candidate.getFileName() + ": " + exception.getMessage());
            }
        }

        return null;
    }

    /**
     * Lists a folder's saved games of a save format, the files of its file name extension, in name order, ignoring case.
     *
     * @param directory the saved game folder
     * @param format    the save format
     * @return the files, empty when the folder does not exist
     */
    public static @NotNull List<Path> list(@NotNull Path directory, @NotNull SaveFormat format) {
        List<Path> files = new ArrayList<>();

        if (!Files.isDirectory(directory)) {
            return files;
        }

        String suffix = ("." + format.getFileExtension()).toLowerCase(Locale.ROOT);

        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                Path name = child.getFileName();

                if (name != null && Files.isRegularFile(child) && name.toString().toLowerCase(Locale.ROOT).endsWith(suffix)) {
                    files.add(child);
                }
            }
        } catch (IOException exception) {
            LOGGER.warning("LAN SAVED GAMES UNREADABLE: " + directory + ": " + exception.getMessage());
        }

        files.sort((first, second) -> String.valueOf(first.getFileName()).compareToIgnoreCase(String.valueOf(second.getFileName())));

        return files;
    }


    private final @NotNull Path file;
    private final @NotNull SaveGameInformation information;

    private final @NotNull MeleeGameSetup setup;
    private final MeleeGameSetup.@NotNull PlayerRecord @NotNull [] records;

    private LanSavedGame(@NotNull Path file, @NotNull SaveGameInformation information,
                         @NotNull MeleeGameSetup setup, MeleeGameSetup.@NotNull PlayerRecord @NotNull [] records) {
        this.file        = file;
        this.information = information;

        this.setup   = setup;
        this.records = records;
    }

    /**
     * @return the saved game file
     */
    public @NotNull Path getFile() {
        return this.file;
    }

    /**
     * @return the saved game's identity
     */
    public @NotNull SaveGameInformation getInformation() {
        return this.information;
    }

    /**
     * @return the lobby the saved game started from
     */
    public @NotNull MeleeGameSetup getSetup() {
        return this.setup;
    }

    /**
     * @return the saved game's options, the {@code 32} bytes its template holds
     */
    public @NotNull LanGameOptions getOptions() {
        return LanGameOptions.parse(this.setup.getTemplate().encode());
    }


    /**
     * Creates a {@link LanLobby.Setup} instance for the host's saved game lobby.
     *
     * @param hostName    the host's name
     * @param mapFileName the map file name the game information names
     * @param mapFileSize the map file size, {@code 0} without the file
     * @param mapChecksum the map checksum, {@code 0} without the file
     * @return the created {@link LanLobby.Setup} instance
     */
    public LanLobby.@NotNull Setup createLobbySetup(@NotNull String hostName, @NotNull String mapFileName, int mapFileSize, int mapChecksum) {
        return LanLobby.Setup.createSaved(this.setup.getMap(), this.getOptions(),
                                          hostName,
                                          mapFileName, mapFileSize, mapChecksum,
                                          this.setup,
                                          this.records.clone(),
                                          this.information);
    }
}
