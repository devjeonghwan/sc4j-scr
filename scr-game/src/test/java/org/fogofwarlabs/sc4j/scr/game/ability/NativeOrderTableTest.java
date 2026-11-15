package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.order.OrderStage;
import org.fogofwarlabs.sc4j.scr.game.order.ScrOrderBehaviors;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.CampaignMissionScenario;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

/**
 * Covers the shape of the SC:R order table in a game's order behaviors: which orders run before the disabled test, which ids the main-order dispatch
 * represents, the hidden-unit default and shared handlers, and the handlers of the creep-growth stop and of AttackTile in a running mission.
 */
final class NativeOrderTableTest {
    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;

    private static final int ORDER_COUNT = 190;
    /**
     * The orders that run before the disabled test: Die, Incomplete Warping, Warp In and Nuke Track.
     */
    private static final @NotNull Set<Integer> UNCONDITIONAL_ORDERS = Set.of(0, 48, 174, 129);

    /**
     * The campaign mission of the order handler test, played as Brood War, and the turns that run before the test body starts.
     */
    private static final @NotNull String HANDLER_MISSION = "bw-protoss-1";
    private static final int START_TURNS = 5;

    private static final int UNIT_POSITION_X = 2048;
    private static final int UNIT_POSITION_Y = 1024;
    private static final int MARINE_POSITION_Y = 1200;

    /**
     * The turns after which a Sunken Colony has stopped its creep growth, and the turns an AttackTile order runs.
     */
    private static final int CREEP_GROWTH_TURNS = 12;
    private static final int ATTACK_TILE_TURNS = 20;
    /**
     * The orders that the creep growth stop leaves and sets, and the order that the empty AttackTile handler keeps.
     */
    private static final int STOPPING_CREEP_GROWTH_ORDER = 103;
    private static final int NOTHING_ORDER = 23;
    private static final int ATTACK_TILE_ORDER = 12;

    private static @NotNull ScrOrderBehaviors createOrderBehaviors() {
        return Game.create(null, new GameMap(1, 1, TILESET, new int[1]), new LcgRandom(0)).getOrderBehaviors();
    }


    @Test
    void order_behaviors_dispatches_every_order_type_except_none() {
        ScrOrderBehaviors behaviors = createOrderBehaviors();

        Assertions.assertEquals(ORDER_COUNT, behaviors.getOrderCount());

        for (int order = 0; order <= ScrOrderIds.NONE; order++) {
            boolean unconditional = behaviors.getStep(OrderStage.MAIN_UNCONDITIONAL, order) != null;
            String message = "order " + order;

            Assertions.assertEquals(UNCONDITIONAL_ORDERS.contains(order), unconditional, message);
            Assertions.assertEquals(!unconditional && order != ScrOrderIds.NONE, behaviors.isRepresented(order), message);
        }
    }

    @Test
    void order_behaviors_gives_every_hidden_order_id_an_order() {
        ScrOrderBehaviors behaviors = createOrderBehaviors();

        for (int orderId = 0; orderId < behaviors.getOrderCount(); orderId++) {
            Assertions.assertNotNull(behaviors.getStep(OrderStage.HIDDEN_MAIN, orderId), Integer.toString(orderId));
        }
    }

    @Test
    void order_behaviors_shares_one_order_between_ids_of_one_handler() {
        ScrOrderBehaviors behaviors = createOrderBehaviors();

        Assertions.assertSame(behaviors.getStep(OrderStage.MAIN_TIMER, ScrOrderIds.MOVE_TO_ATTACK),
                              behaviors.getStep(OrderStage.MAIN_TIMER, ScrOrderIds.HARVEST2));
        Assertions.assertSame(behaviors.getStep(OrderStage.MAIN_UNCONDITIONAL, ScrOrderIds.DIE),
                              behaviors.getStep(OrderStage.HIDDEN_MAIN, ScrOrderIds.DIE));
        Assertions.assertSame(behaviors.getStep(OrderStage.SECONDARY, ScrOrderIds.CLOAK),
                              behaviors.getStep(OrderStage.HIDDEN_SECONDARY, ScrOrderIds.DECLOAK));
    }

    @RequiresInstallation
    @Test
    void stopping_creep_growth_returns_to_idle_and_empty_attack_tile_handler_keeps_order() throws IOException {
        CampaignMissionScenario.run(ExternalInput.INSTALLATION.getPath(), HANDLER_MISSION, true, (game, live) -> {
            Game state = live.getGame();

            CampaignMissionScenario.exchangeTurnsAndStep(game, live, START_TURNS);

            Unit colony = CampaignMissionScenario.createUnit(state,
                                                             ScrUnitIds.ZERG_SUNKEN_COLONY,
                                                             UNIT_POSITION_X, UNIT_POSITION_Y,
                                                             game.getLocalPlayer());

            state.getOrders().issueUntargeted(colony, ScrOrderIds.STOPPING_CREEP_GROWTH, RunningGameScenario.INSTRUCTION_LIMIT);
            colony.setSecondaryOrder(ScrOrderIds.SPREAD_CREEP);

            CampaignMissionScenario.exchangeTurnsAndStep(game, live, CREEP_GROWTH_TURNS);

            Assertions.assertNotEquals(STOPPING_CREEP_GROWTH_ORDER, colony.getPrimaryOrder());
            Assertions.assertEquals(NOTHING_ORDER, colony.getSecondaryOrder());

            // The empty AttackTile handler keeps the order.
            Unit marine = CampaignMissionScenario.createUnit(state,
                                                             ScrUnitIds.TERRAN_MARINE,
                                                             UNIT_POSITION_X, MARINE_POSITION_Y,
                                                             game.getLocalPlayer());

            state.getOrders().issueUntargeted(marine, ScrOrderIds.ATTACK_TILE, RunningGameScenario.INSTRUCTION_LIMIT);

            CampaignMissionScenario.exchangeTurnsAndStep(game, live, ATTACK_TILE_TURNS);

            Assertions.assertEquals(ATTACK_TILE_ORDER, marine.getPrimaryOrder());
        });
    }
}
