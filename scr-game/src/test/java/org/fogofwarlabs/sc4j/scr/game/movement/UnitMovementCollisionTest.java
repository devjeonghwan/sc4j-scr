package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.path.UnitPath;
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
 * Checks how {@link UnitMovementCollision} resolves a collision with the unit recorded on the moving unit's path.
 */
@RequiresInstallation
final class UnitMovementCollisionTest {
    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int UNIT_COUNT = 2;
    private static final int UNIT_POSITION = 100;
    private static final int BLOCKER_X = 140;
    private static final int DISTANT_DESTINATION_X = 400;
    private static final int WAYPOINT_X = 200;
    private static final int MOVING_POOL_INDEX = 1;
    private static final int BLOCKER_POOL_INDEX = 2;
    private static final int UNIT_SPEED = 512;
    private static final int INSTRUCTION_LIMIT = 10000;
    /**
     * A handle bit that makes the recorded collision handle refer to a stale unit.
     */
    private static final int STALE_HANDLE_BIT = 0x800;
    /**
     * The saturated path recalculation countdown.
     */
    private static final int SATURATED_PATH_COUNTDOWN = 255;

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

    private static @NotNull Game createState() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(1));

        game.getUnitSystem().initializeUnitPool(UNIT_COUNT);
        game.getPathSystem().setPathPool(new PathPool());
        game.getPathSystem().setUnitPath(new UnitPath());

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        for (int index = 1; index <= UNIT_COUNT; index++) {
            Unit unit = unitPool.getUnit(index);

            unit.setUnitId(ScrUnitIds.ZERG_DRONE);
            unit.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUND_UNIT);
            unit.setPathCollisionFlags(UnitInitializer.GROUND_ELEVATION_PATH_FLAG);
            unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);
            unit.setMovementControl(ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT);
            unit.setPositionX(index == MOVING_POOL_INDEX ? UNIT_POSITION : BLOCKER_X);
            unit.setPositionY(UNIT_POSITION);
            unit.setSprite(SpriteScenario.createSprite(0, unit.getPositionX(), unit.getPositionY(), 0));
        }

        Unit unit = unitPool.getUnit(MOVING_POOL_INDEX);
        PathPool pathPool = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(pathPool);

        unit.setPathIndex(pathPool.allocate());
        unit.setMovementState(MovementStates.FIX_COLLISION);
        unit.setUserActionFlags(Unit.USER_ACTION_COMMANDED);
        unit.setNextSpeed(UNIT_SPEED);
        unit.setCurrentSpeed(UNIT_SPEED);
        unit.setVelocityX(UNIT_SPEED);

        pathPool.setLastCollisionHandle(unit.getPathIndex(), unitPool.getHandle(BLOCKER_POOL_INDEX));

        return game;
    }


    @Test
    void fix_should_stop_at_shared_point_destination_and_keep_collision_handle() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(MOVING_POOL_INDEX);
        Unit blocker = unitPool.getUnit(BLOCKER_POOL_INDEX);

        unit.setMovementDestinationX(BLOCKER_X);
        unit.setMovementDestinationY(UNIT_POSITION);
        blocker.setMovementDestinationX(BLOCKER_X);
        blocker.setMovementDestinationY(UNIT_POSITION);

        Assertions.assertFalse(game.getUnitMovementCollision().fix(unit, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MovementStates.AT_MOVE_TARGET, unit.getMovementState());
        Assertions.assertEquals(UNIT_POSITION, unit.getMovementDestinationX());
        Assertions.assertEquals(UNIT_POSITION, unit.getMovementDestinationY());
        Assertions.assertEquals(UnitStatusFlags.IMMOVABLE, unit.getStatus() & UnitStatusFlags.IMMOVABLE);
        Assertions.assertEquals(0, unit.getNextSpeed());
        Assertions.assertEquals(0, unit.getCurrentSpeed());
        Assertions.assertEquals(0, unit.getVelocityX());
        Assertions.assertEquals(0, unit.getUserActionFlags() & Unit.USER_ACTION_COMMANDED);

        int expectedCollisionHandle = unitPool.getHandle(BLOCKER_POOL_INDEX);
        PathPool pathPool = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(pathPool);

        Assertions.assertEquals(expectedCollisionHandle, pathPool.getLastCollisionHandle(unit.getPathIndex()));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void fix_should_wait_for_departing_blocker_and_saturate_path_countdown() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(MOVING_POOL_INDEX);
        Unit blocker = unitPool.getUnit(BLOCKER_POOL_INDEX);

        unit.setMovementDestinationX(DISTANT_DESTINATION_X);
        unit.setMovementDestinationY(UNIT_POSITION);
        unit.setMovementWaypointX(WAYPOINT_X);
        unit.setMovementWaypointY(UNIT_POSITION);
        unit.setPathRecalculationCountdown(SATURATED_PATH_COUNTDOWN);
        blocker.setMovementFlags(MovementFlags.ACCELERATING);
        blocker.setTargetHeading(FlingyMotionUtils.DIRECTION_QUARTER_TURN);

        Assertions.assertFalse(game.getUnitMovementCollision().fix(unit, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MovementStates.WAIT_FREE, unit.getMovementState());
        Assertions.assertEquals(SATURATED_PATH_COUNTDOWN, unit.getPathRecalculationCountdown());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void fix_should_return_to_follow_path_when_collision_handle_is_stale() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(MOVING_POOL_INDEX);

        unit.setMovementDestinationX(DISTANT_DESTINATION_X);
        unit.setMovementDestinationY(UNIT_POSITION);

        PathPool pathPool = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(pathPool);

        pathPool.setLastCollisionHandle(unit.getPathIndex(), unitPool.getHandle(BLOCKER_POOL_INDEX) ^ STALE_HANDLE_BIT);

        Assertions.assertFalse(game.getUnitMovementCollision().fix(unit, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MovementStates.FOLLOW_PATH, unit.getMovementState());
        Assertions.assertEquals(1, unit.getPathRecalculationCountdown());
    }
}
