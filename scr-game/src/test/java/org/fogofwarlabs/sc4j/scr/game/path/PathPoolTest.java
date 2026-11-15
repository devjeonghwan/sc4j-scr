package org.fogofwarlabs.sc4j.scr.game.path;

import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks path pool operations, route count clamps, route region reads past a record end, shared route cells across release and reuse, route
 * copies and index validation.
 */
final class PathPoolTest {
    /**
     * The number of captured route count clamp cases and the first case with large route counts.
     */
    private static final int ROUTE_CASE_COUNT = 512;
    private static final int FIRST_LARGE_COUNT_CASE = 64;
    /**
     * The case index multipliers of the large region and waypoint counts.
     */
    private static final int LARGE_REGION_COUNT_MULTIPLIER = 131;
    private static final int LARGE_WAYPOINT_COUNT_MULTIPLIER = 257;
    /**
     * The largest region or waypoint count a route request holds.
     */
    private static final int MAXIMUM_ROUTE_COUNT = 50;
    /**
     * The bytes added to the route WORD bytes: the {@code 32} header bytes and {@code 3} bytes that round the sum up to a DWORD.
     */
    private static final int ROUTE_HEADER_AND_ROUNDING_BYTES = 0x23;
    /**
     * The mask that rounds the requested bytes down to a DWORD multiple.
     */
    private static final int DWORD_ALIGNMENT_MASK = 0xFFFC;
    /**
     * The route WORDs of one waypoint.
     */
    private static final int WAYPOINT_ROUTE_WORDS = 2;
    /**
     * The largest number of record bytes a route may request.
     */
    private static final int MAXIMUM_ROUTE_BYTES = 128;
    /**
     * Half the route WORDs: the waypoint count from which a clamped route keeps one waypoint less and one region.
     */
    private static final int HALF_ROUTE_WORDS = 24;
    /**
     * The mask that reads a route count as an unsigned WORD.
     */
    private static final int WORD_MASK = 0xFFFF;

    /**
     * The size of the saved path records.
     */
    private static final int SAVED_STORAGE_SIZE = 131072;
    /**
     * The number of captured allocations and the interval of the allocations the capture skips.
     */
    private static final int ALLOCATION_COUNT = 1030;
    private static final int SKIPPED_ALLOCATION_INTERVAL = 257;
    /**
     * The waypoint slot and the waypoint pattern written before a record is released.
     */
    private static final int RELEASED_WAYPOINT_SLOT = 23;
    private static final int RELEASED_WAYPOINT_PATTERN = 0xDEADBEEF;

    /**
     * The route position values of the record that follows the allocated record.
     */
    private static final int FOLLOWING_SOURCE_X = 0x1234;
    private static final int FOLLOWING_SOURCE_Y = 0x0567;
    /**
     * The index of the last path record.
     */
    private static final int LAST_RECORD_INDEX = 1023;
    /**
     * A waypoint count and a region index that address route cell {@code 48}, the first WORD of the following record.
     */
    private static final int TAIL_WAYPOINT_COUNT = 19;
    private static final int TAIL_REGION_INDEX = 10;
    /**
     * Another waypoint count and region index that address route cell {@code 48}.
     */
    private static final int LONG_ROUTE_WAYPOINT_COUNT = 23;
    private static final int LONG_ROUTE_REGION_INDEX = 2;
    /**
     * The region index that addresses route cell {@code 49} with {@link #TAIL_WAYPOINT_COUNT} waypoints.
     */
    private static final int PAST_TAIL_REGION_INDEX = 11;

    /**
     * The waypoint capacity and the route cells of a path record.
     */
    private static final int WAYPOINT_CAPACITY = 24;
    private static final int ROUTE_CELL_COUNT = 48;
    /**
     * The base values of the request waypoints and regions.
     */
    private static final int REQUEST_WAYPOINT_BASE = 0xFEDC0000;
    private static final int REQUEST_REGION_BASE = 0x8100;
    /**
     * The waypoint that fills a record before a copy.
     */
    private static final int PRESET_WAYPOINT = 0x76543210;
    /**
     * The seed of the state hash that the rejected calls must keep.
     */
    private static final int STATE_HASH_SEED = 17;

    /**
     * The source position of the shared route cell test.
     */
    private static final int SOURCE_X = 0xABCD;
    private static final int SOURCE_Y = 0xEF12;
    /**
     * The waypoint slot and the waypoint count of the shared route cell test: with one waypoint, the first region cell is the low WORD of waypoint
     * slot {@code 1}.
     */
    private static final int SHARED_WAYPOINT_SLOT = 1;
    private static final int SHARED_WAYPOINT_COUNT = 1;
    /**
     * The waypoint and the destination that the shared route cell test writes before the record is released.
     */
    private static final int SHARED_WAYPOINT = 0xFEDCBA98;
    private static final int SHARED_DESTINATION = 0x12345678;

