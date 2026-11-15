package org.fogofwarlabs.screcorder.sample.location;

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
 * Defines location rectangle payloads and bulk memory sampling.
 */
public final class LocationsSampleDefinition extends SampleDefinition<LocationsSample> {
    private static final int SAMPLE_IDENTIFIER = 6;
    private static final int LOCATION_COUNT = 255;

    private static final int MAXIMUM_UNSIGNED_SHORT = 0xFFFF;
    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;

    private static final int LOCATION_OFFSET = 0xEB70;
    private static final int LOCATION_STRIDE = 20;
    private static final int LOCATION_LEFT_OFFSET = 0;
    private static final int LOCATION_TOP_OFFSET = Integer.BYTES;
    private static final int LOCATION_RIGHT_OFFSET = 2 * Integer.BYTES;
    private static final int LOCATION_BOTTOM_OFFSET = 3 * Integer.BYTES;
    private static final int LOCATION_STRING_IDENTIFIER_OFFSET = 4 * Integer.BYTES;
    private static final int LOCATION_FLAGS_OFFSET = LOCATION_STRING_IDENTIFIER_OFFSET + Short.BYTES;

    private static final int ROW_BYTES = Byte.BYTES + LOCATION_STRIDE;

    private static final @NotNull SampleReader<LocationsSample> READER = LocationsSampleDefinition::read;
    private static final @NotNull SampleWriter<LocationsSample> WRITER = LocationsSampleDefinition::write;

    private static @NotNull LocationsSample read(@NotNull ByteBuffer input) throws IOException {
        input.order(ByteOrder.LITTLE_ENDIAN);

        if (input.remaining() < Integer.BYTES) {
            throw Exceptions.newIOException("Missing location count");
        }

        int locationCount = input.getInt();

        if (locationCount < 0 || locationCount > LOCATION_COUNT || (long) locationCount * ROW_BYTES != input.remaining()) {
            throw Exceptions.newIOException("Invalid location count or payload size: {0}", locationCount);
        }

        List<LocationState> locations = new ArrayList<>(locationCount);
        int previousIdentifier = 0;

        for (int locationIndex = 0; locationIndex < locationCount; locationIndex++) {
            LocationState location = new LocationState(Byte.toUnsignedInt(input.get()),
                                                       Integer.toUnsignedLong(input.getInt()), Integer.toUnsignedLong(input.getInt()),
                                                       Integer.toUnsignedLong(input.getInt()), Integer.toUnsignedLong(input.getInt()),
                                                       Short.toUnsignedInt(input.getShort()), Short.toUnsignedInt(input.getShort()));

            if (location.getIdentifier() <= previousIdentifier) {
                throw Exceptions.newIOException("Invalid or noncanonical location identifier");
            }

            locations.add(location);
            previousIdentifier = location.getIdentifier();
        }

        return new LocationsSample(locations);
    }

    private static void write(@NotNull ByteBuffer output, @NotNull LocationsSample sample) throws IOException {
        List<LocationState> locations = getCanonicalRows(sample);
        int encodedSize = Integer.BYTES + locations.size() * ROW_BYTES;

        if (output.remaining() < encodedSize || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable location buffer: {0}", encodedSize);
        }

        output.order(ByteOrder.LITTLE_ENDIAN).putInt(locations.size());

        for (LocationState location : locations) {
            output.put((byte) location.getIdentifier());
            output.putInt((int) location.getLeft()).putInt((int) location.getTop());
            output.putInt((int) location.getRight()).putInt((int) location.getBottom());
            output.putShort((short) location.getStringIdentifier()).putShort((short) location.getFlags());
        }
    }

    private static @NotNull List<LocationState> getCanonicalRows(@NotNull LocationsSample sample) throws IOException {
        List<LocationState> locations = sample.getLocations();

        if (locations.size() > LOCATION_COUNT) {
            throw Exceptions.newIOException("Location count exceeds its limit: {0}", locations.size());
        }

        boolean ordered = true;
        int previousIdentifier = 0;

        for (LocationState location : locations) {
            if (location.getIdentifier() < 1 || location.getIdentifier() > LOCATION_COUNT ||
                location.getLeft() < 0 || location.getLeft() > MAXIMUM_UNSIGNED_INTEGER ||
                location.getTop() < 0 || location.getTop() > MAXIMUM_UNSIGNED_INTEGER ||
                location.getRight() < 0 || location.getRight() > MAXIMUM_UNSIGNED_INTEGER ||
                location.getBottom() < 0 || location.getBottom() > MAXIMUM_UNSIGNED_INTEGER ||
                location.getStringIdentifier() < 0 || location.getStringIdentifier() > MAXIMUM_UNSIGNED_SHORT ||
                location.getFlags() < 0 || location.getFlags() > MAXIMUM_UNSIGNED_SHORT) {
                throw Exceptions.newIOException("Invalid location value");
            }

            if (location.getIdentifier() <= previousIdentifier) {
                ordered = false;
            }

            previousIdentifier = location.getIdentifier();
        }

        if (ordered) {
            return locations;
        }

        List<LocationState> sortedLocations = new ArrayList<>(locations);

        sortedLocations.sort((first, second) -> Integer.compare(first.getIdentifier(), second.getIdentifier()));

        for (int locationIndex = 1; locationIndex < sortedLocations.size(); locationIndex++) {
            if (sortedLocations.get(locationIndex - 1).getIdentifier() == sortedLocations.get(locationIndex).getIdentifier()) {
                throw Exceptions.newIOException("Duplicate location identifier");
            }
        }

        return sortedLocations;
    }


    /**
     * Creates a {@link LocationsSampleDefinition} instance.
     */
    public LocationsSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "locations", LocationsSample.class, Integer.BYTES + LOCATION_COUNT * ROW_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<LocationsSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<LocationsSample> getWriter() {
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
    public int getEncodedSize(@NotNull LocationsSample sample) throws IOException {
        return Integer.BYTES + getCanonicalRows(sample).size() * ROW_BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<LocationsSample> createSampler(@NotNull CaptureContext context) {
        byte[] storage = new byte[LOCATION_COUNT * LOCATION_STRIDE];
        ByteBuffer memory = ByteBuffer.wrap(storage).order(ByteOrder.LITTLE_ENDIAN);

        return () -> {
            context.getMemory().read(context.getContextAddress() + LOCATION_OFFSET, storage, 0, storage.length);

            List<LocationState> locations = new ArrayList<>(LOCATION_COUNT);

            for (int locationIndex = 0; locationIndex < LOCATION_COUNT; locationIndex++) {
                int rowOffset = locationIndex * LOCATION_STRIDE;

                locations.add(new LocationState(locationIndex + 1,
                                                Integer.toUnsignedLong(memory.getInt(rowOffset + LOCATION_LEFT_OFFSET)),
                                                Integer.toUnsignedLong(memory.getInt(rowOffset + LOCATION_TOP_OFFSET)),
                                                Integer.toUnsignedLong(memory.getInt(rowOffset + LOCATION_RIGHT_OFFSET)),
                                                Integer.toUnsignedLong(memory.getInt(rowOffset + LOCATION_BOTTOM_OFFSET)),
                                                Short.toUnsignedInt(memory.getShort(rowOffset + LOCATION_STRING_IDENTIFIER_OFFSET)),
                                                Short.toUnsignedInt(memory.getShort(rowOffset + LOCATION_FLAGS_OFFSET))));
            }

            return new LocationsSample(locations);
        };
    }
}
