package org.fogofwarlabs.screcorder.sample.eud;

import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.io.SampleReader;
import org.fogofwarlabs.screcorder.sample.io.SampleWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines the emulator descriptor tree and native object payload.
 */
public final class EudSampleDefinition extends SampleDefinition<EudSample> {
    private static final int SAMPLE_IDENTIFIER = 10;

    private static final int REGION_PAYLOAD_BYTES = 36;
    private static final int PREFIX_BYTES = 19;
    private static final int NODE_FLAGS_AND_COUNT_BYTES = 7;
    private static final int MAXIMUM_PREFIX_BYTES = 307;
    private static final int MAXIMUM_PAYLOAD_BYTES = MAXIMUM_PREFIX_BYTES + EudMemory.MAXIMUM_REGIONS * REGION_PAYLOAD_BYTES;

    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;

    private static final int ROOT_NODE_OFFSET = 8;

    private static @NotNull EudSample read(@NotNull ByteBuffer input) throws IOException {
        input.order(ByteOrder.LITTLE_ENDIAN);

        if (input.remaining() < PREFIX_BYTES) {
            throw Exceptions.newIOException("Truncated emulator payload");
        }

        long mode = Integer.toUnsignedLong(input.getInt());
        long emulator = input.getLong();
        byte[] object = new byte[emulator == 0 ? 0 : EudSample.OBJECT_BYTES];

        if (input.remaining() < object.length + NODE_FLAGS_AND_COUNT_BYTES) {
            throw Exceptions.newIOException("Truncated emulator object");
        }

        input.get(object);

        byte[] root = readNode(input);
        byte[] left = readNode(input);
        byte[] right = readNode(input);

        if (input.remaining() < Integer.BYTES) {
            throw Exceptions.newIOException("Missing emulator region count");
        }

        int count = input.getInt();

        if (count < 0 || count > EudMemory.MAXIMUM_REGIONS || input.remaining() != count * REGION_PAYLOAD_BYTES) {
            throw Exceptions.newIOException("Invalid emulator descriptor count or length");
        }

        List<EudRegion> regions = new ArrayList<>(count);
        long previousKey = -1;

        for (int index = 0; index < count; index++) {
            long key = Integer.toUnsignedLong(input.getInt());

            if (key <= previousKey) {
                throw Exceptions.newIOException("Emulator region keys are not strictly ascending");
            }

            previousKey = key;

            regions.add(new EudRegion(key, Integer.toUnsignedLong(input.getInt()), Integer.toUnsignedLong(input.getInt()),
                                      Integer.toUnsignedLong(input.getInt()), input.getLong(), input.getLong(),
                                      Integer.toUnsignedLong(input.getInt())));
        }

        if (emulator == 0 && (root != null || left != null || right != null || !regions.isEmpty())) {
            throw Exceptions.newIOException("Absent emulator has stored native data");
        }

        return new EudSample(mode, emulator, object, root, left, right, regions);
    }

    private static byte @Nullable [] readNode(@NotNull ByteBuffer input) throws IOException {
        if (!input.hasRemaining()) {
            throw Exceptions.newIOException("Missing emulator node presence");
        }

        int presence = Byte.toUnsignedInt(input.get());

        if (presence == 0) {
            return null;
        }

        if (presence != 1 || input.remaining() < EudMemory.NODE_BYTES) {
            throw Exceptions.newIOException("Invalid emulator raw node");
        }

        byte[] node = new byte[EudMemory.NODE_BYTES];

        input.get(node);

        return node;
    }

    private static void writeNode(@NotNull ByteBuffer output, byte @Nullable [] node) {
        output.put((byte) (node == null ? 0 : 1));

        if (node != null) {
            output.put(node);
        }
    }

    private static void requireUnsigned(long value) throws IOException {
        if (value < 0 || value > MAXIMUM_UNSIGNED_INTEGER) {
            throw Exceptions.newIOException("Emulator descriptor exceeds unsigned DWORD range");
        }
    }

    private static void write(@NotNull ByteBuffer output, @NotNull EudSample sample) throws IOException {
        int required = calculateEncodedSize(sample);

        if (output.remaining() < required || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient emulator destination");
        }

        output.order(ByteOrder.LITTLE_ENDIAN);

        output.putInt((int) sample.getMode()).putLong(sample.getEmulator()).put(sample.getObject());
        writeNode(output, sample.getRoot());
        writeNode(output, sample.getLeft());
        writeNode(output, sample.getRight());
        output.putInt(sample.getRegions().size());

        for (EudRegion region : sample.getRegions()) {
            output.putInt((int) region.getKey()).putInt((int) region.getBase()).putInt((int) region.getSize())
                  .putInt((int) region.getType()).putLong(region.getVirtualTable()).putLong(region.getHost())
                  .putInt((int) region.getBound());
        }
    }

