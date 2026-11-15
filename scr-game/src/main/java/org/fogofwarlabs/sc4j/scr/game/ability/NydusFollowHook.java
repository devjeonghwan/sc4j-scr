package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.game.system.GameSystem;
import org.fogofwarlabs.sc4j.engine.order.FollowTargetHook;
import org.fogofwarlabs.sc4j.engine.order.OrderSystem;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a Follow of a Nydus Canal the unit can enter ({@link UnitNydus#canEnter}) into EnterNydusCanal: the SC:R implementation of the follow
 * target point ({@link OrderSystem#FOLLOW_TARGET}), asked first. The hook holds no state of its own; it finds the SC:R game when it is attached.
 */
public final class NydusFollowHook implements FollowTargetHook, GameSystem {
    private @Nullable Game game;

    /**
     * Creates a {@link NydusFollowHook} instance; it calls the SC:R game it finds when it is attached.
     */
    public NydusFollowHook() {
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
     * Checks whether a following unit uses its target; a hook that answers {@code true} has given the unit the order that uses the target.
     * <p>
     * A Nydus Canal the unit can enter gets the unit the EnterNydusCanal order on it.
     *
     * @param unit             the following unit
     * @param target           the followed unit
     * @param instructionLimit the instruction limit of each animation program run the change of the unit's order starts
     * @return {@code true} if the unit uses its target and no longer follows it, {@code false} if this hook gives no answer
     * @throws IllegalArgumentException if a unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     */
    @Override
    public boolean useTarget(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit,
                             org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit target,
                             int instructionLimit) {
        Unit follower = Unit.of(unit);
        Unit canal = Unit.of(target);

        if (canal.getUnitId() != ScrUnitIds.ZERG_NYDUS_CANAL) {
            return false;
        }

        Game attached = this.requireGame();

        if (!attached.getUnitNydus().canEnter(follower, canal)) {
            return false;
        }

        attached.getOrders().issue(follower, ScrOrderIds.ENTER_NYDUS_CANAL, canal, 0, 0, ScrUnitIds.NONE, false, instructionLimit);

        return true;
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R Nydus follow hook is not attached.");
        }

        return attached;
    }
}
