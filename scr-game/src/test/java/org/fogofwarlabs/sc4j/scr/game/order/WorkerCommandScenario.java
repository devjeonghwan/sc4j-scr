package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.replay.SelectionRequest;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStorageConstructor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

/**
 * Builds the worker command world of the command tests: a map of {@code 32} by {@code 32} tiles, one human player, a completed worker of that
 * player that guards its position and is selected, and a Mineral Field beside it.
 */
public final class WorkerCommandScenario {
    /**
     * The horizontal and vertical position of the worker.
     */
    public static final int WORKER_POSITION = 200;
    /**
     * The pool index of the worker.
     */
    public static final int WORKER_POOL_INDEX = 1;
    /**
     * The pool index of the Mineral Field.
     */
    public static final int RESOURCE_POOL_INDEX = 2;

    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int RANDOM_SEED = 1;
    private static final int UNIT_CAPACITY = 2;
    private static final int SPRITE_CAPACITY = 4;
    private static final int IMAGE_CAPACITY = 32;
    private static final int ORDER_CAPACITY = 100;
    private static final int LOCAL_PLAYER_INDEX = 8;
    private static final int INSTRUCTION_LIMIT = 10000;

    private static final int RESOURCE_POSITION = 320;
    private static final int RESOURCE_ELEVATION = 11;

    /**
     * Creates a {@link Game} instance of the worker command world: the worker of player {@code 0} in pool slot {@code 1} at
     * {@link #WORKER_POSITION}, selected by player {@code 0}, and the Mineral Field sprite in pool slot {@code 2}.
     *
     * @param gameData   the installed data
     * @param workerType the unit type of the worker
     * @return the created {@link Game} instance
     */
    public static @NotNull Game createGame(@NotNull GameData gameData, int workerType) {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(SPRITE_CAPACITY);
        game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);
        game.initializeOrderPool(ORDER_CAPACITY);
        game.initializeExecutionScope();
        game.getPlayerTable().setUnitAccounting(PlayerProgressTestUtils.createUnitAccounting());
        game.getPlayerTable().setPlayerUpgrades(PlayerProgressTestUtils.createPlayerUpgrades(true));
        game.getPlayerTable().setPlayerRelations(new PlayerRelations(new int[PlayerTable.PLAYER_SLOTS], false, false, false));
        game.getPlayerTable().setPlayer(0, new Player(0, 0, PlayerTypes.HUMAN, 0, 0));

        UnitPool workerPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(workerPool);

        Unit worker = workerPool.getUnit(WORKER_POOL_INDEX);

        UnitStorageConstructor.clearBeforeInnerInitialization(worker);

        Assertions.assertTrue(game.getUnitInitializer().initialize(worker, workerType, WORKER_POSITION, WORKER_POSITION, 0, INSTRUCTION_LIMIT));

        worker.setStatus(worker.getStatus() | UnitStatusFlags.COMPLETED);
        worker.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);

        UnitPool resourceUnitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(resourceUnitPool);

        Unit resource = resourceUnitPool.getUnit(RESOURCE_POOL_INDEX);

        resource.setUnitId(ScrUnitIds.RESOURCE_MINERAL_FIELD);
        resource.setSprite(SpriteScenario.createSprite(0, RESOURCE_POSITION, RESOURCE_POSITION, RESOURCE_ELEVATION));

        UnitSelection selection = new UnitSelection(workerPool, false, 0, LOCAL_PLAYER_INDEX);

        game.setSelection(selection);
        selection.execute(game,
                          new SelectionRequest(SelectionRequest.Operation.REPLACE,
                                               Short.BYTES,
                                               new long[]{Integer.toUnsignedLong(workerPool.getHandle(WORKER_POOL_INDEX))}),
                          0);

        return game;
    }


    /**
     * Creates a {@link WorkerCommandScenario} instance.
     */
    private WorkerCommandScenario() {
    }
}
