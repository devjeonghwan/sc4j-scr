package org.fogofwarlabs.screcorder.sample.sync;

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

/**
 * Defines sync scalars and named fixed memory blocks without interpretation of embedded addresses, except the leading arena pointer of the
 * {@link SyncSample#getSpriteVector()} block.
 */
public final class SyncSampleDefinition extends SampleDefinition<SyncSample> {
    private static final int SAMPLE_IDENTIFIER = 14;
    private static final int PAYLOAD_BYTES = 4708;

    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;

    private static final long RANDOM_TOTAL_MODULE_OFFSET = StarCraftLayout.RANDOM_BLOCK + StarCraftLayout.RANDOM_TOTAL_OFFSET;
    private static final long DRAWING_REFRESH_FLAG_OFFSET = 0xE76C88L;
    private static final long VISIBILITY_FOLD_OFFSET = 0xE76C8CL;
    private static final long TURN_LENGTH_OFFSET = 0x1092294L;
    private static final long HEAD_OFFSET = 0x1094320L;
    private static final long STATUS_OFFSET = 0x10953F0L;
    private static final long VISIBILITY_ROWS_OFFSET = 0xE77C90L;
    private static final long SPRITE_VECTOR_OFFSET = 0xE76C70L;
    private static final long VISION_OFFSET = 0x10908E0L;
    private static final long PLAYERS_OFFSET = 0xDD5B50L;
    private static final long TURN_OFFSET = 0x10922A0L;
    private static final long RING_OFFSET = 0x1094330L;

    private static final @NotNull SampleReader<SyncSample> READER = SyncSampleDefinition::read;
    private static final @NotNull SampleWriter<SyncSample> WRITER = SyncSampleDefinition::write;

    private static @NotNull SyncSample read(@NotNull ByteBuffer input) throws IOException {
        if (input.remaining() != PAYLOAD_BYTES) {
            throw Exceptions.newIOException("Invalid sync payload size: {0}", input.remaining());
        }

        input.order(ByteOrder.LITTLE_ENDIAN);

        long randomState = Integer.toUnsignedLong(input.getInt());
        long randomTotal = Integer.toUnsignedLong(input.getInt());
        long drawingRefreshFlag = Integer.toUnsignedLong(input.getInt());
        long visibilityFold = Integer.toUnsignedLong(input.getInt());
        long turnLength = Integer.toUnsignedLong(input.getInt());

        byte[] head = new byte[SyncSample.HEAD_BYTES];

        input.get(head);

        byte[] status = new byte[SyncSample.STATUS_BYTES];

        input.get(status);

        byte[] visibilityRows = new byte[SyncSample.VISIBILITY_ROWS_BYTES];

        input.get(visibilityRows);

        byte[] spriteVector = new byte[SyncSample.SPRITE_VECTOR_BYTES];

        input.get(spriteVector);

        byte[] vision = new byte[SyncSample.VISION_BYTES];

        input.get(vision);

        byte[] players = new byte[SyncSample.PLAYERS_BYTES];

        input.get(players);

        byte[] turn = new byte[SyncSample.TURN_BYTES];

        input.get(turn);

        byte[] ring = new byte[SyncSample.RING_BYTES];

        input.get(ring);

        return new SyncSample(randomState, randomTotal, drawingRefreshFlag, visibilityFold, turnLength,
                              head, status, visibilityRows, spriteVector, vision, players, turn, ring);
    }

    private static void write(@NotNull ByteBuffer output, @NotNull SyncSample sample) throws IOException {
        validate(sample);

        if (output.remaining() < PAYLOAD_BYTES || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable sync buffer");
        }

        output.order(ByteOrder.LITTLE_ENDIAN);

        output.putInt((int) sample.getRandomState());
        output.putInt((int) sample.getRandomTotal());
        output.putInt((int) sample.getDrawingRefreshFlag());
        output.putInt((int) sample.getVisibilityFold());
        output.putInt((int) sample.getTurnLength());
        output.put(sample.getHead());
        output.put(sample.getStatus());
        output.put(sample.getVisibilityRows());
        output.put(sample.getSpriteVector());
        output.put(sample.getVision());
        output.put(sample.getPlayers());
        output.put(sample.getTurn());
        output.put(sample.getRing());
    }

    private static void validate(@NotNull SyncSample sample) throws IOException {
        if (sample.getRandomState() < 0 || sample.getRandomState() > MAXIMUM_UNSIGNED_INTEGER ||
            sample.getRandomTotal() < 0 || sample.getRandomTotal() > MAXIMUM_UNSIGNED_INTEGER ||
            sample.getDrawingRefreshFlag() < 0 || sample.getDrawingRefreshFlag() > MAXIMUM_UNSIGNED_INTEGER ||
            sample.getVisibilityFold() < 0 || sample.getVisibilityFold() > MAXIMUM_UNSIGNED_INTEGER ||
            sample.getTurnLength() < 0 || sample.getTurnLength() > MAXIMUM_UNSIGNED_INTEGER) {
            throw Exceptions.newIOException("Sync scalar exceeds unsigned integer range");
        }
    }

