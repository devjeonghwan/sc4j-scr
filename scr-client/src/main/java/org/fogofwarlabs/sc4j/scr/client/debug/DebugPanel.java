package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the development panel of the debug view ({@code /debug}) as a column of titled sections on the left of the game screen: Performance, Camera
 * and map, Selection, Orders and paths, Game state. Rows are label/value pairs with a fixed label column in the interface font; long values are
 * clipped. It is drawn after the console so that the replay and console overlays never cover it. A page selector ({@code /debug page}) shows all
 * sections or one section at a time, and the panel never extends below the given bottom edge (the console top).
 * <p>
 * The panel also measures the client's frame times: {@link #recordFrame(long)} once per rendered frame and {@link #recordUpdate(long)} with the
 * input-and-simulation time of the frame; values are averaged over one second. The frame rate itself is counted by
 * {@link org.fogofwarlabs.sc4j.scr.client.ui.overlay.StatusOverlay}.
 */
public final class DebugPanel {
    /**
     * The value color.
     */
    public static final int VALUE_COLOR = 0xE6ECF2FF;
    /**
     * A muted value color.
     */
    public static final int MUTED_COLOR = 0x8E9BAEFF;
    /**
     * A good-state value color.
     */
    public static final int GOOD_COLOR = 0x6FD6A0FF;
    /**
     * A warning value color.
     */
    public static final int WARNING_COLOR = 0xEBC77AFF;
    /**
     * An error value color.
     */
    public static final int ERROR_COLOR = 0xFF8D8DFF;

    private static final int BACKGROUND_COLOR = 0x0B1018EA;
    private static final int HEADER_COLOR = 0x1B2636F0;
    private static final int TITLE_COLOR = 0x78C6FFFF;
    private static final int LABEL_COLOR = 0x9AA7B8FF;
    private static final int RULE_COLOR = 0x2A3A50FF;
    private static final float TEXT_HEIGHT = 13;
    private static final float PAGE_LABEL_TEXT_HEIGHT = TEXT_HEIGHT - 2;
    private static final float FOOTER_TEXT_HEIGHT = TEXT_HEIGHT - 1;
    private static final float ROW_HEIGHT = 16;
    private static final float TITLE_HEIGHT = 19;
    private static final float HEADER_HEIGHT = TITLE_HEIGHT - 2;
    private static final float PADDING = 9;
    private static final float LABEL_WIDTH = 78;
    private static final float PANEL_WIDTH = 360;
    private static final float BORDER_WIDTH = 1;
    private static final float HEADER_WIDTH = PANEL_WIDTH - 2 * BORDER_WIDTH;
    private static final float PAGE_LABEL_TEXT_OFFSET = 1;
    private static final float TITLE_TEXT_OFFSET = 2;
    private static final float ROW_TEXT_OFFSET = 1;
    private static final float RULE_HEIGHT = 1;
    private static final float FOOTER_RULE_OFFSET = 2;
    private static final float FOOTER_TEXT_OFFSET = 2;
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;
    private static final float NANOSECONDS_PER_MILLISECOND = 1e6f;
    private static final int SECTION_GAP = 5;
    private static final int TITLE_STRIPE_WIDTH = 3;
    private static final @NotNull String CLIP_MARK = "..";

    /**
     * A panel page.
     */
    public enum Page {
        /**
         * All sections.
         */
        ALL,
        /**
         * Performance only.
         */
        PERFORMANCE,
        /**
         * Camera and map only.
         */
        CAMERA,
        /**
         * Selection only.
         */
        SELECTION,
        /**
         * Orders and paths only.
         */
        ORDERS,
        /**
         * Game state only.
         */
        GAME
    }

    /**
     * One titled section of rows.
     */
    public static final class Section {
        private final @NotNull Page page;
        private final @NotNull String title;

        private final @NotNull List<String> labels;
        private final @NotNull List<String> values;
        private final @NotNull List<Integer> colors;

        /**
         * Creates a {@link Section} instance.
         *
         * @param page  the page that shows this section alone
         * @param title the section title
         */
        public Section(@NotNull Page page, @NotNull String title) {
            this.page  = page;
            this.title = title;

            this.labels = new ArrayList<>();
            this.values = new ArrayList<>();
            this.colors = new ArrayList<>();
        }

        /**
         * @return the number of rows
         */
        public int getRowCount() {
            return this.labels.size();
        }

