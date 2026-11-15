package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.engine.game.GameDefinition;
import org.fogofwarlabs.sc4j.engine.lifecycle.UnitLifecycle;
import org.fogofwarlabs.sc4j.engine.movement.MovementSystem;
import org.fogofwarlabs.sc4j.engine.order.OrderSystem;
import org.fogofwarlabs.sc4j.engine.production.ProductionSystem;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.ability.PsiProviderHook;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerHooks;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the SC:R definition registers one SC:R hook per point and that a hook a mod adds runs after it, in
 * registration order.
 */
final class ScrHooksTest {
    private static final int INSTRUCTION_LIMIT = 10000;

    @Test
    void register_should_add_one_scr_hook_per_point() {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        org.fogofwarlabs.sc4j.engine.game.Game engineGame = game.getEngineGame();

        Assertions.assertInstanceOf(ComputerHooks.class, engineGame.getHooks(UnitLifecycle.UNIT_REMOVED).get(0));
        Assertions.assertInstanceOf(ComputerHooks.class, engineGame.getHooks(UnitLifecycle.UNIT_ADOPTED).get(0));
        Assertions.assertInstanceOf(ComputerHooks.class, engineGame.getHooks(UnitLifecycle.BUILDING_DESTROYED).get(0));
        Assertions.assertInstanceOf(PsiProviderHook.class, engineGame.getHooks(UnitLifecycle.UNIT_DYING).get(0));
        Assertions.assertInstanceOf(ComputerHooks.class, engineGame.getHooks(ProductionSystem.QUEUE_SLOT_MOVED).get(0));
        Assertions.assertInstanceOf(ComputerHooks.class, engineGame.getHooks(ProductionSystem.QUEUE_SLOT_CLEARED).get(0));
        Assertions.assertInstanceOf(ComputerHooks.class, engineGame.getHooks(OrderSystem.DISABLED_ORDER).get(0));
        Assertions.assertInstanceOf(ComputerHooks.class, engineGame.getHooks(OrderSystem.ORDER_TIMER).get(0));
        Assertions.assertInstanceOf(ComputerHooks.class, engineGame.getHooks(MovementSystem.TRANSPORT_PICKUP).get(0));
        Assertions.assertInstanceOf(ComputerHooks.class, engineGame.getHooks(ScrHooks.TRANSPORT_LOADED).get(0));
        Assertions.assertInstanceOf(ComputerHooks.class, engineGame.getHooks(ScrHooks.BURROWED_TIMER).get(0));
        Assertions.assertEquals(1, engineGame.getHooks(OrderSystem.ORDER_TIMER).size());
    }

    @Test
    void added_order_hooks_run_after_the_scr_hooks_in_registration_order() {
        List<String> calls = new ArrayList<>();
        GameDefinition definition = Game.register(new GameDefinition.Builder())
                                        .addHook(OrderSystem.ORDER_TIMER, context -> unit -> calls.add("orderTimer " + unit.getIndex()))
                                        .addHook(OrderSystem.DISABLED_ORDER, context -> unit -> calls.add("disabledOrder " + unit.getIndex()))
                                        .build();
        GameSetup setup = GameSetup.of(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        Game game = org.fogofwarlabs.sc4j.engine.game.Game.create(definition, setup.createEngineSetup()).getModSystem(Game.SYSTEM_KEY);
        Unit timed = UnitTestUtils.createUnit(1);
        Unit disabled = UnitTestUtils.createUnit(2);

        timed.setPrimaryOrder(ScrOrderIds.NOTHING_WAIT);
        timed.setPrimaryOrderCountdown(0);
        disabled.setPrimaryOrder(ScrOrderIds.NOTHING_WAIT);
        disabled.setLockdownTimer(1);

        game.getUnitOrders().updatePrimary(timed, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);
        game.getUnitOrders().updatePrimary(disabled, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        Assertions.assertEquals(2, game.getEngineGame().getHooks(OrderSystem.ORDER_TIMER).size());
        Assertions.assertEquals(List.of("orderTimer 1", "disabledOrder 2"), calls);
    }
}
