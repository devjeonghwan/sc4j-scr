package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrControlType;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Draws check-box and radio-button frames from the enabled, checked and pressed states.
 * Labels follow the box and use the hovered or disabled text color.
 * Focus outlines and the box-on-the-right variant are not drawn.
 */
public final class CheckBoxUtils {
    private static final int PRESSED_FRAME_OFFSET = 2;
    /**
     * The frame distance from an unchecked box or radio button to its checked frame.
     */
    private static final int CHECKED_FRAME_OFFSET = 2;

    private static final int CHECK_BASE = 11;
    private static final int CHECK_SIZE_FRAME = 15;
    private static final int CHECK_DISABLED_CHECKED = 139;
    private static final int RADIO_DISABLED = 6;
    private static final int RADIO_BASE = 7;
    private static final int RADIO_SIZE_FRAME = 10;
    private static final int TEXT_GAP = 4;

    /**
     * Returns the box size of a check box or radio button.
     *
     * @param canvas    the painter
     * @param sheetPath the dialog sheet
     * @param radio     {@code true} for a radio button, {@code false} for a check box
     * @return the larger frame dimension in interface pixels
     * @throws IOException if the sheet cannot be read
     */
    public static float getBoxSize(@NotNull DialogPainter canvas, @NotNull String sheetPath, boolean radio) throws IOException {
        DialogPainter.Frame frame = canvas.getSheetFrame(sheetPath, radio ? RADIO_SIZE_FRAME : CHECK_SIZE_FRAME);

        return Math.max(frame.getWidth(), frame.getHeight());
    }

    /**
     * Draws a check box or radio button.
     *
     * @param canvas    the painter
     * @param sheetPath the dialog sheet, for example {@code glue/palnl/dlg.dds.grp}
     * @param control   the control ({@link ScrControlType#CHECKBOX} or {@link ScrControlType#RADIOBUTTON})
     * @param left      the control left in interface pixels
     * @param top       the control top in interface pixels
     * @param text      the drawn string
     * @param checked   whether the control is checked
     * @param enabled   whether the control takes input
     * @param pressed   whether the button is held down over the control
     * @param hovered   whether the pointer is over the control
     * @throws IOException if the sheet cannot be read
     */
    public static void draw(@NotNull DialogPainter canvas,
                            @NotNull String sheetPath,
                            @NotNull DialogControl control,
                            float left, float top,
                            @NotNull String text,
                            boolean checked,
                            boolean enabled,
                            boolean pressed,
                            boolean hovered) throws IOException {
        boolean radio = ScrDialogControlUtils.getType(control) == ScrControlType.RADIOBUTTON;
        float size = getBoxSize(canvas, sheetPath, radio);
        // The check-box initialization grows a lower control to the box size (the tips page's 12-pixel box), so its text has room.
        float height = radio ? control.getHeight() : Math.max(control.getHeight(), size);
        int boxY = (int) (height - size) / 2;
        int frame;

        if (radio) {
            frame = !enabled ? RADIO_DISABLED : CHECKED_FRAME_OFFSET * (checked ? 1 : 0) + (pressed ? RADIO_BASE + 1 : RADIO_BASE);
        } else {
            frame = !enabled
                    ? checked ? CHECK_DISABLED_CHECKED : CHECK_BASE
                    : CHECK_BASE + CHECKED_FRAME_OFFSET * (checked ? 1 : 0) + (pressed ? PRESSED_FRAME_OFFSET : 1);
        }

        canvas.drawFrame(canvas.getSheetFrame(sheetPath, frame), left, top + boxY);

        if (text.isEmpty()) {
            return;
        }

        int code = !enabled ? DialogPainter.CODE_DISABLED
                            : hovered ? DialogPainter.CODE_HIGHLIGHT : radio ? DialogPainter.CODE_BUTTON : DialogPainter.CODE_TEXT;
        float textLeft = left + size + TEXT_GAP;

        canvas.drawCodeText(canvas.getControlFontSlot(control),
                            text,
                            textLeft, top,
                            control.getWidth() - size - TEXT_GAP, height,
                            ControlPainterUtils.ROW_TEXT_FORMAT,
                            code);
    }


    private CheckBoxUtils() {
    }
}
