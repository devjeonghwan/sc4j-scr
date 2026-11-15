package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitPositioning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks how {@link UnitPointMovement} moves toward and stops at a point or unit target.
 */
@RequiresInstallation
final class UnitPointMovementTest {
    private static final int MAP_DIMENSION = 8;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int UNIT_COUNT = 2;
    private static final int ORDER_CAPACITY = 16;
    private static final int UNIT_POSITION = 100;
    private static final int TOP_POSITION_Y = 10;
    private static final int FIXED_POSITION_SHIFT = 8;
    /**
     * Half a pixel, with eight fraction bits.
     */
    private static final int HALF_PIXEL = 128;
    private static final int NEXT_SPEED = 1024;
    private static final int ACCELERATION = 128;
    private static final int INSTRUCTION_LIMIT = 1000;

    private static final int TARGET_POOL_INDEX = 2;
    private static final int EDGE_TARGET_X = 255;
    private static final int EDGE_TARGET_Y = 255;
    private static final int DESTINATION_COUNTDOWN = 7;
    private static final int QUEUED_MOVE_X = 140;
    private static final int QUEUED_MOVE_Y = 150;
    private static final int TARGET_SPRITE_X = 180;
    private static final int TARGET_SPRITE_Y = 170;
    private static final int ORDER_TARGET_X = 20;
    private static final int ORDER_TARGET_Y = 30;

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
        game.initializeOrderPool(ORDER_CAPACITY);

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        for (int poolIndex = 1; poolIndex <= UNIT_COUNT; poolIndex++) {
            Unit unit = unitPool.getUnit(poolIndex);

            unit.setUnitId(ScrUnitIds.TERRAN_MARINE);
            unit.setPrimaryOrder(ScrOrderIds.MOVE);

            Sprite sprite = SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0);
            SpriteImage image = SpriteScenario.createImage(0, 0, 0);

