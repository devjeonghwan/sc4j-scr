package org.fogofwarlabs.screcorder.sample.context;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.sample.RangeMemoryScenario;
import org.fogofwarlabs.screcorder.sample.SampleRoundTripCheck;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Checks that the context sampler captures the complete game context as a detached snapshot and that its reader rejects a short payload.
 */
final class ContextSampleTest {
    private static final long MODULE_BASE = 0x100000000L;
    private static final long CONTEXT_ADDRESS = 0x120000000L;

    @Test
    void context_sampler_samples_the_complete_context_and_keeps_snapshots_detached() throws IOException {
        RangeMemoryScenario memory = new RangeMemoryScenario();
        byte[] contextStorage = memory.allocateRange(CONTEXT_ADDRESS, ContextSample.MEMORY_BYTES);

        contextStorage[0]                         = 5;
        contextStorage[contextStorage.length - 1] = 9;

        ContextSampleDefinition definition = new ContextSampleDefinition();
        ContextSample sample = definition.createSampler(new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS)).sample();

        contextStorage[0] = 99;

        ContextSample decoded = SampleRoundTripCheck.readAfterWrite(definition, sample);

        Assertions.assertEquals(5, decoded.getMemory()[0]);
        Assertions.assertEquals(9, decoded.getMemory()[ContextSample.MEMORY_BYTES - 1]);

        byte[] detached = decoded.getMemory();

        detached[0] = 1;

        Assertions.assertEquals(5, decoded.getMemory()[0]);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(ByteBuffer.allocate(ContextSample.MEMORY_BYTES - 1)));
    }
}
