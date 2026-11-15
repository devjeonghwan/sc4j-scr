package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Applies the Operation Cwal production steps shared by every production path: the build countdown and the construction hit point and shield
 * increments.
 */
public final class UnitBuildTime {
    /**
     * The Operation Cwal shift of the construction increments.
     */
    private static final int OPERATION_CWAL_SHIFT = 4;
    /**
     * The build countdown step under Operation Cwal.
     */
    private static final int OPERATION_CWAL_STEP = 16;
    /**
     * The mask of the WORD construction increments.
     */
    private static final int WORD_MASK = 0xFFFF;

    private final @NotNull Game game;

    /**
     * Creates a {@link UnitBuildTime} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitBuildTime(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Returns the construction hit point increment of a unit, shifted left by {@code 4} as a WORD under Operation Cwal. Its callers are Terran
     * construction, repair, training, hangar training, Protoss warping, add-on construction and Zerg building morph.
     *
     * @param unit the unit under construction or repair
     * @return the unsigned WORD increment
     */
    public int getHitPointIncrement(@NotNull Unit unit) {
        int increment = unit.getConstructionHitPointIncrement() & WORD_MASK;

        return this.game.isOperationCwal() ? increment << OPERATION_CWAL_SHIFT & WORD_MASK : increment;
    }

    /**
     * Returns the construction shield increment of a warping unit, shifted left by {@code 4} as a WORD under Operation Cwal.
     *
     * @param unit the warping unit
     * @return the unsigned WORD increment
     */
    int getShieldIncrement(@NotNull Unit unit) {
        int increment = unit.getConstructionShieldIncrement() & WORD_MASK;

        return this.game.isOperationCwal() ? increment << OPERATION_CWAL_SHIFT & WORD_MASK : increment;
    }


    /**
     * Advances a non-zero remaining build time by {@code 1} frame. Without Operation Cwal it decrements; with it {@code 16} is subtracted and a
     * result that is negative as a signed WORD becomes {@code 0}, so a remaining time above {@code 0x800F} finishes at once.
     *
     * @param unit the unit under production with a non-zero remaining build time
     */
    public void advance(@NotNull Unit unit) {
        int remaining = unit.getRemainingBuildTime();

        if (!this.game.isOperationCwal()) {
            unit.setRemainingBuildTime(remaining - 1);

            return;
        }

        short next = (short) (remaining - OPERATION_CWAL_STEP);

        unit.setRemainingBuildTime(next < 0 ? 0 : next);
    }
}
