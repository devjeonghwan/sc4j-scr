package org.fogofwarlabs.screcorder.sample.random;

import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.io.SampleReader;
import org.fogofwarlabs.screcorder.sample.io.SampleWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Defines the C runtime random state payload and its sampling through the fiber local storage of the target threads.
 */
public final class RuntimeRandomSampleDefinition extends SampleDefinition<RuntimeRandomSample> {
    private static final int SAMPLE_IDENTIFIER = 16;
    private static final int MAXIMUM_THREAD_COUNT = 4096;

    private static final int MAXIMUM_UNSIGNED_SHORT = 0xFFFF;
    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;

    private static final int ROW_BYTES = Short.BYTES + Integer.BYTES;

    private static final long THREAD_DATA_SLOT_OFFSET = 0xE09508;
    private static final long EXCEPTION_ACTIONS_OFFSET = 0xC38280;
    private static final int RANDOM_STATE_OFFSET = 0x28;
    private static final int THREAD_DATA_BYTES = RANDOM_STATE_OFFSET + Integer.BYTES;

    private static final int FIBER_DATA_OFFSET = 0x17C8;
    private static final int FIBER_CHUNKS_OFFSET = 2 * Long.BYTES;
    private static final int FIBER_CHUNK_COUNT = 8;
    private static final int FIBER_CHUNK_HEADER_SLOTS = 1;
    private static final int FIRST_FIBER_CHUNK_SLOT_BITS = 4;
    private static final int FIRST_FIBER_CHUNK_SLOTS = 1 << FIRST_FIBER_CHUNK_SLOT_BITS;
    private static final long MAXIMUM_FIBER_SLOT = ((long) FIRST_FIBER_CHUNK_SLOTS << FIBER_CHUNK_COUNT) - FIRST_FIBER_CHUNK_SLOTS - 1;

    private static final long UNAVAILABLE_THREAD_DATA = -1;

    private static final @NotNull SampleReader<RuntimeRandomSample> READER = RuntimeRandomSampleDefinition::read;
    private static final @NotNull SampleWriter<RuntimeRandomSample> WRITER = RuntimeRandomSampleDefinition::write;


    /**
     * Reads the random state of every thread whose fiber local storage holds runtime thread data.
     * <p>
     * The threads are listed and their thread data blocks found on the first sample; later samples read the found blocks
     * again. A thread whose block can no longer be read, or whose slot no longer addresses its block, has exited: it is
     * left out of that sample and the next sample lists the threads again, which also finds threads created meanwhile.
     * A block that the thread's slot still addresses but that fails the exception action table check fails the sample.
     */
    private static final class RuntimeRandomSampler implements Sampler<RuntimeRandomSample> {
        private final @NotNull CaptureContext context;

        private final byte @NotNull [] pointerStorage;
        private final byte @NotNull [] threadDataStorage;

        private boolean listingStale;
        private int chunkIndex;
        private int chunkPosition;
        private int @NotNull [] threadOrders;
        private long @NotNull [] environmentAddresses;
        private long @NotNull [] threadDataAddresses;

        private RuntimeRandomSampler(@NotNull CaptureContext context) {
            this.context = context;

            this.pointerStorage    = new byte[Long.BYTES];
            this.threadDataStorage = new byte[THREAD_DATA_BYTES];

            this.listingStale         = true;
            this.chunkIndex           = 0;
            this.chunkPosition        = 0;
            this.threadOrders         = new int[0];
            this.environmentAddresses = new long[0];
            this.threadDataAddresses  = new long[0];
        }


