package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.client.dialog.Control;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogDefinition;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrControlType;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks the SC:R dialog runtime on the engine dialog: activation on release over a pressed button, cancellation after hiding it,
 * check boxes toggled by their release, the native hit tests (presses skip disabled controls, the hover does not), widgets attached
 * in place of built controls and the procedure painter of the plain controls.
 */
final class ScrDialogTest {
    private static final int BUTTON = 1;
    private static final int CHECK_BOX = 2;
    private static final int LIST = 3;
    private static final int LIST_TOP = 60;
    private static final int SHIFT = 5;
    private static final int CHECK_BOX_TOP = 40;
    private static final int BUTTON_LEFT = 10;
    private static final int BUTTON_TOP = 10;
    private static final int BUTTON_WIDTH = 40;
    private static final int BUTTON_HEIGHT = 20;
    private static final int DIALOG_SIZE = 100;
    /**
     * A dialog-relative coordinate inside the button on both axes.
     */
    private static final float INSIDE_BUTTON = 20;
    /**
     * A dialog-relative coordinate outside the button and the dialog on both axes.
     */
    private static final float OUTSIDE_BUTTON = 200;

    /**
     * Creates a dialog holding one visible push button.
     *
     * @return the dialog
     */
    private static @NotNull ScrDialog createDialog() {
        DialogControl button = ScrDialogControlUtils.createControl(ScrControlType.PUSHBUTTON,
                                                                   BUTTON,
                                                                   BUTTON_LEFT, BUTTON_TOP,
                                                                   BUTTON_WIDTH, BUTTON_HEIGHT,
                                                                   Set.of("WS_VISIBLE"),
                                                                   "",
                                                                   List.of());
        DialogControl checkBox = ScrDialogControlUtils.createControl(ScrControlType.CHECKBOX,
                                                                     CHECK_BOX,
                                                                     BUTTON_LEFT, CHECK_BOX_TOP,
                                                                     BUTTON_WIDTH, BUTTON_HEIGHT,
                                                                     Set.of("WS_VISIBLE"),
                                                                     "",
                                                                     List.of());

        DialogControl list = ScrDialogControlUtils.createControl(ScrControlType.LISTBOX,
                                                                 LIST,
                                                                 BUTTON_LEFT, LIST_TOP,
                                                                 BUTTON_WIDTH, BUTTON_HEIGHT,
                                                                 Set.of("WS_VISIBLE"),
                                                                 "",
                                                                 List.of());

        return new ScrDialog(new DialogDefinition("test", 0, 0, DIALOG_SIZE, DIALOG_SIZE, List.of(button, checkBox, list)));
    }

    @Test
    void release_should_activate_pressed_button_when_over_it() {
        ScrDialog dialog = createDialog();

        dialog.press(dialog.requireControl(BUTTON), INSIDE_BUTTON, INSIDE_BUTTON);

        Control released = dialog.release(INSIDE_BUTTON, INSIDE_BUTTON);

        Assertions.assertSame(dialog.requireControl(BUTTON), released);
        Assertions.assertNull(dialog.getFocused(), "a press does not move the focus");
    }

    @Test
    void release_should_activate_nothing_away_from_pressed_button() {
        ScrDialog dialog = createDialog();

        dialog.press(dialog.requireControl(BUTTON), INSIDE_BUTTON, INSIDE_BUTTON);

        Assertions.assertNull(dialog.release(OUTSIDE_BUTTON, OUTSIDE_BUTTON));
    }

    @Test
    void release_should_activate_nothing_when_pressed_button_was_hidden() {
        ScrDialog dialog = createDialog();

        dialog.press(dialog.requireControl(BUTTON), INSIDE_BUTTON, INSIDE_BUTTON);
        dialog.requireControl(BUTTON).setVisible(false);
        dialog.requireControl(BUTTON).setVisible(true);

        Assertions.assertNull(dialog.release(INSIDE_BUTTON, INSIDE_BUTTON));
    }

    @Test
    void release_should_toggle_a_check_box_released_over_it() {
        ScrDialog dialog = createDialog();
        ScrControl checkBox = dialog.requireControl(CHECK_BOX);

        dialog.press(checkBox, INSIDE_BUTTON, CHECK_BOX_TOP);
        dialog.release(INSIDE_BUTTON, CHECK_BOX_TOP);

        Assertions.assertTrue(checkBox.isChecked());
    }

    @Test
    void hit_tests_should_skip_disabled_controls_for_presses_only() {
        ScrDialog dialog = createDialog();
        ScrControl button = dialog.requireControl(BUTTON);

        button.setEnabled(false);

        Assertions.assertNull(dialog.findControlAt(INSIDE_BUTTON, INSIDE_BUTTON));
        Assertions.assertSame(button, dialog.findHoverAt(INSIDE_BUTTON, INSIDE_BUTTON));
    }

    @Test
    void attach_should_put_a_widget_in_place_of_the_built_control_with_its_native_state() {
        ScrDialog dialog = createDialog();
        ScrControl built = dialog.requireControl(LIST);

        built.setHighlighted(true);
        built.setShift(SHIFT);
        dialog.setHovered(built);

        ListBox list = dialog.attach(new ListBox(built.getDefinition(), "dlg"));

        Assertions.assertSame(list, dialog.requireControl(LIST));
        Assertions.assertSame(list, dialog.getHovered());
        Assertions.assertTrue(list.isHighlighted());
        Assertions.assertEquals(SHIFT, list.getShift());
        Assertions.assertSame(list, dialog.findControlAt(INSIDE_BUTTON + SHIFT, LIST_TOP));
        Assertions.assertNull(built.getDialog());
    }

    @Test
    void draw_at_should_draw_a_plain_control_through_the_procedure_painter() throws IOException {
        ScrDialog dialog = createDialog();
        ScrControl button = dialog.requireControl(BUTTON);
        List<ScrControl> drawn = new ArrayList<>();

        button.drawAt(new RecordingDialogPainter(), BUTTON_LEFT, BUTTON_TOP);
        dialog.setPainter((control, x, y) -> drawn.add(control));
        button.drawAt(new RecordingDialogPainter(), BUTTON_LEFT, BUTTON_TOP);

        Assertions.assertEquals(List.of(button), drawn);
    }
}
