package org.fogofwarlabs.screcorder.format.recording;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.format.recording.metadata.RecordMetadata;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.SampleDefinitions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32C;

final class RecordFormatUtils {
    static final int FIXED_HEADER_BYTES = 32;
    static final int HEADER_CHECKSUM_OFFSET = FIXED_HEADER_BYTES - Integer.BYTES;
    static final int MAXIMUM_HEADER_BYTES = 1024 * 1024;
    static final int CHUNK_HEADER_BYTES = 24;
    static final int CHUNK_CHECKSUM_OFFSET = 16;
    static final int CHUNK_RESERVED_OFFSET = 20;
    static final int MISSING_RANGE_BYTES = Long.BYTES * 2;
    static final int FRAME_PREFIX_BYTES = 24;
    static final int SAMPLE_HEADER_BYTES = 18;
    static final int SUMMARY_PREFIX_BYTES = 64;
    static final int SUMMARY_REQUIREMENTS_SATISFIED_FLAG = 1;
    static final int MAXIMUM_CHUNK_BYTES = 64 * 1024 * 1024;
    static final int MAXIMUM_SELECTED_SAMPLES = 4096;
    static final int DIGEST_BYTES = 32;

    static final short FORMAT_MAJOR_VERSION = 0;
    static final short FORMAT_MINOR_VERSION = 1;
    static final short FRAME_KIND = 1;
    static final short END_KIND = 2;

    private static final int BUFFER_GROWTH_FACTOR = 2;
    private static final byte @NotNull [] MAGIC = "SCRECBIN".getBytes(StandardCharsets.US_ASCII);

    static @NotNull List<SampleDefinition<?>> validateDefinitions(@NotNull List<SampleDefinition<?>> definitions) throws IOException {
        if (definitions.isEmpty() || definitions.size() > MAXIMUM_SELECTED_SAMPLES) {
            throw Exceptions.newIOException("Selected sample count must be between 1 and {0}", MAXIMUM_SELECTED_SAMPLES);
        }

        List<SampleDefinition<?>> selected = new ArrayList<>(definitions);

        selected.sort(Comparator.comparingInt(SampleDefinition::getIdentifier));

        int previousIdentifier = -1;

        for (SampleDefinition<?> definition : selected) {
            if (definition.getIdentifier() <= previousIdentifier || SampleDefinitions.findByIdentifier(definition.getIdentifier()) != definition) {
                throw Exceptions.newIOException("Sample definitions must be unique canonical definitions");
            }

            previousIdentifier = definition.getIdentifier();
        }

        return List.copyOf(selected);
    }

    static byte @NotNull [] encodeHeader(@NotNull Record record) throws IOException {
        byte[] metadata = record.getMetadata().encode();
        long headerBytes = FIXED_HEADER_BYTES + (long) Integer.BYTES * record.getDefinitions().size() + metadata.length;

        if (headerBytes > MAXIMUM_HEADER_BYTES) {
            throw Exceptions.newIOException("Record header exceeds the format limit");
        }

        ByteBuffer output = ByteBuffer.allocate((int) headerBytes).order(ByteOrder.LITTLE_ENDIAN);

        output.put(MAGIC).putShort(FORMAT_MAJOR_VERSION).putShort(FORMAT_MINOR_VERSION).putInt((int) headerBytes);
        output.putInt(0).putInt(record.getDefinitions().size()).putInt(metadata.length).putInt(0);

        for (SampleDefinition<?> definition : record.getDefinitions()) {
            output.putInt(definition.getIdentifier());
        }

        output.put(metadata);

        CRC32C checksum = new CRC32C();

        checksum.update(output.array());
        output.putInt(HEADER_CHECKSUM_OFFSET, (int) checksum.getValue());

        return output.array();
    }

    static @NotNull Record readHeader(@NotNull InputStream input) throws IOException {
        byte[] fixedHeader = new byte[FIXED_HEADER_BYTES];

        readFully(input, fixedHeader, 0, fixedHeader.length);

        ByteBuffer fixed = ByteBuffer.wrap(fixedHeader).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[MAGIC.length];

        fixed.get(magic);

        if (!Arrays.equals(magic, MAGIC) || fixed.getShort() != FORMAT_MAJOR_VERSION || fixed.getShort() != FORMAT_MINOR_VERSION) {
            throw Exceptions.newIOException("Unsupported recording magic or version");
        }

        int totalHeaderBytes = fixed.getInt();
        int features = fixed.getInt();
        int sampleCount = fixed.getInt();
        int metadataBytes = fixed.getInt();
        int expectedChecksum = fixed.getInt();

        if (totalHeaderBytes < FIXED_HEADER_BYTES || totalHeaderBytes > MAXIMUM_HEADER_BYTES || features != 0 ||
            sampleCount < 1 || sampleCount > MAXIMUM_SELECTED_SAMPLES || metadataBytes < 0 ||
            FIXED_HEADER_BYTES + (long) Integer.BYTES * sampleCount + metadataBytes != totalHeaderBytes) {
            throw Exceptions.newIOException("Invalid recording header bounds or flags");
        }

        byte[] bytes = Arrays.copyOf(fixedHeader, totalHeaderBytes);

        readFully(input, bytes, FIXED_HEADER_BYTES, totalHeaderBytes - FIXED_HEADER_BYTES);

        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        header.putInt(HEADER_CHECKSUM_OFFSET, 0);

        CRC32C checksum = new CRC32C();

        checksum.update(bytes);

        if ((int) checksum.getValue() != expectedChecksum) {
            throw Exceptions.newIOException("Recording header checksum mismatch");
        }

        header.position(FIXED_HEADER_BYTES);

        List<SampleDefinition<?>> definitions = new ArrayList<>(sampleCount);
        int previousIdentifier = -1;

        for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
            int identifier = header.getInt();

            if (identifier <= previousIdentifier) {
                throw Exceptions.newIOException("Sample identifiers must be unique and ordered");
            }

            SampleDefinition<?> definition = SampleDefinitions.findByIdentifier(identifier);

            if (definition == null) {
                throw Exceptions.newIOException("Unsupported sample identifier: {0}", identifier);
            }

            definitions.add(definition);
            previousIdentifier = identifier;
        }

