package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Creates the little-endian record views through which trigger fixtures encode trigger records by hand.
 */
final class TriggerRecordScenario {
    /**
     * Creates a little-endian view of one encoded trigger record of a record array.
     *
     * @param records the complete record bytes
     * @param index   the record index
     * @return the bounded record view
     */
    static @NotNull ByteBuffer createRecord(byte @NotNull [] records, int index) {
        return createRecordAt(records, index * TriggerRecordCodec.RECORD_SIZE);
    }

    /**
     * Creates a little-endian view of one encoded trigger record that starts at a byte offset of the supplied bytes.
     *
     * @param bytes  the backing bytes
     * @param offset the first record byte
     * @return the bounded record view
     */
    static @NotNull ByteBuffer createRecordAt(byte @NotNull [] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, TriggerRecordCodec.RECORD_SIZE).slice().order(ByteOrder.LITTLE_ENDIAN);
    }


    /**
     * Creates a {@link TriggerRecordScenario} instance.
     */
    private TriggerRecordScenario() {
    }
}
