package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.client.dialog.ViewScrollModel;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrControlType;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Draws and operates a vertical web-panel scroll bar over the engine {@link ViewScrollModel} (the content and view heights, the bounded
 * offset that follows the end until the user scrolls away, the thumb's length and place). This class keeps the page's steps (arrow
 * {@code 18}, wheel {@code 44}, track {@code 0.875} of the view), the bar's rectangle, its pressed part and the look. Arrow presses step
 * once without auto-repeat.
 * <p>
 * It is a control of its dialog ({@link ScrDialog#attach}). The native web panels have no dialog definition for the bar: it is a part of the panel
 * that scrolls (the lobby's chat log), so its definition is synthesized ({@link #createDefinition}): a {@code SCROLLBAR} at the panel's right edge,
 * without {@code WS_VISIBLE}, so the dialog neither draws it nor routes the pointer to it. The panel's painter draws it in the native order
 * ({@link #draw}, at the height of the scrolled view) and passes it the presses, drags and wheel turns.
 */
public final class WebScrollBar extends ScrControl {
    /**
     * The bar's width.
     */
    public static final float WIDTH = 8;
    private static final float BUTTON_HEIGHT = 10;
    private static final int TRACK_BORDER = 0x6C0000FF;
    private static final int THUMB_BORDER = 0xD60E00FF;
    private static final float THUMB_RADIUS = 1;
    private static final float LINE_STEP = 18;
    private static final float WHEEL_STEP = 44;
    private static final float PAGE_SHARE = 0.875f;
    private static final @NotNull String UP_IMAGE = "up_arrow_nofill_32.png";
    private static final @NotNull String UP_PRESSED_IMAGE = "up_arrow_filled_32.png";
    private static final @NotNull String DOWN_IMAGE = "down_arrow_nofill_32.png";
    private static final @NotNull String DOWN_PRESSED_IMAGE = "down_arrow_filled_32.png";
    private static final int NONE = 0;
    private static final int UP = 1;
    private static final int DOWN = 2;
    private static final int THUMB = 3;

    /**
     * Creates the synthesized definition of a panel's scroll bar: a hidden {@code SCROLLBAR} of the bar's width along the panel's right edge.
     *
     * @param id    the control id, one the panel's dialog does not use
     * @param panel the panel that scrolls
     * @return the created {@link DialogControl} instance
     */
    public static @NotNull DialogControl createDefinition(int id, @NotNull DialogControl panel) {
        int width = (int) WIDTH;

        return ScrDialogControlUtils.createControl(ScrControlType.SCROLLBAR,
                                                   id,
                                                   panel.getX() + panel.getWidth() - width, panel.getY(),
                                                   width, panel.getHeight(),
                                                   Set.of(),
                                                   "",
                                                   List.of());
    }


    private final @NotNull ViewScrollModel model;

    /**
     * The bar's last drawn rectangle, for the input.
     */
    private float left;
    private float top;
    private float height;
    private boolean shown;
    private int pressed;
    private float dragOffset;

    /**
     * Creates a {@link WebScrollBar} instance that follows the end of supplied content.
     *
     * @param definition the bar's definition ({@link #createDefinition})
     */
    public WebScrollBar(@NotNull DialogControl definition) {
        super(definition);

        this.model = new ViewScrollModel();

        this.left       = 0;
        this.top        = 0;
        this.height     = 0;
        this.shown      = false;
        this.pressed    = NONE;
        this.dragOffset = 0;
    }

    /**
     * Sets the content and view heights; the offset stays inside them, and at the end while it is there.
     *
     * @param content the content's height
     * @param view    the view's height
     */
    public void setContent(float content, float view) {
        this.model.setContent(content, view);
    }

    /**
     * @return how far the content is scrolled up, in interface pixels
     */
    public float getOffset() {
        return this.model.getOffset();
    }

    private boolean isNeeded() {
        return this.model.isScrollable() && this.model.getView() > 2 * BUTTON_HEIGHT;
    }

    private float getTrackLength() {
        return this.height - 2 * BUTTON_HEIGHT;
    }

    private float getThumbLength() {
        return this.model.getThumbLength(this.getTrackLength(), WIDTH);
    }

    private float getThumbTop() {
        return this.top + BUTTON_HEIGHT + this.model.getThumbOffset(this.getTrackLength(), WIDTH);
    }


    /**
     * Scrolls to the end of the content and keeps it there.
     */
    public void scrollToEnd() {
        this.model.scrollToEnd();
    }

    /**
     * Scrolls a wheel notch.
     *
     * @param down {@code true} for a notch toward the user
     */
    public void scrollByWheel(boolean down) {
        this.model.scrollBy(down ? WHEEL_STEP : -WHEEL_STEP);
    }

    /**
     * Draws the control natively at a position: the bar at its definition's height ({@link #draw}).
     *
     * @param canvas the SC:R dialog painter
     * @param left   the control's left edge in interface pixels, the shift included
     * @param top    the control's top edge in interface pixels, the vertical shift included
     * @throws IOException if an arrow image cannot be read
     */
    @Override
    public void drawAt(@NotNull DialogPainter canvas, float left, float top) throws IOException {
        this.draw(canvas, left, top, this.getDefinition().getHeight());
    }

    /**
     * Draws the bar into a rectangle when the content does not fit its view.
     *
     * @param painter the painter
     * @param x       the bar's left
     * @param y       the bar's top
     * @param height  the bar's height (the view's)
     * @throws IOException if an arrow image cannot be read
     */
    public void draw(@NotNull DialogPainter painter, float x, float y, float height) throws IOException {
        this.left   = x;
        this.top    = y;
        this.height = height;
        this.shown  = this.isNeeded();

        if (!this.shown) {
            return;
        }

        painter.drawFrame(painter.getWebImage(this.pressed == UP ? UP_PRESSED_IMAGE : UP_IMAGE), x, y, WIDTH, BUTTON_HEIGHT);
        painter.drawFrame(painter.getWebImage(this.pressed == DOWN ? DOWN_PRESSED_IMAGE : DOWN_IMAGE),
                          x, y + height - BUTTON_HEIGHT,
                          WIDTH, BUTTON_HEIGHT);
        WebPanelStyleUtils.drawBox(painter, x, y + BUTTON_HEIGHT, WIDTH, this.getTrackLength(), TRACK_BORDER, 0, false, false, true, true);
        WebPanelStyleUtils.drawBox(painter, x, this.getThumbTop(), WIDTH, this.getThumbLength(), THUMB_RADIUS, THUMB_BORDER, 0,
                                   true, true, true, true);
    }

    /**
     * Presses the left button.
     *
     * @param x the interface x
     * @param y the interface y
     * @return {@code true} if the press was on the bar, {@code false} otherwise
     */
    public boolean press(float x, float y) {
        if (!this.shown || x < this.left || x >= this.left + WIDTH || y < this.top || y >= this.top + this.height) {
            return false;
        }

        float thumbTop = this.getThumbTop();

        if (y < this.top + BUTTON_HEIGHT) {
            this.pressed = UP;

            this.model.scrollBy(-LINE_STEP);
        } else if (y >= this.top + this.height - BUTTON_HEIGHT) {
            this.pressed = DOWN;

            this.model.scrollBy(LINE_STEP);
        } else if (y >= thumbTop && y < thumbTop + this.getThumbLength()) {
            this.pressed    = THUMB;
            this.dragOffset = y - thumbTop;
        } else {
            this.model.scrollBy((y < thumbTop ? -1 : 1) * this.model.getView() * PAGE_SHARE);
        }

        return true;
    }

    /**
     * Moves the pointer: a held thumb follows it.
     *
     * @param y the interface y
     */
    public void drag(float y) {
        if (this.pressed != THUMB) {
            return;
        }

        this.model.dragThumb(y - this.dragOffset - this.top - BUTTON_HEIGHT, this.getTrackLength(), WIDTH);
    }

    /**
     * Releases the left button.
     */
    public void release() {
        this.pressed = NONE;
    }
}
