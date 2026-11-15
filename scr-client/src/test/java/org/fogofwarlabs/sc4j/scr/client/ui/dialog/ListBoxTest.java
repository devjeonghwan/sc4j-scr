package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.client.dialog.DialogKey;
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
 * Checks that list navigation pages by the rows the painter shows and the initial color of each row; the navigation rules are the
 * engine list model's.
 */
final class ListBoxTest {
    private static final int LIST_IDENTIFIER = 1;
    private static final int LIST_WIDTH = 200;
    /**
     * Three rows of {@code 16} pixels.
     */
    private static final int LIST_HEIGHT = 50;
    private static final int VISIBLE_ROWS = 3;
    private static final int ITEMS = 10;
    private static final int RED = 0xF40404FF;

    /**
     * Creates an empty list of three visible rows.
     *
     * @return the list
     */
    private static @NotNull ListBox createEmptyList() {
        DialogControl control = ScrDialogControlUtils.createControl(ScrControlType.LISTBOX,
                                                                    LIST_IDENTIFIER,
                                                                    0, 0,
                                                                    LIST_WIDTH, LIST_HEIGHT,
                                                                    Set.of("WS_VISIBLE"),
                                                                    "",
                                                                    List.of());

        return new ListBox(control, "sheet");
    }

    /**
     * Creates a list with the numbered rows used by the navigation fixtures.
     *
     * @return the populated list
     */
    private static @NotNull ListBox createList() {
        ListBox list = createEmptyList();

        for (int index = 0; index < ITEMS; index++) {
            list.addItem("row " + index, DialogPainter.CODE_BUTTON);
        }

        return list;
    }

    @Test
    void press_key_should_page_by_the_rows_the_painter_shows() {
        RecordingDialogPainter painter = new RecordingDialogPainter();
        ListBox list = createList();

        Assertions.assertEquals(VISIBLE_ROWS, list.getVisibleRows(painter));

        list.pressKey(painter, DialogKey.HOME);
        list.pressKey(painter, DialogKey.PAGE_DOWN);
        list.pressKey(painter, DialogKey.PAGE_DOWN);

        Assertions.assertEquals(4, list.getSelection(), "two pages of the three rows less one");
        Assertions.assertEquals(2, list.getTopRow(), "the selection stays in view");

        list.scrollByWheel(painter, false);

        Assertions.assertEquals(2, list.getSelection(), "without a scroll bar the wheel pages the selection");
    }

    @Test
    void draw_should_use_item_color_unless_row_is_selected() throws IOException {
        RecordingDialogPainter painter = new RecordingDialogPainter();
        ListBox list = createEmptyList();

        list.addItem("plain", DialogPainter.CODE_BUTTON);
        list.addItem("red", DialogPainter.CODE_BUTTON, RED);
        list.drawAt(painter, 0, 0);

        Assertions.assertEquals(List.of("plain"), painter.getCodeTexts());
        Assertions.assertEquals(List.of(RED), painter.getColors());

        list.select(1, VISIBLE_ROWS);
        painter.getCodeTexts().clear();
        painter.getColors().clear();
        list.drawAt(painter, 0, 0);

        Assertions.assertEquals(List.of("plain", "red"), painter.getCodeTexts(), "the selected row is drawn in the highlight code");
    }
}
