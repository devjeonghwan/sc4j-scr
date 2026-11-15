package org.fogofwarlabs.sc4j.scr.game.unit.finder;

import org.fogofwarlabs.sc4j.engine.movement.StandardMotionStep;
import org.fogofwarlabs.sc4j.engine.random.PcgRandom;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the flyer repulsion grid: every operation that reaches a grid that is not allocated, or a cell outside it, fails with the same
 * exception type.
 */
final class FlyerRepulsionTest {
    private static final int GRID_DIMENSION = 4;
    private static final int CELL_SIZE = 64;
    private static final int RANDOM_SOURCE = 0;
    private static final int RANDOM_SOURCE_COUNT = 1;
    private static final int MAP_SIZE = GRID_DIMENSION * CELL_SIZE;
    private static final int OUTSIDE_CELL = GRID_DIMENSION + 1;
    private static final int POOL_CAPACITY = 1;
    private static final int UNIT_INDEX = 1;

    /**
     * Treats every unit as a repulsion flyer shown at its position.
     */
    private static final @NotNull FlyerRepulsionRules FLYER_RULES = new FlyerRepulsionRules() {
        @Override
        public boolean isRepulsionFlyer(@NotNull Unit unit) {
            return true;
        }

        @Override
        public boolean isRepulsionHeld(@NotNull Unit unit) {
            return false;
        }

        @Override
        public boolean isAtMovementTarget(@NotNull Unit unit) {
            return false;
        }

        @Override
        public int getShownX(@NotNull Unit unit) {
            return unit.getPositionX();
        }

        @Override
        public int getShownY(@NotNull Unit unit) {
            return unit.getPositionY();
        }

        @Override
        public int getLeftEdge(@NotNull Unit unit) {
            return unit.getPositionX();
        }

        @Override
        public int getRightEdge(@NotNull Unit unit) {
            return unit.getPositionX();
        }

        @Override
        public int getTopEdge(@NotNull Unit unit) {
            return unit.getPositionY();
        }

        @Override
        public int getBottomEdge(@NotNull Unit unit) {
            return unit.getPositionY();
        }
    };

    @Test
    void flyer_repulsion_fails_with_an_illegal_state_on_a_grid_that_is_not_allocated() {
        FlyerRepulsion repulsion = new FlyerRepulsion(FLYER_RULES, GRID_DIMENSION, CELL_SIZE, RANDOM_SOURCE);
        PcgRandom random = new PcgRandom(0L, 0L, RANDOM_SOURCE_COUNT);
        Unit unit = new UnitPool(POOL_CAPACITY).getUnit(UNIT_INDEX);

        unit.setPosition(CELL_SIZE, CELL_SIZE);

        Assertions.assertThrows(IllegalStateException.class, () -> repulsion.updateCell(unit, random, false, CELL_SIZE, CELL_SIZE));
        Assertions.assertThrows(IllegalStateException.class, () -> repulsion.register(unit, random));
        Assertions.assertThrows(IllegalStateException.class, () -> repulsion.unregister(unit));
        Assertions.assertThrows(IllegalStateException.class, () -> repulsion.apply(new StandardMotionStep(), unit, MAP_SIZE, MAP_SIZE));
    }

    @Test
    void update_cell_should_fail_with_an_illegal_state_when_the_previous_cell_lies_outside_the_grid() {
        FlyerRepulsion repulsion = new FlyerRepulsion(FLYER_RULES, GRID_DIMENSION, CELL_SIZE, RANDOM_SOURCE);
        PcgRandom random = new PcgRandom(0L, 0L, RANDOM_SOURCE_COUNT);
        Unit unit = new UnitPool(POOL_CAPACITY).getUnit(UNIT_INDEX);

        repulsion.allocate();
        unit.setPosition(CELL_SIZE, CELL_SIZE);
        unit.setRepulsionCellX(OUTSIDE_CELL);
        unit.setRepulsionCellY(OUTSIDE_CELL);

        Assertions.assertThrows(IllegalStateException.class, () -> repulsion.updateCell(unit, random, false, CELL_SIZE, CELL_SIZE));
    }
}
