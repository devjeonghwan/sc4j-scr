package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Decodes an 8-bit single-plane PCX image to palette indices.
 */
public final class PcxIndexedImage {
    private static final int BYTE_MASK = 0xFF;
    private static final int HEADER_SIZE = 128;
    private static final int MANUFACTURER = 0x0A;
    private static final int ENCODING_OFFSET = 2;
    private static final int RUN_LENGTH_ENCODING = 1;
    private static final int BITS_PER_PIXEL_OFFSET = 3;
    private static final int BITS_PER_PIXEL = 8;
    private static final int MINIMUM_X_OFFSET = 4;
    private static final int MINIMUM_Y_OFFSET = 6;
    private static final int MAXIMUM_X_OFFSET = 8;
    private static final int MAXIMUM_Y_OFFSET = 10;
    private static final int PLANE_COUNT_OFFSET = 65;
    private static final int PLANE_COUNT = 1;
    private static final int BYTES_PER_LINE_OFFSET = 66;
    /**
     * The two high bits that mark a run byte; the low six bits hold the run length.
     */
    private static final int RUN_FLAG = 0xC0;
    private static final int RUN_LENGTH_MASK = 0x3F;

    /**
     * Creates a {@link PcxIndexedImage} instance by decoding PCX file content.
     *
     * @param data the PCX file content
     * @return the created {@link PcxIndexedImage} instance
     * @throws IOException if the file is not an 8-bit single-plane PCX image or its data ends early
     */
    public static @NotNull PcxIndexedImage decode(byte @NotNull [] data) throws IOException {
        if (data.length < HEADER_SIZE || data[0] != MANUFACTURER || data[ENCODING_OFFSET] != RUN_LENGTH_ENCODING ||
            data[BITS_PER_PIXEL_OFFSET] != BITS_PER_PIXEL || data[PLANE_COUNT_OFFSET] != PLANE_COUNT) {
            throw Exceptions.newIOException("Not an 8-bit single-plane RLE PCX image.");
        }

        int width = LittleEndianUtils.readUnsigned16(data, MAXIMUM_X_OFFSET) - LittleEndianUtils.readUnsigned16(data, MINIMUM_X_OFFSET) + 1;
        int height = LittleEndianUtils.readUnsigned16(data, MAXIMUM_Y_OFFSET) - LittleEndianUtils.readUnsigned16(data, MINIMUM_Y_OFFSET) + 1;
        int bytesPerLine = LittleEndianUtils.readUnsigned16(data, BYTES_PER_LINE_OFFSET);

        if (width <= 0 || height <= 0 || bytesPerLine < width) {
            throw Exceptions.newIOException("Invalid PCX image extent.");
        }

        byte[] pixels = new byte[width * height];
        int position = HEADER_SIZE;

        for (int row = 0; row < height; row++) {
            int column = 0;

            while (column < bytesPerLine) {
                if (position >= data.length) {
                    throw Exceptions.newIOException("PCX image data ends early.");
                }

                int value = data[position++] & BYTE_MASK;
                int count = 1;

                if ((value & RUN_FLAG) == RUN_FLAG) {
                    count = value & RUN_LENGTH_MASK;

                    if (position >= data.length) {
                        throw Exceptions.newIOException("PCX image data ends early.");
                    }

                    value = data[position++] & BYTE_MASK;
                }

                for (int index = 0; index < count && column < bytesPerLine; index++, column++) {
                    if (column < width) {
                        pixels[row * width + column] = (byte) value;
                    }
                }
            }
        }

        return new PcxIndexedImage(width, height, pixels);
    }


    private final int width;
    private final int height;

    private final byte @NotNull [] pixels;

    private PcxIndexedImage(int width, int height, byte @NotNull [] pixels) {
        this.width  = width;
        this.height = height;

        this.pixels = pixels;
    }

    /**
     * @return the width in pixels
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * @return the height in pixels
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * @param x the column
     * @param y the row
     * @return the palette index
     */
    public int getPixel(int x, int y) {
        return this.pixels[y * this.width + x] & BYTE_MASK;
    }
}
