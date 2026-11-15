package org.fogofwarlabs.screcorder.format.recording;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.format.recording.metadata.LoadedReplayEvidence;
import org.fogofwarlabs.screcorder.format.recording.metadata.RecordMetadata;
import org.fogofwarlabs.screcorder.format.recording.metadata.SamplingMethod;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleObservation;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleStatus;
import org.fogofwarlabs.screcorder.format.recording.observation.StorageMode;
import org.fogofwarlabs.screcorder.format.recording.summary.RecordSummary;
import org.fogofwarlabs.screcorder.format.recording.summary.Termination;
import org.fogofwarlabs.screcorder.sample.SampleDefinitions;
import org.fogofwarlabs.screcorder.sample.player.PlayerState;
import org.fogofwarlabs.screcorder.sample.player.PlayersSample;
import org.fogofwarlabs.screcorder.sample.random.RandomSample;
import org.fogofwarlabs.screcorder.sample.unit.UnitsSample;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32C;

/**
 * Checks that recordings round-trip through the binary format and that the reader rejects truncated, corrupt and forged files.
 * <p>
 * The byte offsets below pin the documented binary layout, because the layout itself is the contract of the format.
 */
final class RecordBinaryTest {
    private static final int HEADER_MAJOR_VERSION_OFFSET = 8;
    private static final int HEADER_MINOR_VERSION_OFFSET = 10;
    private static final int HEADER_LENGTH_OFFSET = 12;
    private static final int SELECTED_SAMPLE_COUNT_OFFSET = 20;
    private static final int METADATA_LENGTH_OFFSET = 24;
    private static final int HEADER_CHECKSUM_OFFSET = 28;
    private static final int FIXED_HEADER_BYTES = 32;
    private static final int FIRST_IDENTIFIER_OFFSET = 32;
    private static final int SECOND_IDENTIFIER_OFFSET = 36;
    private static final int METADATA_ENTRY_HEADER_BYTES = 8;

    private static final int PLAYERS_SAMPLE_IDENTIFIER = 1;
    private static final int RANDOM_SAMPLE_IDENTIFIER = 3;
    private static final int CANONICAL_DIGEST_MAJOR_VERSION = 0;
    private static final int CANONICAL_DIGEST_MINOR_VERSION = 1;

    private static final int CHUNK_FLAGS_OFFSET = 2;
    private static final int CHUNK_PAYLOAD_LENGTH_OFFSET = 4;
    private static final int CHUNK_SEQUENCE_OFFSET = 8;
    private static final int CHUNK_CHECKSUM_OFFSET = 16;
    private static final int CHUNK_RESERVED_OFFSET = 20;
    private static final int CHUNK_HEADER_BYTES = 24;
    private static final int FRAME_PREFIX_BYTES = 24;

    private static final int SAMPLE_STORAGE_MODE_OFFSET = 4;
    private static final int SAMPLE_STATUS_OFFSET = 5;
    private static final int SAMPLE_FRAME_OFFSET = 6;
    private static final int SAMPLE_PAYLOAD_LENGTH_OFFSET = 14;
    private static final int SAMPLE_HEADER_BYTES = 18;

    private static final int SUMMARY_QUALITY_FLAGS_OFFSET = 28;
    private static final int SUMMARY_MISSING_FRAME_COUNT_OFFSET = 32;
    private static final int SUMMARY_RESERVED_OFFSET = 60;

    private static final int DIGEST_BYTES = 32;
    private static final int SHORT_DIGEST_LENGTH = 31;
    private static final int CANONICAL_DIGEST_INPUT_BYTES = 31;
    private static final int UNSUPPORTED_MINOR_VERSION = 2;
    private static final int UNKNOWN_METADATA_TAG = 123;
    private static final int PRODUCER_VERSION_TAG = 1;
    private static final int COVERAGE_REQUIREMENTS_TAG = 7;
    private static final int OPTIONAL_METADATA_FLAGS = 0;
    private static final int REQUIRED_METADATA_FLAGS = 1;
    private static final int SAMPLE_CORRUPTION_MASK = 64;
    private static final int PARTIAL_WRITE_BYTES = 3;

    private static @NotNull PlayersSample createPlayers(int minerals) {
        return new PlayersSample(List.of(new PlayerState(0, minerals, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)));
    }

