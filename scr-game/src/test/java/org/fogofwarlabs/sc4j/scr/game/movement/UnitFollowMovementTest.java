package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
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
 * Checks how {@link UnitFollowMovement} follows, retargets and leaves its order target.
 */
@RequiresInstallation
final class UnitFollowMovementTest {
    /**
     * The player who owns the followed Command Center.
     */
    private static final int ENEMY_PLAYER = 1;
    /**
     * Half a hit point, with eight fraction bits.
     */
    private static final int HALF_HIT_POINT = 0x80;
    /**
     * One hit point, with eight fraction bits.
     */
    private static final int HIT_POINT = 0x100;
    /**
     * Cooldown timers whose order timer is zero, so the Follow update runs.
     */
    private static final int ORDER_TIMER_ELAPSED = 0x120000;
    /**
     * The order timer byte of the cooldown timers.
     */
    private static final int ORDER_TIMER_MASK = 0xFF;
    /**
     * The Follow order state after the first update.
     */
    private static final int FOLLOWING_ORDER_STATE = 1;
    /**
     * The sprite visibility flags of a target that player {@code 0} sees.
     */
    private static final int PLAYER_ZERO_VISIBILITY = 1;
    /**
     * The divisor that halves the Command Center hit points.
     */
    private static final int HALF_DIVISOR = 2;
    /**
     * The order and script instruction budget of the queued Move issues and the follow updates.
     */
    private static final int INSTRUCTION_LIMIT = 1000;

    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int UNIT_COUNT = 2;
    private static final int ORDER_CAPACITY = 16;
    private static final int UNIT_POSITION = 100;
    private static final int FIXED_POSITION_SHIFT = 8;
    private static final int PRIMARY_ORDER_COUNTDOWN = 8;
    private static final int DISTANT_TARGET_X = 700;
    private static final int MOVED_TARGET_X = 800;
    private static final int NEAR_DESTINATION_X = 110;
    private static final int TARGET_POOL_INDEX = 2;
    private static final int FACING_WAYPOINT_X = 123;
    private static final int FACING_WAYPOINT_Y = 124;
    private static final int QUEUED_MOVE_X = 140;
    private static final int QUEUED_MOVE_Y = 150;

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
        game.getPlayerTable().setPlayer(0, new Player(0, 0, PlayerTypes.HUMAN, 0, 0));
        game.getPlayerTable().setPlayerRelations(new PlayerRelations(new int[PlayerTable.PLAYER_SLOTS], false, false, false));

        for (int poolIndex = 1; poolIndex <= UNIT_COUNT; poolIndex++) {
            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit unit = unitPool.getUnit(poolIndex);

            unit.setUnitId(ScrUnitIds.ZERG_OVERLORD);
            unit.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.FLYING | UnitStatusFlags.CAN_MOVE);
            unit.setPrimaryOrder(ScrOrderIds.FOLLOW);
            unit.setPositionX(UNIT_POSITION);
            unit.setPositionY(UNIT_POSITION);
            unit.setFixedPositionX(UNIT_POSITION << FIXED_POSITION_SHIFT);
            unit.setFixedPositionY(UNIT_POSITION << FIXED_POSITION_SHIFT);
            unit.setMovementControl(ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT);

