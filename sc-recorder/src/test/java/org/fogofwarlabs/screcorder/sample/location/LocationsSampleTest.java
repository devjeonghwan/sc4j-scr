package org.fogofwarlabs.screcorder.sample.location;

import org.fogofwarlabs.screcorder.capture.MemoryReader;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Checks that the locations sampler captures memory snapshots and that the locations sample validates its payload.
 */
final class LocationsSampleTest {
    private static final int LOCATION_OFFSET = 0xEB70;
    private static final long MODULE_BASE = 0x140000000L;
    private static final long CONTEXT_ADDRESS = 0x200000000L;
    private static final int LOCATION_COUNT = 255;
    private static final int LOCATION_STRIDE = 20;
    private static final int LOCATION_BYTES = LOCATION_COUNT * LOCATION_STRIDE;
    private static final int LOCATION_ROW_BYTES = 1 + LOCATION_STRIDE;
    private static final int LOCATION_LEFT_OFFSET = 0;
    private static final int LOCATION_TOP_OFFSET = 4;
    private static final int LOCATION_RIGHT_OFFSET = 8;
    private static final int LOCATION_BOTTOM_OFFSET = 12;
    private static final int LOCATION_STRING_OFFSET = 16;
    private static final int LOCATION_FLAGS_OFFSET = 18;

    private static final class LocationMemoryScenario implements MemoryReader {
        private final byte @NotNull [] locations;

        LocationMemoryScenario() {
            this.locations = new byte[LOCATION_BYTES];
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
            if (address != CONTEXT_ADDRESS + LOCATION_OFFSET) {
                throw Exceptions.newIOException("Unexpected restored sample address: {0}", address);
            }

            if (length > this.locations.length) {
                throw Exceptions.newIOException("Read exceeds fixture region");
            }

            System.arraycopy(this.locations, 0, destination, offset, length);
        }
    }


    @Test
    void locations_sampler_captures_all_slots_and_keeps_unsigned_edges() throws IOException {
        LocationMemoryScenario scenario = new LocationMemoryScenario();
        ByteBuffer memory = ByteBuffer.wrap(scenario.locations).order(ByteOrder.LITTLE_ENDIAN);

        memory.putInt(LOCATION_LEFT_OFFSET, -1).putInt(LOCATION_TOP_OFFSET, Integer.MIN_VALUE)
              .putInt(LOCATION_RIGHT_OFFSET, 400).putInt(LOCATION_BOTTOM_OFFSET, 200);
        memory.putShort(LOCATION_STRING_OFFSET, (short) 65535).putShort(LOCATION_FLAGS_OFFSET, (short) 0x8000);
        memory.putInt((LOCATION_COUNT - 1) * LOCATION_STRIDE + LOCATION_LEFT_OFFSET, 1234);

        LocationsSampleDefinition definition = new LocationsSampleDefinition();
        Sampler<LocationsSample> sampler = definition.createSampler(new CaptureContext(scenario, MODULE_BASE, CONTEXT_ADDRESS));
        LocationsSample sample = sampler.sample();

        Assertions.assertEquals(255, sample.getLocations().size());

        LocationState first = sample.getLocations().getFirst();

        Assertions.assertEquals(1, first.getIdentifier());
        Assertions.assertEquals(0xFFFFFFFFL, first.getLeft());
        Assertions.assertEquals(0x80000000L, first.getTop());
        Assertions.assertEquals(65535, first.getStringIdentifier());
        Assertions.assertEquals(0x8000, first.getFlags());
        Assertions.assertEquals(255, sample.getLocations().getLast().getIdentifier());
        Assertions.assertEquals(1234, sample.getLocations().getLast().getLeft());

        ByteBuffer output = ByteBuffer.allocate(definition.getEncodedSize(sample));

        definition.getWriter().write(output, sample);
        output.flip();

        LocationsSample decoded = definition.getReader().read(output);

        Assertions.assertEquals(first.getLeft(), decoded.getLocations().getFirst().getLeft());
        Assertions.assertEquals(first.getFlags(), decoded.getLocations().getFirst().getFlags());

        memory.putInt(LOCATION_LEFT_OFFSET, 50);

        Assertions.assertEquals(50, sampler.sample().getLocations().getFirst().getLeft());
        Assertions.assertEquals(0xFFFFFFFFL, first.getLeft());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> sample.getLocations().clear());
    }

    @Test
    void locations_sample_validates_keys_bounds_and_payload_lengths() throws IOException {
        LocationsSampleDefinition definition = new LocationsSampleDefinition();
        LocationState first = new LocationState(1, 0, 0, 0, 0, 0, 0);
        LocationState last = new LocationState(255, 0xFFFFFFFFL, 0, 0, 0, 65535, 65535);
        LocationsSample unordered = new LocationsSample(List.of(last, first));
        ByteBuffer output = ByteBuffer.allocate(definition.getEncodedSize(unordered));

        definition.getWriter().write(output, unordered);
        output.flip();
        Assertions.assertEquals(1, definition.getReader().read(output).getLocations().getFirst().getIdentifier());
        Assertions.assertThrows(IOException.class, () -> definition.getEncodedSize(new LocationsSample(List.of(first, first))));
        Assertions.assertThrows(IOException.class,
                                () -> definition.getEncodedSize(new LocationsSample(List.of(new LocationState(1, 0x100000000L, 0, 0, 0, 0, 0)))));

        ByteBuffer duplicate = ByteBuffer.wrap(output.array().clone());

        duplicate.put(Integer.BYTES + LOCATION_ROW_BYTES, (byte) 1);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(duplicate));

        ByteBuffer truncated = ByteBuffer.wrap(output.array().clone());

        truncated.limit(truncated.limit() - 1);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(truncated));
        Assertions.assertThrows(IOException.class,
                                () -> definition.getWriter().write(ByteBuffer.allocate(output.capacity()).asReadOnlyBuffer(), unordered));
    }
}
