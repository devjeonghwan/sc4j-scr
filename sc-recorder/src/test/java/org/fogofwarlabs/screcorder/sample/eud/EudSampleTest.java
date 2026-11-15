package org.fogofwarlabs.screcorder.sample.eud;

import org.fogofwarlabs.screcorder.capture.MemoryReader;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Verifies the EUD samplers' traversal of the emulator memory and the canonical payload boundaries.
 */
final class EudSampleTest {
    private static final long MODULE_BASE = 0x10000000L;
    private static final long EMULATOR = 0x20000000L;
    private static final long ROOT = 0x21000000L;
    private static final long LEFT = 0x21000100L;
    private static final long ROOT_REGION = 0x22000000L;
    private static final long LEFT_REGION = 0x22000100L;
    private static final long VALUE = 0x23000000L;
    private static final long HEAD = 0x23000100L;
    private static final int GLOBALS_OFFSET = 0x105423C;
    private static final int OBJECT_BYTES = 144;
    private static final int NODE_BYTES = 48;
    private static final int HOST_HEAD_BYTES = 64;
    private static final int OVERSIZED_REGION_COUNT = 65537;
    private static final int SERVED_VALUE_FLAGS = 0x24;
    private static final long VIRTUAL_TABLE_ADDRESS = 0x140010000L;
    private static final int CLASSIC_REGION_BASE = 0x58A364;
    private static final long CONTEXT_ADDRESS = 0x24000000L;

    private static final int GLOBALS_BYTES = Integer.BYTES + Long.BYTES;
    private static final int GLOBALS_EMULATOR_POINTER_OFFSET = Integer.BYTES;
    private static final int OBJECT_TREE_OFFSET = 8;
    private static final int OBJECT_REGION_COUNT_OFFSET = 40;
    private static final long TREE_ADDRESS = EMULATOR + OBJECT_TREE_OFFSET;
    private static final int NODE_LEFT_OFFSET = 8;
    private static final int NODE_RIGHT_OFFSET = 16;
    private static final int NODE_KEY_OFFSET = 32;
    private static final int NODE_REGION_OFFSET = 40;
    private static final int REGION_BASE_OFFSET = 8;
    private static final int REGION_TYPE_OFFSET = 12;
    private static final int REGION_HOST_OFFSET = 16;
    private static final int REGION_SIZE_OFFSET = 32;
    private static final int REGION_BOUND_OFFSET = 40;
    private static final int FIRST_HOST_KEY_OFFSET = Integer.BYTES;
    private static final int TRUNCATED_EUD_PAYLOAD_BYTES = 18;

    private static final int EUD_MODE = 3;
    private static final int LEFT_KEY = 100;
    private static final int ROOT_KEY = 200;
    private static final int REGION_BOUND = 16;
    private static final int HEAD_LAST_BYTE = 42;
    private static final long OTHER_POINTER_TAG = 0x2000000000000000L;

    private static final class EmulatorMemoryScenario implements MemoryReader {
        private final @NotNull NavigableMap<Long, byte[]> ranges;

        EmulatorMemoryScenario() {
            this.ranges = new TreeMap<>();

            this.ranges.put(MODULE_BASE + GLOBALS_OFFSET, createBuffer(GLOBALS_BYTES).putInt(EUD_MODE).putLong(EMULATOR).array());

            byte[] object = new byte[OBJECT_BYTES];

            createBuffer(object).putLong(OBJECT_TREE_OFFSET, ROOT).putLong(OBJECT_REGION_COUNT_OFFSET, 2);
            this.ranges.put(EMULATOR, object);
            this.ranges.put(ROOT, createNodeMemory(LEFT, ROOT_KEY, ROOT_REGION));
            this.ranges.put(LEFT, createNodeMemory(TREE_ADDRESS, LEFT_KEY, LEFT_REGION));
            this.ranges.put(ROOT_REGION, createRegionMemory(SERVED_VALUE_FLAGS, VALUE));
            this.ranges.put(LEFT_REGION, createRegionMemory(2, HEAD));
            this.ranges.put(VALUE, createBuffer(Integer.BYTES).putInt(0xFEDCBA98).array());

            byte[] head = new byte[HOST_HEAD_BYTES];

            head[HOST_HEAD_BYTES - 1] = HEAD_LAST_BYTE;
            this.ranges.put(HEAD, head);
        }