        /**
         * Adds a label/value row in the default value color.
         *
         * @param label the row label
         * @param value the row value
         * @return this instance for chaining
         */
        public @NotNull Section add(@NotNull String label, @NotNull String value) {
            return this.add(label, value, VALUE_COLOR);
        }

        /**
         * Adds a label/value row.
         *
         * @param label the row label (empty for a continuation line)
         * @param value the row value
         * @param color the packed {@code 0xRRGGBBAA} value color
         * @return this instance for chaining
         */
        public @NotNull Section add(@NotNull String label, @NotNull String value, int color) {
            this.labels.add(label);
            this.values.add(value);
            this.colors.add(color);

            return this;
        }
    }

    private static @NotNull String clip(@NotNull InterfaceRenderer renderer, @NotNull String text, float width) {
        if (renderer.measureText(text, TEXT_HEIGHT) <= width) {
            return text;
        }

        int length = text.length();

        while (length > 0 && renderer.measureText(text.substring(0, length) + CLIP_MARK, TEXT_HEIGHT) > width) {
            length--;
        }

        return text.substring(0, length) + CLIP_MARK;
    }

    private @NotNull Page page;

    private long windowStartNanoseconds;
    private long lastFrameNanoseconds;
    private int windowFrames;
    private long windowFrameNanoseconds;
    private long windowMaximumFrameNanoseconds;
    private long windowUpdateNanoseconds;

    private float averageFrameMilliseconds;
    private float maximumFrameMilliseconds;
    private float averageUpdateMilliseconds;

    /**
     * Creates a {@link DebugPanel} instance showing all sections.
     */
    public DebugPanel() {
        this.page = Page.ALL;

        this.windowStartNanoseconds        = 0;
        this.lastFrameNanoseconds          = 0;
        this.windowFrames                  = 0;
        this.windowFrameNanoseconds        = 0;
        this.windowMaximumFrameNanoseconds = 0;
        this.windowUpdateNanoseconds       = 0;

        this.averageFrameMilliseconds  = 0;
        this.maximumFrameMilliseconds  = 0;
        this.averageUpdateMilliseconds = 0;
    }

    /**
     * @return the shown page
     */
    public @NotNull Page getPage() {
        return this.page;
    }

    /**
     * @return the average frame time of the last complete second in milliseconds
     */
    public float getAverageFrameMilliseconds() {
        return this.averageFrameMilliseconds;
    }

    /**
     * @return the longest frame time of the last complete second in milliseconds
     */
    public float getMaximumFrameMilliseconds() {
        return this.maximumFrameMilliseconds;
    }

    /**
     * @return the average input-and-simulation time per frame of the last complete second in milliseconds
     */
    public float getAverageUpdateMilliseconds() {
        return this.averageUpdateMilliseconds;
    }

    /**
     * Shows the next page (all sections, then each section alone).
     */
    public void cyclePage() {
        Page[] pages = Page.values();

        this.page = pages[(this.page.ordinal() + 1) % pages.length];
    }

    /**
     * Records the start of a rendered frame.
     *
     * @param nanoTime the {@link System#nanoTime()} of the frame
     */
    public void recordFrame(long nanoTime) {
        if (this.lastFrameNanoseconds != 0) {
            long duration = nanoTime - this.lastFrameNanoseconds;

            this.windowFrames++;
            this.windowFrameNanoseconds += duration;
            this.windowMaximumFrameNanoseconds = Math.max(this.windowMaximumFrameNanoseconds, duration);
        }

        this.lastFrameNanoseconds = nanoTime;

        if (this.windowStartNanoseconds == 0) {
            this.windowStartNanoseconds = nanoTime;
        }

        long elapsed = nanoTime - this.windowStartNanoseconds;

        if (elapsed >= NANOSECONDS_PER_SECOND && this.windowFrames > 0) {
            this.averageFrameMilliseconds  = this.windowFrameNanoseconds / NANOSECONDS_PER_MILLISECOND / this.windowFrames;
            this.maximumFrameMilliseconds  = this.windowMaximumFrameNanoseconds / NANOSECONDS_PER_MILLISECOND;
            this.averageUpdateMilliseconds = this.windowUpdateNanoseconds / NANOSECONDS_PER_MILLISECOND / this.windowFrames;

            this.windowStartNanoseconds        = nanoTime;
            this.windowFrames                  = 0;
            this.windowFrameNanoseconds        = 0;
            this.windowMaximumFrameNanoseconds = 0;
            this.windowUpdateNanoseconds       = 0;
        }
    }

