package org.fogofwarlabs.screcorder.sample.eud;

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
import java.util.List;

/**
 * Defines region host values and the first {@code 64} host bytes of flags {@code 0x02} regions.
 */
public final class EudHostsSampleDefinition extends SampleDefinition<EudHostsSample> {
    private static final int SAMPLE_IDENTIFIER = 11;

    private static final int REGION_PREFIX_BYTES = 29;
    private static final int HOST_HEAD_BYTES = 64;
    private static final int MAXIMUM_REGION_BYTES = 97;
    private static final int MAXIMUM_PAYLOAD_BYTES = Integer.BYTES + EudMemory.MAXIMUM_REGIONS * MAXIMUM_REGION_BYTES;

    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;

    private static final int REGION_FLAGS_MASK = 0xFFFF;
    private static final int SERVED_VALUE_FLAGS = 4;
    private static final int SERVED_VALUE_ALTERNATE_FLAGS = 0x24;
    private static final int HOST_HEAD_FLAGS = 2;

    private static final int VALUE_PRESENCE = 1;
    private static final int HEAD_PRESENCE = 2;

    private static @NotNull EudHostsSample read(@NotNull ByteBuffer input) throws IOException {
        input.order(ByteOrder.LITTLE_ENDIAN);

        if (input.remaining() < Integer.BYTES) {
            throw Exceptions.newIOException("Missing emulator host count");
        }

        int count = input.getInt();

        if (count < 0 || count > EudMemory.MAXIMUM_REGIONS || count > input.remaining() / REGION_PREFIX_BYTES) {
            throw Exceptions.newIOException("Invalid emulator host count");
        }

        List<EudHostRegion> regions = new ArrayList<>(count);
        long previousKey = -1;

        for (int index = 0; index < count; index++) {
            if (input.remaining() < REGION_PREFIX_BYTES) {
                throw Exceptions.newIOException("Truncated emulator host");
            }

            long key = Integer.toUnsignedLong(input.getInt());

            if (key <= previousKey) {
                throw Exceptions.newIOException("Emulator host keys are not strictly ascending");
            }

            previousKey = key;

            long type = Integer.toUnsignedLong(input.getInt());
            long host = input.getLong();
            long bound = Integer.toUnsignedLong(input.getInt());
            long base = Integer.toUnsignedLong(input.getInt());
            long size = Integer.toUnsignedLong(input.getInt());
            int presence = Byte.toUnsignedInt(input.get());
            long flags = type & REGION_FLAGS_MASK;
            int expected = isServedValueFlags(flags) ? VALUE_PRESENCE : flags == HOST_HEAD_FLAGS ? HEAD_PRESENCE : 0;

            if (presence != expected ||
                input.remaining() < (presence == VALUE_PRESENCE ? Integer.BYTES : presence == HEAD_PRESENCE ? HOST_HEAD_BYTES : 0)) {
                throw Exceptions.newIOException("Invalid emulator host presence or length");
            }

            Long value = presence == VALUE_PRESENCE ? Integer.toUnsignedLong(input.getInt()) : null;
            byte[] head = presence == HEAD_PRESENCE ? new byte[HOST_HEAD_BYTES] : null;

            if (head != null) {
                input.get(head);
            }

            regions.add(new EudHostRegion(key, type, host, bound, base, size, value, head));
        }

        if (input.hasRemaining()) {
            throw Exceptions.newIOException("Trailing emulator host bytes");
        }

        return new EudHostsSample(regions);
    }

    private static int calculateEncodedSize(@NotNull EudHostsSample sample) throws IOException {
        int length = Integer.BYTES;

        for (EudHostRegion region : sample.getRegions()) {
            if (region.getType() < 0 || region.getType() > MAXIMUM_UNSIGNED_INTEGER ||
                region.getBound() < 0 || region.getBound() > MAXIMUM_UNSIGNED_INTEGER ||
                region.getBase() < 0 || region.getBase() > MAXIMUM_UNSIGNED_INTEGER ||
                region.getSize() < 0 || region.getSize() > MAXIMUM_UNSIGNED_INTEGER) {
                throw Exceptions.newIOException("Emulator host exceeds unsigned DWORD range");
            }

            long flags = region.getType() & REGION_FLAGS_MASK;
            Long value = region.getValue();
            byte[] head = region.getHead();
            boolean expectsValue = isServedValueFlags(flags);
            boolean expectsHead = flags == HOST_HEAD_FLAGS;
            boolean hasValue = value != null;
            boolean hasHead = head != null;

            if (expectsValue != hasValue || expectsHead != hasHead ||
                value != null && (value < 0 || value > MAXIMUM_UNSIGNED_INTEGER) || head != null && head.length != HOST_HEAD_BYTES) {
                throw Exceptions.newIOException("Invalid emulator served value or type-2 head");
            }

            length += REGION_PREFIX_BYTES + (value == null ? 0 : Integer.BYTES) + (head == null ? 0 : HOST_HEAD_BYTES);
        }

        return length;
    }

