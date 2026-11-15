package org.fogofwarlabs.sc4j.scr.client.glue.popup;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueCanvas;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueContext;
import org.fogofwarlabs.sc4j.scr.client.glue.GluePopup;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.DialogPainter;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceFont;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceFonts;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Shows a fatal game notice with centered message, detail and OK text. Closing the box only finishes it; the game that
 * opened it decides what follows (an EUD failure or a stopped network game ends the session).
 */
public final class GlueFatalPopup extends GluePopup {
    /**
     * The dialog file of the box, {@code rez\dlgFatal.ui.json}.
     */
    public static final @NotNull String DIALOG = "dlgFatal";
    /**
     * The panel image under the dialog, {@code \pOPopup.pcx} of the palette folder.
     */
    public static final @NotNull String PANEL = "pOPopup.pcx";

    private static final @NotNull String FOLDER_SEPARATOR = "/";
    private static final @NotNull String EMPTY_DETAIL = "";
    /**
     * The OK button ({@code DEFPUSHBUTTON}, {@code CTRL_ENTER}).
     */
    private static final int OK = -2;
    /**
     * The message, big text ({@code RC_BIGTEXT}).
     */
    private static final int MESSAGE = -10;
    /**
     * The second string, normal text.
     */
    private static final int DETAIL = -20;
    /**
     * Centers control text in both directions.
     */
    private static final int TEXT_FORMAT = GlueCanvas.FORMAT_CENTER | GlueCanvas.FORMAT_MIDDLE;

    private final @NotNull String message;
    private final @NotNull String detail;
    private final @NotNull InterfaceFonts fonts;

    /**
     * Creates a {@link GlueFatalPopup} instance.
     *
     * @param paletteFolder the palette folder of the panel image, for example {@code glue/palnl}
     * @param message       the message
     * @param detail        the second string, or {@code null} for none
     * @param fonts         the game's font set, which the box draws its text in (it is a game dialog)
     */
    public GlueFatalPopup(@NotNull String paletteFolder, @NotNull String message, @Nullable String detail, @NotNull InterfaceFonts fonts) {
        super(DIALOG, paletteFolder, paletteFolder + FOLDER_SEPARATOR + PANEL);

        this.message = message;
        this.detail  = detail == null ? EMPTY_DETAIL : detail;
        this.fonts   = fonts;
    }

    /**
     * @return the dialog's left edge in interface pixels: the dialog rectangle's left edge plus the centered interface anchor
     */
    @Override
    protected float getDialogLeft() {
        return GlueFramedPopup.getAnchoredDialogLeft(this.getContext().getCanvas().getInterfaceWidth(), this.getDialog().getX());
    }

    /**
     * @return the dialog's top edge in interface pixels: the dialog rectangle's top edge ({@code rez\dlgFatal.ui.json}: {@code 45})
     */
    @Override
    protected float getDialogTop() {
        return this.getDialog().getY();
    }


    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     * <p>
     * The popup fills the message and the detail.
     *
     * @param glueContext the shared services
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) {
        this.bindControl(MESSAGE).setReplacedText(this.message);
        this.bindControl(DETAIL).setReplacedText(this.detail);
    }

    /**
     * Draws one control.
     * <p>
     * The message, the detail and the OK button are drawn as centered text in the game font set; other controls use the glue painter.
     *
     * @param canvas  the glue canvas
     * @param control the control
     * @param left    its left edge in interface pixels
     * @param top     its top edge in interface pixels
     * @throws IOException           if an image cannot be read
     * @throws IllegalStateException if the screen is not open
     */
    @Override
    protected void drawControl(@NotNull GlueCanvas canvas, @NotNull DialogControl control, float left, float top) throws IOException {
        int identifier = control.getId();

        if (identifier != OK && identifier != MESSAGE && identifier != DETAIL) {
            super.drawControl(canvas, control, left, top);

            return;
        }

        String text = this.getDrawnString(control);

        if (text.isEmpty()) {
            return;
        }

        // The text procedure keeps the control's color: the button's (3, hovered 4) and the texts' (2, highlighted 6), in
        // the game's font set and color table (tfontgam), as the box is a game dialog.
        int code = !this.requireDialogState().requireControl(identifier).isEnabled()
                   ? DialogPainter.CODE_DISABLED
                   : identifier == OK
                     ? identifier == this.requireDialogState().getHoveredId() ? DialogPainter.CODE_HIGHLIGHT : DialogPainter.CODE_BUTTON
                     : this.requireDialogState().requireControl(identifier).isHighlighted() ? DialogPainter.CODE_WHITE : DialogPainter.CODE_TEXT;
        InterfaceFont font = this.fonts.getFont(canvas.getControlFontSlot(control));

        canvas.drawText(font,
                        text,
                        left, top,
                        control.getWidth(), control.getHeight(),
                        TEXT_FORMAT,
                        InterfaceFont.getCodeColor(code),
                        InterfaceText.GAME_CODE_COLORS);
    }

    /**
     * Called when a control is activated by a click, its hotkey, Esc or Enter.
     *
     * @param control the control id
     */
    @Override
    protected void onActivate(int control) {
        if (control == OK) {
            this.finish();
        }
    }
}
