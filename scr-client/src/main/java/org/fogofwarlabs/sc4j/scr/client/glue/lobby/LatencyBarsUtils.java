package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.engine.net.beaconlink.LatencyClass;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueCanvas;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Draws the latency bars of the lobby page ({@code .bar}: {@code 4 x 10} images side by side, {@code green_bar_32.png} for {@code 4} or
 * {@code 5} bars, yellow for {@code 2} or {@code 3}, red for {@code 1}). The game lobby and the Beacon.Link user list share them.
 */
final class LatencyBarsUtils {
    /**
     * The most bars.
     */
    static final int MAXIMUM_BARS = 5;
    static final float BAR_WIDTH = 4;
    /**
     * The width of the most bars.
     */
    static final float WIDTH = MAXIMUM_BARS * BAR_WIDTH;

    private static final float BAR_HEIGHT = 10;
    private static final int CENTER_DIVISOR = 2;
    private static final int GOOD_BARS = 3;
    private static final int FAIR_BARS = 1;
    /**
     * The bars of a Beacon.Link latency estimate's classes.
     */
    private static final int GOOD_CLASS_BARS = MAXIMUM_BARS;
    private static final int FAIR_CLASS_BARS = 3;
    private static final int POOR_CLASS_BARS = 1;
    /**
     * The bar images for good ({@code 4} or {@code 5} bars), fair ({@code 2} or {@code 3}) and poor ({@code 1}) connections.
     */
    private static final @NotNull String GOOD_IMAGE = "green_bar_32.png";
    private static final @NotNull String FAIR_IMAGE = "yellow_bar_32.png";
    private static final @NotNull String POOR_IMAGE = "red_bar_32.png";

    /**
     * Returns the bars of a latency estimate's class.
     *
     * @param latencyClass the class
     * @return {@code 5} for good, {@code 3} for fair, {@code 1} for poor and {@code 0} (no bars) for an unknown latency
     */
    static int getBarCount(@NotNull LatencyClass latencyClass) {
        return switch (latencyClass) {
            case GOOD -> GOOD_CLASS_BARS;
            case FAIR -> FAIR_CLASS_BARS;
            case POOR -> POOR_CLASS_BARS;
            case UNKNOWN -> 0;
        };
    }

    /**
     * Draws bars from the left of a row, centered in its height, in the color of their count; no bars draw nothing.
     *
     * @param canvas    the glue canvas
     * @param bars      the bar count, {@code 0..5}
     * @param left      the left edge of the bars
     * @param top       the top edge of the row
     * @param rowHeight the row's height
     * @throws IOException if a bar image cannot be read
     */
    static void draw(@NotNull GlueCanvas canvas, int bars, float left, float top, float rowHeight) throws IOException {
        if (bars == 0) {
            return;
        }

        String image = bars > GOOD_BARS ? GOOD_IMAGE : bars > FAIR_BARS ? FAIR_IMAGE : POOR_IMAGE;
        GlueCanvas.Image bar = canvas.getWebImage(image);

        for (int index = 0; index < bars; index++) {
            canvas.drawImage(bar, left + index * BAR_WIDTH, top + (rowHeight - BAR_HEIGHT) / CENTER_DIVISOR, BAR_WIDTH, BAR_HEIGHT);
        }
    }

    private LatencyBarsUtils() {
    }
}
