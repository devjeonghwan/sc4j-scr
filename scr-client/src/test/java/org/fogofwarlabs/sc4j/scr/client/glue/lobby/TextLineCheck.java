package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;

/**
 * Measures, in a drawn frame, where a control's mark (a radio circle, a check box, a list row's icon) and the text beside it stand
 * vertically: the center of the mark's frame-red or lit pixels, the center of the text's letters and the bottom of its last glyph.
 */
final class TextLineCheck {
    /**
     * A red pixel (a mark's border): its red channel at least this and at least this many times each of the others.
     */
    private static final int RED_MINIMUM = 60;
    private static final int RED_FACTOR = 3;
    /**
     * A lit pixel (text or an icon): a channel at least this, not red.
     */
    private static final int LIT_MINIMUM = 100;
    private static final int BYTE_MASK = 0xFF;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final float CENTER_DIVISOR = 2f;
    private static final int NOT_FOUND = -1;

    static boolean isRed(int color) {
        int red = color >> RED_SHIFT & BYTE_MASK;
        int green = color >> GREEN_SHIFT & BYTE_MASK;
        int blue = color & BYTE_MASK;

        return red >= RED_MINIMUM && green * RED_FACTOR <= red && blue * RED_FACTOR <= red;
    }

    static boolean isLit(int color) {
        int brightest = Math.max(color >> RED_SHIFT & BYTE_MASK, Math.max(color >> GREEN_SHIFT & BYTE_MASK, color & BYTE_MASK));

        return brightest >= LIT_MINIMUM && !isRed(color);
    }

    /**
     * Returns the center row of the red pixels in a rectangle of the frame.
     *
     * @return the center row, or {@code -1} without red pixels
     */
    static float findRedCenter(@NotNull BufferedImage frame, int left, int top, int right, int bottom) {
        int first = NOT_FOUND;
        int last = NOT_FOUND;

        for (int row = top; row <= bottom; row++) {
            for (int column = left; column <= right; column++) {
                if (isRed(frame.getRGB(column, row))) {
                    first = first == NOT_FOUND ? row : first;
                    last  = row;
                }
            }
        }

        return first == NOT_FOUND ? NOT_FOUND : (first + last) / CENTER_DIVISOR;
    }

    /**
     * Returns the center row of the lit pixels in a rectangle of the frame.
     *
     * @return the center row, or {@code -1} without lit pixels
     */
    static float findLitCenter(@NotNull BufferedImage frame, int left, int top, int right, int bottom) {
        int[] rows = findLitRows(frame, left, top, right, bottom);

        return rows[0] == NOT_FOUND ? NOT_FOUND : (rows[0] + rows[1]) / CENTER_DIVISOR;
    }

    /**
     * Returns the center row of a one-line text's letters: between the top of its ink and the bottom of its last glyph, which stands on the
     * baseline in the texts measured (no descender at the end), so that a descender before it does not count.
     *
     * @return the center row, or {@code -1} without a glyph
     */
    static float findTextCenter(@NotNull BufferedImage frame, int left, int top, int right, int bottom) {
        int inkTop = findLitRows(frame, left, top, right, bottom)[0];

        return inkTop == NOT_FOUND ? NOT_FOUND : (inkTop + findLastGlyphBottom(frame, left, top, right, bottom)) / CENTER_DIVISOR;
    }

    /**
     * Returns the bottom row of the last glyph of a text: the lit columns from the last lit column left of {@code right} back to the
     * previous column without a lit pixel.
     *
     * @return the bottom row, or {@code -1} without a glyph
     */
    static int findLastGlyphBottom(@NotNull BufferedImage frame, int left, int top, int right, int bottom) {
        int last = right;

        while (last >= left && !hasLitPixel(frame, last, top, bottom)) {
            last--;
        }

        int first = last;

        while (first - 1 >= left && hasLitPixel(frame, first - 1, top, bottom)) {
            first--;
        }

        return last < left ? NOT_FOUND : findLitRows(frame, first, top, last, bottom)[1];
    }

    private static boolean hasLitPixel(@NotNull BufferedImage frame, int column, int top, int bottom) {
        for (int row = top; row <= bottom; row++) {
            if (isLit(frame.getRGB(column, row))) {
                return true;
            }
        }

        return false;
    }

    private static int @NotNull [] findLitRows(@NotNull BufferedImage frame, int left, int top, int right, int bottom) {
        int first = NOT_FOUND;
        int last = NOT_FOUND;

        for (int row = top; row <= bottom; row++) {
            for (int column = left; column <= right; column++) {
                if (isLit(frame.getRGB(column, row))) {
                    first = first == NOT_FOUND ? row : first;
                    last  = row;
                }
            }
        }

        return new int[]{first, last};
    }

    private TextLineCheck() {
    }
}
