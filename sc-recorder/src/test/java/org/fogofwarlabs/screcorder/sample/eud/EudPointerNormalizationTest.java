package org.fogofwarlabs.screcorder.sample.eud;

import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that EUD pointers normalize to image offsets or a presence tag so that recordings do not depend on the process.
 */
final class EudPointerNormalizationTest {
    private static final long MODULE_BASE = 0x7FF686A90000L;
    private static final long HEAP_POINTER = 0x228957C7B50L;
    private static final long OTHER_HEAP_POINTER = 0x2D0DBF979C0L;
    private static final int MEMORY_BYTES = 3 * Long.BYTES;
    private static final int SECOND_WORD_OFFSET = Long.BYTES;
    private static final int THIRD_WORD_OFFSET = 2 * Long.BYTES;

    private static final long IMAGE_POINTER_TAG = 0x4000000000000000L;
    private static final long OTHER_POINTER_TAG = 0x2000000000000000L;

    @Test
    void normalize_pointer_should_keep_zero_and_make_image_pointers_relative() {
        Assertions.assertEquals(0, EudMemory.normalizePointer(0, MODULE_BASE));
        Assertions.assertEquals(IMAGE_POINTER_TAG | 0x123456, EudMemory.normalizePointer(MODULE_BASE + 0x123456, MODULE_BASE));
        Assertions.assertEquals(OTHER_POINTER_TAG, EudMemory.normalizePointer(HEAP_POINTER, MODULE_BASE));
        Assertions.assertEquals(EudMemory.normalizePointer(OTHER_HEAP_POINTER, MODULE_BASE), EudMemory.normalizePointer(HEAP_POINTER, MODULE_BASE));
    }

    @Test
    void normalize_pointer_words_should_change_only_pointer_valued_words() {
        byte[] memory = new byte[MEMORY_BYTES];

        memory[0] = 7;

        for (int index = 0; index < Long.BYTES; index++) {
            memory[SECOND_WORD_OFFSET + index] = (byte) (HEAP_POINTER >>> (index * Byte.SIZE));
            memory[THIRD_WORD_OFFSET + index]  = (byte) ((MODULE_BASE + 0x40) >>> (index * Byte.SIZE));
        }

        byte[] normalized = EudMemory.normalizePointerWords(memory, MODULE_BASE);

        Assertions.assertEquals(7, LittleEndianUtils.readUnsigned(normalized, 0, Long.BYTES));
        Assertions.assertEquals(OTHER_POINTER_TAG, LittleEndianUtils.readUnsigned(normalized, SECOND_WORD_OFFSET, Long.BYTES));
        Assertions.assertEquals(IMAGE_POINTER_TAG | 0x40, LittleEndianUtils.readUnsigned(normalized, THIRD_WORD_OFFSET, Long.BYTES));
        Assertions.assertEquals(HEAP_POINTER, LittleEndianUtils.readUnsigned(memory, SECOND_WORD_OFFSET, Long.BYTES));
    }
}
