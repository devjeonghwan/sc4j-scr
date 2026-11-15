package org.fogofwarlabs.sc4j.scr.data.type;

import org.jetbrains.annotations.NotNull;

/**
 * Holds a requirement index column of a DAT table: for each entry, the position of its requirement script, an unsigned
 * WORD that the requirement tests build at run time ({@code 0xFFFF} until built).
 * <p>
 * The column shares the store of its table ({@link DatColumn}), so a write is visible through the table's getter and the game's EUD memory.
 */
public final class RequirementIndexColumn {
    private static final int WORD_MASK = 0xFFFF;

    private final @NotNull String name;

    private final @NotNull DatColumn values;

    /**
     * Creates a {@link RequirementIndexColumn} instance.
     *
     * @param name   the column name shown in diagnostics
     * @param values the column, shared and not copied
     */
    public RequirementIndexColumn(@NotNull String name, @NotNull DatColumn values) {
        this.name = name;

        this.values = values;
    }

    /**
     * @return the column name shown in diagnostics
     */
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * @return the number of entries
     */
    public int getCount() {
        return this.values.getLength();
    }

    /**
     * @param entry the entry
     * @return the requirement index of the entry
     */
    public int get(int entry) {
        return this.values.get(entry);
    }

    /**
     * Sets the requirement index of an entry, truncated to an unsigned WORD.
     *
     * @param entry the entry
     * @param value the requirement index
     */
    public void set(int entry, int value) {
        this.values.set(entry, value & WORD_MASK);
    }
}
