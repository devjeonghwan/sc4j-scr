package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.replay.SelectionRequest;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

/**
 * Creates the marine-filled game and replaces its selection for the tests of the control-group and history hotkey records.
 */
final class ControlGroupScenario {
    private static final int UNIT_POOL_CAPACITY = 16;
    private static final int MAP_DIMENSION = 8;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int MAP_TILESET = 0;
    private static final int RANDOM_SEED = 1;
    private static final int UNIT_POSITION = 100;
    private static final int LOCAL_PLAYER_INDEX = 8;

    /**
     * Creates a {@link Game} instance on an empty map whose whole unit pool holds guarding marines of player {@code 0}.
     *
     * @param gameData the installed game data
     * @return the created {@link Game} instance
     */
    static @NotNull Game createState(@Nullable GameData gameData) {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, MAP_TILESET, new int[MAP_TILE_COUNT]),
                                new LcgRandom(RANDOM_SEED));

        game.getUnitSystem().initializeUnitPool(UNIT_POOL_CAPACITY);

        UnitPool selectionPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(selectionPool);

        game.setSelection(new UnitSelection(selectionPool, false, 0, LOCAL_PLAYER_INDEX));

        for (int poolIndex = 1; poolIndex <= UNIT_POOL_CAPACITY; poolIndex++) {
            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit unit = unitPool.getUnit(poolIndex);

            unit.setUnitId(ScrUnitIds.TERRAN_MARINE);
            unit.setOwnerIndex(0);
            unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);
            unit.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0));
        }

        return game;
    }

    /**
     * Replaces the selection of player {@code 0} with the units at the supplied pool indices.
     *
     * @param game        the game whose selection is replaced
     * @param poolIndices the pool indices of the units to select
     */
    static void selectUnits(@NotNull Game game, int @NotNull ... poolIndices) {
        long[] handles = new long[poolIndices.length];

        for (int position = 0; position < poolIndices.length; position++) {
            UnitPool handlePool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(handlePool);

            handles[position] = Integer.toUnsignedLong(handlePool.getHandle(poolIndices[position]));
        }

        UnitSelection selectionToExecute = game.getSelection();

        Assertions.assertNotNull(selectionToExecute);

        selectionToExecute.execute(game, new SelectionRequest(SelectionRequest.Operation.REPLACE, Short.BYTES, handles), 0);
    }


    /**
     * Creates a {@link ControlGroupScenario} instance.
     */
    private ControlGroupScenario() {
    }
}
