package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Applies a factory's retained rally destination to a newly completed unit.
 */
public final class UnitRally {
    private final @NotNull Game game;

    /**
     * Creates a {@link UnitRally} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitRally(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Issues Follow for an owned target unit, or Move for a point or foreign target.
     *
     * @param producer         the factory owning the rally fields
     * @param unit             the completed unit
     * @param instructionLimit the order cleanup script budget
     */
    public void issueRallyOrder(@NotNull Unit producer, @NotNull Unit unit, int instructionLimit) {
        Unit target = producer.getRallyTargetUnit();

        if (!this.game.requireGameData().getUnitTypes().isRallyProducer(producer.getUnitId()) || target == producer ||
            producer.getRallyPositionX() == 0) {
            return;
        }

        if (target != null && target.getOwnerIndex() == unit.getOwnerIndex()) {
            this.game.getOrders().issue(unit, ScrOrderIds.FOLLOW, target, 0, 0, ScrUnitIds.NONE, false, instructionLimit);
        } else {
            this.game.getOrders().issue(unit,
                                        ScrOrderIds.MOVE,
                                        null,
                                        producer.getRallyPositionX(), producer.getRallyPositionY(),
                                        ScrUnitIds.NONE,
                                        false,
                                        instructionLimit);
        }
    }
}
