package org.fogofwarlabs.screcorder.sample.random;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.capture.windows.WindowsProcess;
import org.fogofwarlabs.screcorder.sample.RangeMemoryScenario;
import org.fogofwarlabs.screcorder.sample.SampleRoundTripCheck;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Checks that the runtime random sampler follows the fiber local storage of the target threads to their runtime thread data, keeps
 * only blocks that hold runtime thread data, and that the runtime random sample validates its rows and payload.
 */
final class RuntimeRandomSampleTest {
    private static final long MODULE_BASE = 0x140000000L;
    private static final long CONTEXT_ADDRESS = 0x200000000L;
    private static final long FIRST_ENVIRONMENT_ADDRESS = 0x300000000L;
    private static final long ENVIRONMENT_STRIDE = 0x100000L;
    private static final long FIBER_DATA_DISTANCE = 0x10000L;
    private static final long CHUNK_DISTANCE = 0x20000L;
    private static final long THREAD_DATA_DISTANCE = 0x30000L;
    private static final long UNMAPPED_ENVIRONMENT_ADDRESS = 0x900000000L;

    private static final int THREAD_DATA_SLOT_OFFSET = 0xE09508;
    private static final int EXCEPTION_ACTIONS_OFFSET = 0xC38280;
    private static final int RANDOM_STATE_OFFSET = 0x28;
    private static final int THREAD_DATA_BYTES = 0x30;
    private static final int ENVIRONMENT_BYTES = 0x1800;
    private static final int FIBER_DATA_POINTER_OFFSET = 0x17C8;
    private static final int FIBER_DATA_BYTES = 0x50;
    private static final int CHUNK_BYTES = 0x200;

    /**
     * Slot 20 lies in the second storage chunk, at position 4 behind the chunk's header pointer.
     */
    private static final int THREAD_DATA_SLOT = 20;
    private static final int SECOND_CHUNK_POINTER_OFFSET = 0x18;
    private static final int SLOT_VALUE_OFFSET = 0x28;
    private static final int INVALID_SLOT = 4080;

    private static final long MAXIMUM_STATE = 0xFFFFFFFFL;
    private static final long CHANGED_STATE = 0x12345678L;
    private static final long SECOND_THREAD_STATE = 5;
    private static final long LIVE_THREAD_STATE = 0x2A2A2A2AL;
    private static final long FOREIGN_ANCHOR = 0x7FF000000000L;
    private static final int FIRST_THREAD_ORDER = 0;
    private static final int EMPTY_THREAD_ORDER = 1;
    private static final int FOREIGN_THREAD_ORDER = 2;
    private static final int SECOND_THREAD_ORDER = 4;
    private static final int LATE_THREAD_ORDER = 5;
    private static final long LATE_THREAD_STATE = 7;
    private static final int SECOND_LISTED_ORDER = 1;
    private static final int LATE_LISTED_ORDER = 2;
    private static final int ROW_BYTES = 6;
    private static final int MAXIMUM_THREAD_ORDER = 0xFFFF;

    private static long getEnvironmentAddress(int threadOrder) {
        return FIRST_ENVIRONMENT_ADDRESS + threadOrder * ENVIRONMENT_STRIDE;
    }

    private static @NotNull ByteBuffer allocateThread(@NotNull RangeMemoryScenario memory, int threadOrder, long anchor, long state) {
        long environment = getEnvironmentAddress(threadOrder);
        long fiberData = environment + FIBER_DATA_DISTANCE;
        long chunk = environment + CHUNK_DISTANCE;
        long threadData = environment + THREAD_DATA_DISTANCE;

        wrap(memory.allocateRange(environment, ENVIRONMENT_BYTES)).putLong(FIBER_DATA_POINTER_OFFSET, fiberData);
        wrap(memory.allocateRange(fiberData, FIBER_DATA_BYTES)).putLong(SECOND_CHUNK_POINTER_OFFSET, chunk);
        wrap(memory.allocateRange(chunk, CHUNK_BYTES)).putLong(SLOT_VALUE_OFFSET, threadData);

        return wrap(memory.allocateRange(threadData, THREAD_DATA_BYTES)).putLong(0, anchor).putInt(RANDOM_STATE_OFFSET, (int) state);
    }

