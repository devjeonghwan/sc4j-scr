package org.fogofwarlabs.sc4j.scr.game.path;

import org.jetbrains.annotations.NotNull;

/**
 * Retains the route request owned by {@link UnitPath} and reused by every {@link LocalPathSearch} call.
 * <p>
 * The package-private fields form the search workspace and keep partial search results between calls.
 */
final class PathRequest {
    /**
     * The region slots of a path request (slots {@code 0} through {@code 49}).
     */
    static final int REGION_CAPACITY = 50;
    /**
     * The waypoint slots of a path request (slots {@code 0} through {@code 49}).
     */
    static final int WAYPOINT_CAPACITY = 50;

    int sourcePosition;
    int destinationPosition;
    long targetUnitIndex;
    long exclusionRectangle;
    int adjustedDestination;

    byte incompleteRegionRoute;
    byte limitedLocalSearch;
    byte destinationReached;
    byte destinationAdjusted;

    short collisionLeft;
    short collisionTop;
    short collisionRight;
    short collisionBottom;

    short sourceRegion;
    short destinationRegion;
    short regionIndex;
    short regionCount;
    byte fullRegionCount;
    final short @NotNull [] regions;

    short waypointIndex;
    short waypointCount;
    final int @NotNull [] waypoints;

    short regionNodeCount;
    short localNodeCount;
    short regionMaximumHeapCount;
    short localMaximumHeapCount;

    /**
     * Creates a {@link PathRequest} instance with cleared fields and empty route slots.
     */
    PathRequest() {
        this.sourcePosition      = 0;
        this.destinationPosition = 0;
        this.targetUnitIndex     = 0L;
        this.exclusionRectangle  = 0L;
        this.adjustedDestination = 0;

        this.incompleteRegionRoute = 0;
        this.limitedLocalSearch    = 0;
        this.destinationReached    = 0;
        this.destinationAdjusted   = 0;

        this.collisionLeft   = 0;
        this.collisionTop    = 0;
        this.collisionRight  = 0;
        this.collisionBottom = 0;

        this.sourceRegion      = 0;
        this.destinationRegion = 0;
        this.regionIndex       = 0;
        this.regionCount       = 0;
        this.fullRegionCount   = 0;
        this.regions           = new short[REGION_CAPACITY];

        this.waypointIndex = 0;
        this.waypointCount = 0;
        this.waypoints     = new int[WAYPOINT_CAPACITY];

        this.regionNodeCount        = 0;
        this.localNodeCount         = 0;
        this.regionMaximumHeapCount = 0;
        this.localMaximumHeapCount  = 0;
    }

    /**
     * @param index the region slot from {@code 0} through {@code 49}
     * @return the retained signed region WORD
     * @throws ArrayIndexOutOfBoundsException if the slot is outside the region route
     */
    int getRegion(int index) {
        return this.regions[index];
    }

    /**
     * Sets a region slot, narrowed to a WORD.
     *
     * @param index the region slot from {@code 0} through {@code 49}
     * @param value the region
     * @throws ArrayIndexOutOfBoundsException if the slot is outside the region route
     */
    void setRegion(int index, int value) {
        this.regions[index] = (short) value;
    }

    /**
     * @param index the waypoint slot from {@code 0} through {@code 49}
     * @return the packed horizontal and vertical WORD coordinates
     * @throws ArrayIndexOutOfBoundsException if the slot is outside the waypoint route
     */
    int getWaypoint(int index) {
        return this.waypoints[index];
    }

    /**
     * Sets a waypoint slot.
     *
     * @param index the waypoint slot from {@code 0} through {@code 49}
     * @param value the packed horizontal and vertical WORD coordinates
     * @throws ArrayIndexOutOfBoundsException if the slot is outside the waypoint route
     */
    void setWaypoint(int index, int value) {
        this.waypoints[index] = value;
    }
}
