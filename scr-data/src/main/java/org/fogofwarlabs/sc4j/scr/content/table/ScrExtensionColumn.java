package org.fogofwarlabs.sc4j.scr.content.table;

import org.fogofwarlabs.sc4j.engine.entity.column.IntColumn;
import org.fogofwarlabs.sc4j.scr.data.type.DatColumn;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the values of one DAT field in an extension column of an engine type table: element {@code e} is the value of
 * type {@code e + firstType}.
 */
final class ScrExtensionColumn extends DatColumn {
    private final @NotNull IntColumn column;
    private final int firstType;

    /**
     * Creates a {@link ScrExtensionColumn} instance.
     *
     * @param column    the extension column, indexed by the type index
     * @param firstType the type of element {@code 0}
     * @param length    the number of elements of the field
     */
    ScrExtensionColumn(@NotNull IntColumn column, int firstType, int length) {
        super(length);

        this.column    = column;
        this.firstType = firstType;
    }

    /**
     * Reads the value of an element.
     *
     * @param element the element, from {@code 0} to {@code getLength() - 1}
     * @return the value
     */
    @Override
    public int get(int element) {
        return this.column.get(element + this.firstType);
    }

    /**
     * Writes the value of an element.
     *
     * @param element the element, from {@code 0} to {@code getLength() - 1}
     * @param value   the value, already narrowed to the field's width and sign
     */
    @Override
    public void set(int element, int value) {
        this.column.set(element + this.firstType, value);
    }
}
