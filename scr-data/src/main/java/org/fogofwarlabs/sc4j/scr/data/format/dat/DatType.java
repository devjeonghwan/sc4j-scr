package org.fogofwarlabs.sc4j.scr.data.format.dat;

/**
 * Represents the field type of a DAT file column.
 */
public enum DatType {
    /**
     * An unsigned 8-bit field.
     */
    U8(Byte.BYTES, false),
    /**
     * A signed 8-bit field.
     */
    S8(Byte.BYTES, true),
    /**
     * An unsigned 16-bit field.
     */
    U16(Short.BYTES, false),
    /**
     * A signed 16-bit field.
     */
    S16(Short.BYTES, true),
    /**
     * An unsigned 32-bit field.
     */
    U32(Integer.BYTES, false);

    private final int size;
    private final boolean signed;

    /**
     * Creates a {@link DatType} instance.
     *
     * @param size   the size of a single element in bytes
     * @param signed whether the element is a signed value
     */
    DatType(int size, boolean signed) {
        this.size   = size;
        this.signed = signed;
    }

    /**
     * @return the size of a single element in bytes
     */
    public int getSize() {
        return this.size;
    }

    /**
     * @return {@code true} if the element is a signed value, {@code false} otherwise
     */
    public boolean isSigned() {
        return this.signed;
    }
}
