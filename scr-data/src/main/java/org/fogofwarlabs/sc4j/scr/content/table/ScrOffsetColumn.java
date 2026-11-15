package org.fogofwarlabs.sc4j.scr.content.table;

import org.fogofwarlabs.sc4j.scr.data.type.DatColumn;
import org.jetbrains.annotations.NotNull;

/**
 * Holds a range of another column: element {@code e} is element {@code e + offset} of the column. The added unit types of a game read their
 * {@code units.dat} values through it, the elements of the content after the native entries.
 */
final class ScrOffsetColumn extends DatColumn {
    private final @NotNull DatColumn column;
    private final int offset;

    /**
     * Creates a {@link ScrOffsetColumn} instance.
     *
     * @param column the column that holds the values, readable at {@code offset} to {@code offset + length - 1}
     * @param offset the element of the column read as element {@code 0}
     * @param length the number of elements of the range
     */
    ScrOffsetColumn(@NotNull DatColumn column, int offset, int length) {
        super(length);

        this.column = column;
        this.offset = offset;
    }

    /**
     * Reads the value of an element.
     *
     * @param element the element, from {@code 0} to {@code getLength() - 1}
     * @return the value
     */
    @Override
    public int get(int element) {
        return this.column.get(element + this.offset);
    }

    /**
     * Writes the value of an element.
     *
     * @param element the element, from {@code 0} to {@code getLength() - 1}
     * @param value   the value, already narrowed to the field's width and sign
     */
    @Override
    public void set(int element, int value) {
        this.column.set(element + this.offset, value);
    }
}
