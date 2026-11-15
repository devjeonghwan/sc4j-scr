package org.fogofwarlabs.sc4j.scr.data.type;

/**
 * Holds the values of one DAT field, indexed by the element: the store a DAT table and the typed tables read and write.
 * <p>
 * The installed tables keep their values in arrays ({@link ArrayDatColumn}); a game's tables keep them in the game's
 * engine content. A column stores what it is given: the DAT table narrows a value to the field's width and sign before
 * it writes. {@link #get} and {@link #set} take an element from {@code 0} to {@code getLength() - 1}; the callers check
 * the bound, and an element outside it has no defined result.
 */
public abstract class DatColumn {
    private final int length;

    /**
     * Creates a {@link DatColumn} instance.
     *
     * @param length the number of elements of the field
     */
    protected DatColumn(int length) {
        this.length = length;
    }

    /**
     * @return the number of elements of the field
     */
    public final int getLength() {
        return this.length;
    }


    /**
     * Reads the value of an element.
     *
     * @param element the element, from {@code 0} to {@code getLength() - 1}
     * @return the value
     */
    public abstract int get(int element);

    /**
     * Writes the value of an element.
     *
     * @param element the element, from {@code 0} to {@code getLength() - 1}
     * @param value   the value, already narrowed to the field's width and sign
     */
    public abstract void set(int element, int value);
}
