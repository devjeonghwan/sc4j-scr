package org.fogofwarlabs.sc4j.scr.client.ui.portrait;

import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that the portrait static stays full while the clip loads and fades after the clip's first frame is shown.
 */
final class PortraitStaticTest {
    /**
     * The shift of a gray into the {@code 0xRRGGBB00} part of a packed {@code 0xRRGGBBAA} color.
     */
    private static final int COLOR_SHIFT = 8;
    private static final int OPAQUE_ALPHA = 0xFF;
    /**
     * Eight tenths of the canvas pixels: level {@code 0} fills most of them.
     */
    private static final int MOST_PIXELS = PortraitStatic.WIDTH * PortraitStatic.HEIGHT * 8 / 10;
    private static final int SHOWN_LEVEL = 50;
    /**
     * The level two static ticks after the first frame is shown.
     */
    private static final int LEVEL_AFTER_TWO_TICKS = 70;
    /**
     * The static ticks that raise the level from {@link #LEVEL_AFTER_TWO_TICKS} to the clear level and beyond.
     */
    private static final int REMAINING_TICKS = 5;
    /**
     * The level at which the static is clear and no longer drawn.
     */
    private static final int CLEAR_LEVEL = 80;

    @Test
    void static_fades_after_first_frame_shown() {
        PortraitStatic noise = new PortraitStatic(new PortraitGenerator());
        int[] colors = new int[PortraitStatic.GRAYS.length];

        for (int index = 0; index < colors.length; index++) {
            colors[index] = PortraitStatic.GRAYS[index] << COLOR_SHIFT | OPAQUE_ALPHA;
        }

        noise.setColors(colors);

        Assertions.assertFalse(noise.isVisible());

        noise.restart();

        Assertions.assertEquals(0, noise.getLevel());

        noise.tick(false);

        Assertions.assertEquals(0, noise.getLevel(), "Full static while the clip loads");

        byte[] canvas = noise.takeCanvas();
        int opaque = 0;

        for (int pixel = 0; pixel < PortraitStatic.WIDTH * PortraitStatic.HEIGHT; pixel++) {
            opaque += canvas[pixel * OverlayPass.RGBA_BYTES + OverlayPass.RGBA_ALPHA] != 0 ? 1 : 0;
        }

        Assertions.assertTrue(opaque > MOST_PIXELS, "Level 0 fills most pixels (black is transparent)");

        noise.onFrameShown();

        Assertions.assertEquals(SHOWN_LEVEL, noise.getLevel());

        // Timer 2 fires every 30 ms (one level step per firing).
        noise.tick(true);
        noise.tick(true);

        Assertions.assertEquals(LEVEL_AFTER_TWO_TICKS, noise.getLevel());

        for (int firing = 0; firing < REMAINING_TICKS; firing++) {
            noise.tick(true);
        }

        Assertions.assertEquals(CLEAR_LEVEL, noise.getLevel());
        Assertions.assertFalse(noise.isVisible());
    }
}
