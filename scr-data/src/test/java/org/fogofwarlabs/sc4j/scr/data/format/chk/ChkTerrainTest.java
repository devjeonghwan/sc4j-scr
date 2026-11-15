package org.fogofwarlabs.sc4j.scr.data.format.chk;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Tests repeated terrain payload copies without installed assets.
 */
final class ChkTerrainTest {
    private static final int SECTION_HEADER_SIZE = 8;
    private static final int WORD_SECTION_SIZE = 2;
    private static final int DIMENSIONS_SIZE = 4;
    private static final int MAXIMUM_DIMENSION = 256;
    private static final int TILESET_MASK = 0x7;
    private static final int MAXIMUM_TERRAIN_SIZE = 0x20000;
    private static final int TILE_GROUP_COUNT = 3;
    private static final int TILE_WORD_SIZE = 2;
    private static final int DIMENSIONS_SECTION_SIZE = SECTION_HEADER_SIZE + DIMENSIONS_SIZE;

    private static byte @NotNull [] createTerrainChunks(byte @NotNull [] @NotNull ... payloads) {
        int byteCount = 0;

        for (byte[] payload : payloads) {
            byteCount += SECTION_HEADER_SIZE + payload.length;
        }

        ByteBuffer buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN);

        for (byte[] payload : payloads) {
            buffer.put("MTXM".getBytes(StandardCharsets.US_ASCII)).putInt(payload.length).put(payload);
        }

