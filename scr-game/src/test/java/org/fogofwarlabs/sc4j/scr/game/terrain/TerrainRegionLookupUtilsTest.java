package org.fogofwarlabs.sc4j.scr.game.terrain;

import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Checks region lookup with signed coordinates and retained partial records.
 */
final class TerrainRegionLookupUtilsTest {
    private static final int TILE_SIZE = 32;
    private static final int LOOKUP_MAP_WIDTH = 8;
    private static final int LOOKUP_MAP_HEIGHT = 6;
    private static final int LOOKUP_PIXEL_WIDTH = LOOKUP_MAP_WIDTH * TILE_SIZE;
    private static final int LOOKUP_PIXEL_HEIGHT = LOOKUP_MAP_HEIGHT * TILE_SIZE;
    private static final int LOOKUP_CASE_COUNT = 1024;
    private static final int CASE_REGION_COUNT = 4;
    /**
     * The tile identifiers of the lookup cases: identifiers below {@link #CASE_REGION_COUNT} are regions, the others partial records.
     */
    private static final int CASE_IDENTIFIER_COUNT = 7;
    /**
     * The mask of a tile region among the case regions.
     */
    private static final int CASE_REGION_MASK = CASE_REGION_COUNT - 1;
    /**
     * The number of coordinates of a destination.
     */
    private static final int DESTINATION_COORDINATE_COUNT = 2;
    /**
     * The fixed leading and trailing DWORDs of each digested destination-to-region record.
     */
    private static final int RECORD_LEADING_DWORD = 1;
    private static final int RECORD_TRAILING_DWORD = 0x01000000;
    /**
     * The margin of the case destinations around the lookup map and the destination ranges with that margin on both sides.
     */
    private static final int DESTINATION_MARGIN = TILE_SIZE;
    private static final int DESTINATION_RANGE_WIDTH = DESTINATION_MARGIN + LOOKUP_PIXEL_WIDTH + DESTINATION_MARGIN;
    private static final int DESTINATION_RANGE_HEIGHT = DESTINATION_MARGIN + LOOKUP_PIXEL_HEIGHT + DESTINATION_MARGIN;
    /**
     * The fixed-point shift of a region center.
     */
    private static final int FIXED_POSITION_SHIFT = 8;
    /**
     * The terrain type of a blocked region.
     */
    private static final int BLOCKED_TERRAIN = 0x1FFD;

    private static final int TILESET_GROUP_COUNT = 3;
    private static final int TILESET_TILE_COUNT = 16;
    private static final int MINIATURES_PER_TILE = 16;
    private static final int MEGATILE_FLAGS_SIZE = MINIATURES_PER_TILE * Short.BYTES;
    /**
     * The mask of a tile identifier among the tileset tiles.
     */
    private static final int TILESET_IDENTIFIER_MASK = TILESET_TILE_COUNT - 1;
    /**
     * The bit of a raw megatile index above the masked megatile index.
     */
    private static final int RAW_HIGH_MEGATILE_BIT = 0x8000;

    private static final int REMAP_CASE_COUNT = 64;
    private static final int REMAP_MAP_DIMENSION = 8;
    private static final int REMAP_TILE_COUNT = REMAP_MAP_DIMENSION * REMAP_MAP_DIMENSION;
    private static final int MINI_TILES_PER_ROW = 4;
    private static final int MINI_GRID_WIDTH = 1024;
    private static final int MINI_BLOCKED_SHIFT = 7;
    /**
     * The image header bytes before the tile-region WORDs that the remapping cases clear.
     */
    private static final int HEADER_POINTERS = 0x10;
    /**
     * The byte that the remapping cases fill the rest of the image with.
     */
    private static final int FILL_BYTE = 0xA5;

    /**
     * The last tile of the tile-region WORDs.
     */
    private static final int LAST_TILE = TerrainRegionCheck.TILE_ROW * TerrainRegionCheck.TILE_ROW - 1;
    /**
     * The pixel extent of the tile-region rows and columns and the mask of a pixel coordinate inside it.
     */
    private static final int TILE_REGION_PIXEL_EXTENT = TerrainRegionCheck.TILE_ROW * TILE_SIZE;
    private static final int PIXEL_COORDINATE_MASK = TILE_REGION_PIXEL_EXTENT - 1;
    /**
     * The number of sampled positions of the region lookup cases.
     */
    private static final int SAMPLED_POSITION_COUNT = 65536;