            image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
            sprite.setMainImage(image);
            unit.setSprite(sprite);
        }

        return game;
    }


    @Test
    void update_should_clamp_point_destination_but_keep_original_facing_target() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);
        Unit oldTarget = unitPool.getUnit(TARGET_POOL_INDEX);

        unit.setMovementTargetUnit(oldTarget);
        unit.setStatus(UnitStatusFlags.IMMOVABLE);
        unit.setMovementFlags(MovementFlags.BRAKING);
        unit.setOrderTargetX(EDGE_TARGET_X);
        unit.setOrderTargetY(EDGE_TARGET_Y);

        game.getUnitPointMovement().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(1, unit.getPrimaryOrderState());
        Assertions.assertNotNull(gameData);

        Assertions.assertEquals(255 - gameData.getUnits().get("Unit Size Right", ScrUnitIds.TERRAN_MARINE), unit.getMovementDestinationX());
        Assertions.assertEquals(223 - gameData.getUnits().get("Unit Size Down", ScrUnitIds.TERRAN_MARINE), unit.getMovementDestinationY());
        Assertions.assertEquals(EDGE_TARGET_X, unit.getDirectionTargetX());
        Assertions.assertEquals(EDGE_TARGET_Y, unit.getDirectionTargetY());
        Assertions.assertNull(unit.getMovementTargetUnit());
        Assertions.assertEquals(UnitPositioning.DESTINATION_COUNTDOWN, unit.getMovementDestinationCountdown());
        Assertions.assertEquals(0, unit.getStatus() & UnitStatusFlags.IMMOVABLE);
        Assertions.assertEquals(MovementFlags.ORDERED, unit.getMovementFlags() & (MovementFlags.ORDERED | MovementFlags.BRAKING));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void update_should_wait_for_arrival_before_activating_queued_order() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);

        unit.setOrderTargetX(UNIT_POSITION);
        unit.setOrderTargetY(UNIT_POSITION);
        unit.setMovementDestinationX(UNIT_POSITION);
        unit.setMovementDestinationY(UNIT_POSITION);
        unit.setMovementDestinationCountdown(DESTINATION_COUNTDOWN);
        game.getOrders().issue(unit,
                               ScrOrderIds.MOVE,
                               null,
                               QUEUED_MOVE_X, QUEUED_MOVE_Y,
                               ScrUnitIds.NONE,
                               true,
                               INSTRUCTION_LIMIT);

        game.getUnitPointMovement().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(1, unit.getPrimaryOrderState());
        Assertions.assertEquals(DESTINATION_COUNTDOWN, unit.getMovementDestinationCountdown());
        Assertions.assertNotNull(unit.getFirstOrder());

        game.getUnitPointMovement().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(0, unit.getPrimaryOrderState());
        Assertions.assertEquals(QUEUED_MOVE_X, unit.getOrderTargetX());
        Assertions.assertEquals(QUEUED_MOVE_Y, unit.getOrderTargetY());
        Assertions.assertNull(unit.getFirstOrder());
    }

    @Test
    void update_should_follow_unit_target_without_replacing_order_target_position() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);
        Unit target = unitPool.getUnit(TARGET_POOL_INDEX);
        Sprite targetSprite = target.getSpriteOrNull();

        Assertions.assertNotNull(targetSprite);

        targetSprite.setX(TARGET_SPRITE_X);
        targetSprite.setY(TARGET_SPRITE_Y);
        unit.setOrderTargetUnit(target);
        unit.setOrderTargetX(ORDER_TARGET_X);
        unit.setOrderTargetY(ORDER_TARGET_Y);

        game.getUnitPointMovement().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertSame(target, unit.getMovementTargetUnit());
        Assertions.assertEquals(TARGET_SPRITE_X, unit.getMovementDestinationX());
        Assertions.assertEquals(TARGET_SPRITE_Y, unit.getDirectionTargetY());
        Assertions.assertEquals(ORDER_TARGET_X, unit.getOrderTargetX());
        Assertions.assertEquals(ORDER_TARGET_Y, unit.getOrderTargetY());
    }

    @Test
    void stop_should_brake_from_fractional_position_along_next_velocity_direction() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);

        unit.setPositionX(UNIT_POSITION);
        unit.setPositionY(UNIT_POSITION);
        unit.setFixedPositionX((UNIT_POSITION << FIXED_POSITION_SHIFT) + HALF_PIXEL);
        unit.setFixedPositionY(UNIT_POSITION << FIXED_POSITION_SHIFT);
        unit.setMovementControl(ScrFlingyTypeView.MOVEMENT_CONTROL_FLINGY);
        unit.setMovementFlags(MovementFlags.ACCELERATING | MovementFlags.MOVING | MovementFlags.PASSING_THROUGH | MovementFlags.BRAKE_ON_PATH_STEP);
        unit.setPathCollisionFlags(UnitInitializer.ILLEGAL_POSITION_PATH_FLAG);
        unit.setNextSpeed(NEXT_SPEED);
        unit.setAcceleration(ACCELERATION);
        unit.setTargetHeading(FlingyMotionUtils.DIRECTION_QUARTER_TURN);
        unit.setMovementTargetUnit(unitPool.getUnit(TARGET_POOL_INDEX));

        game.getUnitPointMovement().stop(unit);

        Assertions.assertEquals(116, unit.getMovementDestinationX());
        Assertions.assertEquals(UNIT_POSITION, unit.getMovementDestinationY());
        Assertions.assertEquals(116, unit.getMovementWaypointX());
        Assertions.assertNull(unit.getMovementTargetUnit());
        Assertions.assertEquals(6, unit.getMovementFlags());
        Assertions.assertEquals(6, unit.getPathCollisionFlags());
        Assertions.assertEquals(UnitPositioning.DESTINATION_COUNTDOWN, unit.getMovementDestinationCountdown());
        Assertions.assertEquals(NEXT_SPEED, unit.getNextSpeed());
        Assertions.assertEquals((UNIT_POSITION << FIXED_POSITION_SHIFT) + HALF_PIXEL, unit.getFixedPositionX());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void stop_should_clamp_negative_braking_destination_at_top_instead_of_wrapping() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);

        unit.setPositionX(UNIT_POSITION);
        unit.setPositionY(TOP_POSITION_Y);

        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        sprite.setY(TOP_POSITION_Y);
        unit.setFixedPositionX(UNIT_POSITION << FIXED_POSITION_SHIFT);
        unit.setFixedPositionY(TOP_POSITION_Y << FIXED_POSITION_SHIFT);
        unit.setMovementControl(ScrFlingyTypeView.MOVEMENT_CONTROL_FLINGY);
        unit.setMovementFlags(MovementFlags.ACCELERATING);
        unit.setNextSpeed(NEXT_SPEED);
        unit.setAcceleration(ACCELERATION);
        unit.setTargetHeading(0);

        game.getUnitPointMovement().stop(unit);

        Assertions.assertNotNull(gameData);

        Assertions.assertEquals(gameData.getUnits().get("Unit Size Up", ScrUnitIds.TERRAN_MARINE), unit.getMovementDestinationY());
        Assertions.assertEquals(unit.getMovementDestinationY(), unit.getMovementWaypointY());
        Assertions.assertEquals(6, unit.getMovementFlags());
    }
}
