package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the order identifier range of a game's order behaviors.
 */
final class ScrOrderBehaviorsTest {
    /**
     * The tileset of the test map.
     */
    private static final int TILESET = 0;

    @Test
    void get_step_should_reject_ids_outside_order_types_like_order_type() {
        ScrOrderBehaviors behaviors = Game.create(null, new GameMap(1, 1, TILESET, new int[1]), new LcgRandom(0)).getOrderBehaviors();
        int outside = ScrOrderIds.NONE + 1;

        IllegalArgumentException behaviorFailure = Assertions.assertThrows(IllegalArgumentException.class,
                                                                           () -> behaviors.getStep(OrderStage.MAIN_TIMER, outside));
        IllegalArgumentException typeFailure = Assertions.assertThrows(IllegalArgumentException.class, () -> ScrOrderIds.requireValid(outside));

        Assertions.assertEquals(typeFailure.getMessage(), behaviorFailure.getMessage());
        Assertions.assertNull(behaviors.findStep(OrderStage.SECONDARY_UNCONDITIONAL, outside));
        Assertions.assertNull(behaviors.findStep(OrderStage.SECONDARY_UNCONDITIONAL, -1));
    }
}
