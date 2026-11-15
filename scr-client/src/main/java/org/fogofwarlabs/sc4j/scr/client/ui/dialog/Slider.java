package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.client.dialog.ScrollModel;
import org.fogofwarlabs.sc4j.engine.client.dialog.ScrollPart;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Draws a horizontal dialog slider over the engine {@link ScrollModel} (range, value, page and thumb drag): the {@code 2} pixels at
 * each end step the value by one, the track before and after the thumb moves it by a page, and dragging the thumb maps the pointer onto
 * its travel. It draws as the pages' slider: a thin track, round stops and the round thumb ({@link WebPanelStyleUtils#drawSliderThumb}),
 * centered on the control's height, gray while disabled. Keyboard input, focus outlines and held-part auto-repeat are not represented.
 * <p>
 * A slider is the SC:R control of its horizontal {@code SCROLLBAR} definition: a screen attaches it in place of the control its
 * dialog built ({@link ScrDialog#attach}); it takes input and draws enabled while the control is active ({@link #drawAt}).
 */
public final class Slider extends ScrControl {
    private static final int MAXIMUM_TICKS = 7;
    /**
     * The pixels at each end that step the value.
     */
    private static final int EDGE = 2;

    private final @NotNull ScrollModel model;

    private boolean enabled;

    /**
     * Creates a {@link Slider} instance.
     *
     * @param control the dialog control
     */
    public Slider(@NotNull DialogControl control) {
        super(control);

        this.model = new ScrollModel();

        this.enabled = true;
    }

    /**
     * Sets the range and clamps the value into it.
     *
     * @param minimum the smallest value
     * @param maximum the largest value
     */
    public void setRange(int minimum, int maximum) {
        this.model.setRange(minimum, maximum);
    }

    /**
     * Sets the visible page size.
     *
     * @param page the step of a press before or after the thumb
     */
    public void setPage(int page) {
        this.model.setPage(page);
    }

    /**
     * Sets the slider value.
     *
     * @param value the value, clamped to the range
     */
    public void setValue(int value) {
        this.model.setValue(value);
    }

    /**
     * @return the value
     */
    public int getValue() {
        return this.model.getValue();
    }

    /**
     * Sets whether the slider takes input and draws enabled; a slider that does not take input ends its drag.
     *
     * @param enabled whether the slider takes input
     */
    private void setInputEnabled(boolean enabled) {
        this.enabled = enabled;

        if (!enabled) {
            this.model.release();
        }
    }

    /**
     * @return the thumb's travel: the control's width less the thumb
     */
    private int getTravel() {
        return this.getDefinition().getWidth() - (int) WebPanelStyleUtils.getSliderThumbSize();
    }

    /**
     * Presses the slider: a step, a page or the start of a drag.
     *
     * @param offsetX the pointer offset from the control left
     * @return {@code true} if the value changed, {@code false} otherwise
     */
    public boolean press(float offsetX) {
        if (!this.enabled) {
            return false;
        }

        int before = this.model.getValue();
        int thumbStart = EDGE + this.model.getThumbOffset(this.getTravel());
        ScrollPart part = this.model.findPart((float) Math.floor(offsetX),
                                              EDGE, this.getDefinition().getWidth() - EDGE,
                                              thumbStart, (int) WebPanelStyleUtils.getSliderThumbSize());

        this.model.press(part);

        if (this.model.isDragging()) {
            this.dragTo(offsetX);
        }

        return this.model.getValue() != before;
    }

    /**
     * Moves the dragged thumb; nothing when no drag runs.
     *
     * @param offsetX the pointer offset from the control left
     * @return {@code true} if the value changed, {@code false} otherwise
     */
    public boolean dragTo(float offsetX) {
        int thumbSize = (int) WebPanelStyleUtils.getSliderThumbSize();
        int right = this.getDefinition().getWidth() - 1;

        return this.model.dragTo(offsetX, 0, right - thumbSize / 2, thumbSize, this.getTravel());
    }

    /**
     * Ends the current drag.
     */
    public void release() {
        this.model.release();
    }

    /**
     * Draws the control natively at a position: a plain control through its dialog's procedure painter ({@link ScrDialog#setPainter}),
     * nothing while it is not in an SC:R dialog with one; a widget subclass draws its own look.
     * <p>
     * The slider takes input and draws enabled while the control is active, then draws its track, ticks and thumb.
     *
     * @param canvas the SC:R dialog painter
     * @param left   the control's left edge in interface pixels, the shift included
     * @param top    the control's top edge in interface pixels, the vertical shift included
     * @throws IOException if an image cannot be read
     */
    @Override
    public void drawAt(@NotNull DialogPainter canvas, float left, float top) throws IOException {
        this.setInputEnabled(this.isActive());

        float thumbWidth = WebPanelStyleUtils.getSliderThumbSize();
        float half = thumbWidth / 2;
        float centerY = top + this.getDefinition().getHeight() / 2f;
        float travel = this.getDefinition().getWidth() - thumbWidth;
        int ticks = Math.min(MAXIMUM_TICKS, this.model.getMaximum() - this.model.getMinimum());
        float thumbOffset = this.model.getThumbOffset(this.getTravel());

        WebPanelStyleUtils.drawSliderTrack(canvas, left + half, centerY, travel, this.enabled);

        for (int index = 0; index <= ticks && ticks > 0; index++) {
            float offset = travel * index / ticks;

            // The thumb covers the stop it rests on.
            if (Math.abs(offset - thumbOffset) >= half) {
                WebPanelStyleUtils.drawSliderStop(canvas, left + half + offset, centerY, this.enabled);
            }
        }

        WebPanelStyleUtils.drawSliderThumb(canvas, left + thumbOffset + half, centerY, this.enabled);
    }
}