    private static byte @NotNull [] createSingleRandomRecord(@NotNull StorageMode storageMode) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM), new RecordMetadata.Builder().build(), storageMode)) {
            writer.beginFrame(0, 0, CaptureConsistency.MARKER_STABLE);
            writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1));
            writer.endFrame();
            writer.finish(Termination.NORMAL);
        }

        return output.toByteArray();
    }

    private static void readAllFrames(byte @NotNull [] bytes) throws IOException {
        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(bytes))) {
            while (reader.readNextFrame() != null) {
                Assertions.assertNull(reader.getSummary());
            }
        }
    }

    private static int getHeaderBytes(byte @NotNull [] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(HEADER_LENGTH_OFFSET);
    }

    private static int getNextChunkOffset(byte @NotNull [] bytes, int chunkStart) {
        int payloadBytes = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(chunkStart + CHUNK_PAYLOAD_LENGTH_OFFSET);

        return chunkStart + CHUNK_HEADER_BYTES + payloadBytes;
    }

    private static void refreshHeaderChecksum(byte @NotNull [] bytes) {
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        header.putInt(HEADER_CHECKSUM_OFFSET, 0);

        CRC32C checksum = new CRC32C();

        checksum.update(bytes, 0, getHeaderBytes(bytes));
        header.putInt(HEADER_CHECKSUM_OFFSET, (int) checksum.getValue());
    }

    private static void refreshChunkChecksum(byte @NotNull [] bytes, int chunkStart) {
        ByteBuffer content = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int payloadBytes = content.getInt(chunkStart + CHUNK_PAYLOAD_LENGTH_OFFSET);
        CRC32C checksum = new CRC32C();

        checksum.update(bytes, chunkStart, CHUNK_CHECKSUM_OFFSET);
        checksum.update(bytes, chunkStart + CHUNK_RESERVED_OFFSET, Integer.BYTES);
        checksum.update(bytes, chunkStart + CHUNK_HEADER_BYTES, payloadBytes);
        content.putInt(chunkStart + CHUNK_CHECKSUM_OFFSET, (int) checksum.getValue());
    }

    private static byte @NotNull [] appendMetadata(byte @NotNull [] bytes, int tag, int flags, byte @NotNull [] payload) {
        int headerLength = getHeaderBytes(bytes);
        int extraBytes = METADATA_ENTRY_HEADER_BYTES + payload.length;
        byte[] expanded = new byte[bytes.length + extraBytes];

        System.arraycopy(bytes, 0, expanded, 0, headerLength);
        System.arraycopy(bytes, headerLength, expanded, headerLength + extraBytes, bytes.length - headerLength);

        ByteBuffer content = ByteBuffer.wrap(expanded).order(ByteOrder.LITTLE_ENDIAN);

        content.putInt(HEADER_LENGTH_OFFSET, headerLength + extraBytes)
               .putInt(METADATA_LENGTH_OFFSET, content.getInt(METADATA_LENGTH_OFFSET) + extraBytes);
        content.position(headerLength);
        content.putShort((short) tag).putShort((short) flags).putInt(payload.length).put(payload);
        refreshHeaderChecksum(expanded);

        return expanded;
    }


    @Test
    void reader_reads_typed_frames_that_stay_owned_after_next_read_and_closure() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] executableDigest = new byte[DIGEST_BYTES];

        executableDigest[0] = 7;

        RecordMetadata metadata = new RecordMetadata.Builder()
                .setProducerVersion("recorder-test")
                .setExecutableDigest(executableDigest).setRequestedReplayDigest(new byte[DIGEST_BYTES])
                .setLoadedReplayEvidence(LoadedReplayEvidence.HEADER_MATCHED)
                .setSamplingMethod(SamplingMethod.EXTERNAL_MARKER_POLLING).setPollingIntervalNanoseconds(100)
                .setExpectedFrames(0, 1)
                .setCoverageRequirements(RecordMetadata.REQUIRE_NO_GAPS | RecordMetadata.REQUIRE_AVAILABLE_SAMPLES |
                                         RecordMetadata.REQUIRE_MARKER_STABLE | RecordMetadata.REQUIRE_EXPECTED_BOUNDARIES)
                .build();

        executableDigest[0] = 99;

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.UNITS, SampleDefinitions.PLAYERS),
                                                    metadata, StorageMode.VALUES)) {
            writer.beginFrame(0, 5, CaptureConsistency.MARKER_STABLE);
            writer.append(SampleDefinitions.UNITS, new UnitsSample(List.of()));
            writer.append(SampleDefinitions.PLAYERS, createPlayers(50));
            writer.endFrame();
            writer.beginFrame(1, 5, CaptureConsistency.MARKER_STABLE);
            writer.append(SampleDefinitions.PLAYERS, createPlayers(75));
            writer.append(SampleDefinitions.UNITS, new UnitsSample(List.of()));
            writer.endFrame();

            Assertions.assertTrue(writer.finish(Termination.NORMAL).isRequirementsSatisfied());
        }

        Frame retained;

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Assertions.assertEquals(List.of(SampleDefinitions.PLAYERS, SampleDefinitions.UNITS), reader.getRecord().getDefinitions());

            byte[] recordedExecutableDigest = reader.getRecord().getMetadata().getExecutableDigest();

            Assertions.assertNotNull(recordedExecutableDigest);
            Assertions.assertEquals(7, recordedExecutableDigest[0]);
            Assertions.assertEquals("recorder-test", reader.getRecord().getMetadata().getProducerVersion());
            Assertions.assertEquals(100, reader.getRecord().getMetadata().getPollingIntervalNanoseconds());
            Assertions.assertEquals(LoadedReplayEvidence.HEADER_MATCHED, reader.getRecord().getMetadata().getLoadedReplayEvidence());
            Assertions.assertNull(reader.getSummary());

            retained = reader.readNextFrame();

            Assertions.assertNotNull(retained);
            Assertions.assertEquals(50, retained.getSample(PlayersSample.class).getPlayers().getFirst().getMinerals());
            Assertions.assertTrue(retained.getSample(UnitsSample.class).getUnits().isEmpty());

            Frame next = reader.readNextFrame();

            Assertions.assertNotNull(next);
            Assertions.assertEquals(75, next.getSample(PlayersSample.class).getPlayers().getFirst().getMinerals());
            Assertions.assertNull(reader.readNextFrame());
            Assertions.assertNull(reader.readNextFrame());
            Assertions.assertNotNull(reader.getSummary());
            Assertions.assertTrue(reader.getSummary().isRequirementsSatisfied());
            Assertions.assertEquals(2, reader.getSummary().getFrameCount());
        }

        Assertions.assertEquals(50, retained.getSample(PlayersSample.class).getPlayers().getFirst().getMinerals());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> retained.getSample(PlayersSample.class).getPlayers().clear());
        Assertions.assertThrows(IllegalStateException.class, () -> retained.getSample(RandomSample.class));
    }

    @Test
    void digest_storage_matches_independent_canonical_digest_bytes() throws IOException, NoSuchAlgorithmException {
        byte[] file = createSingleRandomRecord(StorageMode.DIGEST);
        ByteBuffer canonical = ByteBuffer.allocate(CANONICAL_DIGEST_INPUT_BYTES).order(ByteOrder.LITTLE_ENDIAN);

        canonical.put("SCREC-STATE".getBytes(StandardCharsets.US_ASCII));
        canonical.putShort((short) CANONICAL_DIGEST_MAJOR_VERSION).putShort((short) CANONICAL_DIGEST_MINOR_VERSION)
                 .putInt(RANDOM_SAMPLE_IDENTIFIER).putInt(23).putInt(7).putInt(1);

        byte[] expected = MessageDigest.getInstance("SHA-256").digest(canonical.array());

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(file))) {
            Frame frame = reader.readNextFrame();

            Assertions.assertNotNull(frame);

            SampleObservation observation = frame.getSampleObservation(RandomSample.class);

            Assertions.assertEquals(StorageMode.DIGEST, observation.getStorageMode());
            Assertions.assertEquals(SampleStatus.AVAILABLE, observation.getStatus());
            Assertions.assertEquals(0, observation.getSampledFrame());
            Assertions.assertArrayEquals(expected, observation.getDigest());

            byte[] detached = observation.getDigest();

            Assertions.assertNotNull(detached);

            detached[0] ^= 1;

            Assertions.assertArrayEquals(expected, observation.getDigest());
            Assertions.assertThrows(IllegalStateException.class, () -> frame.getSample(RandomSample.class));
            Assertions.assertNull(reader.readNextFrame());
        }
    }

    @Test
    void frame_holds_values_and_digests_together() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.PLAYERS, SampleDefinitions.RANDOM),
                                                    new RecordMetadata.Builder().build(), StorageMode.VALUES)) {
            writer.beginFrame(0, CaptureConsistency.UNVERIFIED);
            writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1), StorageMode.DIGEST);
            writer.append(SampleDefinitions.PLAYERS, createPlayers(50));
            writer.endFrame();
            writer.finish(Termination.NORMAL);
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Frame frame = reader.readNextFrame();

            Assertions.assertNotNull(frame);
            Assertions.assertEquals(50, frame.getSample(PlayersSample.class).getPlayers().getFirst().getMinerals());
            Assertions.assertThrows(IllegalStateException.class, () -> frame.getSample(RandomSample.class));
            Assertions.assertNull(reader.readNextFrame());
        }
    }

    @Test
    void summary_reports_leading_internal_and_trailing_gaps_and_unavailable_samples() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordMetadata metadata = new RecordMetadata.Builder()
                .setExpectedFrames(0, 6)
                .setCoverageRequirements(RecordMetadata.REQUIRE_NO_GAPS | RecordMetadata.REQUIRE_AVAILABLE_SAMPLES)
                .build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM), metadata, StorageMode.VALUES)) {
            writer.beginFrame(1, 10, CaptureConsistency.MARKER_STABLE);
            writer.appendUnavailable(SampleDefinitions.RANDOM, SampleStatus.NOT_ATTEMPTED);
            writer.endFrame();
            writer.beginFrame(4, 20, CaptureConsistency.TORN);
            writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1));
            writer.endFrame();

            Assertions.assertFalse(writer.finish(Termination.NORMAL).isRequirementsSatisfied());
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Frame unavailable = reader.readNextFrame();

            Assertions.assertNotNull(unavailable);
            Assertions.assertThrows(IllegalStateException.class, () -> unavailable.getSample(RandomSample.class));
            Assertions.assertEquals(SampleStatus.NOT_ATTEMPTED, unavailable.getSampleObservation(RandomSample.class).getStatus());
            Assertions.assertEquals(-1, unavailable.getSampleObservation(RandomSample.class).getSampledFrame());
            Assertions.assertNotNull(reader.readNextFrame());
            Assertions.assertNull(reader.readNextFrame());

            RecordSummary summary = reader.getSummary();

            Assertions.assertNotNull(summary);
            Assertions.assertEquals(5, summary.getMissingFrameCount());
            Assertions.assertEquals(1, summary.getUnavailableSampleCount());
            Assertions.assertEquals(1, summary.getTornObservationCount());
            Assertions.assertEquals(3, summary.getMissingRanges().size());
            Assertions.assertEquals(0, summary.getMissingRanges().get(0).getFirstFrame());
            Assertions.assertEquals(2, summary.getMissingRanges().get(1).getFirstFrame());
            Assertions.assertEquals(3, summary.getMissingRanges().get(1).getLastFrame());
            Assertions.assertEquals(5, summary.getMissingRanges().get(2).getFirstFrame());
            Assertions.assertFalse(summary.isRequirementsSatisfied());
        }
    }

    @Test
    void writer_infers_no_unknown_leading_coverage_and_accepts_an_extra_endpoint_frame() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordMetadata metadata = new RecordMetadata.Builder().setExpectedFrames(-1, -1).build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM), metadata, StorageMode.VALUES)) {
            for (long frameNumber : new long[]{4, 7}) {
                writer.beginFrame(frameNumber, frameNumber, CaptureConsistency.MARKER_STABLE);
                writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1));
                writer.endFrame();
            }

            Assertions.assertEquals(2, writer.finish(Termination.NORMAL).getMissingFrameCount());
        }

        readAllFrames(output.toByteArray());
        output.reset();

        RecordMetadata bounded = new RecordMetadata.Builder()
                .setExpectedFrames(0, 1)
                .setCoverageRequirements(RecordMetadata.REQUIRE_NO_GAPS | RecordMetadata.REQUIRE_EXPECTED_BOUNDARIES)
                .build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM), bounded, StorageMode.VALUES)) {
            for (long frameNumber = 0; frameNumber <= 2; frameNumber++) {
                writer.beginFrame(frameNumber, frameNumber, CaptureConsistency.MARKER_STABLE);
                writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1));
                writer.endFrame();
            }

            Assertions.assertTrue(writer.finish(Termination.NORMAL).isRequirementsSatisfied());
        }

        readAllFrames(output.toByteArray());
    }

    @Test
    void empty_canceled_session_summary_reports_missing_frames_and_reads_back() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM),
                                                    new RecordMetadata.Builder()
                                                            .setExpectedFrames(0, 4)
                                                            .setCoverageRequirements(RecordMetadata.REQUIRE_NO_GAPS)
                                                            .build(),
                                                    StorageMode.VALUES)) {
            RecordSummary summary = writer.finish(Termination.CANCELLED);

            Assertions.assertEquals(0, summary.getFrameCount());
            Assertions.assertEquals(-1, summary.getFirstFrame());
            Assertions.assertEquals(5, summary.getMissingFrameCount());
            Assertions.assertFalse(summary.isRequirementsSatisfied());
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Assertions.assertNull(reader.readNextFrame());
            Assertions.assertNotNull(reader.getSummary());
            Assertions.assertEquals(Termination.CANCELLED, reader.getSummary().getTermination());
        }
    }

    @Test
    void reader_rejects_every_truncated_prefix_and_trailing_bytes() throws IOException {
        byte[] valid = createSingleRandomRecord(StorageMode.VALUES);

        for (int prefixBytes = 0; prefixBytes < valid.length; prefixBytes++) {
            byte[] truncated = Arrays.copyOf(valid, prefixBytes);

            Assertions.assertThrows(IOException.class, () -> readAllFrames(truncated), "prefix=" + prefixBytes);
        }

        Assertions.assertThrows(IOException.class, () -> readAllFrames(Arrays.copyOf(valid, valid.length + 1)));
    }

    @Test
    void record_keeps_the_maximum_frame_number_and_rejects_overflowing_coverage() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordMetadata metadata = new RecordMetadata.Builder()
                .setExpectedFrames(Long.MAX_VALUE - 1, Long.MAX_VALUE)
                .setCoverageRequirements(RecordMetadata.REQUIRE_NO_GAPS | RecordMetadata.REQUIRE_EXPECTED_BOUNDARIES)
                .build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM), metadata, StorageMode.VALUES)) {
            for (long frameNumber : new long[]{Long.MAX_VALUE - 1, Long.MAX_VALUE}) {
                writer.beginFrame(frameNumber, 0, CaptureConsistency.MARKER_STABLE);
                writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1));
                writer.endFrame();
            }

            Assertions.assertTrue(writer.finish(Termination.NORMAL).isRequirementsSatisfied());
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Frame first = reader.readNextFrame();

            Assertions.assertNotNull(first);
            Assertions.assertEquals(Long.MAX_VALUE - 1, first.getFrameNumber());

            Frame last = reader.readNextFrame();

            Assertions.assertNotNull(last);
            Assertions.assertEquals(Long.MAX_VALUE, last.getFrameNumber());
            Assertions.assertEquals(Long.MAX_VALUE, last.getSampleObservation(RandomSample.class).getSampledFrame());
            Assertions.assertNull(reader.readNextFrame());
        }

        output.reset();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM),
                                                    new RecordMetadata.Builder().setExpectedFrames(0, Long.MAX_VALUE).build(), StorageMode.VALUES)) {
            Assertions.assertThrows(IOException.class, () -> writer.finish(Termination.NORMAL));
        }
    }

    @Test
    void reader_rejects_corrupt_header_frame_and_end_checksums() throws IOException {
        byte[] valid = createSingleRandomRecord(StorageMode.VALUES);
        int chunkStart = getHeaderBytes(valid);
        int endStart = getNextChunkOffset(valid, chunkStart);

        for (int offset : new int[]{FIXED_HEADER_BYTES, chunkStart + CHUNK_HEADER_BYTES, endStart + CHUNK_HEADER_BYTES}) {
            byte[] damaged = valid.clone();

            damaged[offset] ^= 1;

            Assertions.assertThrows(IOException.class, () -> readAllFrames(damaged));
        }
    }

    @Test
    void reader_rejects_forged_sequence_reserved_flags_and_huge_chunk_lengths_before_allocation() throws IOException {
        byte[] valid = createSingleRandomRecord(StorageMode.VALUES);
        int chunkStart = getHeaderBytes(valid);

        for (int offset : new int[]{chunkStart + CHUNK_FLAGS_OFFSET, chunkStart + CHUNK_SEQUENCE_OFFSET, chunkStart + CHUNK_RESERVED_OFFSET}) {
            byte[] damaged = valid.clone();

            damaged[offset] ^= 1;
            refreshChunkChecksum(damaged, chunkStart);

            Assertions.assertThrows(IOException.class, () -> readAllFrames(damaged));
        }

        byte[] oversized = valid.clone();

        ByteBuffer.wrap(oversized).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkStart + CHUNK_PAYLOAD_LENGTH_OFFSET, Integer.MAX_VALUE);
        Assertions.assertThrows(IOException.class, () -> readAllFrames(oversized));

        byte[] hugeHeader = valid.clone();

        ByteBuffer.wrap(hugeHeader).order(ByteOrder.LITTLE_ENDIAN).putInt(HEADER_LENGTH_OFFSET, Integer.MAX_VALUE);
        Assertions.assertThrows(IOException.class, () -> readAllFrames(hugeHeader));
    }

    @Test
    void header_stores_selected_identifiers_and_rejects_unknown_duplicate_or_unordered_ones() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordMetadata metadata = new RecordMetadata.Builder().build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM, SampleDefinitions.PLAYERS),
                                                    metadata, StorageMode.VALUES)) {
            writer.finish(Termination.CANCELLED);
        }

        byte[] valid = output.toByteArray();

        Assertions.assertEquals(FIXED_HEADER_BYTES + Integer.BYTES * 2 + metadata.encode().length, getHeaderBytes(valid));

        ByteBuffer header = ByteBuffer.wrap(valid).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertEquals(PLAYERS_SAMPLE_IDENTIFIER, header.getInt(FIRST_IDENTIFIER_OFFSET));
        Assertions.assertEquals(RANDOM_SAMPLE_IDENTIFIER, header.getInt(SECOND_IDENTIFIER_OFFSET));
        readAllFrames(valid);

        for (int identifier : new int[]{Integer.MAX_VALUE, SampleDefinitions.PLAYERS.getIdentifier(), 0}) {
            byte[] damaged = valid.clone();

            ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN).putInt(SECOND_IDENTIFIER_OFFSET, identifier);
            refreshHeaderChecksum(damaged);

            Assertions.assertThrows(IOException.class, () -> readAllFrames(damaged));
        }

        byte[] unordered = valid.clone();

        ByteBuffer.wrap(unordered).order(ByteOrder.LITTLE_ENDIAN).putInt(FIRST_IDENTIFIER_OFFSET, SampleDefinitions.RANDOM.getIdentifier())
                  .putInt(SECOND_IDENTIFIER_OFFSET, SampleDefinitions.PLAYERS.getIdentifier());
        refreshHeaderChecksum(unordered);
        Assertions.assertThrows(IOException.class, () -> readAllFrames(unordered));
    }

    @Test
    void reader_rejects_old_or_unknown_versions_and_inconsistent_header_lengths() throws IOException {
        byte[] valid = createSingleRandomRecord(StorageMode.VALUES);

        for (int version : new int[]{1, 2}) {
            byte[] damaged = valid.clone();

            ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN).putShort(HEADER_MAJOR_VERSION_OFFSET, (short) version);
            refreshHeaderChecksum(damaged);

            Assertions.assertThrows(IOException.class, () -> readAllFrames(damaged));
        }

        byte[] unsupportedMinor = valid.clone();

        ByteBuffer.wrap(unsupportedMinor).order(ByteOrder.LITTLE_ENDIAN).putShort(HEADER_MINOR_VERSION_OFFSET, (short) UNSUPPORTED_MINOR_VERSION);
        refreshHeaderChecksum(unsupportedMinor);
        Assertions.assertThrows(IOException.class, () -> readAllFrames(unsupportedMinor));

        for (int offset : new int[]{HEADER_LENGTH_OFFSET, SELECTED_SAMPLE_COUNT_OFFSET, METADATA_LENGTH_OFFSET}) {
            byte[] damaged = valid.clone();

            ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN)
                      .putInt(offset, ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN).getInt(offset) + 1);

            Assertions.assertThrows(IOException.class, () -> readAllFrames(damaged));
        }
    }

    @Test
    void reader_rejects_a_digest_with_an_invalid_length_despite_a_valid_chunk_checksum() throws IOException {
        byte[] valid = createSingleRandomRecord(StorageMode.DIGEST);
        int chunkStart = getHeaderBytes(valid);
        int sampleStart = chunkStart + CHUNK_HEADER_BYTES + FRAME_PREFIX_BYTES;

        Assertions.assertEquals(DIGEST_BYTES,
                                ByteBuffer.wrap(valid).order(ByteOrder.LITTLE_ENDIAN).getInt(sampleStart + SAMPLE_PAYLOAD_LENGTH_OFFSET));

        byte[] damaged = valid.clone();

        ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleStart + SAMPLE_PAYLOAD_LENGTH_OFFSET, SHORT_DIGEST_LENGTH);
        refreshChunkChecksum(damaged, chunkStart);
        Assertions.assertThrows(IOException.class, () -> readAllFrames(damaged));
    }

    @Test
    void reader_skips_optional_metadata_and_rejects_required_duplicate_and_malformed_metadata() throws IOException {
        byte[] valid = createSingleRandomRecord(StorageMode.VALUES);

        readAllFrames(appendMetadata(valid, UNKNOWN_METADATA_TAG, OPTIONAL_METADATA_FLAGS, new byte[]{8, 9}));
        Assertions.assertThrows(IOException.class,
                                () -> readAllFrames(appendMetadata(valid, UNKNOWN_METADATA_TAG, REQUIRED_METADATA_FLAGS, new byte[]{8, 9})));
        Assertions.assertThrows(IOException.class,
                                () -> readAllFrames(appendMetadata(valid, COVERAGE_REQUIREMENTS_TAG, REQUIRED_METADATA_FLAGS, new byte[4])));
        Assertions.assertThrows(IOException.class,
                                () -> readAllFrames(appendMetadata(valid, PRODUCER_VERSION_TAG, REQUIRED_METADATA_FLAGS, new byte[]{(byte) 0xFF})));

        String malformedProducer = new String(new char[]{Character.MIN_HIGH_SURROGATE});

        Assertions.assertThrows(IllegalArgumentException.class, () -> new RecordMetadata.Builder().setProducerVersion(malformedProducer));
    }

    @Test
    void reader_rejects_malformed_samples_and_summary_even_with_valid_checksums() throws IOException {
        byte[] valid = createSingleRandomRecord(StorageMode.VALUES);
        int chunkStart = getHeaderBytes(valid);
        int sampleStart = chunkStart + CHUNK_HEADER_BYTES + FRAME_PREFIX_BYTES;

        for (int offset : new int[]{sampleStart, sampleStart + SAMPLE_STORAGE_MODE_OFFSET, sampleStart + SAMPLE_STATUS_OFFSET,
                                    sampleStart + SAMPLE_FRAME_OFFSET, sampleStart + SAMPLE_PAYLOAD_LENGTH_OFFSET}) {
            byte[] damaged = valid.clone();

            damaged[offset] ^= SAMPLE_CORRUPTION_MASK;
            refreshChunkChecksum(damaged, chunkStart);

            Assertions.assertThrows(IOException.class, () -> readAllFrames(damaged), "offset=" + offset);
        }

        int endStart = getNextChunkOffset(valid, chunkStart);
        int summaryStart = endStart + CHUNK_HEADER_BYTES;

        for (int offset : new int[]{summaryStart, summaryStart + SUMMARY_QUALITY_FLAGS_OFFSET, summaryStart + SUMMARY_MISSING_FRAME_COUNT_OFFSET,
                                    summaryStart + SUMMARY_RESERVED_OFFSET}) {
            byte[] damaged = valid.clone();

            damaged[offset] ^= 1;
            refreshChunkChecksum(damaged, endStart);

            Assertions.assertThrows(IOException.class, () -> readAllFrames(damaged));
        }
    }

    @Test
    void reader_rejects_a_huge_table_count_inside_an_otherwise_bounded_frame() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.PLAYERS),
                                                    new RecordMetadata.Builder().build(), StorageMode.VALUES)) {
            writer.beginFrame(0, CaptureConsistency.UNVERIFIED);
            writer.append(SampleDefinitions.PLAYERS, new PlayersSample(List.of()));
            writer.endFrame();
            writer.finish(Termination.NORMAL);
        }

        byte[] malformed = output.toByteArray();
        int chunkStart = getHeaderBytes(malformed);
        int tableCountOffset = chunkStart + CHUNK_HEADER_BYTES + FRAME_PREFIX_BYTES + SAMPLE_HEADER_BYTES;

        ByteBuffer.wrap(malformed).order(ByteOrder.LITTLE_ENDIAN).putInt(tableCountOffset, Integer.MAX_VALUE);
        refreshChunkChecksum(malformed, chunkStart);
        Assertions.assertThrows(IOException.class, () -> readAllFrames(malformed));
    }

    @Test
    void writer_aborts_a_failed_append_and_rejects_duplicate_missing_and_out_of_order_frames() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM),
                                                    new RecordMetadata.Builder().build(), StorageMode.VALUES)) {
            Assertions.assertThrows(IllegalStateException.class, () -> writer.append(SampleDefinitions.RANDOM, new RandomSample(1, 2, 3)));
            writer.beginFrame(0, 0, CaptureConsistency.MARKER_STABLE);
            Assertions.assertThrows(IOException.class, () -> writer.append(SampleDefinitions.RANDOM, new RandomSample(-1, 2, 3)));
            Assertions.assertThrows(IllegalStateException.class, writer::endFrame);
            writer.abortFrame();
            writer.beginFrame(0, 0, CaptureConsistency.MARKER_STABLE);
            writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1));
            Assertions.assertThrows(IOException.class, () -> writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1)));
            writer.abortFrame();
            writer.beginFrame(0, 0, CaptureConsistency.MARKER_STABLE);
            Assertions.assertThrows(IOException.class, writer::endFrame);
            writer.abortFrame();
            writer.beginFrame(0, 0, CaptureConsistency.MARKER_STABLE);
            writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1));
            Assertions.assertThrows(IllegalStateException.class, () -> writer.finish(Termination.NORMAL));
            writer.endFrame();
            Assertions.assertThrows(IOException.class, () -> writer.beginFrame(0, 0, CaptureConsistency.MARKER_STABLE));
            Assertions.assertThrows(IOException.class, () -> writer.beginFrame(1, -1, CaptureConsistency.MARKER_STABLE));
            writer.finish(Termination.NORMAL);
            Assertions.assertThrows(IllegalStateException.class, () -> writer.beginFrame(1, CaptureConsistency.MARKER_STABLE));
        }

        readAllFrames(output.toByteArray());
    }

    @Test
    void writer_never_ends_normally_on_close_and_never_overwrites_existing_files(@TempDir @NotNull Path directory) throws IOException {
        Path destination = directory.resolve("session.screc");

        try (RecordWriter writer = RecordWriter.open(destination, List.of(SampleDefinitions.RANDOM),
                                                     new RecordMetadata.Builder().build(), StorageMode.VALUES)) {
            writer.beginFrame(0, CaptureConsistency.UNVERIFIED);
            writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1));
            writer.endFrame();
        }

        byte[] partial = Files.readAllBytes(destination);

        Assertions.assertThrows(IOException.class, () -> readAllFrames(partial));
        Assertions.assertThrows(IOException.class, () -> {
            try (RecordWriter duplicateWriter = RecordWriter.open(destination, List.of(SampleDefinitions.RANDOM),
                                                                  new RecordMetadata.Builder().build(), StorageMode.VALUES)) {
                duplicateWriter.finish(Termination.CANCELLED);
            }
        });
        Assertions.assertArrayEquals(partial, Files.readAllBytes(destination));
    }

    @Test
    void writer_and_reader_stay_faulted_after_a_partial_output_failure() throws IOException {
        ByteArrayOutputStream retained = new ByteArrayOutputStream();
        OutputStream failing = new OutputStream() {
            private boolean headerWritten;

            /**
             * Writes the specified byte to this output stream.
             * <p>
             * This fixture rejects single-byte writes.
             *
             * @param value the byte
             * @throws IOException if a single-byte write is attempted
             */
            @Override
            public void write(int value) throws IOException {
                throw Exceptions.newIOException("single byte output is unsupported");
            }

            /**
             * Writes {@code length} bytes from the specified byte array starting at {@code offset} to this output stream.
             * <p>
             * This fixture accepts the header and fails after partially writing the next output block.
             *
             * @param source the data
             * @param offset the start offset in the data
             * @param length the number of bytes to write
             * @throws IOException if an output block follows the header
             */
            @Override
            public void write(byte @NotNull [] source, int offset, int length) throws IOException {
                if (this.headerWritten) {
                    retained.write(source, offset, Math.min(length, PARTIAL_WRITE_BYTES));

                    throw Exceptions.newIOException("fixture output failure");
                }

                retained.write(source, offset, length);
                this.headerWritten = true;
            }
        };

        try (RecordWriter writer = new RecordWriter(failing, List.of(SampleDefinitions.RANDOM),
                                                    new RecordMetadata.Builder().build(), StorageMode.VALUES)) {
            writer.beginFrame(0, CaptureConsistency.UNVERIFIED);
            writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1));
            Assertions.assertThrows(IOException.class, writer::endFrame);
            writer.abortFrame();
            Assertions.assertThrows(IOException.class, () -> writer.beginFrame(1, CaptureConsistency.UNVERIFIED));
            Assertions.assertThrows(IOException.class, () -> writer.finish(Termination.FAILED));
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(retained.toByteArray()))) {
            Assertions.assertThrows(IOException.class, reader::readNextFrame);
            Assertions.assertThrows(IOException.class, reader::readNextFrame);
            Assertions.assertNull(reader.getSummary());
        }
    }

    @Test
    void writer_rejects_a_digest_when_the_file_metadata_disables_it() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordMetadata metadata = new RecordMetadata.Builder().setDigestEnabled(false).build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM), metadata, StorageMode.VALUES)) {
            writer.beginFrame(0, CaptureConsistency.UNVERIFIED);
            Assertions.assertThrows(IOException.class, () -> writer.append(SampleDefinitions.RANDOM, new RandomSample(1, 2, 3), StorageMode.DIGEST));
            writer.abortFrame();
            writer.finish(Termination.CANCELLED);
        }

        readAllFrames(output.toByteArray());
    }

    @Test
    void writer_faults_after_an_unchecked_flush_failure_without_duplicating_the_end() throws IOException {
        ByteArrayOutputStream retained = new ByteArrayOutputStream();
        OutputStream failing = new OutputStream() {
            /**
             * Writes the specified byte to this output stream.
             *
             * @param value the byte whose low-order eight bits are written
             */
            @Override
            public void write(int value) {
                retained.write(value);
            }

            /**
             * Writes {@code length} bytes from the specified byte array starting at {@code offset} to this output stream.
             *
             * @param source the data
             * @param offset the start offset in the data
             * @param length the number of bytes to write
             */
            @Override
            public void write(byte @NotNull [] source, int offset, int length) {
                retained.write(source, offset, length);
            }

            /**
             * Flushes this output stream and forces any buffered output bytes to be written out.
             * <p>
             * This fixture fails with an unchecked exception instead of completing the flush.
             *
             * @throws IllegalStateException if flushing is attempted
             */
            @Override
            public void flush() {
                throw Exceptions.newIllegalStateException("fixture unchecked flush failure");
            }
        };

        try (RecordWriter writer = new RecordWriter(failing, List.of(SampleDefinitions.RANDOM),
                                                    new RecordMetadata.Builder().build(), StorageMode.VALUES)) {
            writer.beginFrame(0, CaptureConsistency.MARKER_STABLE);
            writer.append(SampleDefinitions.RANDOM, new RandomSample(23, 7, 1));
            writer.endFrame();
            Assertions.assertThrows(IllegalStateException.class, () -> writer.finish(Termination.NORMAL));
            Assertions.assertNull(writer.getSummary());
            Assertions.assertThrows(IOException.class, () -> writer.finish(Termination.FAILED));
            Assertions.assertThrows(IOException.class, () -> writer.beginFrame(1, CaptureConsistency.MARKER_STABLE));
        }

        readAllFrames(retained.toByteArray());
    }
}