    @Test
    void matches_native_adjust_destination_to_region_cases() throws NoSuchAlgorithmException {
        TerrainRegionCheck check = TerrainRegionCheck.create();
        MessageDigest digest = MessageDigest.getInstance(TerrainRegionCheck.DIGEST_ALGORITHM);
        ByteBuffer output = ByteBuffer.allocate(Integer.BYTES + DESTINATION_COORDINATE_COUNT * Short.BYTES + Integer.BYTES)
                                      .order(ByteOrder.LITTLE_ENDIAN);
        int[] destination = new int[DESTINATION_COORDINATE_COUNT];

        for (int caseIndex = 0; caseIndex < LOOKUP_CASE_COUNT; caseIndex++) {
            for (int regionIndex = 0; regionIndex < CASE_REGION_COUNT; regionIndex++) {
                int record = TerrainRegionCheck.NATIVE_REGIONS + regionIndex * TerrainRegionCheck.NATIVE_REGION_SIZE;

                check.writeInteger(record + TerrainRegionCheck.NATIVE_CENTER_X, (regionIndex * 17 + 64) << FIXED_POSITION_SHIFT);
                check.writeInteger(record + TerrainRegionCheck.NATIVE_CENTER_Y, (regionIndex * 13 + 48) << FIXED_POSITION_SHIFT);
                check.writeWord(record + TerrainRegionCheck.NATIVE_LEFT_BOUND, 0);
                check.writeWord(record + TerrainRegionCheck.NATIVE_UPPER_BOUND, 0);
                check.writeWord(record + TerrainRegionCheck.NATIVE_RIGHT_BOUND, LOOKUP_PIXEL_WIDTH);
                check.writeWord(record + TerrainRegionCheck.NATIVE_LOWER_BOUND, LOOKUP_PIXEL_HEIGHT);
            }

            for (int row = 0; row < LOOKUP_MAP_HEIGHT; row++) {
                for (int column = 0; column < LOOKUP_MAP_WIDTH; column++) {
                    int identifier = (row * 17 + column * 13 + caseIndex) % CASE_IDENTIFIER_COUNT;
                    int tileOffset = TerrainRegionCheck.NATIVE_TILE_REGIONS + (row * TerrainRegionCheck.TILE_ROW + column) * Short.BYTES;
                    int region = caseIndex % 11 == 0
                                 ? 5
                                 : identifier < CASE_REGION_COUNT ? identifier : TerrainRegionCheck.PARTIAL_REGION_BASE + identifier;

                    check.writeWord(tileOffset, region);
                }
            }

            for (int partial = CASE_REGION_COUNT; partial < CASE_IDENTIFIER_COUNT; partial++) {
                int offset = TerrainRegionCheck.NATIVE_PARTIAL_TILES + partial * TerrainRegionCheck.PARTIAL_SIZE;

                check.writeWord(offset, 0);
                check.writeWord(offset + TerrainRegionCheck.PARTIAL_FIRST_REGION, (partial + caseIndex) % CASE_REGION_COUNT);
                check.writeWord(offset + TerrainRegionCheck.PARTIAL_SECOND_REGION, (partial + caseIndex + 1) % CASE_REGION_COUNT);
            }

            destination[0] = caseIndex * 37 % DESTINATION_RANGE_WIDTH - DESTINATION_MARGIN;
            destination[1] = caseIndex * 53 % DESTINATION_RANGE_HEIGHT - DESTINATION_MARGIN;

            TerrainRegionLookupUtils.adjustDestinationToRegion(check.getRegions(),
                                                               LOOKUP_MAP_WIDTH, LOOKUP_MAP_HEIGHT,
                                                               caseIndex % CASE_REGION_COUNT,
                                                               destination);

            output.clear();
            output.putInt(RECORD_LEADING_DWORD).putShort((short) destination[0]).putShort((short) destination[1]).putInt(RECORD_TRAILING_DWORD);
            digest.update(output.array());
        }

        Assertions.assertEquals("3ab0456f2dcedbfa4a8c392cc1591002ea50bda055ca879dd98597991d1f642c", HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void matches_native_adjust_destination_cases() throws NoSuchAlgorithmException {
        TerrainRegionCheck check = TerrainRegionCheck.create();
        MessageDigest digest = MessageDigest.getInstance(TerrainRegionCheck.DIGEST_ALGORITHM);
        ByteBuffer output = ByteBuffer.allocate(Integer.BYTES + DESTINATION_COORDINATE_COUNT * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        int[] destination = new int[DESTINATION_COORDINATE_COUNT];

        for (int caseIndex = 0; caseIndex < LOOKUP_CASE_COUNT; caseIndex++) {
            for (int regionIndex = 0; regionIndex < CASE_REGION_COUNT; regionIndex++) {
                int record = TerrainRegionCheck.NATIVE_REGIONS + regionIndex * TerrainRegionCheck.NATIVE_REGION_SIZE;

                check.writeWord(record + TerrainRegionCheck.NATIVE_TERRAIN_TYPE, (regionIndex & 1) != 0 ? BLOCKED_TERRAIN : 0);
            }

            for (int row = 0; row < LOOKUP_MAP_HEIGHT; row++) {
                for (int column = 0; column < LOOKUP_MAP_WIDTH; column++) {
                    int identifier = (row * 17 + column * 13 + caseIndex) % CASE_IDENTIFIER_COUNT;
                    int tileOffset = TerrainRegionCheck.NATIVE_TILE_REGIONS + (row * TerrainRegionCheck.TILE_ROW + column) * Short.BYTES;
                    int region = caseIndex % 11 == 0
                                 ? 1
                                 : identifier < CASE_REGION_COUNT ? identifier : TerrainRegionCheck.PARTIAL_REGION_BASE + identifier;

                    check.writeWord(tileOffset, region);
                }
            }

            int extent = caseIndex % 33;

            destination[0] = caseIndex * 37 % DESTINATION_RANGE_WIDTH - DESTINATION_MARGIN;
            destination[1] = caseIndex * 53 % DESTINATION_RANGE_HEIGHT - DESTINATION_MARGIN;

            boolean result = TerrainRegionLookupUtils.adjustDestination(check.getRegions(),
                                                                        LOOKUP_PIXEL_WIDTH, LOOKUP_PIXEL_HEIGHT,
                                                                        -extent, -(extent + 1) / 2, extent, extent / 2,
                                                                        destination);

            output.clear();
            output.putInt(result ? 1 : 0).putShort((short) destination[0]).putShort((short) destination[1]);
            digest.update(output.array());
        }

        Assertions.assertEquals("d9e35177dc7c7b388a8aa92a223df33021de5e69de9bcdd46a13c091d1e94f20", HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void matches_native_remap_partial_tiles_cases() throws IOException, NoSuchAlgorithmException {
        byte[] groups = new byte[TILESET_GROUP_COUNT * TerrainTileset.CV5_RECORD_SIZE];
        byte[] flagWords = new byte[TILESET_TILE_COUNT * MEGATILE_FLAGS_SIZE];
        ByteBuffer groupBuffer = ByteBuffer.wrap(groups).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer flagBuffer = ByteBuffer.wrap(flagWords).order(ByteOrder.LITTLE_ENDIAN);

        for (int identifier = 0; identifier < TILESET_TILE_COUNT; identifier++) {
            groupBuffer.putShort(TerrainTileset.CV5_MEGATILES_OFFSET + identifier * Short.BYTES,
                                 (short) (identifier | ((identifier & 1) != 0 ? RAW_HIGH_MEGATILE_BIT : 0)));

            for (int miniatureIndex = 0; miniatureIndex < MINIATURES_PER_TILE; miniatureIndex++) {
                flagBuffer.putShort(identifier * MEGATILE_FLAGS_SIZE + miniatureIndex * Short.BYTES,
                                    (short) (((identifier * 37 + miniatureIndex * 13) >>> (miniatureIndex % 5)) & TerrainTileset.MINI_TILE_WALKABLE));
            }
        }

        TerrainTileset terrain = TerrainTileset.parse(groups, flagWords);
        byte[] capturedCases = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_PARTIAL_REGIONS);

        Assertions.assertEquals("959e32664db276129b543ec18b41ef7ea247e33aea3868aaa0805fdf64ac42f9",
                                HexFormat.of().formatHex(MessageDigest.getInstance(TerrainRegionCheck.DIGEST_ALGORITHM).digest(capturedCases)));

        ByteBuffer expected = ByteBuffer.wrap(capturedCases).order(ByteOrder.LITTLE_ENDIAN);

        for (int caseIndex = 0; caseIndex < REMAP_CASE_COUNT; caseIndex++) {
            TerrainRegionCheck check = TerrainRegionCheck.create();
            byte[] initialStorage = check.copyStorage();

            Arrays.fill(initialStorage, (byte) FILL_BYTE);
            Arrays.fill(initialStorage, HEADER_POINTERS, TerrainRegionCheck.NATIVE_TILE_REGIONS, (byte) 0);
            check.load(initialStorage);
            check.writeWord(TerrainRegionCheck.NATIVE_REGION_COUNT, CASE_REGION_COUNT);

            for (int regionIndex = 0; regionIndex < CASE_REGION_COUNT; regionIndex++) {
                int record = TerrainRegionCheck.NATIVE_REGIONS + regionIndex * TerrainRegionCheck.NATIVE_REGION_SIZE;

                check.writeWord(record + TerrainRegionCheck.NATIVE_TERRAIN_TYPE, (regionIndex & 1) != 0 ? BLOCKED_TERRAIN : regionIndex);
            }

            int[] tileIdentifiers = new int[REMAP_TILE_COUNT];
            int[] tileFlags = new int[REMAP_TILE_COUNT];
            byte[] miniatureGrid = new byte[MINI_GRID_WIDTH * MINI_GRID_WIDTH];
            int expectedCount = 0;

            for (int tileIndex = 0; tileIndex < REMAP_TILE_COUNT; tileIndex++) {
                int row = tileIndex / REMAP_MAP_DIMENSION;
                int column = tileIndex % REMAP_MAP_DIMENSION;
                int identifier = (tileIndex * 5 + caseIndex) & TILESET_IDENTIFIER_MASK;

                tileIdentifiers[tileIndex] = identifier;
                tileFlags[tileIndex]       = (tileIndex + caseIndex) % 3 != 0 ? MapTileFlags.PARTIALLY_WALKABLE : 0;

                if (tileFlags[tileIndex] != 0) {
                    expectedCount++;
                }

                check.writeWord(TerrainRegionCheck.NATIVE_TILE_REGIONS + (row * TerrainRegionCheck.TILE_ROW + column) * Short.BYTES,
                                (tileIndex * 3 + caseIndex) & CASE_REGION_MASK);

                for (int miniatureIndex = 0; miniatureIndex < MINIATURES_PER_TILE; miniatureIndex++) {
                    int offset = (row * MINI_TILES_PER_ROW + miniatureIndex / MINI_TILES_PER_ROW) * MINI_GRID_WIDTH +
                                 column * MINI_TILES_PER_ROW + miniatureIndex % MINI_TILES_PER_ROW;

                    miniatureGrid[offset] = (byte) ((TerrainTileset.MINI_TILE_WALKABLE - terrain.getMiniatureFlags(identifier, miniatureIndex)) <<
                                                    MINI_BLOCKED_SHIFT);
                }
            }

            byte[] retainedGrid = miniatureGrid.clone();
            TerrainRegions regions = check.getRegions();
            GameMap map = new GameMap(REMAP_MAP_DIMENSION, REMAP_MAP_DIMENSION, 0, tileIdentifiers, tileFlags);

            TerrainRegionLookupUtils.remapPartialTiles(regions, map, terrain, miniatureGrid);

            Assertions.assertEquals(expectedCount, regions.getPartialTileCount());
            Assertions.assertArrayEquals(retainedGrid, miniatureGrid);

            byte[] capturedResult = new byte[TerrainRegionCheck.STORAGE_SIZE];

            expected.get(capturedResult);
            TerrainRegionCheck.assertMatches(capturedResult, regions);

            Assertions.assertEquals(expected.getInt(), regions.getPartialTileCount());
        }

        Assertions.assertFalse(expected.hasRemaining());
    }

    @Test
    void lookup_should_resolve_tile_masks_without_address_aliases() {
        TerrainRegions regions = TerrainRegionCheck.create().getRegions();

        regions.setPartialTile(0, 0x8001, 17, 29);
        regions.setTileRegion(0, TerrainRegionCheck.PARTIAL_REGION_BASE);

        Assertions.assertEquals(29, TerrainRegionLookupUtils.lookup(regions, 0, 0));
        Assertions.assertEquals(17, TerrainRegionLookupUtils.lookup(regions, 8, 0));
        Assertions.assertEquals(29, TerrainRegionLookupUtils.lookup(regions, 31, 31));
        Assertions.assertEquals(29, TerrainRegionLookupUtils.lookup(regions, -7, -7));

        regions.setPartialTile(TerrainRegionCheck.PARTIAL_COUNT - 1, 0xFFFF, 3, 4);
        regions.setTileRegion(LAST_TILE, TerrainRegionCheck.PARTIAL_REGION_BASE + TerrainRegionCheck.PARTIAL_COUNT - 1);

        Assertions.assertEquals(4, TerrainRegionLookupUtils.lookup(regions, TILE_REGION_PIXEL_EXTENT - 1, TILE_REGION_PIXEL_EXTENT - 1));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> TerrainRegionLookupUtils.lookup(regions, -32, 0));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> TerrainRegionLookupUtils.lookup(regions, 0, TILE_REGION_PIXEL_EXTENT));

        regions.setTileRegion(0, TerrainRegionCheck.PARTIAL_REGION_BASE + TerrainRegionCheck.PARTIAL_COUNT);

        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> TerrainRegionLookupUtils.lookup(regions, 0, 0));
    }

    @Test
    void matches_native_region_lookup_cases() throws NoSuchAlgorithmException {
        TerrainRegionCheck check = TerrainRegionCheck.create();
        byte[] storage = check.copyStorage();

        for (int offset = 0; offset < storage.length; offset++) {
            storage[offset] = (byte) (offset * 37 + 53);
        }

        check.load(storage);

        byte[] retained = storage.clone();
        MessageDigest digest = MessageDigest.getInstance(TerrainRegionCheck.DIGEST_ALGORITHM);
        ByteBuffer result = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        int cases = 0;
        int[] horizontalValues = {-256, -32, -31, -8, -7, -1, 0, 7, 8, 15, 16, 24, 31, 32, 63, 127, 255, 1023, 4095, 8191};
        int[] verticalValues = {-31, -8, -7, -1, 0, 7, 8, 15, 16, 24, 31, 32, 63, 127, 255, 1023, 4095, 8191};

        for (int horizontal : horizontalValues) {
            for (int vertical : verticalValues) {
                result.clear();
                result.putInt(check.lookupRegion(horizontal, vertical));
                digest.update(result.array());

                cases++;
            }
        }

        for (int index = 0; index < SAMPLED_POSITION_COUNT; index++) {
            result.clear();
            result.putInt(check.lookupRegion((index * 173 + 19) & PIXEL_COORDINATE_MASK, (index * 397 + 23) & PIXEL_COORDINATE_MASK));
            digest.update(result.array());

            cases++;
        }

        Assertions.assertEquals(65896, cases);
        Assertions.assertEquals("e2805a2d8ad173adc55891a383431cdc6132607c40eadae9e7e6bb17f1abb4cb", HexFormat.of().formatHex(digest.digest()));
        Assertions.assertArrayEquals(retained, check.copyStorage());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> check.lookupRegion(0, Integer.MIN_VALUE));
    }
}
