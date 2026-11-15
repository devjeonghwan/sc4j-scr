package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPoolScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that clearing the restriction flags of the created units keeps their other state.
 */
final class UnitPhaseRestrictionFlagsTest {
    /**
     * The parent status before the restriction flags are cleared.
     */
    private static final int PARENT_STATUS = 0x40008002;
    /**
     * The parent status after the restriction flags are cleared.
     */
    private static final int CLEARED_PARENT_STATUS = 0x40000002;
    private static final int SUBUNIT_HIT_POINTS = 123;

    @Test
    void clear_restriction_flags_should_clear_parent_and_subunit_without_changing_vitals() {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit parent = pool.getUnit(UnitPoolScenario.allocate(pool));
        Unit subunit = pool.getUnit(UnitPoolScenario.allocate(pool));

        parent.setStatus(PARENT_STATUS);
        subunit.setStatus(UnitStatusFlags.CANNOT_ATTACK);
        subunit.setHitPoints(SUBUNIT_HIT_POINTS);
        parent.setSubunit(subunit);
        pool.insertCreatedUnit(parent);

        game.getUnitPhase().clearRestrictionFlags();

        Assertions.assertEquals(CLEARED_PARENT_STATUS, parent.getStatus());
        Assertions.assertEquals(0, subunit.getStatus());
        Assertions.assertEquals(SUBUNIT_HIT_POINTS, subunit.getHitPoints());
    }
}
