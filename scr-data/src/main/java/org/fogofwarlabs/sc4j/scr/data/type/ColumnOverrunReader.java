package org.fogofwarlabs.sc4j.scr.data.type;

import org.jetbrains.annotations.NotNull;

/**
 * Reads a DAT column element at an index outside the column from the DAT memory around the column.
 * <p>
 * A typed table has no bound check on an id: each accessor reads an index inside its column from the column and any other index through this
 * reader. A loader supplies the reader from the memory layout of the DAT file.
 *
 * @param <C> the column type of the typed table
 */
@FunctionalInterface
public interface ColumnOverrunReader<C> {
    /**
     * Reads a column element at an index outside the column.
     *
     * @param column the column
     * @param index  the element index outside the column
     * @return the element, with the width and sign of the column
     */
    int read(@NotNull C column, int index);
}