        /**
         * Collects the current sample.
         *
         * @return the immutable observation
         * @throws IOException if required memory cannot be collected
         */
        @Override
        public @NotNull RuntimeRandomSample sample() throws IOException {
            if (this.listingStale) {
                this.findThreadData();
            }

            List<RuntimeRandomState> threads = new ArrayList<>(this.threadDataAddresses.length);
            boolean vanished = false;

            for (int index = 0; index < this.threadDataAddresses.length; index++) {
                boolean holdsThreadData;

                try {
                    holdsThreadData = this.readThreadData(this.threadDataAddresses[index]);
                } catch (IOException unreadable) {
                    vanished = true;

                    continue;
                }

                if (!holdsThreadData) {
                    if (this.isSlotValue(this.environmentAddresses[index], this.threadDataAddresses[index])) {
                        throw Exceptions.newIOException("Thread {0} holds a block that is not runtime thread data", this.threadOrders[index]);
                    }

                    vanished = true;

                    continue;
                }

                threads.add(new RuntimeRandomState(this.threadOrders[index],
                                                   LittleEndianUtils.readUnsigned32(this.threadDataStorage, RANDOM_STATE_OFFSET)));
            }

            this.listingStale = vanished;

            if (threads.isEmpty()) {
                throw Exceptions.newIOException("No listed target thread holds readable runtime thread data");
            }

            return new RuntimeRandomSample(threads);
        }

        private void findThreadData() throws IOException {
            List<Long> environments = this.context.readThreadEnvironmentAddresses();

            this.context.getMemory().read(this.context.getModuleBase() + THREAD_DATA_SLOT_OFFSET, this.pointerStorage, 0, Integer.BYTES);

            long slot = LittleEndianUtils.readUnsigned32(this.pointerStorage, 0);

            if (slot > MAXIMUM_FIBER_SLOT) {
                throw Exceptions.newIOException("Invalid runtime thread data slot: {0}", slot);
            }

            int chunkSlot = (int) slot + FIRST_FIBER_CHUNK_SLOTS;

            this.chunkIndex    = Integer.numberOfTrailingZeros(Integer.highestOneBit(chunkSlot)) - FIRST_FIBER_CHUNK_SLOT_BITS;
            this.chunkPosition = chunkSlot - (FIRST_FIBER_CHUNK_SLOTS << this.chunkIndex);

            int[] orders = new int[environments.size()];
            long[] threadEnvironments = new long[environments.size()];
            long[] addresses = new long[environments.size()];
            int count = 0;

            for (int threadOrder = 0; threadOrder < environments.size(); threadOrder++) {
                long environment = environments.get(threadOrder);

                try {
                    long threadData = this.readSlotValue(environment);

                    if (threadData != 0 && threadData != UNAVAILABLE_THREAD_DATA && this.readThreadData(threadData)) {
                        orders[count]             = threadOrder;
                        threadEnvironments[count] = environment;
                        addresses[count]          = threadData;
                        count++;
                    }
                } catch (IOException ignored) {
                    // A thread that exited after the thread listing has no readable environment block.
                }
            }

            if (count == 0) {
                throw Exceptions.newIOException("No target thread holds runtime thread data");
            }

            this.listingStale         = false;
            this.threadOrders         = Arrays.copyOf(orders, count);
            this.environmentAddresses = Arrays.copyOf(threadEnvironments, count);
            this.threadDataAddresses  = Arrays.copyOf(addresses, count);
        }

        private long readSlotValue(long environment) throws IOException {
            long fiberData = this.readPointer(environment + FIBER_DATA_OFFSET);
            long chunk = fiberData == 0 ? 0 : this.readPointer(fiberData + FIBER_CHUNKS_OFFSET + (long) this.chunkIndex * Long.BYTES);

            return chunk == 0 ? 0 : this.readPointer(chunk + (long) (this.chunkPosition + FIBER_CHUNK_HEADER_SLOTS) * Long.BYTES);
        }

        private boolean isSlotValue(long environment, long threadData) {
            try {
                return this.readSlotValue(environment) == threadData;
            } catch (IOException unreadable) {
                return false;
            }
        }

        private long readPointer(long address) throws IOException {
            this.context.getMemory().read(address, this.pointerStorage, 0, Long.BYTES);

            return LittleEndianUtils.readUnsigned(this.pointerStorage, 0, Long.BYTES);
        }

        /**
         * Reads a runtime thread data block into the session buffer.
         *
         * @param address the block address
         * @return {@code true} if the first pointer of the block addresses the executable's exception action table, {@code false} otherwise
         * @throws IOException if the block cannot be read
         */
        private boolean readThreadData(long address) throws IOException {
            this.context.getMemory().read(address, this.threadDataStorage, 0, this.threadDataStorage.length);

            return LittleEndianUtils.readUnsigned(this.threadDataStorage, 0, Long.BYTES) == this.context.getModuleBase() + EXCEPTION_ACTIONS_OFFSET;
        }
    }

