package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.collision.BlockingHook;
import org.fogofwarlabs.sc4j.engine.collision.CollisionAnswer;
import org.fogofwarlabs.sc4j.engine.collision.CollisionSystem;
import org.fogofwarlabs.sc4j.engine.game.system.GameSystem;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Answers the SC:R branches of the movement blocking rule ({@link CollisionSystem#BLOCKING}): a Larva is blocked only by grounded buildings, every
 * other unit passes Larvae, and a gathering worker is blocked only by grounded buildings and, while it does not return gas, by gathering workers
 * that wait for gas. Every other pair gets no answer, so it blocks. A unit of an added type answers as a unit of its base type
 * ({@link Game#getBaseType(Unit)}). The hook holds no state of its own; it finds the SC:R game when it is attached.
 */
public final class LarvaAndGatheringBlockingHook implements BlockingHook, GameSystem {
    private @Nullable Game game;

    /**
     * Creates a {@link LarvaAndGatheringBlockingHook} instance; it reads the SC:R game it finds when it is attached.
     */
    public LarvaAndGatheringBlockingHook() {
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
     * Answers whether another unit blocks the movement of a unit. The rule asks after it found that both units collide at all.
     *
     * @param unit      the moving unit
     * @param otherUnit the unit in the way
     * @return {@link CollisionAnswer#BLOCKS} or {@link CollisionAnswer#PASSES}, or {@link CollisionAnswer#NONE} if this hook gives no answer
     * @throws IllegalArgumentException if a unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     */
    @Override
    public @NotNull CollisionAnswer answerBlocking(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit,
                                                   org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit otherUnit) {
        Unit incoming = Unit.of(unit);
        Unit candidate = Unit.of(otherUnit);
        Game attached = this.requireGame();

        if (attached.getBaseType(incoming) == ScrUnitIds.ZERG_LARVA) {
            return UnitQueries.isGroundedBuilding(candidate) ? CollisionAnswer.BLOCKS : CollisionAnswer.PASSES;
        }

        if (attached.getBaseType(candidate) == ScrUnitIds.ZERG_LARVA) {
            return CollisionAnswer.PASSES;
        }

        if ((incoming.getStatus() & UnitStatusFlags.GATHERING) == 0 || UnitQueries.isGroundedBuilding(candidate)) {
            return CollisionAnswer.NONE;
        }

        if ((candidate.getStatus() & UnitStatusFlags.GATHERING) == 0 || incoming.getPrimaryOrder() == ScrOrderIds.RETURN_GAS) {
            return CollisionAnswer.PASSES;
        }

        return candidate.getPrimaryOrder() == ScrOrderIds.WAIT_FOR_GAS ? CollisionAnswer.BLOCKS : CollisionAnswer.PASSES;
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R Larva and gathering blocking hook is not attached.");
        }

        return attached;
    }
}