        return buffer.array();
    }


    @Test
    void get_tile_ids_should_overwrite_only_supplied_prefix_including_odd_bytes() throws IOException {
        byte[] data = createTerrainChunks(new byte[]{1, 2, 3, 4, 5, 6}, new byte[]{7, 8, 9}, new byte[0]);

        for (ChkFile map : new ChkFile[]{ChkFile.parse(data), ChkFile.parseTolerant(data)}) {
            Assertions.assertArrayEquals(new int[]{0x0807, 0x0409, 0x0605}, map.getTileIds());
            Assertions.assertArrayEquals(new byte[0], map.getSection("MTXM"));
        }
    }

    @Test
    void get_tile_ids_should_replace_earlier_bytes_when_later_payload_is_longer() throws IOException {
        byte[] data = createTerrainChunks(new byte[]{1, 2, 3}, new byte[]{7, 8, 9, 10, 11, 12});

        Assertions.assertArrayEquals(new int[]{0x0807, 0x0A09, 0x0C0B}, ChkFile.parse(data).getTileIds());
    }

    @Test
    void parse_should_reject_terrain_payloads_beyond_buffer_limit() {
        byte[] data = createTerrainChunks(new byte[MAXIMUM_TERRAIN_SIZE + 1]);

        Assertions.assertThrows(IOException.class, () -> ChkFile.parse(data));
        Assertions.assertThrows(IOException.class, () -> ChkFile.parseTolerant(data));
    }

    @Test
    void get_tile_ids_should_normalize_before_odd_overwrite_and_preserve_valid_sign_bits() throws IOException {
        byte[] terrain = createTerrainChunks(new byte[]{(byte) 0xF1, (byte) 0xFF, 0x2F, (byte) 0x80}, new byte[]{0x11}, new byte[0]);
        byte[] data = ByteBuffer.allocate(DIMENSIONS_SECTION_SIZE + terrain.length).order(ByteOrder.LITTLE_ENDIAN)
                                .put("DIM ".getBytes(StandardCharsets.US_ASCII)).putInt(DIMENSIONS_SIZE)
                                .putShort((short) 2).putShort((short) 1).put(terrain).array();

        for (ChkFile map : new ChkFile[]{ChkFile.parse(data), ChkFile.parseTolerant(data)}) {
            Assertions.assertArrayEquals(new int[]{0xFF11, 0x802F}, map.getTileIds());
            Assertions.assertArrayEquals(new int[]{0x0011, 0x802F}, map.getTileIds(TILE_GROUP_COUNT));
            Assertions.assertArrayEquals(new int[]{0x0011, 0x802F}, map.getTileIds(TILE_GROUP_COUNT));
            Assertions.assertArrayEquals(new byte[0], map.getSection("MTXM"));
        }
    }

    @Test
    void get_tile_ids_should_start_at_first_complete_overwrite_and_reject_unsupplied_bytes() throws IOException {
        byte[] terrain = createTerrainChunks(new byte[]{0}, new byte[]{0x10, 0});
        byte[] data = ByteBuffer.allocate(DIMENSIONS_SECTION_SIZE + terrain.length).order(ByteOrder.LITTLE_ENDIAN)
                                .put("DIM ".getBytes(StandardCharsets.US_ASCII)).putInt(DIMENSIONS_SIZE)
                                .putShort((short) 1).putShort((short) 1).put(terrain).array();
        ChkFile map = ChkFile.parse(data);

        Assertions.assertArrayEquals(new int[]{0x10}, map.getTileIds(TILE_GROUP_COUNT));
        Assertions.assertThrows(IllegalArgumentException.class, () -> map.getTileIds(2));

        ChkFile incompleteMap = ChkFile.parse(Arrays.copyOf(data, 21));

        Assertions.assertThrows(IllegalStateException.class, () -> incompleteMap.getTileIds(TILE_GROUP_COUNT));
    }

    @Test
    void get_tileset_should_mask_era_word_without_changing_raw_payload() throws IOException {
        for (int rawTileset : new int[]{0, 7, 8, 55052, 65535}) {
            byte[] data = ByteBuffer.allocate(SECTION_HEADER_SIZE + WORD_SECTION_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                                    .put("ERA ".getBytes(StandardCharsets.US_ASCII)).putInt(WORD_SECTION_SIZE)
                                    .putShort((short) rawTileset).array();
            ChkFile map = ChkFile.parse(data);

            Assertions.assertEquals(rawTileset & TILESET_MASK, map.getTileset());
            Assertions.assertEquals(rawTileset, ByteBuffer.wrap(map.requireSection("ERA ")).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF);
        }
    }

    @Test
    void get_tile_ids_should_check_each_dimension_limit_without_inventing_a_minimum() throws IOException {
        for (int[] dimensions : new int[][]{{256, 256}, {0, 256}, {256, 0}, {257, 1}, {1, 257}, {512, 128}}) {
            byte[] terrain = createTerrainChunks(new byte[dimensions[0] * dimensions[1] * TILE_WORD_SIZE]);
            byte[] data = ByteBuffer.allocate(DIMENSIONS_SECTION_SIZE + terrain.length).order(ByteOrder.LITTLE_ENDIAN)
                                    .put("DIM ".getBytes(StandardCharsets.US_ASCII)).putInt(DIMENSIONS_SIZE)
                                    .putShort((short) dimensions[0]).putShort((short) dimensions[1]).put(terrain).array();
            ChkFile map = ChkFile.parse(data);

            if (dimensions[0] <= MAXIMUM_DIMENSION && dimensions[1] <= MAXIMUM_DIMENSION) {
                Assertions.assertEquals(dimensions[0] * dimensions[1], map.getTileIds(TILE_GROUP_COUNT).length);
            } else {
                Assertions.assertThrows(IllegalArgumentException.class, () -> map.getTileIds(TILE_GROUP_COUNT));
            }
        }
    }

    @Test
    void get_tile_ids_should_require_exact_dimension_payload_size_when_normalized() throws IOException {
        for (int dimensionBytes : new int[]{0, 3, 5, 8}) {
            byte[] terrain = createTerrainChunks(new byte[0]);
            byte[] data = ByteBuffer.allocate(SECTION_HEADER_SIZE + dimensionBytes + terrain.length).order(ByteOrder.LITTLE_ENDIAN)
                                    .put("DIM ".getBytes(StandardCharsets.US_ASCII)).putInt(dimensionBytes)
                                    .put(new byte[dimensionBytes]).put(terrain).array();
            ChkFile map = ChkFile.parse(data);

            Assertions.assertThrows(IllegalArgumentException.class, () -> map.getTileIds(TILE_GROUP_COUNT));
        }
    }
}
