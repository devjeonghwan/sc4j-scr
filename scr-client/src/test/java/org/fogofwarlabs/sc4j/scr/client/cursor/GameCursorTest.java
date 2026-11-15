package org.fogofwarlabs.sc4j.scr.client.cursor;

import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceImage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that {@link GameCursor} builds platform cursor pictures with the expected size, sampling and hot spot.
 */
final class GameCursorTest {
    private static final int PIXEL_BYTES = 4;
    private static final int GREEN_CHANNEL = 1;
    private static final int ALPHA_CHANNEL = 3;
    private static final int CHECKER_STEP = 16;
    private static final int OPAQUE_ALPHA = 255;
    private static final float MAGNIFIER_HOT_SPOT_FRACTION = 0.48f;

    private static @NotNull InterfaceImage createCheckerImage(int width, int height) {
        byte[] pixels = new byte[width * height * PIXEL_BYTES];

        for (int vertical = 0; vertical < height; vertical++) {
            for (int horizontal = 0; horizontal < width; horizontal++) {
                int offset = (vertical * width + horizontal) * PIXEL_BYTES;

                pixels[offset]                 = (byte) (horizontal * CHECKER_STEP);
                pixels[offset + GREEN_CHANNEL] = (byte) (vertical * CHECKER_STEP);
                pixels[offset + ALPHA_CHANNEL] = (byte) OPAQUE_ALPHA;
            }
        }

        return new InterfaceImage(width, height, pixels);
    }

    @Test
    void create_image_should_magnify_arrow_by_nearest_texel_with_hot_spot_at_corner() {
        CursorImage image = GameCursor.createImage(createCheckerImage(4, 3), 1, new int[0], GameCursor.getHotSpot(GameCursor.ARROW), 0, 2);

        Assertions.assertEquals(8, image.getWidth());
        Assertions.assertEquals(6, image.getHeight());
        Assertions.assertEquals(0, image.getHotSpotX());
        Assertions.assertEquals(0, image.getHotSpotY());

        // Screen pixel (5, 3) shows texel (2, 1).
        int offset = (3 * 8 + 5) * PIXEL_BYTES;

        Assertions.assertEquals(32, Byte.toUnsignedInt(image.getRgba()[offset]));
        Assertions.assertEquals(16, Byte.toUnsignedInt(image.getRgba()[offset + GREEN_CHANNEL]));
    }

    @Test
    void create_image_should_keep_hd_sheet_at_interface_size() {
        // A 2x sheet frame (8x6 texels = 4x3 interface pixels) at 1.5 window pixels per interface pixel is minified.
        CursorImage image = GameCursor.createImage(createCheckerImage(8, 6), 2, new int[0], GameCursor.getHotSpot("TargN"), 0, 1.5f);

        Assertions.assertEquals(6, image.getWidth());
        Assertions.assertEquals(5, image.getHeight());
        Assertions.assertEquals(3, image.getHotSpotX());
        Assertions.assertEquals(2, image.getHotSpotY());
    }

    @Test
    void create_image_should_place_animated_frames_in_box_so_hot_spot_stays() {
        // Box 10x8; frame 0 at (0, 0), frame 1 at (2, 1); the magnifier hot spot is 0.48 of the box.
        int[] box = {10, 8, 0, 0, 2, 1};
        float[] hotSpot = GameCursor.getHotSpot("MagG");
        CursorImage first = GameCursor.createImage(createCheckerImage(8, 7), 1, box, hotSpot, 0, 2);
        CursorImage second = GameCursor.createImage(createCheckerImage(8, 7), 1, box, hotSpot, 1, 2);

        Assertions.assertEquals(first.getWidth(), second.getWidth());
        Assertions.assertEquals(first.getHotSpotX(), second.getHotSpotX());
        Assertions.assertEquals(first.getHotSpotY(), second.getHotSpotY());
        Assertions.assertEquals(Math.round(MAGNIFIER_HOT_SPOT_FRACTION * 10 * 2), first.getHotSpotX());
        // Frame 1 starts 2 interface pixels (4 screen pixels) right and 1 (2) down: transparent before it, opaque at it.
        Assertions.assertEquals(0, second.getRgba()[(2 * second.getWidth() + 3) * PIXEL_BYTES + ALPHA_CHANNEL]);
        Assertions.assertEquals(255, Byte.toUnsignedInt(second.getRgba()[(2 * second.getWidth() + 4) * PIXEL_BYTES + ALPHA_CHANNEL]));
    }
}