        return new Record(definitions, RecordMetadata.decode(header.slice().order(ByteOrder.LITTLE_ENDIAN)));
    }

    /**
     * Reads the encoded size of a sample's values and checks it against the definition's payload bound and the chunk size.
     *
     * @param definition    the sample definition
     * @param sample        the sample values
     * @param <SampleValue> the sample type
     * @return the encoded size of the values in bytes
     * @throws IOException if the size is negative or exceeds the definition's bound or the chunk size
     */
    static <SampleValue extends Sample> int getValueBytes(@NotNull SampleDefinition<SampleValue> definition, @NotNull SampleValue sample) throws IOException {
        int valueBytes = definition.getEncodedSize(sample);

        if (valueBytes < 0 || valueBytes > definition.getMaximumPayloadBytes() || valueBytes > MAXIMUM_CHUNK_BYTES) {
            throw Exceptions.newIOException("Sample payload exceeds its supported bound");
        }

        return valueBytes;
    }

    /**
     * Writes the values of a sample with its definition's writer and checks that it wrote the declared size.
     *
     * @param definition    the sample definition
     * @param sample        the sample values
     * @param values        the destination, positioned at the start of the values
     * @param valueBytes    the declared encoded size ({@link #getValueBytes})
     * @param <SampleValue> the sample type
     * @throws IOException if the writer fails or writes another size
     */
    static <SampleValue extends Sample> void writeValues(@NotNull SampleDefinition<SampleValue> definition, @NotNull SampleValue sample,
                                                         @NotNull ByteBuffer values, int valueBytes) throws IOException {
        definition.getWriter().write(values, sample);

        if (values.position() != valueBytes) {
            throw Exceptions.newIOException("Sample writer did not write its declared payload size");
        }
    }

    /**
     * Calculates the checksum of a chunk: the CRC-32C of its header without the checksum field, then of its payload.
     *
     * @param checksum     the reused checksum, reset first
     * @param chunkHeader  the chunk header bytes
     * @param payload      the payload bytes
     * @param payloadBytes the payload length
     * @return the chunk checksum
     */
    static int calculateChunkChecksum(@NotNull CRC32C checksum, byte @NotNull [] chunkHeader, byte @NotNull [] payload, int payloadBytes) {
        checksum.reset();
        checksum.update(chunkHeader, 0, CHUNK_CHECKSUM_OFFSET);
        checksum.update(chunkHeader, CHUNK_RESERVED_OFFSET, Integer.BYTES);
        checksum.update(payload, 0, payloadBytes);

        return (int) checksum.getValue();
    }

    static void readFully(@NotNull InputStream input, byte @NotNull [] destination, int offset, int length) throws IOException {
        int consumed = 0;

        while (consumed < length) {
            int count = input.read(destination, offset + consumed, length - consumed);

            if (count < 0) {
                throw Exceptions.newIOException("Truncated recording: expected {0} more bytes", length - consumed);
            }

            if (count == 0) {
                int value = input.read();

                if (value < 0) {
                    throw Exceptions.newIOException("Truncated recording");
                }

                destination[offset + consumed] = (byte) value;
                consumed++;
            } else {
                consumed += count;
            }
        }
    }

    static byte @NotNull [] growBuffer(byte @NotNull [] buffer, int requiredBytes) throws IOException {
        if (requiredBytes < 0 || requiredBytes > MAXIMUM_CHUNK_BYTES) {
            throw Exceptions.newIOException("Frame exceeds the maximum chunk size");
        }

        if (buffer.length >= requiredBytes) {
            return buffer;
        }

        int grown = (int) Math.min(MAXIMUM_CHUNK_BYTES, Math.max(requiredBytes, (long) buffer.length * BUFFER_GROWTH_FACTOR));

        return Arrays.copyOf(buffer, grown);
    }


    private RecordFormatUtils() {
    }
}
