package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.client.dialog.Control;
import org.fogofwarlabs.sc4j.engine.client.dialog.DialogLayer;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes one open SC:R dialog (a glue screen or popup, a console dialog) to the engine dialog stack by its control definitions:
 * the pointer state lives in the {@link ScrControl}s of its {@link ScrDialog} runtime (an engine
 * {@link org.fogofwarlabs.sc4j.engine.client.dialog.Dialog}), while the screen places the layer.
 */
public interface ScrDialogLayer extends DialogLayer<DialogControl> {
    private static @Nullable DialogControl getDefinition(@Nullable Control control) {
        return control == null ? null : control.getDefinition();
    }


    /**
     * @return the dialog's runtime state
     */
    @NotNull ScrDialog getDialog();

    /**
     * @return {@code true} if a control of the dialog holds a press, {@code false} otherwise
     */
    @Override
    default boolean hasPressedControl() {
        return this.getDialog().hasPressedControl();
    }


    /**
     * Finds the control a press at a point goes to.
     *
     * @param x the x relative to the dialog
     * @param y the y relative to the dialog
     * @return the enabled control that takes presses there, or {@code null}
     */
    @Override
    default @Nullable DialogControl findControlAt(float x, float y) {
        return getDefinition(this.getDialog().findControlAt(x, y));
    }

    /**
     * Finds the control the pointer hovers at a point.
     *
     * @param x the x relative to the dialog
     * @param y the y relative to the dialog
     * @return the visible control that takes the hover there, or {@code null}
     */
    @Override
    default @Nullable DialogControl findHoverAt(float x, float y) {
        return getDefinition(this.getDialog().findHoverAt(x, y));
    }

    /**
     * Marks a control pressed, or no control.
     *
     * @param control the control the press went to, or {@code null} for the dialog's empty area
     * @param x       the x of the press relative to the dialog
     * @param y       the y of the press relative to the dialog
     */
    @Override
    default void press(@Nullable DialogControl control, float x, float y) {
        ScrDialog dialog = this.getDialog();

        dialog.press(control == null ? null : dialog.requireControl(control), x, y);
    }

    /**
     * Releases the held press.
     *
     * @param x the x relative to the dialog
     * @param y the y relative to the dialog
     * @return the control the release activates (the pressed one, when the pointer is still on it), or {@code null}
     */
    @Override
    default @Nullable DialogControl release(float x, float y) {
        return getDefinition(this.getDialog().release(x, y));
    }

    /**
     * Sets the hovered control.
     *
     * @param control the hovered control, or {@code null} for none
     */
    @Override
    default void setHovered(@Nullable DialogControl control) {
        ScrDialog dialog = this.getDialog();

        dialog.setHovered(control == null ? null : dialog.requireControl(control));
    }
}
