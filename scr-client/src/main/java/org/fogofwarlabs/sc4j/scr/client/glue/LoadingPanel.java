package org.fogofwarlabs.sc4j.scr.client.glue;

import org.fogofwarlabs.sc4j.scr.client.ui.dialog.DialogPainter;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceAssets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws a centered loading panel with its title, caption, percentage and console progress bar.
 * The panel uses the glue popup frame while title and percentage share one font baseline.
 */
public final class LoadingPanel {
    private static final @NotNull String PANEL_ART = GlueScreen.SHARED_PALETTE_FOLDER + GlueScreen.POPUP_PANEL_IMAGE;
    private static final @NotNull String EMPTY_BAR = "game/consoles/terran/pbrempt.DDS";
    private static final @NotNull String FULL_BAR = "game/consoles/terran/pbrfull.DDS";
    private static final @NotNull String LOADING_KEY = "titledlg-2";
    /**
     * The title shown when the control strings have no {@link #LOADING_KEY} text.
     */
    private static final @NotNull String DEFAULT_LOADING_TEXT = "Loading";
    private static final @NotNull String PERCENT_SIGN = "%";
    private static final int TITLE_TOP_OFFSET = 2;

    private static final int PANEL_WIDTH = 320;
    private static final int PADDING = 18;
    private static final int LINE_HEIGHT = 20;
    /**
     * The gap between the title and the percentage, and between the title line and the bar.
     */
    private static final int GAP = 10;
    private static final int DIM_COLOR = 0x00000090;
    private static final int PANEL_BACKING_COLOR = 0x000000FF;
    private static final int PERCENT = 100;
    /**
     * The number of edges across a width or height: the padding and the border lie on both sides.
     */
    private static final int EDGE_COUNT = 2;
    /**
     * The divisor that centers a length in a larger one.
     */
    private static final int CENTER_DIVISOR = 2;

    /**
     * Creates a {@link LoadingPanel} instance by loading its palette and title.
     *
     * @param glueContext the glue services
     * @return the created {@link LoadingPanel} instance
     * @throws IOException if the palette cannot be read
     */
    static @NotNull LoadingPanel load(@NotNull GlueContext glueContext) throws IOException {
        String text = glueContext.getControlText().get(LOADING_KEY);

        return new LoadingPanel(GluePalette.load(glueContext.getCanvas().getStorage(), GlueScreen.SHARED_PALETTE_FOLDER),
                                text.isEmpty() ? DEFAULT_LOADING_TEXT : text);
    }


    private final @NotNull GluePalette palette;
    private final @NotNull String loadingText;

    /**
     * The segment period of the bar art per asset set, found once from the images.
     */
    private final @NotNull Map<String, Float> segmentPeriods;

    private LoadingPanel(@NotNull GluePalette palette, @NotNull String loadingText) {
        this.palette     = palette;
        this.loadingText = loadingText;

        this.segmentPeriods = new HashMap<>();
    }


