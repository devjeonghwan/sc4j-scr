package org.fogofwarlabs.screcorder.sample.unitlist;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.io.SampleReader;
import org.fogofwarlabs.screcorder.sample.io.SampleWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Defines visible and hidden unit traversal payloads and pool-relative sampling.
 */
public final class UnitListsSampleDefinition extends SampleDefinition<UnitListsSample> {
    private static final int SAMPLE_IDENTIFIER = 7;
    private static final int LIST_COUNT = 2;

    private static final long VISIBLE_HEAD_OFFSET = 0xE77FC8;
    private static final long HIDDEN_HEAD_OFFSET = 0xE77FE8;
    private static final int HIDDEN_HEAD_DISTANCE = (int) (HIDDEN_HEAD_OFFSET - VISIBLE_HEAD_OFFSET);
    private static final int HEAD_BYTES = HIDDEN_HEAD_DISTANCE + Long.BYTES;
    private static final int NEXT_OFFSET = 8;
    private static final int POOL_METADATA_BYTES = 2 * Long.BYTES;

    private static final int BIT_WORD_SHIFT = 6;
    private static final int BIT_WORD_MASK = Long.SIZE - 1;
    private static final int VISITED_WORDS = (StarCraftLayout.MAXIMUM_UNITS + Long.SIZE - 1) / Long.SIZE;

    private static final int MAXIMUM_PAYLOAD_BYTES = LIST_COUNT * Integer.BYTES + LIST_COUNT * StarCraftLayout.MAXIMUM_UNITS * Short.BYTES;

    private static final @NotNull SampleReader<UnitListsSample> READER = UnitListsSampleDefinition::read;
    private static final @NotNull SampleWriter<UnitListsSample> WRITER = UnitListsSampleDefinition::write;

    private static @NotNull UnitListsSample read(@NotNull ByteBuffer input) throws IOException {
        input.order(ByteOrder.LITTLE_ENDIAN);

        int[] visible = readIdentifiers(input);
        int[] hidden = readIdentifiers(input);

        if (input.hasRemaining()) {
            throw Exceptions.newIOException("Trailing unit list payload");
        }

        return new UnitListsSample(visible, hidden);
    }

    private static int @NotNull [] readIdentifiers(@NotNull ByteBuffer input) throws IOException {
        if (input.remaining() < Integer.BYTES) {
            throw Exceptions.newIOException("Missing unit list count");
        }

        int count = input.getInt();

        if (count < 0 || count > StarCraftLayout.MAXIMUM_UNITS || count * Short.BYTES > input.remaining()) {
            throw Exceptions.newIOException("Invalid unit list count: {0}", count);
        }

        int[] identifiers = new int[count];

        for (int index = 0; index < count; index++) {
            identifiers[index] = Short.toUnsignedInt(input.getShort());
        }

        validateIdentifiers(identifiers);

        return identifiers;
    }

    private static void write(@NotNull ByteBuffer output, @NotNull UnitListsSample sample) throws IOException {
        int[] visible = sample.getVisibleStorage();
        int[] hidden = sample.getHiddenStorage();

        validateIdentifiers(visible);
        validateIdentifiers(hidden);

        int encodedSize = LIST_COUNT * Integer.BYTES + (visible.length + hidden.length) * Short.BYTES;

        if (output.remaining() < encodedSize || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable unit list buffer: {0}", encodedSize);
        }

        output.order(ByteOrder.LITTLE_ENDIAN).putInt(visible.length);

        for (int identifier : visible) {
            output.putShort((short) identifier);
        }

        output.putInt(hidden.length);

        for (int identifier : hidden) {
            output.putShort((short) identifier);
        }
    }

    private static void validateIdentifiers(int @NotNull [] identifiers) throws IOException {
        if (identifiers.length > StarCraftLayout.MAXIMUM_UNITS) {
            throw Exceptions.newIOException("Unit list exceeds the pool limit: {0}", identifiers.length);
        }

        long[] visited = new long[VISITED_WORDS];

        for (int identifier : identifiers) {
            if (identifier < 1 || identifier > StarCraftLayout.MAXIMUM_UNITS) {
                throw Exceptions.newIOException("Invalid unit list identifier: {0}", identifier);
            }

            if (!markVisited(visited, identifier - 1)) {
                throw Exceptions.newIOException("Duplicate unit list identifier: {0}", identifier);
            }
        }
    }

