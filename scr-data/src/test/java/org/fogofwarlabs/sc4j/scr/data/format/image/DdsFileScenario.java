package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Creates synthetic DDS textures for the image tests and names the DDS header fields they write.
 */
final class DdsFileScenario {
    /**
     * The size of the DDS header: the magic DWORD and the {@code 124}-byte header descriptor.
     */
    static final int HEADER_SIZE = 128;
    static final int MAGIC = 0x20534444;
    static final int DESCRIPTOR_SIZE_FIELD = 4;
    static final int DESCRIPTOR_SIZE = 124;
    static final int HEIGHT_FIELD = 12;
    static final int WIDTH_FIELD = 16;
    static final int PIXEL_FLAGS_FIELD = 80;
    static final int FOUR_CHARACTER_CODE_FIELD = 84;
    static final int RED_MASK_FIELD = 92;
    static final int GREEN_MASK_FIELD = 96;
    static final int BLUE_MASK_FIELD = 100;
    static final int ALPHA_MASK_FIELD = 104;

    static final int PIXEL_FORMAT_FOUR_CHARACTER_CODE = 0x4;
    static final int FOUR_CHARACTER_DXT1 = 0x31545844;
    static final int FOUR_CHARACTER_DXT3 = 0x33545844;
    static final int FOUR_CHARACTER_DXT5 = 0x35545844;
    static final int FOUR_CHARACTER_DX10 = 0x30315844;
    static final int DXT1_BLOCK_SIZE = 8;
    static final int DXT3_BLOCK_SIZE = 16;
    static final int DXT5_BLOCK_SIZE = 16;

    private static final int FLAGS_FIELD = 8;
    /**
     * The header flags of the synthetic textures: capabilities, height, width and pixel format.
     */
    private static final int HEADER_FLAGS = 0x1007;
    private static final int PIXEL_FORMAT_SIZE_FIELD = 76;
    private static final int PIXEL_FORMAT_SIZE = 32;
    private static final int BIT_COUNT_FIELD = 88;

    /**
     * Creates the contents of a DDS file of a compressed format, marked by its four-character code, with a zero payload.
     *
     * @param width             the texture width
     * @param height            the texture height
     * @param fourCharacterCode the four-character code of the format
     * @param payloadSize       the size of the payload after the header
     * @return the file contents, in little-endian order and positioned at the payload
     */
    static @NotNull ByteBuffer createCompressedHeader(int width, int height, int fourCharacterCode, int payloadSize) {
        return createHeader(width, height, PIXEL_FORMAT_FOUR_CHARACTER_CODE, fourCharacterCode, 0, payloadSize);
    }

    /**
     * Creates the contents of a DDS file with the given header values and a zero payload.
     *
     * @param width             the texture width
     * @param height            the texture height
     * @param pixelFlags        the pixel format flags
     * @param fourCharacterCode the four-character code of a compressed format, {@code 0} otherwise
     * @param bitCount          the bits per pixel of an uncompressed format, {@code 0} otherwise
     * @param payloadSize       the size of the payload after the header
     * @return the file contents, in little-endian order and positioned at the payload
     */
    static @NotNull ByteBuffer createHeader(int width, int height, int pixelFlags, int fourCharacterCode, int bitCount, int payloadSize) {
        ByteBuffer contents = ByteBuffer.allocate(HEADER_SIZE + payloadSize).order(ByteOrder.LITTLE_ENDIAN);

        contents.putInt(0, MAGIC).putInt(DESCRIPTOR_SIZE_FIELD, DESCRIPTOR_SIZE).putInt(FLAGS_FIELD, HEADER_FLAGS)
                .putInt(HEIGHT_FIELD, height).putInt(WIDTH_FIELD, width);
        contents.putInt(PIXEL_FORMAT_SIZE_FIELD, PIXEL_FORMAT_SIZE).putInt(PIXEL_FLAGS_FIELD, pixelFlags)
                .putInt(FOUR_CHARACTER_CODE_FIELD, fourCharacterCode).putInt(BIT_COUNT_FIELD, bitCount);
        contents.position(HEADER_SIZE);

        return contents;
    }


    /**
     * Creates a {@link DdsFileScenario} instance.
     */
    private DdsFileScenario() {
    }
}