        /**
         * Reads the entire requested range.
         *
         * @param address     the target address
         * @param destination the destination storage
         * @param offset      the destination offset
         * @param length      the byte count
         * @throws IOException if the entire range cannot be read
         */
        @Override
        public void read(long address, byte @NotNull [] destination, int offset, int length) throws IOException {
            Map.Entry<Long, byte[]> range = this.ranges.floorEntry(address);

            if (range == null || address - range.getKey() + length > range.getValue().length) {
                throw Exceptions.newIOException("Unreadable fixture address: {0}", address);
            }

            System.arraycopy(range.getValue(), (int) (address - range.getKey()), destination, offset, length);
        }

        @NotNull CaptureContext createCaptureContext() {
            return new CaptureContext(this, MODULE_BASE, CONTEXT_ADDRESS);
        }
    }

    private static @NotNull ByteBuffer createBuffer(int length) {
        return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static @NotNull ByteBuffer createBuffer(byte @NotNull [] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte @NotNull [] createNodeMemory(long left, long key, long region) {
        return createBuffer(NODE_BYTES).putLong(NODE_LEFT_OFFSET, left).putLong(NODE_RIGHT_OFFSET, TREE_ADDRESS).putInt(NODE_KEY_OFFSET, (int) key)
                                       .putLong(NODE_REGION_OFFSET, region).array();
    }

    private static byte @NotNull [] createRegionMemory(long type, long host) {
        return createBuffer(NODE_BYTES).putLong(0, VIRTUAL_TABLE_ADDRESS)
                                       .putInt(REGION_BASE_OFFSET, CLASSIC_REGION_BASE).putInt(REGION_TYPE_OFFSET, (int) type)
                                       .putLong(REGION_HOST_OFFSET, host).putInt(REGION_SIZE_OFFSET, HOST_HEAD_BYTES)
                                       .putInt(REGION_BOUND_OFFSET, REGION_BOUND).array();
    }


    @Test
    void eud_samplers_collect_descriptors_raw_nodes_and_host_values() throws IOException {
        EmulatorMemoryScenario scenario = new EmulatorMemoryScenario();
        EudSampleDefinition definition = new EudSampleDefinition();
        EudSample sample = definition.createSampler(scenario.createCaptureContext()).sample();

        Assertions.assertEquals(EUD_MODE, sample.getMode());
        Assertions.assertEquals(OTHER_POINTER_TAG, sample.getEmulator());
        Assertions.assertEquals(OBJECT_BYTES, sample.getObject().length);
        Assertions.assertEquals(NODE_BYTES, Objects.requireNonNull(sample.getRoot()).length);
        Assertions.assertEquals(NODE_BYTES, Objects.requireNonNull(sample.getLeft()).length);
        Assertions.assertEquals(NODE_BYTES, Objects.requireNonNull(sample.getRight()).length);
        Assertions.assertEquals(LEFT_KEY, sample.getRegions().getFirst().getKey());
        Assertions.assertEquals(ROOT_KEY, sample.getRegions().getLast().getKey());
        Assertions.assertEquals(CLASSIC_REGION_BASE, sample.getRegions().getFirst().getBase());
        Assertions.assertEquals(HOST_HEAD_BYTES, sample.getRegions().getFirst().getSize());
        Assertions.assertEquals(REGION_BOUND, sample.getRegions().getFirst().getBound());

        ByteBuffer payload = createBuffer(definition.getEncodedSize(sample));

        definition.getWriter().write(payload, sample);
        Assertions.assertFalse(payload.hasRemaining());
        payload.flip();

        EudSample restored = definition.getReader().read(payload);

        Assertions.assertArrayEquals(sample.getObject(), restored.getObject());
        Assertions.assertArrayEquals(sample.getRoot(), restored.getRoot());
        Assertions.assertEquals(sample.getRegions().getLast().getHost(), restored.getRegions().getLast().getHost());

        EudHostsSampleDefinition hostsDefinition = new EudHostsSampleDefinition();
        EudHostsSample hosts = hostsDefinition.createSampler(scenario.createCaptureContext()).sample();

        Assertions.assertNull(hosts.getRegions().getFirst().getValue());
        Assertions.assertEquals(HEAD_LAST_BYTE, Objects.requireNonNull(hosts.getRegions().getFirst().getHead())[HOST_HEAD_BYTES - 1]);
        Assertions.assertEquals(0xFEDCBA98L, hosts.getRegions().getLast().getValue());
        Assertions.assertNull(hosts.getRegions().getLast().getHead());

        ByteBuffer hostPayload = createBuffer(hostsDefinition.getEncodedSize(hosts));

        hostsDefinition.getWriter().write(hostPayload, hosts);
        Assertions.assertFalse(hostPayload.hasRemaining());
        hostPayload.flip();

        EudHostsSample restoredHosts = hostsDefinition.getReader().read(hostPayload);

        Assertions.assertArrayEquals(hosts.getRegions().getFirst().getHead(), restoredHosts.getRegions().getFirst().getHead());
        Assertions.assertEquals(hosts.getRegions().getLast().getValue(), restoredHosts.getRegions().getLast().getValue());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> hosts.getRegions().clear());
    }

    @Test
    void eud_sample_writers_write_little_endian_and_reject_read_only_destinations() throws IOException {
        EmulatorMemoryScenario scenario = new EmulatorMemoryScenario();
        EudSampleDefinition definition = new EudSampleDefinition();
        EudSample sample = definition.createSampler(scenario.createCaptureContext()).sample();
        ByteBuffer payload = ByteBuffer.allocate(definition.getEncodedSize(sample));

        definition.getWriter().write(payload, sample);
        Assertions.assertArrayEquals(new byte[]{3, 0, 0, 0}, Arrays.copyOf(payload.array(), Integer.BYTES));
        payload.flip().order(ByteOrder.BIG_ENDIAN);

        EudSample restored = definition.getReader().read(payload);

        Assertions.assertEquals(sample.getMode(), restored.getMode());
        Assertions.assertEquals(sample.getEmulator(), restored.getEmulator());
        Assertions.assertEquals(sample.getRegions().getLast().getHost(), restored.getRegions().getLast().getHost());

        ByteBuffer readOnly = ByteBuffer.allocate(definition.getEncodedSize(sample)).asReadOnlyBuffer();

        Assertions.assertThrows(IOException.class, () -> definition.getWriter().write(readOnly, sample));
        Assertions.assertEquals(0, readOnly.position());

        EudHostsSampleDefinition hostsDefinition = new EudHostsSampleDefinition();
        EudHostsSample hosts = hostsDefinition.createSampler(scenario.createCaptureContext()).sample();
        ByteBuffer hostPayload = ByteBuffer.allocate(hostsDefinition.getEncodedSize(hosts));

        hostsDefinition.getWriter().write(hostPayload, hosts);
        Assertions.assertArrayEquals(new byte[]{2, 0, 0, 0}, Arrays.copyOf(hostPayload.array(), Integer.BYTES));
        hostPayload.flip().order(ByteOrder.BIG_ENDIAN);

        EudHostsSample restoredHosts = hostsDefinition.getReader().read(hostPayload);

        Assertions.assertEquals(hosts.getRegions().getLast().getValue(), restoredHosts.getRegions().getLast().getValue());

        ByteBuffer readOnlyHosts = ByteBuffer.allocate(hostsDefinition.getEncodedSize(hosts)).asReadOnlyBuffer();

        Assertions.assertThrows(IOException.class, () -> hostsDefinition.getWriter().write(readOnlyHosts, hosts));
        Assertions.assertEquals(0, readOnlyHosts.position());
    }

    @Test
    void eud_sampler_represents_an_absent_emulator_without_fabricated_object_bytes() throws IOException {
        EmulatorMemoryScenario scenario = new EmulatorMemoryScenario();

        createBuffer(scenario.ranges.get(MODULE_BASE + GLOBALS_OFFSET)).putLong(GLOBALS_EMULATOR_POINTER_OFFSET, 0);

        EudSampleDefinition definition = new EudSampleDefinition();
        EudSample sample = definition.createSampler(scenario.createCaptureContext()).sample();

        Assertions.assertEquals(0, sample.getEmulator());
        Assertions.assertEquals(0, sample.getObject().length);
        Assertions.assertTrue(sample.getRegions().isEmpty());
        Assertions.assertNull(sample.getRoot());

        ByteBuffer payload = createBuffer(definition.getEncodedSize(sample));

        definition.getWriter().write(payload, sample);
        payload.flip();
        Assertions.assertEquals(0, definition.getReader().read(payload).getObject().length);
        Assertions.assertTrue(new EudHostsSampleDefinition().createSampler(scenario.createCaptureContext()).sample().getRegions().isEmpty());
    }

    @Test
    void eud_samplers_reject_cycles_count_mismatches_and_invalid_pointers() {
        EmulatorMemoryScenario cycle = new EmulatorMemoryScenario();

        createBuffer(cycle.ranges.get(ROOT)).putLong(NODE_LEFT_OFFSET, ROOT);
        Assertions.assertThrows(IOException.class, () -> new EudSampleDefinition().createSampler(cycle.createCaptureContext()).sample());

        EmulatorMemoryScenario oversized = new EmulatorMemoryScenario();

        createBuffer(oversized.ranges.get(EMULATOR)).putLong(OBJECT_REGION_COUNT_OFFSET, OVERSIZED_REGION_COUNT);
        Assertions.assertThrows(IOException.class, () -> new EudHostsSampleDefinition().createSampler(oversized.createCaptureContext()).sample());

        EmulatorMemoryScenario countMismatch = new EmulatorMemoryScenario();

        createBuffer(countMismatch.ranges.get(EMULATOR)).putLong(OBJECT_REGION_COUNT_OFFSET, 1);
        Assertions.assertThrows(IOException.class, () -> new EudSampleDefinition().createSampler(countMismatch.createCaptureContext()).sample());

        EmulatorMemoryScenario nullObject = new EmulatorMemoryScenario();

        createBuffer(nullObject.ranges.get(ROOT)).putLong(NODE_REGION_OFFSET, 0);
        Assertions.assertThrows(IOException.class, () -> new EudHostsSampleDefinition().createSampler(nullObject.createCaptureContext()).sample());
    }

    @Test
    void eud_sample_reader_rejects_noncanonical_keys_and_truncated_payloads() throws IOException {
        EudHostsSampleDefinition definition = new EudHostsSampleDefinition();
        EudHostsSample sample = definition.createSampler(new EmulatorMemoryScenario().createCaptureContext()).sample();
        ByteBuffer payload = createBuffer(definition.getEncodedSize(sample));

        definition.getWriter().write(payload, sample);
        payload.putInt(FIRST_HOST_KEY_OFFSET, 200);
        payload.flip();
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(payload));
        Assertions.assertThrows(IOException.class, () -> new EudSampleDefinition().getReader().read(createBuffer(TRUNCATED_EUD_PAYLOAD_BYTES)));

        ByteBuffer valid = createBuffer(definition.getEncodedSize(sample));

        definition.getWriter().write(valid, sample);
        valid.flip();
        valid.limit(valid.limit() - 1);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(valid));
    }
}
