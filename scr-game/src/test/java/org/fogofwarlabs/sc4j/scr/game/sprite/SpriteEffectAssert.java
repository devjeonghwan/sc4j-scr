package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;

/**
 * Compares written pool records with the records of extracted native pool captures, skipping the alignment padding of every slot.
 */
public final class SpriteEffectAssert {
    /**
     * The slot field that holds alignment padding rather than game state.
     */
    private static final int PADDING_FIELD = 3;

    /**
     * Compares a written pool record with the next record of an extracted capture, skipping the padding field of every slot.
     * <p>
     * The written record is flipped for reading first and must end where the compared fields end.
     *
     * @param expected    the capture, positioned at the record
     * @param actual      the written record, flipped by this method
     * @param headerWords the DWORD fields before the slots
     * @param slotCount   the number of slots
     * @param slotWords   the DWORD fields of one slot in the capture, including the padding field the written record omits
     * @param tailWords   the DWORD fields after the slots
     */
    public static void assertRecord(@NotNull ByteBuffer expected, @NotNull ByteBuffer actual,
                                    int headerWords, int slotCount, int slotWords, int tailWords) {
        actual.flip();

        for (int field = 0; field < headerWords; field++) {
            Assertions.assertEquals(expected.getInt(), actual.getInt(), "Pool header " + field);
        }

        for (int slot = 0; slot < slotCount; slot++) {
            for (int field = 0; field < slotWords; field++) {
                int expectedValue = expected.getInt();

                if (field != PADDING_FIELD) {
                    Assertions.assertEquals(expectedValue, actual.getInt(), "Slot " + slot + " field " + field);
                }
            }
        }

        for (int field = 0; field < tailWords; field++) {
            Assertions.assertEquals(expected.getInt(), actual.getInt(), "Pool trailing field " + field);
        }

        Assertions.assertFalse(actual.hasRemaining());
    }


    private SpriteEffectAssert() {
    }
}
