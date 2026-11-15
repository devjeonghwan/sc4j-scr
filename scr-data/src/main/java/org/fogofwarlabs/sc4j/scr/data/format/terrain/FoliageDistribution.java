package org.fogofwarlabs.sc4j.scr.data.format.terrain;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A tileset's foliage distribution ({@code tileset/<name>.fol}): for every megatile, the population probability and the
 * bit set of definitions that may grow on it.
 * <p>
 * The file is a header of three DWORDs (record count, definition count, selection bytes per record) followed by the
 * records: a float probability and the selection bytes, definition {@code d} in bit {@code 0x80 >>> (d & 7)} of byte
 * {@code d / 8}.
 */
public final class FoliageDistribution {
    private static final int HEADER_SIZE = 12;
    private static final int RECORD_COUNT_OFFSET = 0;
    private static final int DEFINITION_COUNT_OFFSET = 4;
    private static final int SELECTION_SIZE_OFFSET = 8;
    private static final int MAXIMUM_DEFINITIONS = 0xFFFF;
    private static final int PROBABILITY_SIZE = Float.BYTES;
    /**
     * The bit of definition {@code 0} in its selection byte; the next definitions take the lower bits.
     */
    private static final int FIRST_SELECTION_BIT = 0x80;
    private static final int SELECTION_BIT_MASK = Byte.SIZE - 1;

    /**
     * Creates a {@link FoliageDistribution} instance for the given number of definitions.
     *
     * @param data            the file contents
     * @param definitionCount the number of loaded foliage definitions
     * @return the created {@link FoliageDistribution} instance
     * @throws IllegalArgumentException if the header is truncated or its extents do not match
     */
    public static @NotNull FoliageDistribution parse(byte @NotNull [] data, int definitionCount) {
        if (data.length < HEADER_SIZE) {
            throw Exceptions.newIllegalArgumentException("Incomplete foliage distribution or CV5 data.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        long recordCount = Integer.toUnsignedLong(buffer.getInt(RECORD_COUNT_OFFSET));
        long declaredDefinitions = Integer.toUnsignedLong(buffer.getInt(DEFINITION_COUNT_OFFSET));
        long selectionBytes = Integer.toUnsignedLong(buffer.getInt(SELECTION_SIZE_OFFSET));

        if (declaredDefinitions != definitionCount || declaredDefinitions > MAXIMUM_DEFINITIONS ||
            selectionBytes < (declaredDefinitions + SELECTION_BIT_MASK) / Byte.SIZE || selectionBytes > Integer.MAX_VALUE - PROBABILITY_SIZE ||
            recordCount > (data.length - (long) HEADER_SIZE) / (selectionBytes + PROBABILITY_SIZE)) {
            throw Exceptions.newIllegalArgumentException("Unsupported foliage distribution extent.");
        }

        return new FoliageDistribution(buffer, data, (int) recordCount, (int) selectionBytes);
    }


    private final int selectionByteCount;

    private final float @NotNull [] probabilities;
    private final byte @NotNull [] @NotNull [] selections;
    private final boolean @NotNull [] available;

    private FoliageDistribution(@NotNull ByteBuffer buffer, byte @NotNull [] data, int recordCount, int selectionBytes) {
        this.selectionByteCount = selectionBytes;

        this.probabilities = new float[recordCount];
        this.selections    = new byte[recordCount][selectionBytes];
        this.available     = new boolean[recordCount];

        for (int record = 0; record < recordCount; record++) {
            int position = HEADER_SIZE + record * (selectionBytes + PROBABILITY_SIZE);

            this.probabilities[record] = buffer.getFloat(position);

            for (int selectionIndex = 0; selectionIndex < selectionBytes; selectionIndex++) {
                byte selection = data[position + PROBABILITY_SIZE + selectionIndex];

                this.selections[record][selectionIndex] = selection;
                this.available[record] |= selection != 0;
            }
        }
    }

    /**
     * @return the number of records (one per megatile)
     */
    public int getRecordCount() {
        return this.probabilities.length;
    }

    /**
     * @return the number of selection bytes of one record
     */
    public int getSelectionByteCount() {
        return this.selectionByteCount;
    }

    /**
     * @param record the megatile record
     * @return the population probability in percent
     */
    public float getProbability(int record) {
        return this.probabilities[record];
    }

    /**
     * @param record     the megatile record
     * @param definition the foliage definition
     * @return {@code true} if the definition may grow on the megatile, {@code false} otherwise
     */
    public boolean isSelectable(int record, int definition) {
        return (this.selections[record][definition / Byte.SIZE] & (FIRST_SELECTION_BIT >>> (definition & SELECTION_BIT_MASK))) != 0;
    }

    /**
     * @param record the megatile record
     * @return {@code true} if any definition may grow on the megatile, {@code false} otherwise
     */
    public boolean hasSelection(int record) {
        return this.available[record];
    }

    /**
     * @param record         the megatile record
     * @param selectionIndex the selection byte
     * @return the raw selection byte
     */
    public byte getSelectionByte(int record, int selectionIndex) {
        return this.selections[record][selectionIndex];
    }
}
