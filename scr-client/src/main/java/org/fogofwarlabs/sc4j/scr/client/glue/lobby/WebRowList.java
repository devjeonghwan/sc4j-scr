package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueCanvas;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.WebPanelStyleUtils;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.WebScrollBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Shows a web page's list of equal rows in a rectangle: the visible rows clipped to it, the page's scroll bar at its right while the rows do
 * not fit, and one selected row; a framed list ({@link #drawFramed}) draws the pages' rounded frame around the rectangle and its rows
 * {@link #FRAME_PADDING} inside it. The Beacon.Link user list, channel list and friends list share it; each draws its own rows.
 */
final class WebRowList {
    /**
     * The selection of a list without a selected row.
     */
    static final int NO_SELECTION = -1;
    /**
     * The space between a framed list's frame and its rows on every side ({@code .channelHolder}: border {@code 1}, {@code .channelSelector}:
     * padding {@code 4}).
     */
    static final int FRAME_PADDING = 5;

    private static final @NotNull String NO_SCROLL_BAR_MESSAGE = "The row list has no scroll bar yet.";
    private static final int SIDE_COUNT = 2;

    /**
     * Draws one row of the list.
     */
    interface RowPainter {
        /**
         * Draws a row.
         *
         * @param canvas   the glue canvas
         * @param row      the row index
         * @param selected whether the row is the selected one
         * @param left     the row's left edge in interface pixels
         * @param top      the row's top edge in interface pixels
         * @param width    the row's width, less the scroll bar while it shows
         * @throws IOException if an image cannot be read
         */
        void drawRow(@NotNull GlueCanvas canvas, int row, boolean selected, float left, float top, float width) throws IOException;
    }

    private final float rowHeight;

    private @Nullable WebScrollBar scroll;
    private int selection;

    /**
     * Creates a {@link WebRowList} instance without a scroll bar and without a selected row.
     *
     * @param rowHeight the height of a row
     */
    WebRowList(float rowHeight) {
        this.rowHeight = rowHeight;

        this.scroll    = null;
        this.selection = NO_SELECTION;
    }

    /**
     * @return the selected row, or {@link #NO_SELECTION}
     */
    int getSelection() {
        return this.selection;
    }

    /**
     * Sets the selected row.
     *
     * @param selection the row, or {@link #NO_SELECTION}
     */
    void setSelection(int selection) {
        this.selection = selection;
    }

    /**
     * Sets the list's scroll bar, which the screen attached to its dialog at open.
     *
     * @param scroll the scroll bar
     */
    void setScrollBar(@NotNull WebScrollBar scroll) {
        this.scroll = scroll;
    }


    /**
     * Finds the row under a point of the list's rectangle.
     *
     * @param x        the interface x
     * @param y        the interface y
     * @param left     the list's left edge in interface pixels
     * @param top      the list's top edge in interface pixels
     * @param width    the list's width
     * @param height   the list's height
     * @param rowCount the number of rows
     * @return the row, or {@link #NO_SELECTION} if the point is in the list below its last row
     * @throws IllegalStateException if the list has no scroll bar yet
     */
    int findRow(float x, float y, float left, float top, float width, float height, int rowCount) {
        if (!GlueCanvas.contains(x, y, left, top, width, height)) {
            return NO_SELECTION;
        }

        int row = (int) ((y - top + this.requireScrollBar().getOffset()) / this.rowHeight);

        return row < rowCount ? row : NO_SELECTION;
    }

    /**
     * Draws the visible rows clipped to the list's rectangle, then the scroll bar while the rows do not fit.
     *
     * @param canvas   the glue canvas
     * @param left     the list's left edge in interface pixels
     * @param top      the list's top edge in interface pixels
     * @param width    the list's width
     * @param height   the list's height
     * @param rowCount the number of rows
     * @param painter  draws each row
     * @throws IOException           if an image cannot be read
     * @throws IllegalStateException if the list has no scroll bar yet
     */
    void draw(@NotNull GlueCanvas canvas, float left, float top, float width, float height, int rowCount, @NotNull RowPainter painter)
    throws IOException {
        WebScrollBar bar = this.requireScrollBar();
        float rowWidth = rowCount * this.rowHeight > height ? width - WebScrollBar.WIDTH : width;

        bar.setContent(rowCount * this.rowHeight, height);
        canvas.setClip(left, top, width, height);

        float rowTop = top - bar.getOffset();

        for (int index = 0; index < rowCount; index++, rowTop += this.rowHeight) {
            if (rowTop + this.rowHeight <= top || rowTop >= top + height) {
                continue;
            }

            painter.drawRow(canvas, index, index == this.selection, left, rowTop, rowWidth);
        }

        canvas.setClip(0, 0, GlueCanvas.NO_CLIP_SIZE, 0);
        bar.draw(canvas, left + width - WebScrollBar.WIDTH, top, height);
    }

    /**
     * Draws a framed list: the pages' rounded frame around the rectangle, then the rows {@link #FRAME_PADDING} inside it ({@link #draw}).
     *
     * @param canvas   the glue canvas
     * @param left     the frame's left edge in interface pixels
     * @param top      the frame's top edge in interface pixels
     * @param width    the frame's width
     * @param height   the frame's height
     * @param rowCount the number of rows
     * @param painter  draws each row
     * @throws IOException           if an image cannot be read
     * @throws IllegalStateException if the list has no scroll bar yet
     */
    void drawFramed(@NotNull GlueCanvas canvas, float left, float top, float width, float height, int rowCount, @NotNull RowPainter painter)
    throws IOException {
        WebPanelStyleUtils.drawRoundedFrame(canvas, left, top, width, height, WebPanelStyleUtils.BORDER);
        this.draw(canvas,
                  left + FRAME_PADDING, top + FRAME_PADDING,
                  width - SIDE_COUNT * FRAME_PADDING, height - SIDE_COUNT * FRAME_PADDING,
                  rowCount,
                  painter);
    }

    /**
     * Presses the scroll bar.
     *
     * @param x the interface x
     * @param y the interface y
     * @return {@code true} if the press hit the scroll bar, {@code false} otherwise
     * @throws IllegalStateException if the list has no scroll bar yet
     */
    boolean press(float x, float y) {
        return this.requireScrollBar().press(x, y);
    }

    /**
     * Drags the pressed scroll bar.
     *
     * @param y the interface y
     * @throws IllegalStateException if the list has no scroll bar yet
     */
    void drag(float y) {
        this.requireScrollBar().drag(y);
    }

    /**
     * Releases the scroll bar.
     *
     * @throws IllegalStateException if the list has no scroll bar yet
     */
    void release() {
        this.requireScrollBar().release();
    }

    /**
     * Scrolls the list by a wheel notch.
     *
     * @param down {@code true} for a notch toward the user
     * @throws IllegalStateException if the list has no scroll bar yet
     */
    void scrollByWheel(boolean down) {
        this.requireScrollBar().scrollByWheel(down);
    }

    private @NotNull WebScrollBar requireScrollBar() {
        WebScrollBar bar = this.scroll;

        if (bar == null) {
            throw Exceptions.newIllegalStateException(NO_SCROLL_BAR_MESSAGE);
        }

        return bar;
    }
}