    private static @NotNull ByteBuffer wrap(byte @NotNull [] storage) {
        return ByteBuffer.wrap(storage).order(ByteOrder.LITTLE_ENDIAN);
    }


    @Test
    void runtime_random_sampler_reads_the_states_of_threads_that_hold_runtime_thread_data() throws IOException {
        RangeMemoryScenario memory = new RangeMemoryScenario();

        wrap(memory.allocateRange(MODULE_BASE + THREAD_DATA_SLOT_OFFSET, Integer.BYTES)).putInt(0, THREAD_DATA_SLOT);

        ByteBuffer firstThreadData = allocateThread(memory, FIRST_THREAD_ORDER, MODULE_BASE + EXCEPTION_ACTIONS_OFFSET, MAXIMUM_STATE);

        memory.allocateRange(getEnvironmentAddress(EMPTY_THREAD_ORDER), ENVIRONMENT_BYTES);
        allocateThread(memory, FOREIGN_THREAD_ORDER, FOREIGN_ANCHOR, SECOND_THREAD_STATE);
        allocateThread(memory, SECOND_THREAD_ORDER, MODULE_BASE + EXCEPTION_ACTIONS_OFFSET, SECOND_THREAD_STATE);

        List<Long> environments = List.of(getEnvironmentAddress(FIRST_THREAD_ORDER), getEnvironmentAddress(EMPTY_THREAD_ORDER),
                                          getEnvironmentAddress(FOREIGN_THREAD_ORDER), UNMAPPED_ENVIRONMENT_ADDRESS,
                                          getEnvironmentAddress(SECOND_THREAD_ORDER));
        RuntimeRandomSampleDefinition definition = new RuntimeRandomSampleDefinition();
        Sampler<RuntimeRandomSample> sampler = definition.createSampler(new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS, environments));
        RuntimeRandomSample sample = SampleRoundTripCheck.readAfterWrite(definition, sampler.sample());

        Assertions.assertEquals(2, sample.getThreads().size());
        Assertions.assertEquals(FIRST_THREAD_ORDER, sample.getThreads().getFirst().getThreadOrder());
        Assertions.assertEquals(MAXIMUM_STATE, sample.getThreads().getFirst().getState());
        Assertions.assertEquals(SECOND_THREAD_ORDER, sample.getThreads().getLast().getThreadOrder());
        Assertions.assertEquals(SECOND_THREAD_STATE, sample.getThreads().getLast().getState());

        firstThreadData.putInt(RANDOM_STATE_OFFSET, (int) CHANGED_STATE);

        Assertions.assertEquals(CHANGED_STATE, sampler.sample().getThreads().getFirst().getState());
        Assertions.assertEquals(MAXIMUM_STATE, sample.getThreads().getFirst().getState());

        firstThreadData.putLong(0, FOREIGN_ANCHOR);

