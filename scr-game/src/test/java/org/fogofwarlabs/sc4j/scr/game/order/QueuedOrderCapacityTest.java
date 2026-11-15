package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayGameVersion;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool.Order;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.GameRulesVersion;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks queued-order capacity and replay-dependent reset-collision insertion.
 */
@RequiresInstallation
final class QueuedOrderCapacityTest {
    /**
     * The highlighted Move orders that a test unit queues, the capacity of the highlighted queue.
     */
    private static final int QUEUED_ORDER_COUNT = 8;
    private static final int ORDER_CAPACITY = 100;
    /**
     * The target position of the replacing order.
     */
    private static final int TARGET_POSITION = 10;
    /**
     * The build number of the old replay of kind {@code 5}.
     */
    private static final int OLD_REPLAY_BUILD = 0x1FD8;
    private static final int OLD_REPLAY_KIND = 5;

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

    private static @NotNull Game createGame(@NotNull GameRulesVersion rules) {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.initializeOrderPool(ORDER_CAPACITY);
        game.setRulesVersion(rules);

        return game;
    }

    /**
     * Creates a {@link Unit} instance with the given current order and eight queued highlighted Move orders.
     *
     * @param game         the game whose order pool holds the queued orders
     * @param currentOrder the current order of the unit
     * @return the created {@link Unit} instance
     */
    private static @NotNull Unit createUnit(@NotNull Game game, int currentOrder) {
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setPrimaryOrder(currentOrder);

        for (int index = 0; index < QUEUED_ORDER_COUNT; index++) {
            game.getOrders().queueBack(unit, ScrOrderIds.MOVE, null, index, index, ScrUnitIds.NONE);
        }

        Assertions.assertEquals(QUEUED_ORDER_COUNT, unit.getHighlightedOrderCount());

        return unit;
    }

    private static int countOrders(@NotNull Unit unit, int orderId) {
        int count = 0;

        for (Order order = unit.getFirstOrder(); order != null; order = order.getNext()) {
            if (order.getOrderId() == orderId) {
                count++;
            }
        }

        return count;
    }


    @Test
    void queue_replacing_same_orders_should_drop_orders_beyond_eight_in_new_game() {
        GameRulesVersion fixtureRules = GameRulesVersion.newGame();
        Game game = createGame(fixtureRules);
        ScrOrderSystem assignment = game.getOrders();
        Unit unit = createUnit(game, ScrOrderIds.MOVE);

        assignment.queueReplacingSameOrders(unit, ScrOrderIds.PLAYER_GUARD, null, TARGET_POSITION, TARGET_POSITION, ScrUnitIds.NONE);

        Assertions.assertEquals(0, countOrders(unit, ScrOrderIds.PLAYER_GUARD));
        Assertions.assertEquals(QUEUED_ORDER_COUNT, countOrders(unit, ScrOrderIds.MOVE));
    }

    @Test
    void queue_replacing_same_orders_should_append_beyond_eight_in_legacy_replay() {
        GameRulesVersion fixtureRules = GameRulesVersion.forReplay(ReplayGameVersion.absent(), true);
        Game game = createGame(fixtureRules);
        ScrOrderSystem assignment = game.getOrders();
        Unit unit = createUnit(game, ScrOrderIds.MOVE);

        assignment.queueReplacingSameOrders(unit, ScrOrderIds.PLAYER_GUARD, null, TARGET_POSITION, TARGET_POSITION, ScrUnitIds.NONE);

        Assertions.assertEquals(1, countOrders(unit, ScrOrderIds.PLAYER_GUARD));
    }

    @Test
    void queue_replacing_same_orders_should_queue_reset_collision_once_when_constructing() {
        GameRulesVersion fixtureRules = GameRulesVersion.newGame();
        Game game = createGame(fixtureRules);
        ScrOrderSystem assignment = game.getOrders();
        Unit unit = createUnit(game, ScrOrderIds.CONSTRUCTING_BUILDING);

        assignment.queueReplacingSameOrders(unit, ScrOrderIds.RESET_COLLISION, null, TARGET_POSITION, TARGET_POSITION, ScrUnitIds.NONE);

        Assertions.assertEquals(1, countOrders(unit, ScrOrderIds.RESET_COLLISION));
    }

    @Test
    void queue_replacing_same_orders_should_queue_reset_collision_twice_in_old_kind_five_replay() {
        GameRulesVersion fixtureRules = GameRulesVersion.forReplay(ReplayGameVersion.of(OLD_REPLAY_BUILD, OLD_REPLAY_KIND, 0), false);
        Game game = createGame(fixtureRules);
        ScrOrderSystem assignment = game.getOrders();
        Unit unit = createUnit(game, ScrOrderIds.CONSTRUCTING_BUILDING);

        assignment.queueReplacingSameOrders(unit, ScrOrderIds.RESET_COLLISION, null, TARGET_POSITION, TARGET_POSITION, ScrUnitIds.NONE);

        Assertions.assertEquals(2, countOrders(unit, ScrOrderIds.RESET_COLLISION));
    }
}
