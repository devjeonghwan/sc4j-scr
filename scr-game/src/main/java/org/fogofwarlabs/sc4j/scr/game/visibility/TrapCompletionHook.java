package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.game.system.GameSystem;
import org.fogofwarlabs.sc4j.engine.lifecycle.UnitCompletedHook;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cloaks a completed trap ({@link org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView#isTrap}) undetected, with its visibility updated in
 * the next frame: the SC:R implementation of the unit completed point. The hook holds no state of its own; it finds the SC:R game when it is
 * attached.
 */
public final class TrapCompletionHook implements UnitCompletedHook, GameSystem {
    private @Nullable Game game;

    /**
     * Creates a {@link TrapCompletionHook} instance; it reads the SC:R game it finds when it is attached.
     */
    public TrapCompletionHook() {
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
     * Called when a unit is completed.
     * <p>
     * A trap requires a detector, is cloaked and undetected, and updates its visibility in the next frame.
     *
     * @param unit the completed unit
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the hook is not attached
     * @throws NullPointerException     if the world was built without game data
     */
    @Override
    public void onUnitCompleted(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R trap completion hook is not attached.");
        }

        Unit scrUnit = Unit.of(unit);

        if (attached.requireGameData().getUnitTypes().isTrap(scrUnit.getUnitId())) {
            scrUnit.setStatus(scrUnit.getStatus() | (UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED));
            scrUnit.setDetectionFlags(Unit.DETECTION_FLAGS_UNDETECTED);
            scrUnit.setVisibilityUpdateCountdown(0);
        }
    }
}
