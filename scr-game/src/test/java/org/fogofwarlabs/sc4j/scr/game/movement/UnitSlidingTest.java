package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks how {@link UnitSliding} prepares, frees and ends the sliding movement states.
 */
@RequiresInstallation
final class UnitSlidingTest {
    private static final int MAP_DIMENSION = 16;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int UNIT_POSITION = 100;
    private static final int DESTINATION_X = 200;
    private static final int FIXED_POSITION_SHIFT = 8;
    private static final int ESCAPE_SPEED = 700;
    private static final int INSTRUCTION_LIMIT = 100;
    private static final int SATURATED_PATH_COUNTDOWN = 255;
    /**
     * The path ages below and at the age from which a zero-speed slide no longer continues on its path.
     */
    private static final int YOUNG_PATH_AGE = 149;
    private static final int OLD_PATH_AGE = 150;

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
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);
        game.getPathSystem().setPathPool(new PathPool());

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);

        unit.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0));
        unit.setPositionX(UNIT_POSITION);
        unit.setPositionY(UNIT_POSITION);
        unit.setFixedPositionX(UNIT_POSITION << FIXED_POSITION_SHIFT);
        unit.setFixedPositionY(UNIT_POSITION << FIXED_POSITION_SHIFT);
        unit.setMovementDestinationX(DESTINATION_X);
        unit.setMovementDestinationY(UNIT_POSITION);
        unit.setMovementWaypointX(DESTINATION_X);
        unit.setMovementWaypointY(UNIT_POSITION);

        PathPool pathPool = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(pathPool);

        int pathIndex = pathPool.allocate();

        unit.setPathIndex(pathIndex);
        pathPool.setCreationFrame(pathIndex, 0);

        return game;
    }


    @Test
    void update_should_reduce_inertial_speed_on_slide_preparation_and_recompute_velocity() {
        int[] speeds = {768, 512, 511, 257, 256, 128};
        int[] expected = {384, 256, 256, 256, 256, 128};

        for (int index = 0; index < speeds.length; index++) {
            Game game = createState();
            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit unit = unitPool.getUnit(1);

            unit.setMovementState(MovementStates.SLIDE_PREP);
            unit.setMovementControl(ScrFlingyTypeView.MOVEMENT_CONTROL_FLINGY);
            unit.setTargetHeading(FlingyMotionUtils.DIRECTION_QUARTER_TURN);

            PathPool pathPool = game.getPathSystem().getPathPool();

            Assertions.assertNotNull(pathPool);

            pathPool.setSavedSpeed(unit.getPathIndex(), speeds[index]);

            Assertions.assertTrue(game.getUnitSliding().update(new UnitMovement.Context(0, 0), unit, INSTRUCTION_LIMIT));

            Assertions.assertEquals(MovementStates.SLIDE_FREE, unit.getMovementState());
            Assertions.assertEquals(expected[index], pathPool.getSavedSpeed(unit.getPathIndex()));
            Assertions.assertEquals(expected[index], unit.getCurrentSpeed());
            Assertions.assertEquals(expected[index], unit.getNextSpeed());
            Assertions.assertEquals(expected[index], unit.getVelocityX());
            Assertions.assertEquals(0, unit.getVelocityY());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void update_should_release_path_and_clear_velocity_on_forced_escape_unless_arrived() {
        for (boolean arrived : new boolean[]{false, true}) {
            Game game = createState();
            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit unit = unitPool.getUnit(1);
            int pathIndex = unit.getPathIndex();

            unit.setMovementState(MovementStates.FORCE_MOVE_FREE);
            unit.setNextSpeed(ESCAPE_SPEED);
            FlingyMotionUtils.setDirection(unit, FlingyMotionUtils.DIRECTION_QUARTER_TURN);
            FlingyMotionUtils.setSpeed(unit, ESCAPE_SPEED);

            if (arrived) {
                unit.setMovementDestinationX(UNIT_POSITION);
            }

            Assertions.assertEquals(!arrived, game.getUnitSliding().update(new UnitMovement.Context(0, 0), unit, INSTRUCTION_LIMIT));

            Assertions.assertEquals(arrived ? MovementStates.AT_MOVE_TARGET : MovementStates.TURN_AND_START, unit.getMovementState());
            Assertions.assertEquals(arrived ? pathIndex : -1, unit.getPathIndex());
            Assertions.assertEquals(arrived ? ESCAPE_SPEED : 0, unit.getNextSpeed());
            Assertions.assertEquals(arrived ? ESCAPE_SPEED : 0, unit.getVelocityX());
            Assertions.assertEquals(1, unit.getPathRecalculationCountdown());

            if (!arrived) {
                PathPool pathPool = game.getPathSystem().getPathPool();

                Assertions.assertNotNull(pathPool);

                Assertions.assertEquals(pathIndex, pathPool.getFirstFreeIndex());
            }
        }
    }

    @Test
    void update_should_saturate_counter_and_respect_path_age_boundary_at_zero_speed() {
        for (int age : new int[]{YOUNG_PATH_AGE, OLD_PATH_AGE}) {
            Game game = createState();
            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit unit = unitPool.getUnit(1);

            unit.setMovementState(MovementStates.SLIDE_FREE);
            unit.setMovementControl(ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT);
            unit.setPathRecalculationCountdown(SATURATED_PATH_COUNTDOWN);
            game.setCurrentFrame(age);

            Assertions.assertFalse(game.getUnitSliding().update(new UnitMovement.Context(0, 0), unit, INSTRUCTION_LIMIT));

            Assertions.assertEquals(age == YOUNG_PATH_AGE ? MovementStates.FOLLOW_PATH : MovementStates.TURN_AND_START, unit.getMovementState());
            Assertions.assertEquals(SATURATED_PATH_COUNTDOWN, unit.getPathRecalculationCountdown());
            Assertions.assertEquals(UNIT_POSITION, unit.getPositionX());
            Assertions.assertEquals(UNIT_POSITION, unit.getPositionY());
            Assertions.assertEquals(MovementFlags.ORDERED, unit.getMovementFlags() & MovementFlags.ORDERED);
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }
    }
}
