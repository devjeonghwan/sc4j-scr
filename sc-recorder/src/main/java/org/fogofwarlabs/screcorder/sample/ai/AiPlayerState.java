package org.fogofwarlabs.screcorder.sample.ai;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Stores immutable AI route, player and region state for one owner slot.
 */
public final class AiPlayerState {
    /**
     * Size of the route marker table.
     */
    public static final int ROUTE_MARK_BYTES = 2500;

    /**
     * Size of the recorded AI player memory prefix.
     */
    public static final int PLAYER_MEMORY_BYTES = 32;

    private final int owner;
    private final long routeClock;
    private final int routeSource;
    private final byte @NotNull [] routeMarks;
    private final long regionTablePresence;
    private final byte @NotNull [] playerMemory;
    private final @NotNull List<AiRegionState> regions;

    /**
     * Creates a {@link AiPlayerState} instance.
     *
     * @param owner               the owner slot
     * @param routeClock          the unsigned route cache clock
     * @param routeSource         the signed route cache source
     * @param routeMarks          the route marker bytes, copied on construction
     * @param regionTablePresence {@code 1} if the region table is present, {@code 0} otherwise
     * @param playerMemory        the AI player memory prefix, copied on construction
     * @param regions             the immutable region states
     * @throws IllegalArgumentException if {@code routeMarks} or {@code playerMemory} does not have its fixed length
     */
    public AiPlayerState(int owner,
                         long routeClock, int routeSource, byte @NotNull [] routeMarks,
                         long regionTablePresence, byte @NotNull [] playerMemory, @NotNull List<AiRegionState> regions) {
        if (routeMarks.length != ROUTE_MARK_BYTES || playerMemory.length != PLAYER_MEMORY_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid AI player memory bounds");
        }

        this.owner               = owner;
        this.routeClock          = routeClock;
        this.routeSource         = routeSource;
        this.routeMarks          = routeMarks.clone();
        this.regionTablePresence = regionTablePresence;
        this.playerMemory        = playerMemory.clone();
        this.regions             = List.copyOf(regions);
    }


    /**
     * @return the owner slot
     */
    public int getOwner() {
        return this.owner;
    }

    /**
     * @return the unsigned route cache clock
     */
    public long getRouteClock() {
        return this.routeClock;
    }

    /**
     * @return the signed route cache source
     */
    public int getRouteSource() {
        return this.routeSource;
    }

    /**
     * @return a detached copy of the route marker bytes
     */
    public byte @NotNull [] getRouteMarks() {
        return this.routeMarks.clone();
    }

    /**
     * @return {@code 1} if the region table is present, {@code 0} otherwise
     */
    public long getRegionTablePresence() {
        return this.regionTablePresence;
    }

    /**
     * @return a detached copy of the AI player memory prefix
     */
    public byte @NotNull [] getPlayerMemory() {
        return this.playerMemory.clone();
    }

    /**
     * @return the immutable region states in region identifier order
     */
    public @NotNull List<AiRegionState> getRegions() {
        return this.regions;
    }
}