    private static @NotNull RuntimeRandomSample read(@NotNull ByteBuffer input) throws IOException {
        input.order(ByteOrder.LITTLE_ENDIAN);

        if (input.remaining() < Integer.BYTES) {
            throw Exceptions.newIOException("Missing runtime random thread count");
        }

        int threadCount = input.getInt();

        if (threadCount < 0 || threadCount > MAXIMUM_THREAD_COUNT || (long) threadCount * ROW_BYTES != input.remaining()) {
            throw Exceptions.newIOException("Invalid runtime random thread count or payload size: {0}", threadCount);
        }

        List<RuntimeRandomState> threads = new ArrayList<>(threadCount);
        int previousOrder = -1;

        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
            RuntimeRandomState thread = new RuntimeRandomState(Short.toUnsignedInt(input.getShort()), Integer.toUnsignedLong(input.getInt()));

            if (thread.getThreadOrder() <= previousOrder) {
                throw Exceptions.newIOException("Invalid or noncanonical runtime random thread order");
            }

            threads.add(thread);
            previousOrder = thread.getThreadOrder();
        }

        return new RuntimeRandomSample(threads);
    }

    private static void write(@NotNull ByteBuffer output, @NotNull RuntimeRandomSample sample) throws IOException {
        validate(sample);

        int encodedSize = Integer.BYTES + sample.getThreads().size() * ROW_BYTES;

        if (output.remaining() < encodedSize || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable runtime random buffer: {0}", encodedSize);
        }

        output.order(ByteOrder.LITTLE_ENDIAN).putInt(sample.getThreads().size());

        for (RuntimeRandomState thread : sample.getThreads()) {
            output.putShort((short) thread.getThreadOrder()).putInt((int) thread.getState());
        }
    }

    private static void validate(@NotNull RuntimeRandomSample sample) throws IOException {
        if (sample.getThreads().size() > MAXIMUM_THREAD_COUNT) {
            throw Exceptions.newIOException("Runtime random thread count exceeds its limit: {0}", sample.getThreads().size());
        }

        int previousOrder = -1;

        for (RuntimeRandomState thread : sample.getThreads()) {
            if (thread.getThreadOrder() <= previousOrder || thread.getThreadOrder() > MAXIMUM_UNSIGNED_SHORT ||
                thread.getState() < 0 || thread.getState() > MAXIMUM_UNSIGNED_INTEGER) {
                throw Exceptions.newIOException("Invalid or unordered runtime random thread state");
            }

            previousOrder = thread.getThreadOrder();
        }
    }


    /**
     * Creates a {@link RuntimeRandomSampleDefinition} instance.
     */
    public RuntimeRandomSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "runtime-random", RuntimeRandomSample.class, Integer.BYTES + MAXIMUM_THREAD_COUNT * ROW_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<RuntimeRandomSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<RuntimeRandomSample> getWriter() {
        return WRITER;
    }


    /**
     * Calculates the exact payload size before reserving reusable output storage.
     *
     * @param sample the sample to encode
     * @return the exact encoded byte count
     * @throws IOException if the sample is invalid
     */
    @Override
    public int getEncodedSize(@NotNull RuntimeRandomSample sample) throws IOException {
        validate(sample);

        return Integer.BYTES + sample.getThreads().size() * ROW_BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     * <p>
     * The sampler reads the runtime thread data slot from the executable, follows the fiber local storage of every
     * listed target thread to its runtime thread data block and keeps the blocks whose first pointer addresses the
     * executable's exception action table. A thread that exited is left out and the threads are listed again for the
     * next sample; thread orders are ranks by creation time in the latest listing, so after a relisting the orders of
     * threads created after an exited thread decrease, while the initial thread keeps order {@code 0}. A sample fails if no
     * listed thread holds readable runtime thread data, or if a block that its thread's slot still addresses fails the check.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<RuntimeRandomSample> createSampler(@NotNull CaptureContext context) {
        return new RuntimeRandomSampler(context);
    }
}
