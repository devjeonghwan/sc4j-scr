package org.fogofwarlabs.sc4j.scr.data.type;

import org.jetbrains.annotations.NotNull;

/**
 * Holds the values of one DAT field in an array: the store of the parsed DAT files and of their copies.
 */
public final class ArrayDatColumn extends DatColumn {
    private final int @NotNull [] values;

    /**
     * Creates an {@link ArrayDatColumn} instance over an array, which it uses and does not copy.
     *
     * @param values the values in element order
     */
    public ArrayDatColumn(int @NotNull [] values) {
        super(values.length);

        this.values = values;
    }

    /**
     * Reads the value of an element.
     *
     * @param element the element, from {@code 0} to {@code getLength() - 1}
     * @return the value
     */
    @Override
    public int get(int element) {
        return this.values[element];
    }

    /**
     * Writes the value of an element.
     *
     * @param element the element, from {@code 0} to {@code getLength() - 1}
     * @param value   the value, already narrowed to the field's width and sign
     */
    @Override
    public void set(int element, int value) {
        this.values[element] = value;
    }
}
