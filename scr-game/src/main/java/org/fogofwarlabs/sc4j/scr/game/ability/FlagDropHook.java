package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.game.system.GameSystem;
import org.fogofwarlabs.sc4j.engine.lifecycle.UnitOwnerChangedHook;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Puts down the Flag a worker carries when the worker passes to the Flag's owner or to a player from {@code 8}; other carried powerups stay
 * with the worker: the SC:R implementation of the unit owner changed point. The hook holds no state of its own; it finds the SC:R game when it
 * is attached.
 */
public final class FlagDropHook implements UnitOwnerChangedHook, GameSystem {
    private @Nullable Game game;

    /**
     * Creates a {@link FlagDropHook} instance; it calls the SC:R game it finds when it is attached.
     */
    public FlagDropHook() {
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
     * Called when the owner change of a unit is finished.
     * <p>
     * A worker carrying a Flag puts it down when its new owner owns the Flag or is a player from {@code 8}.
     *
     * @param unit             the unit with its new owner
     * @param instructionLimit the instruction limit of each animation program run the hook starts
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     */
    @Override
    public void onUnitOwnerChanged(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, int instructionLimit) {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R flag drop hook is not attached.");
        }

        Unit worker = Unit.of(unit);

        if (attached.getUnitQueries().isCarryingFlag(worker)) {
            Unit flag = worker.getCarriedPowerup();
            int owner = worker.getOwnerIndex();

            if (owner == flag.getOwnerIndex() || owner >= PlayerTable.PLAYABLE_PLAYER_SLOTS) {
                attached.getUnitPowerups().drop(worker, instructionLimit);
            }
        }
    }
}
