package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStorageConstructor;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitPositioning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the Terran-versus-computer replay ({@link GameTestResources#REPLAY_TERRAN_VS_COMPUTER}) to an early frame, as a replay or as a live game of
 * its human player, and creates completed units in its running game, inside a command dispatch, for the tests that check one routine on such units.
 * It also counts the turn steps of any game as its session does.
 */
public final class RunningGameScenario {
    /**
     * The computer player of the replay.
     */
    public static final int COMPUTER = 0;
    /**
     * The human player of the replay.
     */
    public static final int HUMAN = 1;
    /**
     * The instruction limit shared by the game tests: the scenario passes it to the replay runtime and to the created and completed units, and the
     * tests pass it to their own runtimes and game calls.
     */
    public static final int INSTRUCTION_LIMIT = 100_000;
    /**
     * The viewport width in pixels shared by the game tests: the scenario passes it to the replay runtime, and the tests pass it to their own
     * runtimes.
     */
    public static final int VIEWPORT_WIDTH = 640;
    /**
     * The viewport height in pixels shared by the game tests, used with {@link #VIEWPORT_WIDTH}.
     */
    public static final int VIEWPORT_HEIGHT = 480;

    private static final int START_FRAME = 200;
    private static final int CREATION_TAG_OWNER_SHIFT = 8;

    /**
     * Starts the replay and runs it to its start frame.
     *
     * @param gameData the installed game data, which must stay open while the runtime is used
     * @return the started replay runtime
     * @throws IOException if the replay cannot be read
     */
    public static @NotNull ReplayRuntime start(@NotNull GameData gameData) throws IOException {
        return run(createRuntime(readReplay(), gameData));
    }

    /**
     * Starts the replay as a live game whose local player is {@link #HUMAN} and runs it to its start frame.
     *
     * @param gameData the installed game data, which must stay open while the runtime is used
     * @return the started live game runtime
     * @throws IOException if the replay cannot be read
     */
    public static @NotNull ReplayRuntime startLiveGame(@NotNull GameData gameData) throws IOException {
        ReplayFile replay = readReplay();
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData);

        return run(ReplayRuntime.createLiveGame(replay, ChkFile.parseTolerant(mapData), gameData, HUMAN,
                                                VIEWPORT_WIDTH, VIEWPORT_HEIGHT, INSTRUCTION_LIMIT));
    }

    /**
     * Creates a {@link ReplayRuntime} instance that plays a replay on the map stored in the replay, with the shared viewport
     * ({@link #VIEWPORT_WIDTH} by {@link #VIEWPORT_HEIGHT}) and the shared {@link #INSTRUCTION_LIMIT}. The runtime is not started.
     *
     * @param replay   the replay, which must contain its map
     * @param gameData the installed game data, which must stay open while the runtime is used
     * @return the created {@link ReplayRuntime} instance
     * @throws IOException if the map of the replay cannot be parsed
     */
    public static @NotNull ReplayRuntime createRuntime(@NotNull ReplayFile replay, @NotNull GameData gameData) throws IOException {
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData, "The replay has no map data.");

        return new ReplayRuntime(replay, ChkFile.parseTolerant(mapData), gameData, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, INSTRUCTION_LIMIT);
    }

    /**
     * Reads the Terran-versus-computer replay.
     *
     * @return the parsed replay
     * @throws IOException if the replay cannot be read
     */
    private static @NotNull ReplayFile readReplay() throws IOException {
        Path path = GameTestResources.getPath(GameTestResources.REPLAY_TERRAN_VS_COMPUTER);

        return ReplayFile.parse(Files.readAllBytes(path));
    }

    /**
     * Starts a runtime and runs it to the start frame.
     *
     * @param runtime the created runtime
     * @return the started runtime
     */
    private static @NotNull ReplayRuntime run(@NotNull ReplayRuntime runtime) {
        runtime.start(0, 0);

        Game game = runtime.getGame();

        while (game.getCurrentFrame() < START_FRAME) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
        }

        return runtime;
    }

    /**
     * Runs the body inside a command dispatch of the runtime's game.
     *
     * @param runtime the started replay runtime
     * @param body    the body to run
     */
    public static void runInCommandDispatch(@Nullable ReplayRuntime runtime, @NotNull Runnable body) {
        Assertions.assertNotNull(runtime);

        GameExecutionScope scope = runtime.getGame().getExecutionScope();

        Assertions.assertNotNull(scope);

        scope.beginCommandDispatch();

        try {
            body.run();
        } finally {
            scope.endCommandDispatch();
        }
    }

    /**
     * Creates a {@link Unit} instance as a completed unit at a position clamped to the map.
     *
     * @param game      the running game
     * @param unitType  the unit type
     * @param positionX the horizontal position
     * @param positionY the vertical position
     * @param owner     the owning player
     * @return the created {@link Unit} instance
     */
    public static @NotNull Unit createUnit(@NotNull Game game, int unitType, int positionX, int positionY, int owner) {
        GameData unitData = game.getGameData();

        Assertions.assertNotNull(unitData);

        ScrUnitTypeView units = unitData.getUnitTypes();
        UnitCreationContext creation = game.getUnitSystem().getUnitCreationContext();

        Assertions.assertNotNull(creation);

        int horizontal = UnitPositioning.clampCoordinate(positionX,
                                                         units.getDimensionLeft(unitType), units.getDimensionRight(unitType),
                                                         game.getMap().getWidth() * TerrainTileset.TILE_SIZE,
                                                         0);
        int vertical = UnitPositioning.clampCoordinate(positionY,
                                                       units.getDimensionUp(unitType), units.getDimensionDown(unitType),
                                                       game.getMap().getHeight() * TerrainTileset.TILE_SIZE,
                                                       UnitPositioning.MAP_BOTTOM_MARGIN);
        UnitStorageConstructor creationConstructor = game.getUnitStorageConstructor();
        UnitAccounting creationAccounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(creationAccounting);

        Unit unit = creationConstructor.create(creation,
                                               creationAccounting,
                                               unitType,
                                               horizontal, vertical,
                                               owner,
                                               owner | owner << CREATION_TAG_OWNER_SHIFT,
                                               INSTRUCTION_LIMIT);

        Assertions.assertNotNull(unit);

        UnitStorageConstructor completionConstructor = game.getUnitStorageConstructor();
        Player completionPlayer = game.getPlayerTable().getPlayer(owner);

        Assertions.assertNotNull(completionPlayer);

        Assertions.assertTrue(completionConstructor.tryComplete(creation, unit, completionPlayer.getType(), INSTRUCTION_LIMIT));

        return unit;
    }

    /**
     * Counts turn steps as the session does, which the selection history stamps.
     *
     * @param game  the game
     * @param turns the number of turn steps
     */
    public static void advanceTurns(@NotNull Game game, int turns) {
        for (int turn = 0; turn < turns; turn++) {
            game.incrementTurnCount();
        }
    }


    /**
     * Creates a {@link RunningGameScenario} instance.
     */
    private RunningGameScenario() {
    }
}
