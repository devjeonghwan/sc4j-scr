package org.fogofwarlabs.sc4j.scr.game.terrain;

import org.fogofwarlabs.sc4j.engine.math.IntegerMathUtils;
import org.fogofwarlabs.sc4j.engine.path.RegionRouteSearch;
import org.jetbrains.annotations.NotNull;

/**
 * Retains bounded region search storage for movement routes and resource-area distance queries: the SC:R view over the engine region route
 * search ({@link RegionRouteSearch}) on the SC:R regions, which are its region graph, with the SC:R distance argument order.
 */
public final class RegionPathSearch {
    /**
     * The route count of a direct route, which holds only the start and the end region.
     */
    public static final int DIRECT_ROUTE_COUNT = RegionRouteSearch.DIRECT_ROUTE_COUNT;

    /**
     * Approximates integer distance while retaining DWORD overflow ({@link IntegerMathUtils#getApproximateDistance}, in the SC:R argument order).
     *
     * @param firstHorizontal  the first horizontal coordinate
     * @param secondHorizontal the second horizontal coordinate
     * @param firstVertical    the first vertical coordinate
     * @param secondVertical   the second vertical coordinate
     * @return the original distance DWORD
     */
    public static int calculateDistance(int firstHorizontal, int secondHorizontal, int firstVertical, int secondVertical) {
        return IntegerMathUtils.getApproximateDistance(firstHorizontal, firstVertical, secondHorizontal, secondVertical);
    }

    private final @NotNull RegionRouteSearch search;

    /**
     * Creates a {@link RegionPathSearch} instance with reusable region-graph search storage.
     *
     * @param regions the original region records and ordered neighbor lists
     */
    public RegionPathSearch(@NotNull TerrainRegions regions) {
        this.search = new RegionRouteSearch(regions);
    }

    /**
     * Sets whether searches skip a region when traversing neighbor lists. The retained lists remain unchanged.
     *
     * @param region   the region index
     * @param excluded whether searches skip the region as a neighbor
     */
    public void setExcludedRegion(int region, boolean excluded) {
        this.search.setExcludedRegion(region, excluded);
    }

    /**
     * @param index the zero-based position below {@link #getRouteCount()}
     * @return the region identifier at the requested route position
     * @throws IndexOutOfBoundsException if the position is outside the current route
     */
    public int getRouteRegion(int index) {
        return this.search.getRouteRegion(index);
    }

    /**
     * @return the original path count clamped to the route capacity {@code 50}
     */
    public int getRouteCount() {
        return this.search.getRouteCount();
    }

    /**
     * @return the full predecessor count before the original BYTE truncation
     */
    public int getFullRouteCount() {
        return this.search.getFullRouteCount();
    }

    /**
     * @return the original allocated-node WORD
     */
    public int getNodeCount() {
        return this.search.getNodeCount();
    }

    /**
     * @return the original maximum heap-size WORD
     */
    public int getMaximumHeapCount() {
        return this.search.getMaximumHeapCount();
    }

    /**
     * @return {@code true} if exhausting the heap required the closest discovered region, {@code false} otherwise
     */
    public boolean isIncomplete() {
        return this.search.isIncomplete();
    }


    /**
     * Searches a route between the regions of two positions, including reverse search and reachable-component fallback. The original heap tie order,
     * {@code 125}-entry stop and {@code 350}-node stop are retained.
     *
     * @param sourceHorizontal      the source signed pixel WORD
     * @param sourceVertical        the source signed pixel WORD
     * @param destinationHorizontal the destination signed pixel WORD
     * @param destinationVertical   the destination signed pixel WORD
     * @return {@code true} if the search produces a nonempty route, {@code false} otherwise
     */
    public boolean search(int sourceHorizontal, int sourceVertical, int destinationHorizontal, int destinationVertical) {
        return this.search.search(sourceHorizontal, sourceVertical, destinationHorizontal, destinationVertical);
    }

    /**
     * Searches a route between explicit source and destination regions, the request's region WORDs.
     * <p>
     * Callers such as the computer choke search store region centers as positions while supplying the regions themselves, which need not contain
     * their own centers.
     *
     * @param source                the source region WORD
     * @param sourceHorizontal      the source signed pixel WORD
     * @param sourceVertical        the source signed pixel WORD
     * @param destination           the destination region WORD
     * @param destinationHorizontal the destination signed pixel WORD
     * @param destinationVertical   the destination signed pixel WORD
     * @return {@code true} if the search produces a nonempty route, {@code false} otherwise
     */
    public boolean search(int source, int sourceHorizontal, int sourceVertical, int destination, int destinationHorizontal, int destinationVertical) {
        return this.search.search(source, sourceHorizontal, sourceVertical, destination, destinationHorizontal, destinationVertical);
    }
}
