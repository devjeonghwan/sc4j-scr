package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.SelectionRequest;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the formation positions that {@link UnitGroupMovement} assigns to selected units.
 */
@RequiresInstallation
final class UnitGroupMovementTest {
    private static final int MAP_DIMENSION = 32;
    private static final int UNIT_COUNT = 2;
    private static final int UNIT_POSITION = 200;
    private static final int TIGHT_SECOND_UNIT_X = 264;
    private static final int WIDE_SECOND_UNIT_X = 400;
    private static final int FIRST_POOL_INDEX = 1;
    private static final int SECOND_POOL_INDEX = 2;

    private static final int TARGET_X = 600;
    private static final int TARGET_Y = 500;
    private static final int INSIDE_TARGET_X = 300;
    private static final int INSIDE_TARGET_Y = 200;
    private static final int CORNER_TARGET_X = 1023;
    private static final int CORNER_TARGET_Y = 1023;

    private static final int SELECTION_HANDLE_WIDTH = 2;
    private static final int SELECTION_LOCAL_PLAYER = 8;
    private static final int WORD_MASK = 0xFFFF;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    private static @NotNull Game createState(int secondX, boolean collisionEnabled) throws IOException {
        GameMap map = WalkableTerrainScenario.createWalkableMap(MAP_DIMENSION);
        Game game = Game.create(gameData, map, new LcgRandom(1));

        game.getUnitSystem().initializeUnitPool(UNIT_COUNT);
        game.getPathSystem().setTerrainContours(WalkableTerrainScenario.createWalkableContours(map));

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        for (int index = 1; index <= UNIT_COUNT; index++) {
            Unit unit = unitPool.getUnit(index);

            unit.setUnitId(ScrUnitIds.ZERG_DRONE);
            unit.setOwnerIndex(0);
            unit.setStatus(UnitStatusFlags.COMPLETED);
            unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);
            unit.setPathCollisionFlags(collisionEnabled ? UnitInitializer.GROUND_ELEVATION_PATH_FLAG : 0);
            unit.setSprite(SpriteScenario.createSprite(0, index == FIRST_POOL_INDEX ? UNIT_POSITION : secondX, UNIT_POSITION, 0));
        }

        UnitSelection selection = new UnitSelection(unitPool, false, 0, SELECTION_LOCAL_PLAYER);

        game.setSelection(selection);
        selection.execute(game,
                          new SelectionRequest(SelectionRequest.Operation.REPLACE,
                                               SELECTION_HANDLE_WIDTH,
                                               new long[]{unitPool.getHandle(FIRST_POOL_INDEX), unitPool.getHandle(SECOND_POOL_INDEX)}),
                          0);

        Assertions.assertSame(unitPool.getUnit(SECOND_POOL_INDEX), selection.getSelectedUnit(0, 1));

        return game;
    }


    @Test
    void calculate_position_should_translate_tight_formation_without_changing_units_or_random() throws IOException {
        Game game = createState(TIGHT_SECOND_UNIT_X, true);
        long randomCount = game.getRandom().getTotalRandomCounts();
        UnitGroupMovement group = new UnitGroupMovement(game, 0, TARGET_X, TARGET_Y, true);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Assertions.assertEquals(568 | 500 << Short.SIZE, group.calculatePosition(game, unitPool.getUnit(FIRST_POOL_INDEX), TARGET_X, TARGET_Y));
        Assertions.assertEquals(632 | 500 << Short.SIZE, group.calculatePosition(game, unitPool.getUnit(SECOND_POOL_INDEX), TARGET_X, TARGET_Y));

        Sprite originalSprite = unitPool.getUnit(FIRST_POOL_INDEX).getSpriteOrNull();

        Assertions.assertNotNull(originalSprite);

        Assertions.assertEquals(UNIT_POSITION, originalSprite.getX());
        Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void calculate_position_should_compress_to_32_pixels_when_target_is_inside_selection() throws IOException {
        Game game = createState(WIDE_SECOND_UNIT_X, true);
        UnitGroupMovement group = new UnitGroupMovement(game, 0, INSIDE_TARGET_X, INSIDE_TARGET_Y, true);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Assertions.assertEquals(268 | 200 << Short.SIZE,
                                group.calculatePosition(game, unitPool.getUnit(FIRST_POOL_INDEX), INSIDE_TARGET_X, INSIDE_TARGET_Y));
        Assertions.assertEquals(332 | 200 << Short.SIZE,
                                group.calculatePosition(game, unitPool.getUnit(SECOND_POOL_INDEX), INSIDE_TARGET_X, INSIDE_TARGET_Y));
    }

    @Test
    void calculate_position_should_use_different_span_thresholds_for_ground_and_air_formations() throws IOException {
        Game ground = createState(WIDE_SECOND_UNIT_X, true);
        UnitGroupMovement groundGroup = new UnitGroupMovement(ground, 0, TARGET_X, TARGET_Y, true);
        UnitPool unitPool = ground.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Assertions.assertEquals(600 | 500 << Short.SIZE,
                                groundGroup.calculatePosition(ground, unitPool.getUnit(FIRST_POOL_INDEX), TARGET_X, TARGET_Y));

        Game air = createState(WIDE_SECOND_UNIT_X, false);
        UnitGroupMovement airGroup = new UnitGroupMovement(air, 0, TARGET_X, TARGET_Y, true);
        UnitPool airUnitPool = air.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(airUnitPool);

        Assertions.assertEquals(500 | 500 << Short.SIZE, airGroup.calculatePosition(air, airUnitPool.getUnit(FIRST_POOL_INDEX), TARGET_X, TARGET_Y));
    }

    @Test
    void calculate_position_should_ignore_dead_units_and_clamp_above_bottom_ui_row() throws IOException {
        Game game = createState(TIGHT_SECOND_UNIT_X, false);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit dead = unitPool.getUnit(SECOND_POOL_INDEX);

        dead.setPrimaryOrder(ScrOrderIds.DIE);
        dead.setPrimaryOrderState(Unit.DIE_STATE_DESTROYED);

        UnitGroupMovement group = new UnitGroupMovement(game, 0, CORNER_TARGET_X, CORNER_TARGET_Y, true);
        int position = group.calculatePosition(game, unitPool.getUnit(FIRST_POOL_INDEX), CORNER_TARGET_X, CORNER_TARGET_Y);

        Assertions.assertNotNull(gameData);

        Assertions.assertEquals(1023 - gameData.getUnits().get("Unit Size Right", ScrUnitIds.ZERG_DRONE), position & WORD_MASK);
        Assertions.assertEquals(991 - gameData.getUnits().get("Unit Size Down", ScrUnitIds.ZERG_DRONE), position >>> Short.SIZE);
    }
}
