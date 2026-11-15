package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceTransform;
import org.jetbrains.annotations.NotNull;

/**
 * Positions console dialogs and the three HD console pieces for a wide interface. The wide variant splits the minimap and command-card sides; the
 * centered variant keeps the base layout centered. The resource counters remain anchored to the right edge.
 */
public final class HdConsoleLayout {
    /**
     * The HD2 texture pixels per interface pixel.
     */
    public static final int HD2_SCALE = 2;

    private static final float LEFT_PIECE_OVERLAP = 106.666656f;
    private static final int CENTER_PIECE_LEFT = 137;
    /**
     * The share of the extra interface width that centers a piece.
     */
    private static final float HALF = 0.5f;

    /**
     * The HD console layout variant.
     */
    public enum Variant {
        /**
         * Minimap at the left edge, command card at the right edge.
         */
        WIDE,
        /**
         * The {@code 640}-wide layout centered.
         */
        CENTERED
    }

    /**
     * Creates a {@link ConsoleLayout} instance with the SD dialogs moved for a wide interface.
     *
     * @param sdLayout       the SD console layout of the race
     * @param interfaceWidth the game screen width in interface pixels
     * @param variant        the layout variant
     * @return the created {@link ConsoleLayout} instance
     */
    public static @NotNull ConsoleLayout createLayout(@NotNull ConsoleLayout sdLayout, float interfaceWidth, @NotNull Variant variant) {
        // Dialogs move by whole pixels: every dialog moves by the centered-dialog offset, half the extra width; the wide
        // variant moves the right-side dialogs by that offset again and the minimap back by it.
        float extra = interfaceWidth - ConsoleLayout.INTERFACE_WIDTH;
        float half = InterfaceTransform.getCenteredDialogLeft(interfaceWidth);
        float right = variant == Variant.WIDE ? 2 * half : half;
        float left = variant == Variant.WIDE ? 0 : half;

        // The resource dialog is anchored to the right edge: it moves by the full extra width, not by twice the truncated
        // centered offset, and no split move applies to it, so its right edge is the screen edge in both variants.
        return sdLayout.createMovedLayout(interfaceWidth, ConsoleLayout.CONSOLE_TOP, right, 0, half, 0, (int) extra, 0, right, 0, left, 0, right, 0);
    }

    /**
     * Positions the left, center and right pieces along the interface bottom edge. The wide variant separates the side pieces by
     * the centered-dialog offset.
     *
     * @param interfaceWidth the interface width in pixels
     * @param variant        the console placement variant
     * @param left           the left piece width and height in HD2 pixels
     * @param center         the center piece width and height in HD2 pixels
     * @param right          the right piece width and height in HD2 pixels
     * @return the left, center and right top-left coordinate pairs
     */
    public static float @NotNull [] placePieces(float interfaceWidth,
                                                @NotNull Variant variant,
                                                int @NotNull [] left,
                                                int @NotNull [] center,
                                                int @NotNull [] right) {
        float extra = interfaceWidth - ConsoleLayout.INTERFACE_WIDTH;
        int half = InterfaceTransform.getCenteredDialogLeft(interfaceWidth);
        float leftX = (int) (extra * HALF - LEFT_PIECE_OVERLAP);
        float centerX = (int) (CENTER_PIECE_LEFT + extra * HALF);
        float rightX = (int) (float) Math.ceil(ConsoleLayout.INTERFACE_WIDTH - (right[0] / (float) HD2_SCALE - LEFT_PIECE_OVERLAP) + extra * HALF);

        if (variant == Variant.WIDE) {
            leftX -= half;
            rightX += half;
        }

        float bottom = ConsoleLayout.INTERFACE_HEIGHT;

        return new float[]{
                leftX, bottom - left[1] / HD2_SCALE,
                centerX, bottom - center[1] / HD2_SCALE,
                rightX, bottom - right[1] / HD2_SCALE,
                };
    }

    private HdConsoleLayout() {
    }
}
