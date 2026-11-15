package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the cargo of transports and Bunkers: what the interface shows of a container (the status panel's cargo slots, the command card's
 * unload condition). The loading and unloading rules are {@link UnitTransport}'s.
 */
public interface TransportCargo {
    /**
     * @param transport the container
     * @param slot      the handle slot from {@code 0} through {@code 7}
     * @return the live unit in the slot, or {@code null}
     * @throws UnsupportedOperationException if the unit pool is not initialized
     */
    @Nullable Unit getLoadedUnit(@NotNull Unit transport, int slot);

    /**
     * @param transport the container
     * @return {@code true} if any slot holds a live unit, {@code false} otherwise
     */
    boolean hasLoadedUnits(@NotNull Unit transport);
}
