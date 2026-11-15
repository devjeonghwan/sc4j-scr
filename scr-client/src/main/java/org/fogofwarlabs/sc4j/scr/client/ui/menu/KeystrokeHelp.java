package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the localized keystroke-help rows in table order.
 * The first and last rows use dedicated keys; intermediate rows append their row numbers.
 * Missing keys and placeholder entries become a single-space row.
 */
public final class KeystrokeHelp {
    /**
     * The localized text table the page loads.
     */
    public static final @NotNull String TABLE = "help_txt.json";
    /**
     * The list's control id.
     */
    public static final int LIST = 1;

    /**
     * The last row ({@code 0x33}).
     */
    private static final int LAST_ROW = 0x33;
    private static final @NotNull String FIRST_KEY = "STR_HELP_1";
    private static final @NotNull String ROW_KEY_PREFIX = "STR_HELP_1-";
    private static final @NotNull String LAST_KEY = "STR_HELP_LAST";
    /**
     * The string of a row without text.
     */
    private static final @NotNull String PLACEHOLDER = "<<PLACEHOLDER>>";
    /**
     * The text of a row without text.
     */
    private static final @NotNull String EMPTY_ROW = " ";

    private final @NotNull GluText table;

    /**
     * Creates a {@link KeystrokeHelp} instance.
     *
     * @param table the loaded {@code help_txt.json} of the interface locale
     */
    public KeystrokeHelp(@NotNull GluText table) {
        this.table = table;
    }

    /**
     * @return the list rows in order
     */
    public @NotNull List<String> getRows() {
        List<String> rows = new ArrayList<>();

        for (int row = 0; row <= LAST_ROW; row++) {
            String key = row == 0 ? FIRST_KEY : row == LAST_ROW ? LAST_KEY : ROW_KEY_PREFIX + row;
            String text = this.table.contains(key) ? this.table.get(key) : PLACEHOLDER;

            rows.add(text.equals(PLACEHOLDER) ? EMPTY_ROW : text);
        }

        return rows;
    }
}