    /**
     * Computes the record bytes a route of the given counts requests.
     *
     * @param regionCount   the region count
     * @param waypointCount the waypoint count
     * @return the requested record bytes
     */
    private static int computeRequestedBytes(int regionCount, int waypointCount) {
        return ((regionCount + waypointCount * WAYPOINT_ROUTE_WORDS) * Short.BYTES + ROUTE_HEADER_AND_ROUNDING_BYTES) & DWORD_ALIGNMENT_MASK;
    }


    @Test
    void matches_native_route_count_clamp_cases() throws IOException {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_ROUTE_ALLOCATION);
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);

        for (int caseIndex = 0; caseIndex < ROUTE_CASE_COUNT; caseIndex++) {
            PathPool pool = new PathPool();
            short[] counts = {(short) (caseIndex >= FIRST_LARGE_COUNT_CASE ? caseIndex * LARGE_REGION_COUNT_MULTIPLIER : caseIndex),
                              (short) (caseIndex >= FIRST_LARGE_COUNT_CASE ? caseIndex * LARGE_WAYPOINT_COUNT_MULTIPLIER : caseIndex / 2)};
            short expectedRegions = expected.getShort();
            short expectedWaypoints = expected.getShort();
            int expectedRecord = expected.getInt();

            if (counts[0] >= 0 && counts[0] <= MAXIMUM_ROUTE_COUNT && (counts[1] & WORD_MASK) <= MAXIMUM_ROUTE_COUNT) {
                Assertions.assertEquals(expectedRecord, pool.allocateForRoute(counts), "allocation case=" + caseIndex);

                Assertions.assertEquals(expectedRegions, counts[0], "region case=" + caseIndex);
                Assertions.assertEquals(expectedWaypoints, counts[1], "waypoint case=" + caseIndex);
            } else {
                Assertions.assertThrows(IllegalArgumentException.class, () -> pool.allocateForRoute(counts));

                int requestedBytes = computeRequestedBytes(counts[0], counts[1] & WORD_MASK);

                if (requestedBytes > MAXIMUM_ROUTE_BYTES) {
                    if (counts[1] >= HALF_ROUTE_WORDS) {
                        counts[1] = HALF_ROUTE_WORDS - 1;
                        counts[0] = 1;
                    } else {
                        counts[0] = (short) ((HALF_ROUTE_WORDS - counts[1]) * WAYPOINT_ROUTE_WORDS);
                    }

                    requestedBytes = computeRequestedBytes(counts[0], counts[1]);
                }

                Assertions.assertEquals(expectedRegions, counts[0]);
                Assertions.assertEquals(expectedWaypoints, counts[1]);
                Assertions.assertEquals(expectedRecord, requestedBytes > MAXIMUM_ROUTE_BYTES ? -1 : 1);
            }
        }

        Assertions.assertFalse(expected.hasRemaining());
    }

    @Test
    void matches_native_path_pool_operation_cases() throws IOException {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_PATH_POOL);
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        PathPool pool = new PathPool();
        byte[] initial = new byte[SAVED_STORAGE_SIZE];

        expected.get(initial);

        PathPoolAssert.assertMatches(initial, pool);

        Assertions.assertEquals(1, pool.getFirstFreeIndex());

        for (int operationIndex = 0; operationIndex < ALLOCATION_COUNT; operationIndex++) {
            Assertions.assertEquals(expected.getInt(), operationIndex % SKIPPED_ALLOCATION_INTERVAL == 0 ? -1 : pool.allocate());

            Assertions.assertEquals(expected.getInt(), pool.getFirstFreeIndex());
        }

        for (int recordIndex : new int[]{1, 500, LAST_RECORD_INDEX, 0}) {
            pool.setWaypoint(recordIndex, RELEASED_WAYPOINT_SLOT, recordIndex ^ RELEASED_WAYPOINT_PATTERN);

            pool.release(recordIndex);

            Assertions.assertEquals(-1, expected.getInt());
            Assertions.assertEquals(expected.getInt(), pool.getFirstFreeIndex());
        }

        for (int requestedBytes : new int[]{-1, 0, 129, 64, 128, 128}) {
            Assertions.assertEquals(expected.getInt(), requestedBytes > MAXIMUM_ROUTE_BYTES ? -1 : pool.allocate());

            Assertions.assertEquals(expected.getInt(), pool.getFirstFreeIndex());
        }

        byte[] finalStorage = new byte[SAVED_STORAGE_SIZE];

        expected.get(finalStorage);

        PathPoolAssert.assertMatches(finalStorage, pool);

        Assertions.assertFalse(expected.hasRemaining());
    }

    @Test
    void get_route_region_should_read_the_following_record_past_the_tail_end() {
        PathPool pool = new PathPool();
        int first = pool.allocate();

        pool.setSourceX(first + 1, FOLLOWING_SOURCE_X);
        pool.setSourceY(first + 1, FOLLOWING_SOURCE_Y);

        Assertions.assertEquals(FOLLOWING_SOURCE_X, pool.getRouteRegion(first, TAIL_WAYPOINT_COUNT, TAIL_REGION_INDEX),
                                "Cell 48 is the following record's first WORD.");
        Assertions.assertEquals(FOLLOWING_SOURCE_X, pool.getRouteRegion(first, LONG_ROUTE_WAYPOINT_COUNT, LONG_ROUTE_REGION_INDEX));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> pool.getRouteRegion(first, TAIL_WAYPOINT_COUNT, PAST_TAIL_REGION_INDEX));
        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> pool.getRouteRegion(LAST_RECORD_INDEX, TAIL_WAYPOINT_COUNT, TAIL_REGION_INDEX));
    }

    @Test
    void shared_route_cells_survive_release_and_reuse() {
        PathPool pool = new PathPool();
        int record = pool.allocate();

        pool.setSourceX(record, SOURCE_X);
        pool.setSourceY(record, SOURCE_Y);

        Assertions.assertEquals(0xEF12ABCD, PathPoolAssert.readSourcePositionOrFreeLink(pool, record));

        pool.setWaypoint(record, SHARED_WAYPOINT_SLOT, SHARED_WAYPOINT);
        pool.setWaypointCount(record, SHARED_WAYPOINT_COUNT);
        pool.setDestination(record, SHARED_DESTINATION);

        Assertions.assertEquals((short) 0xBA98, pool.getRouteRegion(record, SHARED_WAYPOINT_COUNT, 0));

        pool.release(record);

        Assertions.assertEquals(record, pool.allocate());

        Assertions.assertEquals(SHARED_DESTINATION, pool.getDestination(record));
        Assertions.assertEquals(SHARED_WAYPOINT, pool.getWaypoint(record, SHARED_WAYPOINT_SLOT));
    }

    @Test
    void route_copies_write_every_bounded_prefix_and_preserve_remaining_cells() {
        PathRequest request = new PathRequest();

        for (int index = 0; index < MAXIMUM_ROUTE_COUNT; index++) {
            request.setWaypoint(index, REQUEST_WAYPOINT_BASE + index);
            request.setRegion(index, REQUEST_REGION_BASE + index);
        }

        for (int record : new int[]{0, 1, LAST_RECORD_INDEX}) {
            for (int count = 0; count <= WAYPOINT_CAPACITY; count++) {
                PathPool pool = new PathPool();

                for (int index = 0; index < WAYPOINT_CAPACITY; index++) {
                    pool.setWaypoint(record, index, PRESET_WAYPOINT);
                }

                pool.copyWaypointsFrom(request, record, count);

                for (int index = 0; index < WAYPOINT_CAPACITY; index++) {
                    Assertions.assertEquals(index < count ? request.getWaypoint(index) : PRESET_WAYPOINT, pool.getWaypoint(record, index));
                }

                int regionCount = ROUTE_CELL_COUNT - count * WAYPOINT_ROUTE_WORDS;

                pool.copyRegionsFrom(request, record, count, regionCount);

                for (int index = 0; index < regionCount; index++) {
                    Assertions.assertEquals(request.getRegion(index), pool.getRouteRegion(record, count, index));
                }
            }
        }
    }

    @Test
    void path_pool_rejects_invalid_indices_and_counts_before_mutation() {
        PathPool pool = new PathPool();
        PathRequest request = new PathRequest();
        int hash = pool.computeStateHash(STATE_HASH_SEED);

        for (int index : new int[]{-1, LAST_RECORD_INDEX + 1, Integer.MAX_VALUE, 1 << 25}) {
            Assertions.assertThrows(IndexOutOfBoundsException.class, () -> pool.release(index));
            Assertions.assertThrows(IndexOutOfBoundsException.class, () -> pool.copyWaypointsFrom(request, index, 1));
        }

        for (int count : new int[]{-1, 25, 58, Integer.MAX_VALUE}) {
            Assertions.assertThrows(IndexOutOfBoundsException.class, () -> pool.copyWaypointsFrom(request, 1, count));
        }

        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> pool.copyRegionsFrom(request, 1, WAYPOINT_CAPACITY, 1));
        Assertions.assertThrows(NullPointerException.class, () -> pool.copyWaypointsFrom(null, 1, 0));

        Assertions.assertEquals(hash, pool.computeStateHash(STATE_HASH_SEED));

        pool.setWaypoint(1, WAYPOINT_CAPACITY - 1, 1);

        Assertions.assertEquals(1, pool.getWaypoint(1, WAYPOINT_CAPACITY - 1));
    }
}
