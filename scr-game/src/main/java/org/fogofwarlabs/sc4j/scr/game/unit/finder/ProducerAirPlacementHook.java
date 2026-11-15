package org.fogofwarlabs.sc4j.scr.game.unit.finder;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.collision.CollisionAnswer;
import org.fogofwarlabs.sc4j.engine.collision.CollisionSystem;
import org.fogofwarlabs.sc4j.engine.collision.PlacementExemptionHook;
import org.fogofwarlabs.sc4j.engine.game.system.GameSystem;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Answers the SC:R branch of the placement blocking rule ({@link CollisionSystem#PLACEMENT_EXEMPTION}): a Starport blocks the placement of a
 * Wraith, Dropship, Science Vessel, Battlecruiser or Valkyrie, a Robotics Facility that of a Shuttle or Observer and a Stargate that of a Scout,
 * Carrier, Arbiter or Corsair, although they fly. Every other pair gets no answer, so the layer rule decides. A unit of an added type is placed
 * as a unit of its base type ({@link Game#getBaseType(Unit)}). The hook holds no state of its own; it finds the SC:R game when it is attached.
 */
public final class ProducerAirPlacementHook implements PlacementExemptionHook, GameSystem {
    private @Nullable Game game;

    /**
     * Creates a {@link ProducerAirPlacementHook} instance; it reads the SC:R game it finds when it is attached.
     */
    public ProducerAirPlacementHook() {
        this.game = null;
    }

    /**
     * Resolves what this system uses from the game. Called once, after every system exists.
     * <p>
     * Binds the SC:R game system of the game.
     *
     * @param game the game
     */
    @Override
    public void attach(org.fogofwarlabs.sc4j.engine.game.@NotNull Game game) {
        this.game = game.getModSystem(Game.SYSTEM_KEY);
    }

    /**
     * Answers whether another unit blocks a unit being put down. The rule asks before its layer rule, after it found that both units collide at
     * all.
     *
     * @param unit      the unit being put down
     * @param otherUnit the unit already there
     * @return {@link CollisionAnswer#BLOCKS} if the other unit is the producer of the unit's air type, {@link CollisionAnswer#NONE} otherwise
     * @throws IllegalArgumentException if a unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     */
    @Override
    public @NotNull CollisionAnswer answerPlacementBlocking(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit,
                                                            org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit otherUnit) {
        int incomingType = this.requireGame().getBaseType(Unit.of(unit));
        int candidateType = Unit.of(otherUnit).getUnitId();

        if (candidateType == ScrUnitIds.TERRAN_STARPORT &&
            (incomingType == ScrUnitIds.TERRAN_WRAITH || incomingType == ScrUnitIds.TERRAN_DROPSHIP ||
             incomingType == ScrUnitIds.TERRAN_SCIENCE_VESSEL || incomingType == ScrUnitIds.TERRAN_BATTLECRUISER ||
             incomingType == ScrUnitIds.TERRAN_VALKYRIE)) {
            return CollisionAnswer.BLOCKS;
        }

        if (candidateType == ScrUnitIds.PROTOSS_ROBOTICS_FACILITY &&
            (incomingType == ScrUnitIds.PROTOSS_SHUTTLE || incomingType == ScrUnitIds.PROTOSS_OBSERVER)) {
            return CollisionAnswer.BLOCKS;
        }

        if (candidateType == ScrUnitIds.PROTOSS_STARGATE &&
            (incomingType == ScrUnitIds.PROTOSS_SCOUT || incomingType == ScrUnitIds.PROTOSS_CARRIER ||
             incomingType == ScrUnitIds.PROTOSS_ARBITER || incomingType == ScrUnitIds.PROTOSS_CORSAIR)) {
            return CollisionAnswer.BLOCKS;
        }

        return CollisionAnswer.NONE;
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R producer air placement hook is not attached.");
        }

        return attached;
    }
}
