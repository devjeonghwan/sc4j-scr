package org.fogofwarlabs.sc4j.scr.game.path;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Compares path records with captured records in the saved path record layout ({@link PathPool#writeSaveRecords}).
 */
public final class PathPoolAssert {
    /**
     * The names and the saved record offsets of the DWORD fields.
     */
    private static final @NotNull String @NotNull [] INTEGER_FIELDS = {
            "sourcePositionOrFreeLink", "waypointPositionOrIdentifier", "destinations", "creationFrames", "savedSpeeds"
    };
    private static final int @NotNull [] INTEGER_OFFSETS = {0, 4, 8, 12, 20};

    /**
     * The names and the saved record offsets of the BYTE fields.
     */
    private static final @NotNull String @NotNull [] BYTE_FIELDS = {
            "flags", "timers", "fullRegionCounts", "regionCounts", "regionIndices", "waypointCounts", "waypointIndices"
    };
    private static final int @NotNull [] BYTE_OFFSETS = {24, 25, 27, 28, 29, 30, 31};

    /**
     * The offset and the number of the route WORDs in a saved record.
     */
    private static final int ROUTE_OFFSET = 32;
    private static final int ROUTE_CELL_COUNT = 48;
    /**
     * The number of saved path records.
     */
    private static final int RECORD_COUNT = PathPool.SAVED_SIZE / PathPool.SAVED_RECORD_SIZE;

    /**
     * The offset of the region count BYTE in a saved record.
     */
    private static final int REGION_COUNT_OFFSET = 28;

    /**
     * Serializes every path record in the saved record layout.
     *
     * @param pool the path pool
     * @return the {@link PathPool#SAVED_SIZE} bytes, little-endian
     */
    public static @NotNull ByteBuffer serialize(@NotNull PathPool pool) {
        ByteBuffer records = ByteBuffer.allocate(PathPool.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        pool.writeSaveRecords(records);

        return records;
    }

    /**
     * Reads the source position or free-list link of a record, the DWORD at the start of the saved record.
     *
     * @param pool        the path pool
     * @param recordIndex the record index
     * @return the source position or free-list link
     */
    public static int readSourcePositionOrFreeLink(@NotNull PathPool pool, int recordIndex) {
        return serialize(pool).getInt(recordIndex * PathPool.SAVED_RECORD_SIZE);
    }

    /**
     * Reads the region count BYTE of a saved record.
     *
     * @param pool        the path pool
     * @param recordIndex the record index
     * @return the region count
     */
    public static int readRegionCount(@NotNull PathPool pool, int recordIndex) {
        return serialize(pool).get(recordIndex * PathPool.SAVED_RECORD_SIZE + REGION_COUNT_OFFSET);
    }

    /**
     * Compares every captured record with the saved record of the same index.
     *
     * @param captured the captured records, one saved record after another
     * @param pool     the path pool
     */
    static void assertMatches(byte @NotNull [] captured, @NotNull PathPool pool) {
        ByteBuffer records = serialize(pool);

        for (int recordIndex = 0; recordIndex < RECORD_COUNT; recordIndex++) {
            assertRecordMatches(captured, recordIndex * PathPool.SAVED_RECORD_SIZE, records, recordIndex);
        }
    }

    /**
     * Compares a captured record with a saved path record at the offsets the capture holds: the source, waypoint, destination, creation frame and
     * speed DWORDs, the flag, timer and region/waypoint BYTEs and the route WORDs.
     *
     * @param captured    the captured record bytes
     * @param offset      the captured record offset
     * @param pool        the path pool
     * @param recordIndex the record index
     */
    public static void assertRecordMatches(byte @NotNull [] captured, int offset, @NotNull PathPool pool, int recordIndex) {
        assertRecordMatches(captured, offset, serialize(pool), recordIndex);
    }

    private static void assertRecordMatches(byte @NotNull [] captured, int offset, @NotNull ByteBuffer records, int recordIndex) {
        ByteBuffer capturedRecords = ByteBuffer.wrap(captured).order(ByteOrder.LITTLE_ENDIAN);
        int recordOffset = recordIndex * PathPool.SAVED_RECORD_SIZE;

        for (int index = 0; index < INTEGER_FIELDS.length; index++) {
            Assertions.assertEquals(capturedRecords.getInt(offset + INTEGER_OFFSETS[index]), records.getInt(recordOffset + INTEGER_OFFSETS[index]),
                                    INTEGER_FIELDS[index]);
        }

        for (int index = 0; index < BYTE_FIELDS.length; index++) {
            Assertions.assertEquals(capturedRecords.get(offset + BYTE_OFFSETS[index]), records.get(recordOffset + BYTE_OFFSETS[index]),
                                    BYTE_FIELDS[index]);
        }

        for (int cell = 0; cell < ROUTE_CELL_COUNT; cell++) {
            Assertions.assertEquals(capturedRecords.getShort(offset + ROUTE_OFFSET + cell * Short.BYTES),
                                    records.getShort(recordOffset + ROUTE_OFFSET + cell * Short.BYTES),
                                    "route cell " + cell);
        }
    }


    /**
     * Creates a {@link PathPoolAssert} instance.
     */
    private PathPoolAssert() {
    }
}
