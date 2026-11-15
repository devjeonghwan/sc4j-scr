package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.game.state.ScrHooks;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Adjusts the transport capacity of a unit ({@link ScrHooks#TRANSPORT_CAPACITY}); a unit with capacity {@code 0} is no transport.
 */
@FunctionalInterface
public interface TransportCapacityHook {
    /**
     * Returns the transport capacity of a unit after this hook.
     *
     * @param unit     the unit, no hallucination
     * @param capacity the capacity before this hook
     * @return the capacity after this hook
     */
    int getTransportCapacity(@NotNull Unit unit, int capacity);
}
