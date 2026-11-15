package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

/**
 * Compares the channels of decoded DDS texels for the image tests.
 */
final class DdsPixelAssert {
    private static final int CHANNEL_COUNT = 4;

    /**
     * Asserts the channels of one pixel of decoded RGBA texels.
     *
     * @param pixels     the decoded texels, four channel bytes per pixel
     * @param pixelIndex the index of the pixel
     * @param red        the expected red channel
     * @param green      the expected green channel
     * @param blue       the expected blue channel
     * @param alpha      the expected alpha channel
     */
    static void assertPixel(byte @NotNull [] pixels, int pixelIndex, int red, int green, int blue, int alpha) {
        Assertions.assertEquals(red, Byte.toUnsignedInt(pixels[pixelIndex * CHANNEL_COUNT]), "red of pixel " + pixelIndex);
        Assertions.assertEquals(green, Byte.toUnsignedInt(pixels[pixelIndex * CHANNEL_COUNT + 1]), "green of pixel " + pixelIndex);
        Assertions.assertEquals(blue, Byte.toUnsignedInt(pixels[pixelIndex * CHANNEL_COUNT + 2]), "blue of pixel " + pixelIndex);
        Assertions.assertEquals(alpha, Byte.toUnsignedInt(pixels[pixelIndex * CHANNEL_COUNT + 3]), "alpha of pixel " + pixelIndex);
    }


    /**
     * Creates a {@link DdsPixelAssert} instance.
     */
    private DdsPixelAssert() {
    }
}
