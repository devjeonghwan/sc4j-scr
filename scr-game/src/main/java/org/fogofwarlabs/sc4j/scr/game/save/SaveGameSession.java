package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeaderBuilder;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayWriter;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.session.setup.SavedGamePlayers;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.GameStructureView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Builds the lobby, seed and local player for a game restored from a saved file.
 * <p>
 * The game starts from the saved header and the scenario built from the saved game ({@link SavedGameStartUtils},
 * {@link MeleeGameSetup#createSaved}) as a new game: the caller supplies the lobby's localized names
 * ({@link MeleeGameSetup#setNationNames}), creates the session with {@link #createLiveGame()}, starts the game and
 * restores it with {@link SaveGameUtils#read}. The started game's command stream begins empty.
 */
public final class SaveGameSession {
    /**
     * Creates a {@link SaveGameSession} instance from the saved header, scenario, pool limits, map path and local player.
     * The saved generator state supplies the seed and continues after the game is restored.
     *
     * @param file the saved game
     * @return the created {@link SaveGameSession} instance, whose live session is created by {@link #createLiveGame()}
     * @throws IOException if the saved game cannot start a game
     */
    public static @NotNull SaveGameSession read(@NotNull SaveGameFile file) throws IOException {
        int seed = file.getRandomState();
        byte[] header = SavedGameStartUtils.buildHeader(file, seed);
        int[] limits = file.getLimits();
        boolean classicLimits = Arrays.equals(limits, ReplayWriter.CLASSIC_LIMITS);
        int recordSlot = file.getRecordSlot();
        int localPlayerId = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                                      .getInt(ReplayHeaderBuilder.PLAYERS_OFFSET + recordSlot * ReplayHeaderBuilder.PLAYER_RECORD_SIZE +
                                              ReplayHeaderBuilder.PLAYER_ID_OFFSET);

        byte[] gameStructure = file.getGameStructure();
        byte[] lobbyTypes = Arrays.copyOfRange(gameStructure,
                                               GameStructureView.PLAYER_TYPES_OFFSET,
                                               GameStructureView.PLAYER_TYPES_OFFSET + PlayerTable.PLAYER_SLOTS);
        byte[] lobbyRaces = Arrays.copyOfRange(gameStructure,
                                               GameStructureView.PLAYER_RACES_OFFSET,
                                               GameStructureView.PLAYER_RACES_OFFSET + PlayerTable.PLAYER_SLOTS);

        MeleeGameSetup setup = MeleeGameSetup.createSaved(SavedGameStartUtils.buildScenario(file), header,
                                                          file.getDescription().isMultiplayer(),
                                                          lobbyTypes, lobbyRaces,
                                                          classicLimits,
                                                          toPath(file.getDescription().getMapPath()));

        if (limits != null && !classicLimits && !Arrays.equals(limits, ReplayWriter.CURRENT_LIMITS)) {
            throw Exceptions.newIOException("The saved game ran with the pool limits {0}, which a game does not start with.",
                                            Arrays.toString(limits));
        }

        return new SaveGameSession(setup, seed, localPlayerId);
    }

    private static @Nullable Path toPath(@NotNull String mapPath) {
        if (mapPath.isEmpty()) {
            return null;
        }

        try {
            return Path.of(mapPath);
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private final @NotNull MeleeGameSetup setup;
    private final int seed;
    private final int localPlayerId;

    private SaveGameSession(@NotNull MeleeGameSetup setup, int seed, int localPlayerId) {
        this.setup         = setup;
        this.seed          = seed;
        this.localPlayerId = localPlayerId;
    }

    /**
     * @return the lobby the game starts from
     */
    public @NotNull MeleeGameSetup getSetup() {
        return this.setup;
    }

    /**
     * @return the game seed
     */
    public int getSeed() {
        return this.seed;
    }

    /**
     * Creates a {@link LiveGame} instance for the saving machine's player.
     *
     * @return the created {@link LiveGame} instance
     * @throws IOException if the lobby cannot be resolved
     */
    public @NotNull LiveGame createLiveGame() throws IOException {
        return this.createLiveGame(null, this.localPlayerId);
    }

    /**
     * Creates a {@link LiveGame} instance for a network saved game loaded into a new lobby.
     * The supplied players take the human slots of the saved records.
     *
     * @param players       the new game's players of the saved slots, or {@code null} to keep the saved ids
     * @param localPlayerId the local player's network id in the new game
     * @return the created {@link LiveGame} instance
     * @throws IOException if the lobby cannot be resolved
     */
    public @NotNull LiveGame createLiveGame(@Nullable SavedGamePlayers players, int localPlayerId) throws IOException {
        return new LiveGame(this.setup, this.seed, localPlayerId, players);
    }
}
