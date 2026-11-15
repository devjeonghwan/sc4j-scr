package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.table.ScrDatKind;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds one value per field of a DAT table for an entry a mod adds after the native entries ({@link ScrAddedImage}, {@link ScrAddedSprite},
 * {@link ScrAddedFlingy}): a copy of a native base entry of the installed table, which the mod then changes. A field without an element for the
 * base entry (a field whose first element belongs to a later entry) is {@code 0}.
 */
final class ScrAddedDatValues {
    private final @NotNull ScrDatKind kind;
    private final int @NotNull [] values;

    /**
     * Creates a {@link ScrAddedDatValues} instance with the values of a native base entry.
     *
     * @param kind    the DAT table kind
     * @param table   the installed DAT table the values are read from
     * @param baseId  the native base entry
     * @param entries the number of native entries of the table
     * @throws IllegalArgumentException if the base entry is outside the native table
     */
    ScrAddedDatValues(@NotNull ScrDatKind kind, @NotNull DatTable table, int baseId, int entries) {
        if (baseId < 0 || baseId >= entries) {
            throw Exceptions.newIllegalArgumentException("The base entry {0} is outside the native {1} table.", baseId, kind);
        }

        List<String> fields = kind.getFields();

        this.kind   = kind;
        this.values = new int[fields.size()];

        for (int index = 0; index < this.values.length; index++) {
            String field = fields.get(index);
            int element = baseId - kind.getFirstType(field);

            this.values[index] = element >= 0 && element < table.getCount(field) ? table.get(field, element) : 0;
        }
    }

    private ScrAddedDatValues(@NotNull ScrAddedDatValues source) {
        this.kind   = source.kind;
        this.values = source.values.clone();
    }

    /**
     * Returns the value of a field.
     *
     * @param field the field name of the table
     * @return the value
     * @throws IllegalArgumentException if the field does not exist
     */
    int get(@NotNull String field) {
        return this.values[this.getFieldIndex(field)];
    }

    /**
     * Sets the value of a field, kept as given.
     *
     * @param field the field name of the table
     * @param value the value
     * @throws IllegalArgumentException if the field does not exist
     */
    void set(@NotNull String field, int value) {
        this.values[this.getFieldIndex(field)] = value;
    }

    /**
     * @return a copy of the values
     */
    @NotNull ScrAddedDatValues copy() {
        return new ScrAddedDatValues(this);
    }

    private int getFieldIndex(@NotNull String field) {
        int index = this.kind.getFields().indexOf(field);

        if (index < 0) {
            throw Exceptions.newIllegalArgumentException("The {0} field does not exist: {1}", this.kind, field);
        }

        return index;
    }
}
