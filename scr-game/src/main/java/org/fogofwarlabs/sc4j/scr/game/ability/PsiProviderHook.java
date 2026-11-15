package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.game.system.GameSystem;
import org.fogofwarlabs.sc4j.engine.lifecycle.UnitDyingHook;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Removes a dying Pylon from the psi providers ({@link UnitPsi#removeProvider}): the SC:R implementation of the unit
 * dying point. The hook holds no state of its own; it finds the SC:R game when it is attached.
 */
public final class PsiProviderHook implements UnitDyingHook, GameSystem {
    private @Nullable Game game;

    /**
     * Creates a {@link PsiProviderHook} instance; it calls the SC:R game it finds when it is attached.
     */
    public PsiProviderHook() {
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
     * Called while a unit dies.
     * <p>
     * A Pylon leaves the psi providers with its power field sprite.
     *
     * @param unit the dying unit
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     */
    @Override
    public void onUnitDying(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        Unit scrUnit = Unit.of(unit);

        if (scrUnit.getUnitId() == ScrUnitIds.PROTOSS_PYLON) {
            this.requireGame().getPsi().removeProvider(scrUnit);
        }
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R psi provider hook is not attached.");
        }

        return attached;
    }
}
