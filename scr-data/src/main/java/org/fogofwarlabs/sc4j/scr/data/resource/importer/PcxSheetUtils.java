package org.fogofwarlabs.sc4j.scr.data.resource.importer;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.resource.graphics.PixelLayer;
import org.fogofwarlabs.sc4j.engine.resource.graphics.SpriteFrame;
import org.fogofwarlabs.sc4j.engine.resource.graphics.SpriteSheet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Imports an {@code 8}-bit, single-plane run-length encoded PCX picture as an engine {@link SpriteSheet} of one frame:
 * the picture's palette indices in one {@link org.fogofwarlabs.sc4j.engine.resource.graphics.PixelFormat#INDEXED_8}
 * layer, the frame box the picture's size with the anchor at its top-left pixel. SC:R uses such pictures as remapping
 * tables ({@code game/tunit.pcx}, the tileset lighting tables) and as interface pictures.
 * <p>
 * The run-length data is one stream over every row of {@code bytesPerLine} bytes; the bytes past the width of a row are
 * dropped.
 */
public final class PcxSheetUtils {
    /**
     * The PCX header layout: size, manufacturer, encoding, bits per pixel, extent, planes and line size.
     */
    private static final int HEADER_SIZE = 128;
    private static final int MANUFACTURER_OFFSET = 0;
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
     * Creates a one-frame sprite sheet of a PCX picture.
     *
     * @param contents the PCX file
     * @return the sprite sheet
     * @throws IOException if the file is not an {@code 8}-bit, single-plane run-length encoded picture or its data is malformed
     */
    public static @NotNull SpriteSheet createSheet(byte @NotNull [] contents) throws IOException {
        if (contents.length < HEADER_SIZE || contents[MANUFACTURER_OFFSET] != MANUFACTURER || contents[ENCODING_OFFSET] != RUN_LENGTH_ENCODING ||
            contents[BITS_PER_PIXEL_OFFSET] != BITS_PER_PIXEL || contents[PLANE_COUNT_OFFSET] != PLANE_COUNT) {
            throw Exceptions.newIOException("Not an 8-bit, one-plane run-length encoded PCX picture.");
        }

        int width = readExtent(contents, MINIMUM_X_OFFSET, MAXIMUM_X_OFFSET);
        int height = readExtent(contents, MINIMUM_Y_OFFSET, MAXIMUM_Y_OFFSET);
        int stride = LittleEndianUtils.readUnsigned16(contents, BYTES_PER_LINE_OFFSET);

        if (width < 1 || height < 1) {
            throw Exceptions.newIOException("Invalid PCX picture extent: {0}x{1}", width, height);
        }

        if (stride < width) {
            throw Exceptions.newIOException("PCX row stride is shorter than its width.");
        }

        byte[] result = new byte[width * height];
        int source = HEADER_SIZE;
        int destination = 0;

        while (destination < stride * height) {
            if (source >= contents.length) {
                throw Exceptions.newIOException("Truncated PCX pixels.");
            }

            int value = Byte.toUnsignedInt(contents[source++]);
            int count = 1;

            if ((value & RUN_FLAG) == RUN_FLAG) {
                count = value & RUN_LENGTH_MASK;

                if (count == 0 || source >= contents.length) {
                    throw Exceptions.newIOException("Invalid PCX run.");
                }

                value = Byte.toUnsignedInt(contents[source++]);
            }

            if (destination + count > stride * height) {
                throw Exceptions.newIOException("PCX pixels exceed the picture.");
            }

            for (int repeat = 0; repeat < count; repeat++, destination++) {
                int horizontal = destination % stride;

                if (horizontal < width) {
                    result[destination / stride * width + horizontal] = (byte) value;
                }
            }
        }

        return new SpriteSheet(width, height, 0, 0, List.of(new SpriteFrame(0, 0, width, height, 0)), PixelLayer.ofIndexed(result), null, null);
    }

    /**
     * Reads an inclusive PCX header extent: the maximum coordinate minus the minimum coordinate plus {@code 1}.
     *
     * @param contents      the PCX file
     * @param minimumOffset the header offset of the minimum coordinate
     * @param maximumOffset the header offset of the maximum coordinate
     * @return the width or height in pixels
     */
    private static int readExtent(byte @NotNull [] contents, int minimumOffset, int maximumOffset) {
        return LittleEndianUtils.readUnsigned16(contents, maximumOffset) - LittleEndianUtils.readUnsigned16(contents, minimumOffset) + 1;
    }

    private PcxSheetUtils() {
    }
}
