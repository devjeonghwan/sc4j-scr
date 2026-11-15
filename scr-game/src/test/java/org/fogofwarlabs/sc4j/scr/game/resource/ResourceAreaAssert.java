package org.fogofwarlabs.sc4j.scr.game.resource;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Loads and compares captured native resource-area tables, which have the saved area table layout of
 * {@link ResourceAreas#writeSaveRecords}: {@code 48}-byte records, then the area count and the update countdown DWORDs.
 */
public final class ResourceAreaAssert {
    private static final int RECORD_SIZE = 48;
    private static final int AREA_COUNT = ResourceAreas.LAST_AREA + 1;
    private static final int AREA_COUNT_OFFSET = 0x2EE0;
    private static final int UPDATE_COUNTDOWN_OFFSET = 0x2EE4;

    /**
     * The modeled fields of a record: offset, size and name; the bytes from {@code 16} on are record padding.
     */
    private static final int @NotNull [] FIELD_OFFSETS = {0, 2, 4, 5, 6, 7, 8, 12};
    private static final int @NotNull [] FIELD_SIZES = {2, 2, 1, 1, 1, 1, 4, 4};
    private static final String @NotNull [] FIELD_NAMES = {
            "horizontal", "vertical", "minerals", "gas", "start", "occupancy", "mineral total", "gas total"
    };

    /**
     * Loads the resource-area table from an original snapshot through {@link ResourceAreas#readSaveRecords}.
     *
     * @param areas           the resource areas receiving the snapshot fields
     * @param capturedRecords the original little-endian resource-area records
     * @throws IllegalArgumentException if the snapshot byte length is invalid
     */
    public static void load(@NotNull ResourceAreas areas, byte @NotNull [] capturedRecords) {
        if (capturedRecords.length != ResourceAreas.SAVED_SIZE) {
            throw Exceptions.newIllegalArgumentException("Resource-area snapshot has an unexpected byte length.");
        }

        areas.readSaveRecords(ByteBuffer.wrap(capturedRecords).order(ByteOrder.LITTLE_ENDIAN));
    }

    /**
     * Serializes the resource-area table through {@link ResourceAreas#writeSaveRecords} into zero-initialized records.
     *
     * @param areas the resource areas to read
     * @return the serialized little-endian resource-area records
     */
    public static byte @NotNull [] createSnapshot(@NotNull ResourceAreas areas) {
        byte[] result = new byte[ResourceAreas.SAVED_SIZE];

        areas.writeSaveRecords(ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN));

        return result;
    }

    /**
     * Compares every modeled resource-area field, the area count and the update countdown with the original records.
     *
     * @param expected the original little-endian resource-area records
     * @param areas    the resource areas to compare
     */
    public static void assertKnownFields(byte @NotNull [] expected, @NotNull ResourceAreas areas) {
        ByteBuffer records = ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer actual = ByteBuffer.wrap(createSnapshot(areas)).order(ByteOrder.LITTLE_ENDIAN);

        for (int area = 0; area < AREA_COUNT; area++) {
            for (int field = 0; field < FIELD_OFFSETS.length; field++) {
                int offset = area * RECORD_SIZE + FIELD_OFFSETS[field];

                Assertions.assertEquals(readField(records, offset, FIELD_SIZES[field]), readField(actual, offset, FIELD_SIZES[field]),
                                        FIELD_NAMES[field] + " area " + area);
            }
        }

        Assertions.assertEquals(records.getInt(AREA_COUNT_OFFSET), areas.getAreaCount());
        Assertions.assertEquals(records.getInt(UPDATE_COUNTDOWN_OFFSET), actual.getInt(UPDATE_COUNTDOWN_OFFSET), "update countdown");
    }

    private static int readField(@NotNull ByteBuffer records, int offset, int size) {
        return switch (size) {
            case Byte.BYTES -> records.get(offset);
            case Short.BYTES -> records.getShort(offset);
            default -> records.getInt(offset);
        };
    }


    private ResourceAreaAssert() {
    }
}
