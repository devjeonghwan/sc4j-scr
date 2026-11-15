package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that critters wander with the original random sources.
 */
@RequiresInstallation
final class CritterOrdersTest {
    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;

    private static final int RANDOM_SEED = 0x2468ACE;
    private static final int MAP_SIZE = 64;
    private static final int TILE_COUNT = MAP_SIZE * MAP_SIZE;
    private static final int CRITTER_POSITION = 1024;
    private static final int VECTOR_Y_SHIFT = 32;

    private static final int STATE_WANDER = 0;
    private static final int STATE_MOVING = 1;
    private static final int STATE_PAUSING = 2;
    private static final int WANDER_RANDOM_SOURCE = 48;
    private static final int PAUSE_RANDOM_SOURCE = 47;
    private static final int LAST_DIRECTION = 255;
    private static final int WANDER_DISTANCE = 32;
    private static final int KAKARU_MINIMUM_DISTANCE = 160;
    private static final int KAKARU_MAXIMUM_DISTANCE = 640;
    private static final int MAXIMUM_PAUSE = 75;
    private static final int SHORT_PAUSE_LIMIT = 15;

    /**
     * The wandering random calls of one wander step: the direction, and for the Kakaru also the distance.
     */
    private static final int WANDER_RANDOM_CALLS = 1;
    private static final int KAKARU_WANDER_RANDOM_CALLS = 2;
    /**
     * The unit pool index of the critter.
     */
    private static final int CRITTER_POOL_INDEX = 1;


    @Test
    void update_should_wander_critters_with_the_original_random_sources() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            for (int unitType : new int[]{ScrUnitIds.CRITTER_RHYNADON, ScrUnitIds.CRITTER_KAKARU}) {
                LcgRandom random = new LcgRandom(RANDOM_SEED);

                random.setEnabled(1);

                Game game = Game.create(data, new GameMap(MAP_SIZE, MAP_SIZE, TILESET, new int[TILE_COUNT]), random);
                Unit critter = UnitTestUtils.createUnit(CRITTER_POOL_INDEX);

                critter.setUnitId(unitType);
                critter.setSprite(SpriteScenario.createSprite(0, CRITTER_POSITION, CRITTER_POSITION, 0));
                critter.setPositionX(CRITTER_POSITION);
                critter.setPositionY(CRITTER_POSITION);
                critter.setMovementDestinationX(CRITTER_POSITION);
                critter.setMovementDestinationY(CRITTER_POSITION);

                LcgRandom expected = new LcgRandom(RANDOM_SEED);

                expected.setEnabled(1);

                int direction = expected.next(WANDER_RANDOM_SOURCE, 0, LAST_DIRECTION);
                int distance = unitType == ScrUnitIds.CRITTER_KAKARU
                               ? expected.next(WANDER_RANDOM_SOURCE, KAKARU_MINIMUM_DISTANCE, KAKARU_MAXIMUM_DISTANCE)
                               : WANDER_DISTANCE;
                long offset = FlingyMotionUtils.calculateDirectionVector(direction, distance);

                game.getCritterOrders().update(critter);

                Assertions.assertEquals(STATE_MOVING, critter.getPrimaryOrderState());
                Assertions.assertEquals(CRITTER_POSITION + (int) offset, (short) critter.getOrderTargetX());
                Assertions.assertEquals(CRITTER_POSITION + (int) (offset >>> VECTOR_Y_SHIFT), (short) critter.getOrderTargetY());
                Assertions.assertEquals((short) critter.getOrderTargetX(), (short) critter.getMovementDestinationX());
                Assertions.assertEquals(unitType == ScrUnitIds.CRITTER_KAKARU ? KAKARU_WANDER_RANDOM_CALLS : WANDER_RANDOM_CALLS,
                                        random.getCallCount(WANDER_RANDOM_SOURCE));

                game.getCritterOrders().update(critter);

                Assertions.assertEquals(STATE_MOVING, critter.getPrimaryOrderState(), "The critter waits until it reaches its move target.");

                critter.setMovementDestinationX(CRITTER_POSITION);
                critter.setMovementDestinationY(CRITTER_POSITION);

                game.getCritterOrders().update(critter);

                if (unitType == ScrUnitIds.CRITTER_KAKARU) {
                    Assertions.assertEquals(STATE_WANDER, critter.getPrimaryOrderState(), "The Kakaru wanders again without a pause.");
                    Assertions.assertEquals(0, random.getCallCount(PAUSE_RANDOM_SOURCE));
                } else {
                    int pause = expected.next(PAUSE_RANDOM_SOURCE, 0, MAXIMUM_PAUSE);

                    Assertions.assertEquals(STATE_PAUSING, critter.getPrimaryOrderState());
                    Assertions.assertEquals(pause <= SHORT_PAUSE_LIMIT ? 0 : pause, critter.getCooldownTimers() & Unit.ORDER_TIMER_MASK);

                    critter.setCooldownTimers(critter.getCooldownTimers() & ~Unit.ORDER_TIMER_MASK);

                    game.getCritterOrders().update(critter);

                    Assertions.assertEquals(STATE_WANDER, critter.getPrimaryOrderState());
                }
            }
        }
    }
}