    private static int calculateEncodedSize(@NotNull EudSample sample) throws IOException {
        for (EudRegion region : sample.getRegions()) {
            requireUnsigned(region.getBase());
            requireUnsigned(region.getSize());
            requireUnsigned(region.getType());
            requireUnsigned(region.getBound());
        }

        return PREFIX_BYTES + sample.getObject().length +
               (sample.getRoot() == null ? 0 : EudMemory.NODE_BYTES) +
               (sample.getLeft() == null ? 0 : EudMemory.NODE_BYTES) +
               (sample.getRight() == null ? 0 : EudMemory.NODE_BYTES) + sample.getRegions().size() * REGION_PAYLOAD_BYTES;
    }


    /**
     * Creates a {@link EudSampleDefinition} instance.
     */
    public EudSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "eud", EudSample.class, MAXIMUM_PAYLOAD_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<EudSample> getReader() {
        return EudSampleDefinition::read;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<EudSample> getWriter() {
        return EudSampleDefinition::write;
    }


    /**
     * Calculates the exact payload size before reserving reusable output storage.
     *
     * @param sample the sample to encode
     * @return the exact encoded byte count
     * @throws IOException if the sample is invalid
     */
    @Override
    public int getEncodedSize(@NotNull EudSample sample) throws IOException {
        return calculateEncodedSize(sample);
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<EudSample> createSampler(@NotNull CaptureContext context) {
        EudMemory memory = new EudMemory(context);
        long moduleBase = context.getModuleBase();

        return () -> {
            long emulator = memory.readModeAndEmulator();
            long mode = memory.getMode();

            if (emulator == 0) {
                return new EudSample(mode, 0, new byte[0], null, null, null, List.of());
            }

            byte[] object = memory.readBytes(emulator, EudSample.OBJECT_BYTES);
            List<EudMemory.Node> nodes = memory.readNodes(emulator);
            long rootAddress = LittleEndianUtils.readUnsigned(object, ROOT_NODE_OFFSET, Long.BYTES);
            byte[] root = rootAddress == 0 ? null : memory.readBytes(rootAddress, EudMemory.NODE_BYTES);
            long leftAddress = root == null ? 0 : LittleEndianUtils.readUnsigned(root, EudMemory.LEFT_NODE_OFFSET, Long.BYTES);
            long rightAddress = root == null ? 0 : LittleEndianUtils.readUnsigned(root, EudMemory.RIGHT_NODE_OFFSET, Long.BYTES);
            byte[] left = leftAddress == 0 ? null : memory.readBytes(leftAddress, EudMemory.NODE_BYTES);
            byte[] right = rightAddress == 0 ? null : memory.readBytes(rightAddress, EudMemory.NODE_BYTES);
            List<EudRegion> regions = new ArrayList<>(nodes.size());

            for (EudMemory.Node node : nodes) {
                byte[] region = node.getRegion();

                regions.add(new EudRegion(node.getKey(), LittleEndianUtils.readUnsigned32(region, EudMemory.REGION_BASE_OFFSET),
                                          LittleEndianUtils.readUnsigned32(region, EudMemory.REGION_SIZE_OFFSET),
                                          LittleEndianUtils.readUnsigned32(region, EudMemory.REGION_TYPE_OFFSET),
                                          EudMemory.normalizePointer(LittleEndianUtils.readUnsigned(region, 0, Long.BYTES), moduleBase),
                                          EudMemory.normalizePointer(LittleEndianUtils.readUnsigned(region, EudMemory.REGION_HOST_OFFSET, Long.BYTES),
                                                                     moduleBase),
                                          LittleEndianUtils.readUnsigned32(region, EudMemory.REGION_BOUND_OFFSET)));
            }

            if (memory.readModeAndEmulator() != emulator || memory.getMode() != mode) {
                throw Exceptions.newIOException("Emulator changed during descriptor collection");
            }

            return new EudSample(mode, EudMemory.normalizePointer(emulator, moduleBase), EudMemory.normalizePointerWords(object, moduleBase),
                                 root == null ? null : EudMemory.normalizePointerWords(root, moduleBase),
                                 left == null ? null : EudMemory.normalizePointerWords(left, moduleBase),
                                 right == null ? null : EudMemory.normalizePointerWords(right, moduleBase), regions);
        };
    }
}
