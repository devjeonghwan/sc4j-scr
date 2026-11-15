package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitCollisionRules;
import org.jetbrains.annotations.NotNull;

/**
 * Creates units for tests that need a unit outside a game: every unit belongs to a pool, so each call creates a small
 * {@link UnitPool} of its own.
 */
public final class UnitTestUtils {
    /**
     * Creates a {@link Unit} instance in a new pool whose last slot it is: the pool capacity is the slot index, every slot
     * is free and every field has its initial value.
     *
     * @param index the positive slot index of the unit
     * @return the created {@link Unit} instance
     * @throws IllegalArgumentException if the index is less than {@code 1} or past the largest unit pool capacity
     */
    public static @NotNull Unit createUnit(int index) {
        if (index < 1) {
            throw Exceptions.newIllegalArgumentException("Unit pool index must be positive: {0}", index);
        }

        return new UnitPool(index).getUnit(index);
    }

    /**
     * Creates a {@link UnitCollisionRules} instance with the SC:R collision rules of a game without game data and the SC:R hooks of their
     * points: the rules that the unit finder and movement tests outside a game run.
     *
     * @return the created {@link UnitCollisionRules} instance
     */
    public static @NotNull UnitCollisionRules createCollisionRules() {
        return Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0)).getHooks().getCollisionRules();
    }


    private UnitTestUtils() {
    }
}