    /**
     * Replaces the game arena pointer at the start of the block by its offset from the unit pool, which lies in the same arena; the arena base moves
     * with every process.
     *
     * @param context the capture session
     * @param block   the block whose first QWORD is normalized
     * @throws IOException if the unit pool pointer cannot be read
     */
    private static void normalizeArenaPointer(@NotNull CaptureContext context, byte @NotNull [] block) throws IOException {
        ByteBuffer values = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
        long pointer = values.getLong(0);

        if (pointer != 0) {
            byte[] poolPointerStorage = new byte[Long.BYTES];

            context.getMemory().read(context.getModuleBase() + StarCraftLayout.UNIT_POOL_POINTER, poolPointerStorage, 0, poolPointerStorage.length);
            values.putLong(0, pointer - ByteBuffer.wrap(poolPointerStorage).order(ByteOrder.LITTLE_ENDIAN).getLong(0));
        }
    }


    /**
     * Creates a {@link SyncSampleDefinition} instance.
     */
    public SyncSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "sync", SyncSample.class, PAYLOAD_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<SyncSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<SyncSample> getWriter() {
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
    public int getEncodedSize(@NotNull SyncSample sample) throws IOException {
        validate(sample);

        return PAYLOAD_BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<SyncSample> createSampler(@NotNull CaptureContext context) {
        byte[] scalarStorage = new byte[Integer.BYTES];
        ByteBuffer scalar = ByteBuffer.wrap(scalarStorage).order(ByteOrder.LITTLE_ENDIAN);
        byte[] head = new byte[SyncSample.HEAD_BYTES];
        byte[] status = new byte[SyncSample.STATUS_BYTES];
        byte[] visibilityRows = new byte[SyncSample.VISIBILITY_ROWS_BYTES];
        byte[] spriteVector = new byte[SyncSample.SPRITE_VECTOR_BYTES];
        byte[] vision = new byte[SyncSample.VISION_BYTES];
        byte[] players = new byte[SyncSample.PLAYERS_BYTES];
        byte[] turn = new byte[SyncSample.TURN_BYTES];
        byte[] ring = new byte[SyncSample.RING_BYTES];

        return () -> {
            context.getMemory().read(context.getModuleBase() + StarCraftLayout.RANDOM_BLOCK, scalarStorage, 0, scalarStorage.length);
            long randomState = Integer.toUnsignedLong(scalar.getInt(0));
            context.getMemory().read(context.getModuleBase() + RANDOM_TOTAL_MODULE_OFFSET, scalarStorage, 0, scalarStorage.length);
            long randomTotal = Integer.toUnsignedLong(scalar.getInt(0));
            context.getMemory().read(context.getModuleBase() + DRAWING_REFRESH_FLAG_OFFSET, scalarStorage, 0, scalarStorage.length);
            long drawingRefreshFlag = Integer.toUnsignedLong(scalar.getInt(0));
            context.getMemory().read(context.getModuleBase() + VISIBILITY_FOLD_OFFSET, scalarStorage, 0, scalarStorage.length);
            long visibilityFold = Integer.toUnsignedLong(scalar.getInt(0));
            context.getMemory().read(context.getModuleBase() + TURN_LENGTH_OFFSET, scalarStorage, 0, scalarStorage.length);
            long turnLength = Integer.toUnsignedLong(scalar.getInt(0));

            context.getMemory().read(context.getModuleBase() + HEAD_OFFSET, head, 0, head.length);
            context.getMemory().read(context.getModuleBase() + STATUS_OFFSET, status, 0, status.length);
            context.getMemory().read(context.getModuleBase() + VISIBILITY_ROWS_OFFSET, visibilityRows, 0, visibilityRows.length);
            context.getMemory().read(context.getModuleBase() + SPRITE_VECTOR_OFFSET, spriteVector, 0, spriteVector.length);
            normalizeArenaPointer(context, spriteVector);
            context.getMemory().read(context.getModuleBase() + VISION_OFFSET, vision, 0, vision.length);
            context.getMemory().read(context.getModuleBase() + PLAYERS_OFFSET, players, 0, players.length);
            context.getMemory().read(context.getModuleBase() + TURN_OFFSET, turn, 0, turn.length);
            context.getMemory().read(context.getModuleBase() + RING_OFFSET, ring, 0, ring.length);

            return new SyncSample(randomState, randomTotal, drawingRefreshFlag, visibilityFold, turnLength,
                                  head, status, visibilityRows, spriteVector, vision, players, turn, ring);
        };
    }
}
