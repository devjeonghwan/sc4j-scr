package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrControlType;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Checks combo box presentation: selection by value, the grayed placeholder, the item colors and the list opening upward near
 * the screen bottom.
 */
final class ComboBoxTest {
    private static final int COMBO_BOX_IDENTIFIER = 1;
    private static final int COMBO_BOX_WIDTH = 80;
    private static final int COMBO_BOX_HEIGHT = 14;
    private static final int ZERG_VALUE = 0;
    private static final int TERRAN_VALUE = 1;
    private static final int RED_VALUE = 2;
    private static final int RED_COLOR = 0xF40404FF;
    private static final int UNKNOWN_VALUE = 7;
    /**
     * The pointer's offset from the control left inside the list.
     */
    private static final float POINTER_X = 10;
    /**
     * Ample room in interface pixels on one side of the control.
     */
    private static final float WIDE_ROOM = 400;
    /**
     * Room in interface pixels too small for the three-row list.
     */
    private static final float NARROW_ROOM = 60;
    /**
     * Room in interface pixels smaller than {@link #NARROW_ROOM}.
     */
    private static final float SMALL_ROOM = 10;
    /**
     * The list rows above the control top when it opens upward.
     */
    private static final int UPWARD_ROWS = 3;
    /**
     * The color code of disabled text.
     */
    private static final int DISABLED_CODE = 5;
    private static final int NO_ITEM = -1;

    /**
     * Creates the three-item combo box used by the presentation fixtures.
     *
     * @return the populated combo box
     */
    private static @NotNull ComboBox createComboBox() {
        DialogControl control = ScrDialogControlUtils.createControl(ScrControlType.COMBOBOX,
                                                                    COMBO_BOX_IDENTIFIER,
                                                                    0, 0,
                                                                    COMBO_BOX_WIDTH, COMBO_BOX_HEIGHT,
                                                                    Set.of("WS_VISIBLE"),
                                                                    "",
                                                                    List.of());
        ComboBox comboBox = new ComboBox(control, "sheet");

        comboBox.addItem("Zerg", ZERG_VALUE);
        comboBox.addItem("Terran", TERRAN_VALUE);
        comboBox.addItem("Red", RED_VALUE, RED_COLOR);

        return comboBox;
    }

    @Test
    void combo_box_selects_by_value_and_grays_placeholder_without_selection() throws IOException {
        ComboBox comboBox = createComboBox();

        comboBox.setPlaceholder("Select Race");

        Assertions.assertTrue(comboBox.selectValue(TERRAN_VALUE));
        Assertions.assertEquals(1, comboBox.getSelection());
        Assertions.assertFalse(comboBox.selectValue(UNKNOWN_VALUE), "an unknown value clears the selection");

        RecordingDialogPainter painter = new RecordingDialogPainter();

        comboBox.draw(painter, 0, 0, false);

        Assertions.assertEquals(List.of("Select Race"), painter.getTexts());
        Assertions.assertEquals(List.of(DISABLED_CODE), painter.getCodes(), "the placeholder is greyed while the combo box is enabled");
    }

    @Test
    void draw_should_use_item_color_unless_disabled() throws IOException {
        ComboBox comboBox = createComboBox();

        comboBox.selectValue(RED_VALUE);

        RecordingDialogPainter painter = new RecordingDialogPainter();

        comboBox.draw(painter, 0, 0, false);

        Assertions.assertEquals(List.of(RED_COLOR), painter.getColors());
        Assertions.assertEquals(List.of("Red"), painter.getTexts());

        comboBox.setInputEnabled(false);

        RecordingDialogPainter disabled = new RecordingDialogPainter();

        comboBox.draw(disabled, 0, 0, false);

        Assertions.assertEquals(List.of(), disabled.getColors());
        Assertions.assertEquals(List.of(DISABLED_CODE), disabled.getCodes());
    }

    @Test
    void set_open_should_open_upward_only_without_room_below_and_more_room_above() throws IOException {
        RecordingDialogPainter painter = new RecordingDialogPainter();
        ComboBox comboBox = createComboBox();

        comboBox.setOpen(painter, true, WIDE_ROOM, NARROW_ROOM);

        Assertions.assertTrue(comboBox.isOpen());
        // Upward: the first row starts three rows above the control top.
        Assertions.assertEquals(0, comboBox.findItem(painter, POINTER_X, -UPWARD_ROWS * RecordingDialogPainter.BAR_HEIGHT + 1));
        Assertions.assertEquals(NO_ITEM, comboBox.findItem(painter, POINTER_X, RecordingDialogPainter.BAR_HEIGHT + 1));

        comboBox.setOpen(painter, true, SMALL_ROOM, WIDE_ROOM);

        Assertions.assertEquals(0,
                                comboBox.findItem(painter, POINTER_X, RecordingDialogPainter.BAR_HEIGHT + 1),
                                "with room below the list opens downward");
    }
}