        Assertions.assertThrows(IOException.class, sampler::sample);
    }

    @Test
    void runtime_random_sampler_leaves_out_exited_threads_and_finds_threads_created_later() throws IOException {
        RangeMemoryScenario memory = new RangeMemoryScenario();

        wrap(memory.allocateRange(MODULE_BASE + THREAD_DATA_SLOT_OFFSET, Integer.BYTES)).putInt(0, THREAD_DATA_SLOT);
        allocateThread(memory, FIRST_THREAD_ORDER, MODULE_BASE + EXCEPTION_ACTIONS_OFFSET, MAXIMUM_STATE);

        ByteBuffer secondThreadData = allocateThread(memory, SECOND_THREAD_ORDER, MODULE_BASE + EXCEPTION_ACTIONS_OFFSET, SECOND_THREAD_STATE);

        memory.allocateRange(getEnvironmentAddress(LATE_THREAD_ORDER), ENVIRONMENT_BYTES);

        List<Long> environments = List.of(getEnvironmentAddress(FIRST_THREAD_ORDER), getEnvironmentAddress(SECOND_THREAD_ORDER),
                                          getEnvironmentAddress(LATE_THREAD_ORDER));
        CaptureContext context = new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS, environments);
        Sampler<RuntimeRandomSample> sampler = new RuntimeRandomSampleDefinition().createSampler(context);

        Assertions.assertEquals(2, sampler.sample().getThreads().size());

        memory.allocateRange(getEnvironmentAddress(FIRST_THREAD_ORDER) + THREAD_DATA_DISTANCE, 0);
        allocateThread(memory, LATE_THREAD_ORDER, MODULE_BASE + EXCEPTION_ACTIONS_OFFSET, LATE_THREAD_STATE);

        RuntimeRandomSample withoutExitedThread = sampler.sample();

        Assertions.assertEquals(1, withoutExitedThread.getThreads().size());
        Assertions.assertEquals(SECOND_LISTED_ORDER, withoutExitedThread.getThreads().getFirst().getThreadOrder());

        RuntimeRandomSample relisted = sampler.sample();

        Assertions.assertEquals(2, relisted.getThreads().size());
        Assertions.assertEquals(SECOND_LISTED_ORDER, relisted.getThreads().getFirst().getThreadOrder());
        Assertions.assertEquals(LATE_LISTED_ORDER, relisted.getThreads().getLast().getThreadOrder());
        Assertions.assertEquals(LATE_THREAD_STATE, relisted.getThreads().getLast().getState());

        memory.allocateRange(getEnvironmentAddress(SECOND_THREAD_ORDER), ENVIRONMENT_BYTES);
        secondThreadData.putLong(0, FOREIGN_ANCHOR);

        Assertions.assertEquals(LATE_LISTED_ORDER, sampler.sample().getThreads().getFirst().getThreadOrder());

        memory.allocateRange(getEnvironmentAddress(LATE_THREAD_ORDER) + THREAD_DATA_DISTANCE, 0);

        Assertions.assertThrows(IOException.class, sampler::sample);
    }

    @Test
    void runtime_random_sampler_fails_without_thread_data_or_with_an_invalid_slot() {
        RangeMemoryScenario memory = new RangeMemoryScenario();
        ByteBuffer slot = wrap(memory.allocateRange(MODULE_BASE + THREAD_DATA_SLOT_OFFSET, Integer.BYTES)).putInt(0, THREAD_DATA_SLOT);
        RuntimeRandomSampleDefinition definition = new RuntimeRandomSampleDefinition();

        Assertions.assertThrows(IOException.class, () -> definition.createSampler(new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS)).sample());

        allocateThread(memory, FIRST_THREAD_ORDER, MODULE_BASE + EXCEPTION_ACTIONS_OFFSET, MAXIMUM_STATE);
        slot.putInt(0, INVALID_SLOT);

        CaptureContext context = new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS, List.of(getEnvironmentAddress(FIRST_THREAD_ORDER)));

        Assertions.assertThrows(IOException.class, () -> definition.createSampler(context).sample());
    }

    @Test
    void runtime_random_sample_validates_thread_order_state_and_payload_lengths() throws IOException {
        RuntimeRandomSampleDefinition definition = new RuntimeRandomSampleDefinition();
        RuntimeRandomState first = new RuntimeRandomState(FIRST_THREAD_ORDER, MAXIMUM_STATE);
        RuntimeRandomState last = new RuntimeRandomState(MAXIMUM_THREAD_ORDER, 0);
        RuntimeRandomSample sample = new RuntimeRandomSample(List.of(first, last));
        ByteBuffer output = ByteBuffer.allocate(definition.getEncodedSize(sample));

        definition.getWriter().write(output, sample);

        byte[] canonical = output.array().clone();

        Assertions.assertArrayEquals(new byte[]{2, 0, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0}, canonical);
        Assertions.assertThrows(IOException.class, () -> definition.getEncodedSize(new RuntimeRandomSample(List.of(last, first))));
        Assertions.assertThrows(IOException.class, () -> definition.getEncodedSize(new RuntimeRandomSample(List.of(first, first))));
        Assertions.assertThrows(IOException.class,
                                () -> definition.getEncodedSize(new RuntimeRandomSample(List.of(new RuntimeRandomState(0, MAXIMUM_STATE + 1)))));

        ByteBuffer duplicate = ByteBuffer.wrap(canonical.clone());

        duplicate.put(Integer.BYTES + ROW_BYTES, (byte) 0).put(Integer.BYTES + ROW_BYTES + 1, (byte) 0);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(duplicate));

        ByteBuffer truncated = ByteBuffer.wrap(canonical.clone());

        truncated.limit(truncated.limit() - 1);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(truncated));

        ByteBuffer enormousCount = ByteBuffer.wrap(canonical.clone()).order(ByteOrder.LITTLE_ENDIAN);

        enormousCount.putInt(0, Integer.MAX_VALUE);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(enormousCount));
        Assertions.assertThrows(IOException.class,
                                () -> definition.getWriter().write(ByteBuffer.allocate(canonical.length).asReadOnlyBuffer(), sample));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @SuppressWarnings("restricted")
    void runtime_random_sampler_reads_the_thread_data_that_a_live_thread_stores_in_fiber_local_storage() throws Throwable {
        try (Arena memory = Arena.ofConfined(); WindowsProcess process = WindowsProcess.open(ProcessHandle.current().pid())) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel = SymbolLookup.libraryLookup("kernel32.dll", memory);
            MethodHandle allocateSlot = linker.downcallHandle(kernel.findOrThrow("FlsAlloc"),
                                                              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            MethodHandle setSlotValue = linker.downcallHandle(kernel.findOrThrow("FlsSetValue"),
                                                              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            MethodHandle freeSlot = linker.downcallHandle(kernel.findOrThrow("FlsFree"),
                                                          FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            MemorySegment image = memory.allocate(THREAD_DATA_SLOT_OFFSET + Integer.BYTES, Long.BYTES);
            MemorySegment threadData = memory.allocate(THREAD_DATA_BYTES, Long.BYTES);
            int slot = (int) allocateSlot.invokeExact(MemorySegment.NULL);

            Assertions.assertTrue(slot >= 0);

            try {
                image.set(ValueLayout.JAVA_INT, THREAD_DATA_SLOT_OFFSET, slot);
                threadData.set(ValueLayout.JAVA_LONG, 0, image.address() + EXCEPTION_ACTIONS_OFFSET);
                threadData.set(ValueLayout.JAVA_INT, RANDOM_STATE_OFFSET, (int) LIVE_THREAD_STATE);
                Assertions.assertNotEquals(0, (int) setSlotValue.invokeExact(slot, threadData));

                CaptureContext context = new CaptureContext(process, image.address(), CONTEXT_ADDRESS, process.readThreadEnvironmentAddresses());
                RuntimeRandomSample sample = new RuntimeRandomSampleDefinition().createSampler(context).sample();

                Assertions.assertEquals(1, sample.getThreads().size());
                Assertions.assertEquals(LIVE_THREAD_STATE, sample.getThreads().getFirst().getState());
            } finally {
                int cleared = (int) setSlotValue.invokeExact(slot, MemorySegment.NULL);
                int freed = (int) freeSlot.invokeExact(slot);

                Assertions.assertNotEquals(0, cleared);
                Assertions.assertNotEquals(0, freed);
            }
        }
    }
}
