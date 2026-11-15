package org.fogofwarlabs.sc4j.scr.game.unit;

/**
 * Interprets the native unit handle's index and generation fields.
 * <p>
 * The native decoder and encoder use a caller-dependent capacity and a one-based pool index. These operations do not
 * resolve a unit, validate native memory, or establish any relationship to a CHK placement serial.
 */
public final class UnitHandle {
    /**
     * The index width of the handles of a classic unit capacity.
     */
    private static final int CLASSIC_INDEX_BITS = 11;
    /**
     * The index width of the handles of an extended unit capacity.
     */
    private static final int EXTENDED_INDEX_BITS = 13;
    /**
     * The mask of the generation advance: the stored generation is a full byte (a save or an EUD write can hold any value),
     * but every successful storage initialization keeps the advanced value below {@code 32}.
     */
    private static final int GENERATION_MASK = 0x1F;
    /**
     * The mask of the generation byte that {@link #encode} stores.
     */
    private static final int BYTE_MASK = 0xFF;

    /**
     * Returns the index width using the original signed capacity comparison.
     *
     * @param capacity the runtime capacity DWORD
     * @return {@code 11} for signed capacity at most {@code 1700}, otherwise {@code 13}
     */
    public static int getIndexBitCount(int capacity) {
        return capacity > UnitPool.CLASSIC_UNIT_CAPACITY ? EXTENDED_INDEX_BITS : CLASSIC_INDEX_BITS;
    }

    /**
     * Extracts the masked one-based pool index without a range check.
     *
     * @param handle   the complete raw handle DWORD
     * @param capacity the runtime capacity DWORD
     * @return the index field, which can be zero or outside the allocated pool
     */
    public static int getPoolIndex(int handle, int capacity) {
        return handle & ((1 << getIndexBitCount(capacity)) - 1);
    }

    /**
     * Extracts the generation comparison value with the original arithmetic shift.
     *
     * @param handle   the complete raw handle DWORD
     * @param capacity the runtime capacity DWORD
     * @return the shifted signed value, without reducing it to five bits or a byte
     */
    public static int getGeneration(int handle, int capacity) {
        return handle >> getIndexBitCount(capacity);
    }

    /**
     * Encodes the index and generation byte after the original unsigned capacity check.
     * <p>
     * The caller supplies the index already derived from a native pointer. Null pointers are handled separately by the
     * original encoder. This method does not invent additional index or generation validation absent from that encoder.
     *
     * @param poolIndex  the one-based pool index DWORD
     * @param generation the generation field, of which only the low byte is read
     * @param capacity   the runtime capacity DWORD
     * @return the handle DWORD, or {@code 0} if the unsigned index exceeds capacity
     */
    public static int encode(int poolIndex, int generation, int capacity) {
        if (Integer.compareUnsigned(poolIndex, capacity) > 0) {
            return 0;
        }

        return ((generation & BYTE_MASK) << getIndexBitCount(capacity)) | poolIndex;
    }

    /**
     * Advances the generation field after successful native storage initialization.
     *
     * @param generation the previous generation byte
     * @return the incremented value reduced modulo {@code 32}
     */
    public static int nextGeneration(int generation) {
        return (generation + 1) & GENERATION_MASK;
    }


    private UnitHandle() {
    }
}
