package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds the stats of the SC:R mod that the engine stats ({@link Stat#getEngineStats()}) do not cover, in the order the mod registers them
 * right after the engine stats.
 */
public final class ScrStats {
    /**
     * The number of units a Carrier or Reaver type builds into its hangar and launches (Interceptors, Scarabs).
     */
    public static final @NotNull Stat HANGAR_CAPACITY = new Stat("hangar capacity");
    /**
     * The armor of the shields of a unit type, with eight fraction bits: what the shield damage layer takes off a hit before it absorbs the rest.
     */
    public static final @NotNull Stat SHIELD_ARMOR = new Stat("shield armor");

    private static final @NotNull List<@NotNull Stat> TYPE_LIST = List.of(HANGAR_CAPACITY, SHIELD_ARMOR);

    /**
     * @return every SC:R stat in registration order
     */
    public static @NotNull List<@NotNull Stat> getTypes() {
        return TYPE_LIST;
    }


    private ScrStats() {
    }
}
