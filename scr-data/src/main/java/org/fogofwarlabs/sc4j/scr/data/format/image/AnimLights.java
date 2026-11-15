package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Reads per-image frame light positions, colors, radii and intensities while borrowing the source array.
 */
public final class AnimLights {
    private static final int BYTE_MASK = 0xFF;
    private static final int TABLE_OFFSET = 12;
    private static final int COUNT_SIZE = 4;
    private static final int OFFSET_SIZE = 4;

    private static final int RECORD_SIZE = 20;
    private static final int Y_OFFSET = 4;
    private static final int COLOR_OFFSET = 8;
    private static final int RADIUS_OFFSET = 12;
    private static final int INTENSITY_OFFSET = 16;

    private static final int GREEN_OFFSET = 1;
    private static final int BLUE_OFFSET = 2;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;

    /**
     * Creates an {@link AnimLights} instance from {@code main.lit} content.
     *
     * @param data the file content
     * @return the created {@link AnimLights} instance
     * @throws IOException if the table or an entry exceeds the content
     */
    public static @NotNull AnimLights parse(byte @NotNull [] data) throws IOException {
        if (data.length < COUNT_SIZE) {
            throw Exceptions.newIOException("main.lit is shorter than its header.");
        }

        int count = LittleEndianUtils.readInt32(data, 0);

        if (count < 0 || TABLE_OFFSET + count * (long) OFFSET_SIZE > data.length) {
            throw Exceptions.newIOException("main.lit entry table exceeds the content.");
        }

        int[] offsets = new int[count];
        int[] frameCounts = new int[count];

        for (int image = 0; image < count; image++) {
            int offset = LittleEndianUtils.readInt32(data, TABLE_OFFSET + image * OFFSET_SIZE);

            if (offset == 0) {
                continue;
            }

            if (offset < 0 || offset + (long) COUNT_SIZE > data.length) {
                throw Exceptions.newIOException("main.lit entry {0} exceeds the content.", image);
            }

            int frames = LittleEndianUtils.readInt32(data, offset);

            if (frames < 0 || offset + (long) COUNT_SIZE + (long) frames * RECORD_SIZE > data.length) {
                throw Exceptions.newIOException("main.lit entry {0} frames exceed the content.", image);
            }

            offsets[image]     = offset + COUNT_SIZE;
            frameCounts[image] = frames;
        }

        return new AnimLights(data, offsets, frameCounts);
    }


    private final byte @NotNull [] data;
    private final int @NotNull [] offsets;
    private final int @NotNull [] frameCounts;

    private AnimLights(byte @NotNull [] data, int @NotNull [] offsets, int @NotNull [] frameCounts) {
        this.data        = data;
        this.offsets     = offsets;
        this.frameCounts = frameCounts;
    }

    /**
     * @return the number of image entries
     */
    public int getImageCount() {
        return this.offsets.length;
    }

    /**
     * @param image the image identifier
     * @return the number of frame records of the image, {@code 0} without an entry
     */
    public int getFrameCount(int image) {
        return image >= 0 && image < this.frameCounts.length ? this.frameCounts[image] : 0;
    }

    /**
     * @param image the image identifier
     * @param frame the frame index
     * @return {@code true} if the frame has a light, {@code false} otherwise
     */
    public boolean hasLight(int image, int frame) {
        return frame >= 0 && frame < this.getFrameCount(image) && this.getRadius(image, frame) != 0;
    }

    /**
     * @param image the image identifier
     * @param frame the frame index
     * @return the light's horizontal canvas position
     */
    public int getX(int image, int frame) {
        return LittleEndianUtils.readInt32(this.data, this.offsets[image] + frame * RECORD_SIZE);
    }

    /**
     * @param image the image identifier
     * @param frame the frame index
     * @return the light's vertical canvas position
     */
    public int getY(int image, int frame) {
        return LittleEndianUtils.readInt32(this.data, this.offsets[image] + frame * RECORD_SIZE + Y_OFFSET);
    }

    /**
     * @param image the image identifier
     * @param frame the frame index
     * @return the packed {@code 0xRRGGBB} light color
     */
    public int getColor(int image, int frame) {
        int offset = this.offsets[image] + frame * RECORD_SIZE + COLOR_OFFSET;

        return (this.data[offset] & BYTE_MASK) << RED_SHIFT |
               (this.data[offset + GREEN_OFFSET] & BYTE_MASK) << GREEN_SHIFT |
               (this.data[offset + BLUE_OFFSET] & BYTE_MASK);
    }

    /**
     * @param image the image identifier
     * @param frame the frame index
     * @return the light radius
     */
    public int getRadius(int image, int frame) {
        return LittleEndianUtils.readInt32(this.data, this.offsets[image] + frame * RECORD_SIZE + RADIUS_OFFSET);
    }

    /**
     * @param image the image identifier
     * @param frame the frame index
     * @return the light intensity out of {@code 255}
     */
    public int getIntensity(int image, int frame) {
        return LittleEndianUtils.readInt32(this.data, this.offsets[image] + frame * RECORD_SIZE + INTENSITY_OFFSET);
    }
}
