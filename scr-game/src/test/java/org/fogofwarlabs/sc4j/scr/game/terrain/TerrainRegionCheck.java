package org.fogofwarlabs.sc4j.scr.game.terrain;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Holds a native region memory image ({@link #STORAGE_SIZE} bytes) and applies it to terrain regions through the saved pathing data
 * ({@link TerrainRegions#readSaveData}); comparisons read the regions back through {@link TerrainRegions#writeSaveData}.
 * <p>
 * The native image and the saved data hold the same fields at other offsets: the region count WORD, the tile-region WORDs, the six-byte split tiles,
 * the region records (72 bytes natively, 64 saved) and the shared neighbor WORDs. The image's neighbor-list pointers, shared-neighbor end and
 * split-tile count are process state; applied regions list their neighbors inline and have no shared neighbors in use and no split tiles.
 */
final class TerrainRegionCheck {
    static final int STORAGE_SIZE = 0xA1670;
    /**
     * The native image: the region count WORD, the tile-region WORDs of the 256-tile rows, the split tile records and the region records.
     */
    static final int NATIVE_REGION_COUNT = 0x00;
    static final int NATIVE_TILE_REGIONS = 0x18;
    static final int NATIVE_PARTIAL_TILES = 0x20018;
    static final int NATIVE_REGIONS = 0x44A08;
    static final int NATIVE_REGION_SIZE = 72;
    static final int TILE_ROW = 256;
    /**
     * The fields of a native region record.
     */
    static final int NATIVE_TERRAIN_TYPE = 0x00;
    static final int NATIVE_COMPONENT = 0x02;
    static final int NATIVE_WALKABLE_NEIGHBORS = 0x06;
    static final int NATIVE_NEIGHBORS = 0x07;
    static final int NATIVE_CENTER_X = 0x18;
    static final int NATIVE_CENTER_Y = 0x1C;
    static final int NATIVE_LEFT_BOUND = 0x20;
    static final int NATIVE_UPPER_BOUND = 0x22;
    static final int NATIVE_RIGHT_BOUND = 0x24;
    static final int NATIVE_LOWER_BOUND = 0x26;
    static final int NATIVE_INLINE_NEIGHBORS = 0x34;
    /**
     * The split tile records: the miniature mask WORD, the first region WORD and the second region WORD.
     */
    static final int PARTIAL_SIZE = 6;
    static final int PARTIAL_FIRST_REGION = Short.BYTES;
    static final int PARTIAL_SECOND_REGION = PARTIAL_FIRST_REGION + Short.BYTES;
    static final int PARTIAL_COUNT = 25000;
    /**
     * The first tile-region identifier that refers to a split tile record.
     */
    static final int PARTIAL_REGION_BASE = 0x2000;
    /**
     * The neighbor list DWORD of a region whose neighbors are inline.
     */
    static final int INLINE_NEIGHBOR_LIST = -1;
    /**
     * The digest algorithm of the captured case comparisons.
     */
    static final @NotNull String DIGEST_ALGORITHM = "SHA-256";

    private static final int NATIVE_SHARED_NEIGHBORS = 0x9C848;

    private static final int SAVED_REGION_COUNT = 0x00;
    private static final int SAVED_SHARED_END = 0x04;
    private static final int SAVED_PARTIAL_COUNT = 0x08;
    private static final int SAVED_TILE_REGIONS = 0x0C;
    private static final int SAVED_PARTIAL_TILES = 0x2000C;
    private static final int SAVED_REGIONS = 0x449FC;
    private static final int SAVED_REGION_SIZE = 0x40;
    private static final int SAVED_NEIGHBOR_LIST = 0x0C;
    private static final int SAVED_SHARED_NEIGHBORS = 0x92BFC;

    private static final int TILE_COUNT = TILE_ROW * TILE_ROW;
    private static final int SHARED_NEIGHBOR_COUNT = 10000;
    private static final int INLINE_NEIGHBOR_COUNT = 10;

    /**
     * The fields of a region record: native offset, saved offset, width in bytes and name, the inline neighbors last.
     */
    private static final int @NotNull [] RECORD_NATIVE_OFFSETS = {0x00, 0x02, 0x04, 0x06, 0x07, 0x18, 0x1C, 0x20, 0x22, 0x24, 0x26, 0x28, 0x29};
    private static final int @NotNull [] RECORD_SAVED_OFFSETS = {0x00, 0x02, 0x04, 0x06, 0x07, 0x10, 0x14, 0x18, 0x1A, 0x1C, 0x1E, 0x20, 0x21};
    private static final int @NotNull [] RECORD_WIDTHS = {2, 2, 2, 1, 1, 4, 4, 2, 2, 2, 2, 1, 1};
    private static final @NotNull String @NotNull [] RECORD_NAMES = {
            "terrainTypes", "components", "tileCounts", "walkableNeighborCounts", "neighborCounts",
            "centerHorizontalFixed", "centerVerticalFixed", "leftBounds", "upperBounds", "rightBounds", "lowerBounds",
            "neighboringComponentCounts", "zeroNeighborCounts"
    };
    private static final int RECORD_SAVED_INLINE_NEIGHBORS = 0x2C;

    /**
     * The pixel geometry of a tile and of its four-by-four miniatures.
     */
    private static final int TILE_SIZE = 32;
    private static final int MINI_TILE_SIZE = 8;
    private static final int MINI_TILES_PER_ROW = 4;
    private static final int MINI_TILE_COORDINATE_MASK = MINI_TILES_PER_ROW - 1;
    /**
     * The split-tile mask bit of miniature {@code 0}; miniature {@code n} has this bit shifted left by {@code n}.
     */
    private static final int MINI_TILE_MASK_BIT = 1;

    /**
     * The empty map and tileset of the regions created for a check.
     */
    private static final int EMPTY_MAP_DIMENSION = 8;
    private static final int EMPTY_MAP_TILE_COUNT = EMPTY_MAP_DIMENSION * EMPTY_MAP_DIMENSION;
    private static final int EMPTY_TILESET_GROUP_COUNT = 3;
    private static final int EMPTY_MEGATILE_FLAGS_SIZE = 32;

    /**
     * Creates a {@link TerrainRegionCheck} instance over new terrain regions, which hold the applied empty image.
     *
     * @return the created {@link TerrainRegionCheck} instance
     * @throws UncheckedIOException if the empty tileset input cannot be parsed
     */
    static @NotNull TerrainRegionCheck create() {
        TerrainRegions regions;

        try {
            regions = createEmptyRegions();
        } catch (IOException exception) {
            throw Exceptions.newUncheckedIOException(exception);
        }

        TerrainRegionCheck check = new TerrainRegionCheck(regions);

        check.apply();

        return check;
    }

    /**
     * Creates a {@link TerrainRegions} instance for an empty eight-by-eight map without contour boundaries.
     *
     * @return the created {@link TerrainRegions} instance
     * @throws IOException if the empty tileset input cannot be parsed
     */
    static @NotNull TerrainRegions createEmptyRegions() throws IOException {
        TerrainTileset tileset = TerrainTileset.parse(new byte[EMPTY_TILESET_GROUP_COUNT * TerrainTileset.CV5_RECORD_SIZE],
                                                      new byte[EMPTY_MEGATILE_FLAGS_SIZE]);
        GameMap map = new GameMap(EMPTY_MAP_DIMENSION, EMPTY_MAP_DIMENSION, 0, new int[EMPTY_MAP_TILE_COUNT], new int[EMPTY_MAP_TILE_COUNT]);

        return TerrainContours.createWithoutBoundaries(map, tileset).getRegions();
    }

    /**
     * Compares terrain regions with a native region memory image.
     *
     * @param expected the native image
     * @param regions  the regions
     */
    static void assertMatches(byte @NotNull [] expected, @NotNull TerrainRegions regions) {
        ByteBuffer saved = ByteBuffer.allocate(TerrainRegions.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        regions.writeSaveData(saved);

        assertMatches(ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN), saved, regions);
    }

    private static void assertMatches(@NotNull ByteBuffer expected, @NotNull ByteBuffer saved, @NotNull TerrainRegions regions) {
        Assertions.assertEquals(Short.toUnsignedInt(expected.getShort(NATIVE_REGION_COUNT)), regions.getRegionCount());

        for (int tile = 0; tile < TILE_COUNT; tile++) {
            assertField(expected, NATIVE_TILE_REGIONS + tile * Short.BYTES,
                        saved, SAVED_TILE_REGIONS + tile * Short.BYTES,
                        Short.BYTES, "tileRegions", tile);
        }

        for (int partial = 0; partial < PARTIAL_COUNT; partial++) {
            int nativePartial = NATIVE_PARTIAL_TILES + partial * PARTIAL_SIZE;
            int savedPartial = SAVED_PARTIAL_TILES + partial * PARTIAL_SIZE;

            assertField(expected, nativePartial, saved, savedPartial, Short.BYTES, "partialTileMasks", partial);
            assertField(expected, nativePartial + PARTIAL_FIRST_REGION,
                        saved, savedPartial + PARTIAL_FIRST_REGION,
                        Short.BYTES, "partialFirstRegions", partial);
            assertField(expected, nativePartial + PARTIAL_SECOND_REGION,
                        saved, savedPartial + PARTIAL_SECOND_REGION,
                        Short.BYTES, "partialSecondRegions", partial);
        }

        for (int region = 0; region < TerrainRegions.REGION_COUNT; region++) {
            int nativeRecord = NATIVE_REGIONS + region * NATIVE_REGION_SIZE;
            int savedRecord = SAVED_REGIONS + region * SAVED_REGION_SIZE;

            for (int field = 0; field < RECORD_NAMES.length; field++) {
                assertField(expected, nativeRecord + RECORD_NATIVE_OFFSETS[field],
                            saved, savedRecord + RECORD_SAVED_OFFSETS[field],
                            RECORD_WIDTHS[field], RECORD_NAMES[field], region);
            }

            for (int neighbor = 0; neighbor < INLINE_NEIGHBOR_COUNT; neighbor++) {
                assertField(expected, nativeRecord + NATIVE_INLINE_NEIGHBORS + neighbor * Short.BYTES,
                            saved, savedRecord + RECORD_SAVED_INLINE_NEIGHBORS + neighbor * Short.BYTES,
                            Short.BYTES, "inlineNeighborRegions", region * INLINE_NEIGHBOR_COUNT + neighbor);
            }
        }

        for (int neighbor = 0; neighbor < SHARED_NEIGHBOR_COUNT; neighbor++) {
            assertField(expected, NATIVE_SHARED_NEIGHBORS + neighbor * Short.BYTES,
                        saved, SAVED_SHARED_NEIGHBORS + neighbor * Short.BYTES,
                        Short.BYTES, "sharedNeighborRegions", neighbor);
        }
    }

    private static void assertField(@NotNull ByteBuffer expected, int nativeOffset,
                                    @NotNull ByteBuffer saved, int savedOffset,
                                    int width, @NotNull String name, int index) {
        int expectedValue = readValue(expected, nativeOffset, width);
        int actualValue = readValue(saved, savedOffset, width);

        if (expectedValue != actualValue) {
            Assertions.assertEquals(expectedValue, actualValue, name + "[" + index + "]");
        }
    }

    private static int readValue(@NotNull ByteBuffer data, int offset, int width) {
        return width == Byte.BYTES ? data.get(offset) : width == Short.BYTES ? data.getShort(offset) : data.getInt(offset);
    }

    private static void copyValue(@NotNull ByteBuffer source, int sourceOffset, @NotNull ByteBuffer destination, int destinationOffset, int width) {
        if (width == Byte.BYTES) {
            destination.put(destinationOffset, source.get(sourceOffset));
        } else if (width == Short.BYTES) {
            destination.putShort(destinationOffset, source.getShort(sourceOffset));
        } else {
            destination.putInt(destinationOffset, source.getInt(sourceOffset));
        }
    }


    private final @NotNull TerrainRegions regions;

    private final @NotNull ByteBuffer storage;
    private boolean pending;

    private TerrainRegionCheck(@NotNull TerrainRegions regions) {
        this.regions = regions;

        this.storage = ByteBuffer.allocate(STORAGE_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        this.pending = false;
    }

    /**
     * @return the regions, with every write since the last use applied
     */
    @NotNull TerrainRegions getRegions() {
        if (this.pending) {
            this.apply();
        }

        return this.regions;
    }

    byte @NotNull [] copyStorage() {
        return this.storage.array().clone();
    }

    int readUnsignedWord(int offset) {
        return Short.toUnsignedInt(this.storage.getShort(offset));
    }


    void writeByte(int offset, int value) {
        this.storage.put(offset, (byte) value);
        this.pending = true;
    }

    void writeWord(int offset, int value) {
        this.storage.putShort(offset, (short) value);
        this.pending = true;
    }

    void writeInteger(int offset, int value) {
        this.storage.putInt(offset, value);
        this.pending = true;
    }

    void load(byte @NotNull [] image) {
        if (image.length != STORAGE_SIZE) {
            throw Exceptions.newIllegalArgumentException("Unexpected terrain capture length.");
        }

        this.storage.clear().put(image);
        this.pending = true;
    }


    /**
     * Looks up the region of a pixel position in the image the way the native lookup reads it, and checks that the regions, with every write applied,
     * return the same region where the image has a tile and a represented split tile.
     *
     * @param positionX the horizontal pixel position
     * @param positionY the vertical pixel position
     * @return the unsigned region identifier of the image
     * @throws UnsupportedOperationException if the tile lies outside the image
     */
    int lookupRegion(int positionX, int positionY) {
        long tileIndex = (long) (positionY / TILE_SIZE) * TILE_ROW + positionX / TILE_SIZE;
        long tileOffset = NATIVE_TILE_REGIONS + tileIndex * Short.BYTES;

        if (tileOffset < 0 || tileOffset + Short.BYTES > STORAGE_SIZE) {
            throw Exceptions.newUnsupportedOperationException("Native region lookup references unrepresented tile memory.");
        }

        int region = this.readUnsignedWord((int) tileOffset);
        int result = region;

        if (region >= PARTIAL_REGION_BASE) {
            int miniatureIndex = (positionX / MINI_TILE_SIZE & MINI_TILE_COORDINATE_MASK) +
                                 (positionY / MINI_TILE_SIZE & MINI_TILE_COORDINATE_MASK) * MINI_TILES_PER_ROW;
            int partialOffset = NATIVE_PARTIAL_TILES + (region - PARTIAL_REGION_BASE) * PARTIAL_SIZE;
            int mask = this.readUnsignedWord(partialOffset);

            result = this.readUnsignedWord(partialOffset +
                                           ((mask & (MINI_TILE_MASK_BIT << miniatureIndex)) != 0 ? PARTIAL_SECOND_REGION : PARTIAL_FIRST_REGION));
        }

        if (tileIndex >= 0 && tileIndex < TILE_COUNT && region < PARTIAL_REGION_BASE + PARTIAL_COUNT) {
            Assertions.assertEquals(result, TerrainRegionLookupUtils.lookup(this.getRegions(), positionX, positionY));
        }

        return result;
    }

    /**
     * Compares the regions, with every write applied, with the image.
     */
    void assertMatchesDomain() {
        assertMatches(this.storage.array(), this.getRegions());
    }

    /**
     * Translates the image into saved pathing data and reads it into the regions.
     */
    private void apply() {
        ByteBuffer saved = ByteBuffer.allocate(TerrainRegions.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        saved.putShort(SAVED_REGION_COUNT, this.storage.getShort(NATIVE_REGION_COUNT));
        saved.putInt(SAVED_SHARED_END, 0);
        saved.putInt(SAVED_PARTIAL_COUNT, 0);

        for (int tile = 0; tile < TILE_COUNT; tile++) {
            copyValue(this.storage, NATIVE_TILE_REGIONS + tile * Short.BYTES, saved, SAVED_TILE_REGIONS + tile * Short.BYTES, Short.BYTES);
        }

        for (int partial = 0; partial < PARTIAL_COUNT; partial++) {
            int nativePartial = NATIVE_PARTIAL_TILES + partial * PARTIAL_SIZE;
            int savedPartial = SAVED_PARTIAL_TILES + partial * PARTIAL_SIZE;

            for (int word = 0; word < PARTIAL_SIZE; word += Short.BYTES) {
                copyValue(this.storage, nativePartial + word, saved, savedPartial + word, Short.BYTES);
            }
        }

        for (int region = 0; region < TerrainRegions.REGION_COUNT; region++) {
            int nativeRecord = NATIVE_REGIONS + region * NATIVE_REGION_SIZE;
            int savedRecord = SAVED_REGIONS + region * SAVED_REGION_SIZE;

            for (int field = 0; field < RECORD_NAMES.length; field++) {
                copyValue(this.storage, nativeRecord + RECORD_NATIVE_OFFSETS[field],
                          saved, savedRecord + RECORD_SAVED_OFFSETS[field],
                          RECORD_WIDTHS[field]);
            }

            saved.putInt(savedRecord + SAVED_NEIGHBOR_LIST, INLINE_NEIGHBOR_LIST);

            for (int neighbor = 0; neighbor < INLINE_NEIGHBOR_COUNT; neighbor++) {
                copyValue(this.storage, nativeRecord + NATIVE_INLINE_NEIGHBORS + neighbor * Short.BYTES,
                          saved, savedRecord + RECORD_SAVED_INLINE_NEIGHBORS + neighbor * Short.BYTES,
                          Short.BYTES);
            }
        }

        for (int neighbor = 0; neighbor < SHARED_NEIGHBOR_COUNT; neighbor++) {
            copyValue(this.storage, NATIVE_SHARED_NEIGHBORS + neighbor * Short.BYTES,
                      saved, SAVED_SHARED_NEIGHBORS + neighbor * Short.BYTES,
                      Short.BYTES);
        }

        this.regions.readSaveData(saved);
        this.pending = false;
    }
}
