package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.game.state.ScrHooks;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Hears that a unit is loaded into a transport ({@link ScrHooks#TRANSPORT_LOADED}), before it is hidden in the transport.
 */
@FunctionalInterface
public interface TransportLoadedHook {
    /**
     * Called when a unit is loaded into a transport.
     *
     * @param unit the loaded unit
     */
    void onTransportLoaded(@NotNull Unit unit);
}
