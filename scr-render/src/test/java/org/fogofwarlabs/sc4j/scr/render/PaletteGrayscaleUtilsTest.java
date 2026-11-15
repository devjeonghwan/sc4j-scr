package org.fogofwarlabs.sc4j.scr.render;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the paused-game palette of {@link PaletteGrayscaleUtils}.
 */
final class PaletteGrayscaleUtilsTest {
    @Test
    void apply_should_weigh_each_channel_separately_and_keep_the_fourth_byte() {
        byte[] palette = {(byte) 255, (byte) 255, (byte) 255, 7, (byte) 200, 100, 50, (byte) 0x80, 0, 0, 0, 0};
        // 255: 76 + 150 + 27 = 253 (each product shifted on its own); (200, 100, 50): 60 + 58 + 5 = 123.
        byte[] expected = {(byte) 253, (byte) 253, (byte) 253, 7, 123, 123, 123, (byte) 0x80, 0, 0, 0, 0};

        Assertions.assertArrayEquals(expected, PaletteGrayscaleUtils.apply(palette));
    }
}
