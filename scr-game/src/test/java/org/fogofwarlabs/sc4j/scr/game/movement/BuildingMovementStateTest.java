package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks how {@link UnitMovement} turns units in the building turret, bunker and turret movement states.
 */
@RequiresInstallation
final class BuildingMovementStateTest {
    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;

    private static final int UNIT_POSITION = 512;
    private static final int DIRECTION_TARGET_X = 544;
    private static final int TURN_RATE = 16;
    private static final int TRANSITION_LIMIT = 4;
    private static final int FRAME_PULSE = 1;

    private static final int TURRET_DESTINATION_X = 600;
    private static final int FIXED_TURRET_DIRECTION = 40;

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

    private static @NotNull Unit createUnit(int movementState) {
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0));
        unit.setPositionX(UNIT_POSITION);
        unit.setPositionY(UNIT_POSITION);
        unit.setMovementDestinationX(UNIT_POSITION);
        unit.setMovementDestinationY(UNIT_POSITION);
        unit.setMovementWaypointX(UNIT_POSITION);
        unit.setMovementWaypointY(UNIT_POSITION);
        unit.setDirectionTargetX(DIRECTION_TARGET_X);
        unit.setDirectionTargetY(UNIT_POSITION);
        unit.setTurnRate(TURN_RATE);
        unit.setMovementState(movementState);

        return unit;
    }


    @Test
    void update_should_turn_building_turret_toward_waypoint_and_keep_vision_refresh() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getPathSystem().setPathPool(new PathPool());

        Unit unit = createUnit(MovementStates.BUILDING_TURRET);
        UnitMovement.Context context = new UnitMovement.Context(0, 0);

        context.setSourceByte(FRAME_PULSE);

        game.getUnitMovement().update(context, unit, TRANSITION_LIMIT);

        Assertions.assertEquals(MovementStates.BUILDING_TURRET, unit.getMovementState());
        Assertions.assertEquals(64, unit.getDesiredMovementDirection(), "The desired direction points toward the waypoint.");
        Assertions.assertEquals(8, unit.getMovementDirection(), "Non-control-2 turning uses half the turn rate.");
        Assertions.assertEquals(8, unit.getTargetHeading());
        Assertions.assertEquals(16, unit.getHeading(), "The heading turns by the full rate towards the desired direction.");
        Assertions.assertEquals(0, unit.getMovementFlags() & MovementFlags.ACCELERATING, "A unit at its move target does not start moving.");
        Assertions.assertEquals(1, context.getResult(), "BldgTurret keeps the vision refresh result.");
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void update_should_turn_bunker_without_vision_refresh() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getPathSystem().setPathPool(new PathPool());

        Unit unit = createUnit(MovementStates.BUNKER);
        UnitMovement.Context context = new UnitMovement.Context(0, 0);

        context.setSourceByte(FRAME_PULSE);

        game.getUnitMovement().update(context, unit, TRANSITION_LIMIT);

        Assertions.assertEquals(MovementStates.BUNKER, unit.getMovementState());
        Assertions.assertEquals(16, unit.getHeading());
        Assertions.assertEquals(0, context.getResult());
    }

    @Test
    void update_should_pin_turret_move_target_and_skip_heading_for_fixed_turret() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getPathSystem().setPathPool(new PathPool());

        Unit unit = createUnit(MovementStates.TURRET);

        unit.setMovementDestinationX(TURRET_DESTINATION_X);
        unit.setMovementFlags(MovementFlags.ORDERED);

        UnitMovement.Context context = new UnitMovement.Context(0, 0);

        context.setSourceByte(FRAME_PULSE);

        game.getUnitMovement().update(context, unit, TRANSITION_LIMIT);

        Assertions.assertEquals(UNIT_POSITION, unit.getMovementDestinationX(), "set_unit_move_target pins the turret to its sprite.");
        Assertions.assertEquals(UNIT_POSITION, unit.getMovementDestinationY());
        Assertions.assertEquals(64, unit.getDesiredMovementDirection());
        Assertions.assertEquals(16, unit.getHeading());
        Assertions.assertEquals(0, context.getResult());

        Unit fixed = createUnit(MovementStates.TURRET);

        fixed.setStatus(UnitStatusFlags.TURRET_FOLLOWS_BASE);
        fixed.setDesiredMovementDirection(FIXED_TURRET_DIRECTION);
        fixed.setMovementFlags(MovementFlags.ORDERED);

        game.getUnitMovement().update(new UnitMovement.Context(0, 0), fixed, TRANSITION_LIMIT);

        Assertions.assertEquals(0, fixed.getHeading(), "Status 0x2000000 skips the heading update.");
        Assertions.assertEquals(FIXED_TURRET_DIRECTION, fixed.getDesiredMovementDirection());
        Assertions.assertEquals(MovementFlags.ORDERED, fixed.getMovementFlags() & MovementFlags.ORDERED,
                                "A direction error beyond ten keeps movement flag 1.");
    }
}