    /**
     * Marks a pool slot in the visited bit set.
     *
     * @param visited   the visited bit set, one bit per pool slot
     * @param slotIndex the zero-based pool slot
     * @return {@code true} if the slot was not visited before, {@code false} otherwise
     */
    private static boolean markVisited(long @NotNull [] visited, int slotIndex) {
        int wordIndex = slotIndex >>> BIT_WORD_SHIFT;
        long mask = 1L << (slotIndex & BIT_WORD_MASK);

        if ((visited[wordIndex] & mask) != 0) {
            return false;
        }

        visited[wordIndex] |= mask;

        return true;
    }

    private static int @NotNull [] traverse(long head,
                                            long poolAddress, int poolSlots, @NotNull ByteBuffer pool,
                                            int @NotNull [] identifiers, long @NotNull [] visited) throws IOException {
        Arrays.fill(visited, 0L);

        int count = 0;
        long pointer = head;

        while (pointer != 0) {
            long offset = pointer - poolAddress;

            if (pointer < poolAddress || offset < 0 || offset % StarCraftLayout.UNIT_STRIDE != 0 ||
                offset / StarCraftLayout.UNIT_STRIDE >= poolSlots || count >= poolSlots) {
                throw Exceptions.newIOException("Unit list pointer is outside the declared pool");
            }

            int slotIndex = (int) (offset / StarCraftLayout.UNIT_STRIDE);

            if (!markVisited(visited, slotIndex)) {
                throw Exceptions.newIOException("Cyclic unit list");
            }

            identifiers[count++] = slotIndex + 1;
            pointer              = pool.getLong(slotIndex * StarCraftLayout.UNIT_STRIDE + NEXT_OFFSET);
        }

        return Arrays.copyOf(identifiers, count);
    }


    /**
     * Creates a {@link UnitListsSampleDefinition} instance.
     */
    public UnitListsSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "lists", UnitListsSample.class, MAXIMUM_PAYLOAD_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<UnitListsSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<UnitListsSample> getWriter() {
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
    public int getEncodedSize(@NotNull UnitListsSample sample) throws IOException {
        int[] visible = sample.getVisibleStorage();
        int[] hidden = sample.getHiddenStorage();

        validateIdentifiers(visible);
        validateIdentifiers(hidden);

        return LIST_COUNT * Integer.BYTES + (visible.length + hidden.length) * Short.BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<UnitListsSample> createSampler(@NotNull CaptureContext context) {
        byte[] metadataStorage = new byte[POOL_METADATA_BYTES];
        ByteBuffer metadata = ByteBuffer.wrap(metadataStorage).order(ByteOrder.LITTLE_ENDIAN);
        byte[] headStorage = new byte[HEAD_BYTES];
        ByteBuffer heads = ByteBuffer.wrap(headStorage).order(ByteOrder.LITTLE_ENDIAN);
        byte[] poolStorage = new byte[StarCraftLayout.MAXIMUM_UNITS * StarCraftLayout.UNIT_STRIDE];
        ByteBuffer pool = ByteBuffer.wrap(poolStorage).order(ByteOrder.LITTLE_ENDIAN);
        int[] identifiers = new int[StarCraftLayout.MAXIMUM_UNITS];
        long[] visited = new long[VISITED_WORDS];

        return () -> {
            context.getMemory().read(context.getModuleBase() + StarCraftLayout.UNIT_POOL_POINTER, metadataStorage, 0, metadataStorage.length);
            long poolAddress = metadata.getLong(0);
            long declaredSlots = metadata.getLong(Long.BYTES);

            if (declaredSlots < 0 || declaredSlots > StarCraftLayout.MAXIMUM_UNITS || (declaredSlots != 0 && poolAddress <= 0)) {
                throw Exceptions.newIOException("Invalid unit list pool metadata: {0}", declaredSlots);
            }

            int poolSlots = (int) declaredSlots;

            context.getMemory().read(context.getModuleBase() + VISIBLE_HEAD_OFFSET, headStorage, 0, headStorage.length);

            if (poolSlots != 0) {
                context.getMemory().read(poolAddress, poolStorage, 0, poolSlots * StarCraftLayout.UNIT_STRIDE);
            }

            int[] visible = traverse(heads.getLong(0), poolAddress, poolSlots, pool, identifiers, visited);
            int[] hidden = traverse(heads.getLong(HIDDEN_HEAD_DISTANCE), poolAddress, poolSlots, pool, identifiers, visited);

            return new UnitListsSample(visible, hidden);
        };
    }
}
