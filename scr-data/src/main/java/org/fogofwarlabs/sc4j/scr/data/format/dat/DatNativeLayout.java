package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Describes how the game keeps the column blocks of a DAT file in its memory.
 * <p>
 * The blocks follow each other in file order, each one starting at the next multiple of its alignment. A block whose array holds references to loaded
 * data instead of the file values takes {@value #REFERENCE_SIZE} bytes per element, and its memory is not represented.
 */
final class DatNativeLayout {
    /**
     * The size of a reference to loaded data in the game's memory.
     */
    static final int REFERENCE_SIZE = Long.BYTES;

    private final int defaultAlignment;
    private final @NotNull Map<String, Integer> blockAlignments;
    private final @NotNull Set<String> referenceFields;

    /**
     * Creates a {@link DatNativeLayout} instance.
     *
     * @param defaultAlignment the alignment of every block without its own alignment
     * @param blockAlignments  the alignment of a block by the name of its first field
     * @param referenceFields  the names of the fields whose arrays hold references to loaded data
     */
    DatNativeLayout(int defaultAlignment, @NotNull Map<String, Integer> blockAlignments, @NotNull Set<String> referenceFields) {
        this.defaultAlignment = defaultAlignment;
        this.blockAlignments  = blockAlignments;
        this.referenceFields  = referenceFields;
    }

    /**
     * @param fieldName the name of the first field of a block
     * @return the alignment of the block in bytes
     */
    int getAlignment(@NotNull String fieldName) {
        return this.blockAlignments.getOrDefault(fieldName, this.defaultAlignment);
    }

    /**
     * @param fieldName the name of the first field of a block
     * @return {@code true} if the block holds references to loaded data instead of the file values, {@code false} otherwise
     */
    boolean isReference(@NotNull String fieldName) {
        return this.referenceFields.contains(fieldName);
    }
}