    private static void write(@NotNull ByteBuffer output, @NotNull EudHostsSample sample) throws IOException {
        if (output.remaining() < calculateEncodedSize(sample) || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient emulator host destination");
        }

        output.order(ByteOrder.LITTLE_ENDIAN);

        output.putInt(sample.getRegions().size());

        for (EudHostRegion region : sample.getRegions()) {
            Long value = region.getValue();
            byte[] head = region.getHead();

            output.putInt((int) region.getKey()).putInt((int) region.getType()).putLong(region.getHost())
                  .putInt((int) region.getBound()).putInt((int) region.getBase()).putInt((int) region.getSize())
                  .put((byte) (value != null ? VALUE_PRESENCE : head != null ? HEAD_PRESENCE : 0));

            if (value != null) {
                output.putInt(value.intValue());
            }

            if (head != null) {
                output.put(head);
            }
        }
    }

    private static boolean isServedValueFlags(long flags) {
        return flags == SERVED_VALUE_FLAGS || flags == SERVED_VALUE_ALTERNATE_FLAGS;
    }


    /**
     * Creates a {@link EudHostsSampleDefinition} instance.
     */
    public EudHostsSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "eud-hosts", EudHostsSample.class, MAXIMUM_PAYLOAD_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<EudHostsSample> getReader() {
        return EudHostsSampleDefinition::read;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<EudHostsSample> getWriter() {
        return EudHostsSampleDefinition::write;
    }


    /**
     * Calculates the exact payload size before reserving reusable output storage.
     *
     * @param sample the sample to encode
     * @return the exact encoded byte count
     * @throws IOException if the sample is invalid
     */
    @Override
    public int getEncodedSize(@NotNull EudHostsSample sample) throws IOException {
        return calculateEncodedSize(sample);
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<EudHostsSample> createSampler(@NotNull CaptureContext context) {
        EudMemory memory = new EudMemory(context);
        byte[] valueStorage = new byte[Integer.BYTES];

        return () -> {
            long emulator = memory.readModeAndEmulator();

            if (emulator == 0) {
                return new EudHostsSample(List.of());
            }

            List<EudMemory.Node> nodes = memory.readNodes(emulator);
            List<EudHostRegion> regions = new ArrayList<>(nodes.size());

            for (EudMemory.Node node : nodes) {
                byte[] region = node.getRegion();
                long type = LittleEndianUtils.readUnsigned32(region, EudMemory.REGION_TYPE_OFFSET);
                long host = LittleEndianUtils.readUnsigned(region, EudMemory.REGION_HOST_OFFSET, Long.BYTES);
                long flags = type & REGION_FLAGS_MASK;
                Long value = null;
                byte[] head = null;

                if (isServedValueFlags(flags)) {
                    context.getMemory().read(host, valueStorage, 0, valueStorage.length);
                    value = LittleEndianUtils.readUnsigned32(valueStorage, 0);
                } else if (flags == HOST_HEAD_FLAGS) {
                    head = EudMemory.normalizePointerWords(memory.readBytes(host, HOST_HEAD_BYTES), context.getModuleBase());
                }

                regions.add(new EudHostRegion(node.getKey(), type, EudMemory.normalizePointer(host, context.getModuleBase()),
                                              LittleEndianUtils.readUnsigned32(region, EudMemory.REGION_BOUND_OFFSET),
                                              LittleEndianUtils.readUnsigned32(region, EudMemory.REGION_BASE_OFFSET),
                                              LittleEndianUtils.readUnsigned32(region, EudMemory.REGION_SIZE_OFFSET),
                                              value, head));
            }

            if (memory.readModeAndEmulator() != emulator) {
                throw Exceptions.newIOException("Emulator changed during host collection");
            }

            return new EudHostsSample(regions);
        };
    }
}
