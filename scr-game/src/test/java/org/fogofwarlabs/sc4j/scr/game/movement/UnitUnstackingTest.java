package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.path.PathPoolAssert;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks how {@link UnitUnstacking} clips segments, finds an escape from a stacked unit and moves it to a legal position.
 */
@RequiresInstallation
final class UnitUnstackingTest {
    private static final int CREATION_FRAME = 77;
    private static final int INSTRUCTION_LIMIT = 512;
    private static final int ESCAPE_RANDOM_CALL = 50;
    private static final int WAIT_ROLL_MAXIMUM = 31;
    private static final int WAIT_ROLL_LIMIT = 24;

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

    private static @NotNull Game createState() throws IOException {
        Assertions.assertNotNull(gameData);

        return WalkableTerrainScenario.createStackedUnitGame(gameData);
    }


    @Test
    void clip_segment_should_clip_crossing_segments_and_reject_segments_outside_one_side() {
        long bounds = UnitUnstacking.packRectangle(10, 20, 30, 40);

        Assertions.assertEquals(UnitUnstacking.packRectangle(10, 30, 30, 30),
                                UnitUnstacking.clipSegment(UnitUnstacking.packRectangle(0, 30, 50, 30), bounds));
        Assertions.assertEquals(UnitUnstacking.packRectangle(20, 20, 20, 40),
                                UnitUnstacking.clipSegment(UnitUnstacking.packRectangle(20, 0, 20, 60), bounds));
        Assertions.assertEquals(Long.MIN_VALUE, UnitUnstacking.clipSegment(UnitUnstacking.packRectangle(0, 19, 50, 19), bounds));
        Assertions.assertEquals(UnitUnstacking.packRectangle(10, 20, 30, 40),
                                UnitUnstacking.clipSegment(UnitUnstacking.packRectangle(0, 10, 40, 50), bounds));
    }

    @Test
    void escape_selects_free_side_reusing_path_storage_then_moves_to_legal() throws IOException {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(WalkableTerrainScenario.UNIT_POOL_INDEX);
        Unit blocker = unitPool.getUnit(WalkableTerrainScenario.BLOCKER_POOL_INDEX);
        UnitUnstacking unstacking = new UnitUnstacking();
        PathPool pathPool = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(pathPool);

        int previousPathIndex = pathPool.allocate();

        unit.setPathIndex(previousPathIndex);
        pathPool.setCreationFrame(previousPathIndex, CREATION_FRAME);

        Assertions.assertTrue(unstacking.findEscape(game, unit, blocker));

        Assertions.assertEquals(previousPathIndex, unit.getPathIndex());
        Assertions.assertEquals(CREATION_FRAME, pathPool.getCreationFrame(previousPathIndex));
        Assertions.assertEquals(1, pathPool.getWaypointCount(previousPathIndex));
        Assertions.assertEquals(1, PathPoolAssert.readRegionCount(pathPool, previousPathIndex));
        Assertions.assertEquals(0, pathPool.getFlags(previousPathIndex));
        Assertions.assertEquals(0, pathPool.getTimer(previousPathIndex));

        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(spatialIndex);
        Assertions.assertNotNull(gameData);

        Assertions.assertNull(spatialIndex.findMovementBlocker(game.getHooks().getCollisionRules(), unit, gameData.getUnitTypes(),
                                                               unit.getMovementDestinationX(), unit.getMovementDestinationY()));
        Assertions.assertEquals(512, unit.getPositionX());
        Assertions.assertEquals(512, unit.getPositionY());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        Assertions.assertEquals(0, unit.getTerrainCollisionLeft());
        Assertions.assertEquals(0, unit.getTerrainCollisionRight());

        unit.setMovementState(MovementStates.MOVE_TO_LEGAL);
        unit.setPathCollisionFlags(UnitInitializer.ILLEGAL_POSITION_PATH_FLAG | UnitInitializer.STOPPED_MOVING_TO_LEGAL_PATH_FLAG);

        Assertions.assertTrue(unstacking.moveToLegal(new UnitMovement.Context(0, 0), game, unit, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MovementStates.AT_REST, unit.getMovementState());
        Assertions.assertEquals(-1, unit.getPathIndex());
        Assertions.assertEquals(previousPathIndex, pathPool.getFirstFreeIndex());
        Assertions.assertEquals(UnitInitializer.ILLEGAL_POSITION_PATH_FLAG, unit.getPathCollisionFlags());
        Assertions.assertEquals(UnitStatusFlags.COLLISION, unit.getStatus() & UnitStatusFlags.COLLISION);
    }

    @Test
    void find_escape_should_wait_for_moving_blocker_with_one_random_draw() throws IOException {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(WalkableTerrainScenario.UNIT_POOL_INDEX);
        Unit blocker = unitPool.getUnit(WalkableTerrainScenario.BLOCKER_POOL_INDEX);

        blocker.setStatus(blocker.getStatus() | UnitStatusFlags.CAN_MOVE);
        blocker.setMovementState(MovementStates.CHECK_ILLEGAL);

        LcgRandom expected = new LcgRandom(0);

        Assertions.assertTrue(expected.next(ESCAPE_RANDOM_CALL, 0, WAIT_ROLL_MAXIMUM) < WAIT_ROLL_LIMIT);

        Assertions.assertFalse(new UnitUnstacking().findEscape(game, unit, blocker));

        Assertions.assertEquals(1, game.getRandom().getCallCount(ESCAPE_RANDOM_CALL));
        Assertions.assertEquals(1, game.getRandom().getTotalRandomCounts());
        Assertions.assertEquals(-1, unit.getPathIndex());
        Assertions.assertEquals(512, unit.getMovementDestinationX());
        Assertions.assertEquals(512, unit.getMovementDestinationY());
    }
}
