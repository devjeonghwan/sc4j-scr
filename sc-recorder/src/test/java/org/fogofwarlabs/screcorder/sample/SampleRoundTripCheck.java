package org.fogofwarlabs.screcorder.sample;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Computes the round trip of a sample through the canonical writer and the bounded reader of its definition.
 */
public final class SampleRoundTripCheck {
    /**
     * Writes a sample and reads it back, checking that the writer fills the exact encoded size and that the reader consumes it.
     *
     * @param definition    the definition whose writer and reader are used
     * @param sample        the sample to write
     * @param <SampleValue> the sample model type
     * @return the decoded sample
     * @throws IOException if the sample cannot be encoded or decoded
     */
    public static <SampleValue extends Sample> @NotNull SampleValue readAfterWrite(@NotNull SampleDefinition<SampleValue> definition,
                                                                                   @NotNull SampleValue sample) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(definition.getEncodedSize(sample)).order(ByteOrder.LITTLE_ENDIAN);

        definition.getWriter().write(payload, sample);
        Assertions.assertEquals(payload.capacity(), payload.position());
        payload.flip();

        SampleValue decoded = definition.getReader().read(payload);

        Assertions.assertFalse(payload.hasRemaining());

        return decoded;
    }


    private SampleRoundTripCheck() {
    }
}
