package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.game.state.ScrHooks;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Hears that the order timer of a burrowed unit with a computer record elapsed ({@link ScrHooks#BURROWED_TIMER}); the
 * timer is already restarted.
 */
@FunctionalInterface
public interface BurrowedTimerHook {
    /**
     * Called when the order timer of a burrowed unit elapses.
     *
     * @param unit             the burrowed unit
     * @param instructionLimit the instruction limit of each animation program run the hook starts
     */
    void onBurrowedTimer(@NotNull Unit unit, int instructionLimit);
}
