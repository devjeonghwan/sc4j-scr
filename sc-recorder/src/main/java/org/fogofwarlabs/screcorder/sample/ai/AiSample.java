package org.fogofwarlabs.screcorder.sample.ai;

import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Stores immutable AI clocks, routes, player regions and unit state.
 */
public final class AiSample implements Sample {
    private final int regionCount;
    private final long clock;
    private final @NotNull List<AiPlayerState> players;
    private final @NotNull List<AiUnitState> units;

    /**
     * Creates a {@link AiSample} instance.
     *
     * @param regionCount the signed native region count before bounded sampling
     * @param clock       the unsigned AI clock
     * @param players     the immutable AI player states
     * @param units       the immutable active unit AI states
     */
    public AiSample(int regionCount, long clock, @NotNull List<AiPlayerState> players, @NotNull List<AiUnitState> units) {
        this.regionCount = regionCount;
        this.clock       = clock;
        this.players     = List.copyOf(players);
        this.units       = List.copyOf(units);
    }


    /**
     * @return the signed native region count before bounded sampling
     */
    public int getRegionCount() {
        return this.regionCount;
    }

    /**
     * @return the unsigned AI clock
     */
    public long getClock() {
        return this.clock;
    }

    /**
     * @return the immutable AI player states in owner order
     */
    public @NotNull List<AiPlayerState> getPlayers() {
        return this.players;
    }

    /**
     * @return the immutable active unit AI states in logical slot order
     */
    public @NotNull List<AiUnitState> getUnits() {
        return this.units;
    }
}
