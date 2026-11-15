package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Verifies that DDS files decode texels and reject invalid content.
 */
final class DdsFileTest {
    /**
     * The number of bytes of the {@code 3}-bit alpha indices of a DXT5 block.
     */
    private static final int ALPHA_INDEX_BYTE_COUNT = 6;
    private static final int RGB_ALPHA_PIXEL_FORMAT = 0x41;
    private static final int RGB32_BIT_COUNT = 32;
    private static final int RED_CHANNEL_MASK = 0x00FF0000;
    private static final int GREEN_CHANNEL_MASK = 0x0000FF00;
    private static final int BLUE_CHANNEL_MASK = 0x000000FF;
    private static final int ALPHA_CHANNEL_MASK = 0xFF000000;

    @Test
    void decode_rgba_should_decode_dxt1_four_color_block() throws IOException {
        ByteBuffer contents = DdsFileScenario.createCompressedHeader(4, 4, DdsFileScenario.FOUR_CHARACTER_DXT1, DdsFileScenario.DXT1_BLOCK_SIZE);

        // Color 0 pure red (0xF800) > color 1 pure blue (0x001F): four-color mode; selectors 0,1,2,3 across each row.
        contents.putShort((short) 0xF800).putShort((short) 0x001F).putInt(0xE4E4E4E4);

        DdsFile file = DdsFile.parse(contents.array());

        Assertions.assertEquals(DdsFile.Format.DXT1, file.getFormat());
        Assertions.assertEquals(DdsFileScenario.DXT1_BLOCK_SIZE, file.getLevelSize(0));

        byte[] pixels = file.decodeRgba();

        DdsPixelAssert.assertPixel(pixels, 0, 255, 0, 0, 255);
        DdsPixelAssert.assertPixel(pixels, 1, 0, 0, 255, 255);
        DdsPixelAssert.assertPixel(pixels, 2, 170, 0, 85, 255);
        DdsPixelAssert.assertPixel(pixels, 3, 85, 0, 170, 255);
    }

    @Test
    void decode_rgba_should_decode_dxt1_transparent_selector_in_three_color_mode() throws IOException {
        ByteBuffer contents = DdsFileScenario.createCompressedHeader(4, 4, DdsFileScenario.FOUR_CHARACTER_DXT1, DdsFileScenario.DXT1_BLOCK_SIZE);

        contents.putShort((short) 0x001F).putShort((short) 0xF800).putInt(0xE4E4E4E4);

        byte[] pixels = DdsFile.parse(contents.array()).decodeRgba();

        DdsPixelAssert.assertPixel(pixels, 2, 127, 0, 127, 255);
        DdsPixelAssert.assertPixel(pixels, 3, 0, 0, 0, 0);
    }

    @Test
    void decode_rgba_should_decode_dxt5_interpolated_alpha() throws IOException {
        ByteBuffer contents = DdsFileScenario.createCompressedHeader(4, 4, DdsFileScenario.FOUR_CHARACTER_DXT5, DdsFileScenario.DXT5_BLOCK_SIZE);

        contents.put((byte) 255).put((byte) 0);

        // First texel selects alpha index 0, second index 1, third index 2 (6/7 * 255 + 1/7 * 0 = 218), fourth index 7.
        long alphaBits = 0L | 1L << 3 | 2L << 6 | 7L << 9;

        for (int byteIndex = 0; byteIndex < ALPHA_INDEX_BYTE_COUNT; byteIndex++) {
            contents.put((byte) (alphaBits >>> (byteIndex * Byte.SIZE)));
        }

        contents.putShort((short) 0xFFFF).putShort((short) 0).putInt(0);

        byte[] pixels = DdsFile.parse(contents.array()).decodeRgba();

        DdsPixelAssert.assertPixel(pixels, 0, 255, 255, 255, 255);
        DdsPixelAssert.assertPixel(pixels, 1, 255, 255, 255, 0);
        DdsPixelAssert.assertPixel(pixels, 2, 255, 255, 255, 218);
        DdsPixelAssert.assertPixel(pixels, 3, 255, 255, 255, 36);
    }

    @Test
    void decode_rgba_should_decode_dxt3_explicit_alpha_and_uncompressed_masks() throws IOException {
        ByteBuffer compressed = DdsFileScenario.createCompressedHeader(4, 4, DdsFileScenario.FOUR_CHARACTER_DXT3, DdsFileScenario.DXT3_BLOCK_SIZE);

        compressed.put((byte) 0xF0).put(new byte[7]).putShort((short) 0x07E0).putShort((short) 0).putInt(0);

        byte[] compressedPixels = DdsFile.parse(compressed.array()).decodeRgba();

        DdsPixelAssert.assertPixel(compressedPixels, 0, 0, 255, 0, 0);
        DdsPixelAssert.assertPixel(compressedPixels, 1, 0, 255, 0, 255);

        ByteBuffer uncompressed = DdsFileScenario.createHeader(1, 1, RGB_ALPHA_PIXEL_FORMAT, 0, RGB32_BIT_COUNT, Integer.BYTES);

        uncompressed.putInt(DdsFileScenario.RED_MASK_FIELD, RED_CHANNEL_MASK)
                    .putInt(DdsFileScenario.GREEN_MASK_FIELD, GREEN_CHANNEL_MASK)
                    .putInt(DdsFileScenario.BLUE_MASK_FIELD, BLUE_CHANNEL_MASK)
                    .putInt(DdsFileScenario.ALPHA_MASK_FIELD, ALPHA_CHANNEL_MASK);
        uncompressed.putInt(DdsFileScenario.HEADER_SIZE, 0x80102030);

        DdsFile file = DdsFile.parse(uncompressed.array());

        Assertions.assertEquals(DdsFile.Format.RGB32, file.getFormat());
        DdsPixelAssert.assertPixel(file.decodeRgba(), 0, 0x10, 0x20, 0x30, 0x80);
    }

    @Test
    void parse_should_reject_truncated_and_unsupported_files() {
        ByteBuffer truncated = DdsFileScenario.createCompressedHeader(8, 8, DdsFileScenario.FOUR_CHARACTER_DXT1, DdsFileScenario.DXT1_BLOCK_SIZE);

        Assertions.assertThrows(IOException.class, () -> DdsFile.parse(truncated.array()));

        ByteBuffer extended = DdsFileScenario.createCompressedHeader(4, 4, DdsFileScenario.FOUR_CHARACTER_DX10, DdsFileScenario.DXT1_BLOCK_SIZE);

        Assertions.assertThrows(IOException.class, () -> DdsFile.parse(extended.array()));
        Assertions.assertThrows(IOException.class, () -> DdsFile.parse(new byte[64]));
    }
}