    /**
     * Records the input-and-simulation time of the current frame.
     *
     * @param nanoseconds the elapsed nanoseconds
     */
    public void recordUpdate(long nanoseconds) {
        this.windowUpdateNanoseconds += nanoseconds;
    }

    /**
     * Draws the sections of the shown page and a footer line.
     *
     * @param renderer the interface renderer between {@code begin} and {@code end}
     * @param sections the sections in display order
     * @param footer   the key help line
     * @param left     the left screen edge
     * @param top      the top screen edge
     * @param bottom   the lowest screen row the panel may use (the console top)
     */
    public void draw(@NotNull InterfaceRenderer renderer,
                     @NotNull List<Section> sections,
                     @NotNull String footer,
                     float left, float top,
                     float bottom) {
        List<Section> shown = new ArrayList<>();

        for (Section section : sections) {
            if (this.page == Page.ALL || section.page == this.page) {
                shown.add(section);
            }
        }

        float height = PADDING * 2 + TITLE_HEIGHT + ROW_HEIGHT;

        for (Section section : shown) {
            height += TITLE_HEIGHT + section.getRowCount() * ROW_HEIGHT + SECTION_GAP;
        }

        height = Math.min(height, Math.max(0, bottom - top));

        if (height < TITLE_HEIGHT + PADDING * 2) {
            return;
        }

        renderer.fillRectangle(left, top, PANEL_WIDTH, height, BACKGROUND_COLOR);
        renderer.drawRectangle(left, top, PANEL_WIDTH, height, BORDER_WIDTH, RULE_COLOR);

        float limit = top + height - PADDING - ROW_HEIGHT;
        float y = top + PADDING;
        float width = PANEL_WIDTH - PADDING * 2;

        renderer.drawText("DEBUG", left + PADDING, y, TEXT_HEIGHT, TITLE_COLOR);

        String pageLabel = this.page.name().toLowerCase(Locale.ROOT) + " page  \u00b7  /debug page next  \u00b7  /debug off close";

        renderer.drawText(pageLabel,
                          left + PANEL_WIDTH - PADDING - renderer.measureText(pageLabel, PAGE_LABEL_TEXT_HEIGHT),
                          y + PAGE_LABEL_TEXT_OFFSET,
                          PAGE_LABEL_TEXT_HEIGHT,
                          MUTED_COLOR);

        y += TITLE_HEIGHT;

        for (Section section : shown) {
            if (y + TITLE_HEIGHT > limit) {
                break;
            }

            renderer.fillRectangle(left + BORDER_WIDTH, y, HEADER_WIDTH, HEADER_HEIGHT, HEADER_COLOR);
            renderer.fillRectangle(left + BORDER_WIDTH, y, TITLE_STRIPE_WIDTH, HEADER_HEIGHT, TITLE_COLOR);
            renderer.drawText(section.title, left + PADDING, y + TITLE_TEXT_OFFSET, TEXT_HEIGHT, TITLE_COLOR);

            y += TITLE_HEIGHT;

            for (int row = 0; row < section.getRowCount() && y + ROW_HEIGHT <= limit; row++) {
                renderer.drawText(section.labels.get(row), left + PADDING, y + ROW_TEXT_OFFSET, TEXT_HEIGHT, LABEL_COLOR);
                renderer.drawText(clip(renderer, section.values.get(row), width - LABEL_WIDTH),
                                  left + PADDING + LABEL_WIDTH,
                                  y + ROW_TEXT_OFFSET,
                                  TEXT_HEIGHT,
                                  section.colors.get(row));

                y += ROW_HEIGHT;
            }

            y += SECTION_GAP;
        }

        renderer.fillRectangle(left + PADDING, top + height - PADDING - ROW_HEIGHT - FOOTER_RULE_OFFSET, width, RULE_HEIGHT, RULE_COLOR);
        renderer.drawText(clip(renderer, footer, width),
                          left + PADDING,
                          top + height - PADDING - ROW_HEIGHT + FOOTER_TEXT_OFFSET,
                          FOOTER_TEXT_HEIGHT,
                          MUTED_COLOR);
    }
}