    /**
     * Draws the panel centered on the interface.
     *
     * @param canvas   the glue canvas
     * @param caption  what loads, for example {@code HD2}, or {@code null}
     * @param progress the progress {@code 0..1}
     * @throws IOException if an image cannot be read
     */
    void draw(@NotNull GlueCanvas canvas, @Nullable String caption, float progress) throws IOException {
        canvas.fillRectangle(0, 0, canvas.getInterfaceWidth(), GlueCanvas.INTERFACE_HEIGHT, DIM_COLOR);

        boolean hasBar = canvas.hasImage(EMPTY_BAR) && canvas.hasImage(FULL_BAR);
        GlueCanvas.Image empty = hasBar ? canvas.getImage(EMPTY_BAR) : null;
        GlueCanvas.Image full = hasBar ? canvas.getImage(FULL_BAR) : null;
        String title = caption == null ? this.loadingText : this.loadingText + " (" + caption + ")";
        float shownProgress = Math.clamp(progress, 0f, 1f);
        // The panel keeps its width unless the title line (the percentage at its widest) needs more.
        float lineWidth = canvas.measure(GlueCanvas.BIG_FONT, title) + GAP + canvas.measure(GlueCanvas.BIG_FONT, PERCENT + PERCENT_SIGN);
        float innerWidth = (float) Math.ceil(Math.max(lineWidth, PANEL_WIDTH - EDGE_COUNT * PADDING));
        float period = empty == null ? 0 : this.getSegmentPeriod(canvas);

        if (empty != null) {
            // The bar repeats whole segments, so its width is the art's plus whole periods; the panel follows it.
            innerWidth = DialogPainter.fitProgressBarWidth(empty.getWidth(), innerWidth, period);
        }

        float barHeight = empty == null ? 0 : empty.getHeight();
        float panelWidth = innerWidth + EDGE_COUNT * PADDING;
        float panelHeight = PADDING + LINE_HEIGHT + GAP + barHeight + PADDING;
        float left = (int) ((canvas.getInterfaceWidth() - panelWidth) / CENTER_DIVISOR);
        float top = (int) ((GlueCanvas.INTERFACE_HEIGHT - panelHeight) / CENTER_DIVISOR);

        canvas.fillRectangle(left + GlueCanvas.POPUP_PANEL_BORDER, top + GlueCanvas.POPUP_PANEL_BORDER,
                             panelWidth - EDGE_COUNT * GlueCanvas.POPUP_PANEL_BORDER, panelHeight - EDGE_COUNT * GlueCanvas.POPUP_PANEL_BORDER,
                             PANEL_BACKING_COLOR);

        if (canvas.hasImage(PANEL_ART)) {
            canvas.drawPopupPanel(canvas.getImage(PANEL_ART), left, top, panelWidth, panelHeight);
        }

        canvas.setPalette(this.palette);

        float textLeft = left + PADDING;
        // The title and the percentage are one line of one font laid out from the same top, so they share its baseline;
        // the line is centered in its row by the font's line height.
        float textTop = top + PADDING - TITLE_TOP_OFFSET + (int) ((LINE_HEIGHT - canvas.getLineHeight(GlueCanvas.BIG_FONT)) / CENTER_DIVISOR);

        canvas.drawText(GlueCanvas.BIG_FONT,
                        title,
                        textLeft, textTop,
                        innerWidth, LINE_HEIGHT,
                        GlueCanvas.FORMAT_LEFT | GlueCanvas.FORMAT_TOP,
                        this.palette.getCodeColor(DialogPainter.CODE_WHITE),
                        this.palette);
        canvas.drawText(GlueCanvas.BIG_FONT,
                        Math.round(shownProgress * PERCENT) + PERCENT_SIGN,
                        textLeft, textTop,
                        innerWidth, LINE_HEIGHT,
                        GlueCanvas.FORMAT_RIGHT | GlueCanvas.FORMAT_TOP,
                        this.palette.getCodeColor(DialogPainter.CODE_HIGHLIGHT),
                        this.palette);

        if (empty != null) {
            canvas.drawProgressBar(empty, full, textLeft, top + PADDING + LINE_HEIGHT + GAP, shownProgress * PERCENT, innerWidth, period);
        }
    }

    /**
     * Finds the segment period of the current asset set's bar art, detecting it once per asset set.
     *
     * @param canvas the glue canvas
     * @return the segment period of the current set's bar art ({@link DialogPainter#detectSegmentPeriod}), {@code 0} when it does not repeat
     * @throws IOException if the bar art cannot be read
     */
    private float getSegmentPeriod(@NotNull GlueCanvas canvas) throws IOException {
        String assetSet = canvas.getQualityPrefix();
        Float period = this.segmentPeriods.get(assetSet);

        if (period == null) {
            InterfaceAssets.ScaledImage empty = canvas.getAssets().decodeTexture(EMPTY_BAR);
            InterfaceAssets.ScaledImage full = canvas.getAssets().decodeTexture(FULL_BAR);

            period = DialogPainter.detectSegmentPeriod(empty.getScale(), empty.getImage(), full.getImage());
            this.segmentPeriods.put(assetSet, period);
        }

        return period;
    }
}