            Sprite sprite = SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0);
            SpriteImage image = SpriteScenario.createImage(0, 0, 0);

            image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
            sprite.setMainImage(image);
            unit.setSprite(sprite);
        }

        return game;
    }


    @Test
    void update_should_keep_follow_and_stop_near_unqueued_mobile_target() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);
        Unit target = unitPool.getUnit(TARGET_POOL_INDEX);

        unit.setOrderTargetUnit(target);
        unit.setPrimaryOrderCountdown(PRIMARY_ORDER_COUNTDOWN);
        unit.setCooldownTimers(ORDER_TIMER_ELAPSED);

        game.getUnitFollowMovement().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(ScrOrderIds.FOLLOW, unit.getPrimaryOrder());
        Assertions.assertEquals(FOLLOWING_ORDER_STATE, unit.getPrimaryOrderState());
        Assertions.assertEquals(0x120007, unit.getCooldownTimers());
        Assertions.assertEquals(PRIMARY_ORDER_COUNTDOWN, unit.getPrimaryOrderCountdown());
        Assertions.assertEquals(UNIT_POSITION, unit.getMovementDestinationX());
        Assertions.assertEquals(UNIT_POSITION, unit.getDirectionTargetX());
        Assertions.assertSame(target, unit.getOrderTargetUnit());
    }

    @Test
    void update_should_retarget_visible_distant_unit_without_changing_facing_waypoint() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);
        Unit target = unitPool.getUnit(TARGET_POOL_INDEX);
        Sprite targetSprite = target.getSpriteOrNull();

        Assertions.assertNotNull(targetSprite);

        targetSprite.setX(DISTANT_TARGET_X);
        targetSprite.setVisibilityFlags(PLAYER_ZERO_VISIBILITY);
        unit.setOrderTargetUnit(target);
        unit.setPrimaryOrderCountdown(PRIMARY_ORDER_COUNTDOWN);
        unit.setCooldownTimers(ORDER_TIMER_ELAPSED);
        unit.setPrimaryOrderState(FOLLOWING_ORDER_STATE);
        unit.setDirectionTargetX(FACING_WAYPOINT_X);
        unit.setDirectionTargetY(FACING_WAYPOINT_Y);

        game.getUnitFollowMovement().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(DISTANT_TARGET_X, unit.getMovementDestinationX());
        Assertions.assertEquals(FACING_WAYPOINT_X, unit.getDirectionTargetX());
        Assertions.assertEquals(FACING_WAYPOINT_Y, unit.getDirectionTargetY());
        Assertions.assertEquals(UnitPositioning.DESTINATION_COUNTDOWN, unit.getMovementDestinationCountdown());

        unit.setCooldownTimers(unit.getCooldownTimers() & ~ORDER_TIMER_MASK);
        targetSprite.setX(MOVED_TARGET_X);

        game.getUnitFollowMovement().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(DISTANT_TARGET_X, unit.getMovementDestinationX());
    }

    @Test
    void update_should_advance_queued_movement_after_target_disappears() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);

        game.getOrders().issue(unit,
                               ScrOrderIds.MOVE,
                               null,
                               QUEUED_MOVE_X, QUEUED_MOVE_Y,
                               ScrUnitIds.NONE,
                               true,
                               INSTRUCTION_LIMIT);

        game.getUnitFollowMovement().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(ScrOrderIds.MOVE, unit.getPrimaryOrder());
        Assertions.assertEquals(QUEUED_MOVE_X, unit.getOrderTargetX());
        Assertions.assertNull(unit.getFirstOrder());
    }

    @Test
    void update_should_finish_unseen_distant_target_only_after_reaching_last_destination() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);
        Unit target = unitPool.getUnit(TARGET_POOL_INDEX);
        Sprite targetSprite = target.getSpriteOrNull();

        Assertions.assertNotNull(targetSprite);

        targetSprite.setX(DISTANT_TARGET_X);
        targetSprite.setVisibilityFlags(0);
        unit.setOrderTargetUnit(target);
        unit.setPrimaryOrderState(FOLLOWING_ORDER_STATE);
        unit.setMovementDestinationX(NEAR_DESTINATION_X);
        unit.setMovementDestinationY(UNIT_POSITION);
        game.getOrders().issue(unit,
                               ScrOrderIds.MOVE,
                               null,
                               QUEUED_MOVE_X, QUEUED_MOVE_Y,
                               ScrUnitIds.NONE,
                               true,
                               INSTRUCTION_LIMIT);

        game.getUnitFollowMovement().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(ScrOrderIds.FOLLOW, unit.getPrimaryOrder());
        Assertions.assertNotNull(unit.getFirstOrder());

        unit.setCooldownTimers(0);
        unit.setMovementDestinationX(UNIT_POSITION);

        game.getUnitFollowMovement().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(ScrOrderIds.MOVE, unit.getPrimaryOrder());
        Assertions.assertNull(unit.getFirstOrder());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void update_should_guard_a_queen_against_a_command_center_below_half_of_its_rounded_up_hit_points() {
        Game game = createState();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);
        Assertions.assertNotNull(gameData);

        Unit queen = unitPool.getUnit(1);
        Unit commandCenter = unitPool.getUnit(TARGET_POOL_INDEX);
        int halfHitPoints = gameData.getUnitTypes().getHitPoints(ScrUnitIds.TERRAN_COMMAND_CENTER) / HALF_DIVISOR;

        game.getPlayerTable().setPlayer(ENEMY_PLAYER, new Player(ENEMY_PLAYER, ENEMY_PLAYER, PlayerTypes.HUMAN, 0, 0));
        queen.setUnitId(ScrUnitIds.ZERG_QUEEN);
        queen.setOrderTargetUnit(commandCenter);
        queen.setPrimaryOrderState(FOLLOWING_ORDER_STATE);
        queen.setCooldownTimers(ORDER_TIMER_ELAPSED);
        commandCenter.setUnitId(ScrUnitIds.TERRAN_COMMAND_CENTER);
        commandCenter.setOwnerIndex(ENEMY_PLAYER);
        commandCenter.setStatus(UnitStatusFlags.COMPLETED);
        // Half a point below half rounds up to exactly half: the Command Center cannot be infested yet.
        commandCenter.setHitPoints(halfHitPoints - HALF_HIT_POINT);

        game.getUnitFollowMovement().update(queen, INSTRUCTION_LIMIT);

        Assertions.assertEquals(ScrOrderIds.FOLLOW, queen.getPrimaryOrder());

        queen.setCooldownTimers(ORDER_TIMER_ELAPSED);
        commandCenter.setHitPoints(halfHitPoints - HALF_HIT_POINT - HIT_POINT);

        game.getUnitFollowMovement().update(queen, INSTRUCTION_LIMIT);

        Assertions.assertEquals(ScrOrderIds.GUARD, queen.getPrimaryOrder());
    }
}
