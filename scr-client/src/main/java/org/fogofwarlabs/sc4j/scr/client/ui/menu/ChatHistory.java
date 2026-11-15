package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ListBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Keeps formatted message records and appends wrapped rows while the history list is open.
 * Rows retain the message color and continuation color codes. A full list drops its first row.
 * Appending selects the new last row when the list was already at its end.
 */
public final class ChatHistory {
    /**
     * The most rows a list holds: the list keeps its row count in a byte.
     */
    public static final int MAXIMUM_ROWS = 0xFF;
    /**
     * The highest inline color code a continuation row carries over (codes {@code 1..0x1f}).
     */
    private static final int LAST_COLOR_CODE = 0x1f;

    /**
     * One kept message record: its formatted text, its color code and its starting color.
     */
    private static final class Entry {
        private final @NotNull String text;
        private final int code;
        private final int color;

        private Entry(@NotNull String text, int code, int color) {
            this.text  = text;
            this.code  = code;
            this.color = color;
        }
    }

    /**
     * Wraps text before a character exceeds the row width. Continuation rows retain the preceding row's last color code.
     *
     * @param text    the text
     * @param measure measures a text in the list's font
     * @param width   the row width; {@code 0} or less keeps one row
     * @return the rows
     */
    public static @NotNull List<String> wrap(@NotNull String text, @NotNull ToDoubleFunction<String> measure, float width) {
        List<String> rows = new ArrayList<>();

        if (width <= 0 || measure.applyAsDouble(text) <= width) {
            rows.add(text);

            return rows;
        }

        StringBuilder row = new StringBuilder();
        char carried = 0;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            row.append(character);

            if (row.length() > 1 && measure.applyAsDouble(row.toString()) > width) {
                row.setLength(row.length() - 1);

                String finished = row.toString();

                rows.add(finished);

                char lastCode = carried;

                for (int colorIndex = 0; colorIndex < finished.length(); colorIndex++) {
                    char colorCharacter = finished.charAt(colorIndex);

                    if (colorCharacter >= 1 && colorCharacter <= LAST_COLOR_CODE) {
                        lastCode = colorCharacter;
                    }
                }

                carried = lastCode;

                row.setLength(0);

                if (carried != 0) {
                    row.append(carried);
                }

                row.append(character);
            }
        }

        if (!row.isEmpty()) {
            rows.add(row.toString());
        }

        return rows;
    }

    private final @NotNull List<Entry> entries;

    private @Nullable ListBox list;
    private @Nullable ToDoubleFunction<String> measure;
    private float width;
    private int rows;

    /**
     * Creates a {@link ChatHistory} instance without message records for a new game.
     */
    public ChatHistory() {
        this.entries = new ArrayList<>();

        this.list    = null;
        this.measure = null;
        this.width   = 0;
        this.rows    = 0;
    }

    /**
     * @return the number of kept message records
     */
    public int getEntryCount() {
        return this.entries.size();
    }

    /**
     * @return the open dialog's list, or {@code null} while the dialog is closed
     */
    public @Nullable ListBox getList() {
        return this.list;
    }


    /**
     * Keeps a message record without a starting color.
     *
     * @param text the formatted line
     * @param code the line's color code
     */
    public void add(@NotNull String text, int code) {
        this.add(text, code, ListBox.NO_COLOR);
    }

    /**
     * Keeps a message record and, while the dialog is open, appends its rows to the list.
     *
     * @param text  the formatted line
     * @param code  the line's color code
     * @param color the packed {@code 0xRRGGBBAA} starting color of the line (the sender's player color), or {@link ListBox#NO_COLOR}
     */
    public void add(@NotNull String text, int code, int color) {
        Entry entry = new Entry(text, code, color);

        this.entries.add(entry);

        if (this.list != null && this.measure != null) {
            this.append(this.list, this.measure, this.width, entry);
        }
    }

    /**
     * Opens the dialog's list: the rows of every kept record are added, the last row in view.
     *
     * @param list    the dialog's list control (control {@code 1})
     * @param measure measures a text in the list's font, in interface pixels
     * @param width   the wrap width of a row in interface pixels
     * @param rows    the rows the list shows
     */
    public void open(@NotNull ListBox list, @NotNull ToDoubleFunction<String> measure, float width, int rows) {
        this.list    = list;
        this.measure = measure;
        this.width   = width;
        this.rows    = rows;

        list.setItems(List.of());

        for (Entry entry : this.entries) {
            this.append(list, measure, width, entry);
        }
    }

    private void append(@NotNull ListBox list, @NotNull ToDoubleFunction<String> measure, float width, @NotNull Entry entry) {
        for (String row : wrap(entry.text, measure, width)) {
            if (list.getCount() >= MAXIMUM_ROWS) {
                list.removeItem(0);
            }

            // Select and keep the new row in view when the list was already at its end.
            boolean atEnd = list.getTopRow() + this.rows >= list.getCount();

            list.addItem(row, entry.code, entry.color);

            if (atEnd) {
                list.select(list.getCount() - 1, this.rows);
            }
        }
    }


    /**
     * Detaches the open list and its measurement state while retaining message records.
     */
    public void close() {
        this.list    = null;
        this.measure = null;
        this.width   = 0;
        this.rows    = 0;
    }
}
