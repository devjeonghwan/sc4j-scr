package org.fogofwarlabs.screcorder.format.recording;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleObservation;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleStatus;
import org.fogofwarlabs.screcorder.format.recording.observation.StorageMode;
import org.fogofwarlabs.screcorder.format.recording.summary.FrameRange;
import org.fogofwarlabs.screcorder.format.recording.summary.RecordSummary;
import org.fogofwarlabs.screcorder.format.recording.summary.Termination;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32C;

/**
 * Validates {@code SCRECBIN 0.1} framing and reads immutable typed frames.
 * <p>
 * This reader owns its input stream. End of input is successful only after a validated {@code END} chunk and physical EOF.
 * Instances are confined to one thread.
 */
public final class RecordReader implements Closeable {
    private static final int INITIAL_BUFFER_BYTES = 4096;
    private static final @NotNull StorageMode @NotNull [] STORAGE_MODES = StorageMode.values();
    private static final @NotNull SampleStatus @NotNull [] SAMPLE_STATUSES = SampleStatus.values();
    private static final @NotNull CaptureConsistency @NotNull [] CONSISTENCIES = CaptureConsistency.values();
    private static final @NotNull Termination @NotNull [] TERMINATIONS = Termination.values();

    /**
     * Creates a {@link RecordReader} instance for the supplied path.
     *
     * @param path the record path
     * @return the created {@link RecordReader} instance
     * @throws IOException if opening or header validation fails
     */
    public static @NotNull RecordReader open(@NotNull Path path) throws IOException {
        return new RecordReader(new BufferedInputStream(Files.newInputStream(path)));
    }

    private static <SampleValue extends Sample> @NotNull SampleValue decodeSample(@NotNull SampleDefinition<SampleValue> definition,
                                                                                  @NotNull ByteBuffer payload) throws IOException {
        SampleValue value = definition.getReader().read(payload);

        if (payload.hasRemaining()) {
            throw Exceptions.newIOException("Sample reader did not consume its complete payload: {0}", definition.getName());
        }

        return value;
    }


    private final @NotNull InputStream input;
    private final @NotNull Record record;
    private final @NotNull RecordQuality quality;

    private final @NotNull CRC32C checksum;
    private final byte @NotNull [] chunkHeader;

    private byte @NotNull [] payload;
    private long sequence;
    private boolean complete;
    private boolean faulted;
    private boolean closed;
    private @Nullable RecordSummary summary;

    /**
     * Creates a {@link RecordReader} instance and validates its header.
     * <p>
     * Ownership of the supplied stream transfers immediately, including constructor failure.
     *
     * @param input the owned input stream
     * @throws IOException if the header is invalid or truncated
     */
    public RecordReader(@NotNull InputStream input) throws IOException {
        this.input = input;

        try {
            this.record  = RecordFormatUtils.readHeader(input);
            this.quality = new RecordQuality(this.record.getMetadata());

            this.checksum    = new CRC32C();
            this.chunkHeader = new byte[RecordFormatUtils.CHUNK_HEADER_BYTES];

            this.payload  = new byte[INITIAL_BUFFER_BYTES];
            this.sequence = 0;
            this.complete = false;
            this.faulted  = false;
            this.closed   = false;
            this.summary  = null;
        } catch (IOException | RuntimeException exception) {
            try {
                input.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }

            throw exception;
        }
    }

    /**
     * @return the immutable session descriptor
     */
    public @NotNull Record getRecord() {
        return this.record;
    }

    /**
     * @return the validated {@code END} summary, or {@code null} before {@code END} and physical EOF validation
     */
    public @Nullable RecordSummary getSummary() {
        return this.summary;
    }


    /**
     * Reads the next complete frame.
     *
     * @return the immutable frame, or {@code null} after a valid {@code END} chunk and physical EOF
     * @throws IOException           if input is incomplete, corrupt, unsupported, or inconsistent
     * @throws IllegalStateException if the reader is closed
     */
    public @Nullable Frame readNextFrame() throws IOException {
        if (this.closed) {
            throw Exceptions.newIllegalStateException("The record reader is closed");
        }

        if (this.faulted) {
            throw Exceptions.newIOException("The record reader is faulted after an input failure");
        }

        if (this.complete) {
            return null;
        }

        try {
            RecordFormatUtils.readFully(this.input, this.chunkHeader, 0, this.chunkHeader.length);

            ByteBuffer header = ByteBuffer.wrap(this.chunkHeader).order(ByteOrder.LITTLE_ENDIAN);
            int kind = Short.toUnsignedInt(header.getShort());
            int flags = Short.toUnsignedInt(header.getShort());
            int payloadBytes = header.getInt();
            long chunkSequence = header.getLong();
            int expectedChecksum = header.getInt();
            int reserved = header.getInt();

            if ((kind != RecordFormatUtils.FRAME_KIND && kind != RecordFormatUtils.END_KIND) ||
                flags != 0 || reserved != 0 || payloadBytes < 0 || payloadBytes > RecordFormatUtils.MAXIMUM_CHUNK_BYTES ||
                chunkSequence != this.sequence) {
                throw Exceptions.newIOException("Invalid chunk kind, flags, sequence, or bounds");
            }

            this.payload = RecordFormatUtils.growBuffer(this.payload, payloadBytes);
            RecordFormatUtils.readFully(this.input, this.payload, 0, payloadBytes);

            if (RecordFormatUtils.calculateChunkChecksum(this.checksum, this.chunkHeader, this.payload, payloadBytes) != expectedChecksum) {
                throw Exceptions.newIOException("Chunk checksum mismatch at sequence {0}", this.sequence);
            }

            this.sequence++;

            ByteBuffer content = ByteBuffer.wrap(this.payload, 0, payloadBytes).slice().order(ByteOrder.LITTLE_ENDIAN);

            if (kind == RecordFormatUtils.END_KIND) {
                RecordSummary finalSummary = this.readSummary(content);

                if (this.input.read() != -1) {
                    throw Exceptions.newIOException("Trailing bytes after the END chunk");
                }

                this.summary  = finalSummary;
                this.complete = true;

                return null;
            }

            return this.readFrame(content);
        } catch (IOException exception) {
            this.faulted = true;

            throw exception;
        } catch (BufferUnderflowException | IllegalArgumentException | ArithmeticException exception) {
            this.faulted = true;

            throw Exceptions.newIOException(exception, "Malformed recording payload");
        }
    }

    private @NotNull Frame readFrame(@NotNull ByteBuffer content) throws IOException {
        if (content.remaining() < RecordFormatUtils.FRAME_PREFIX_BYTES) {
            throw Exceptions.newIOException("Truncated frame prefix");
        }

        long frameNumber = content.getLong();
        long elapsedNanoseconds = content.getLong();
        int sampleCount = content.getInt();
        int consistencyCode = content.getInt();

        if (sampleCount != this.record.getDefinitions().size() || consistencyCode < 0 || consistencyCode >= CONSISTENCIES.length) {
            throw Exceptions.newIOException("Invalid frame sample count or consistency evidence");
        }

        this.quality.validateFrame(frameNumber, elapsedNanoseconds);

        CaptureConsistency consistency = CONSISTENCIES[consistencyCode];
        Map<Class<? extends Sample>, Sample> samples = new HashMap<>(sampleCount);
        Map<Class<? extends Sample>, SampleObservation> observations = new HashMap<>(sampleCount);
        long unavailableSamples = 0;

        for (SampleDefinition<?> definition : this.record.getDefinitions()) {
            if (content.remaining() < RecordFormatUtils.SAMPLE_HEADER_BYTES) {
                throw Exceptions.newIOException("Truncated sample observation header");
            }

            int identifier = content.getInt();
            int storageCode = Byte.toUnsignedInt(content.get());
            int statusCode = Byte.toUnsignedInt(content.get());
            long sampledFrame = content.getLong();
            int sampleBytes = content.getInt();

            if (identifier != definition.getIdentifier() ||
                storageCode >= STORAGE_MODES.length || statusCode >= SAMPLE_STATUSES.length ||
                sampleBytes < 0 || sampleBytes > content.remaining()) {
                throw Exceptions.newIOException("Invalid sample identity, ordering, representation, or bounds");
            }

            StorageMode storageMode = STORAGE_MODES[storageCode];
            SampleStatus status = SAMPLE_STATUSES[statusCode];
            ByteBuffer samplePayload = content.slice(content.position(), sampleBytes).order(ByteOrder.LITTLE_ENDIAN);

            content.position(content.position() + sampleBytes);

            byte[] sampleDigest = null;

            if (status != SampleStatus.AVAILABLE) {
                if (storageMode != StorageMode.NONE || sampleBytes != 0 || sampledFrame != -1) {
                    throw Exceptions.newIOException("Unavailable sample contains values or a sampled frame");
                }

                unavailableSamples++;
            } else {
                if (storageMode == StorageMode.NONE || sampledFrame < 0 || sampledFrame > frameNumber) {
                    throw Exceptions.newIOException("Available sample has no representation or invalid sampled frame");
                }

                if (storageMode == StorageMode.VALUES) {
                    if (sampleBytes > definition.getMaximumPayloadBytes()) {
                        throw Exceptions.newIOException("Sample payload exceeds its supported bound");
                    }

                    samples.put(definition.getSampleClass(), decodeSample(definition, samplePayload));
                } else {
                    if (!this.record.getMetadata().isDigestEnabled() || sampleBytes != RecordFormatUtils.DIGEST_BYTES) {
                        throw Exceptions.newIOException("Invalid digest configuration or length");
                    }

                    sampleDigest = new byte[RecordFormatUtils.DIGEST_BYTES];
                    samplePayload.get(sampleDigest);
                }
            }

            observations.put(definition.getSampleClass(), new SampleObservation(status, storageMode, sampledFrame, sampleDigest));
        }

        if (content.hasRemaining()) {
            throw Exceptions.newIOException("Trailing frame payload bytes");
        }

        this.quality.commit(frameNumber, elapsedNanoseconds, consistency, unavailableSamples);

        return new Frame(frameNumber, elapsedNanoseconds, consistency, samples, observations);
    }

    private @NotNull RecordSummary readSummary(@NotNull ByteBuffer content) throws IOException {
        if (content.remaining() < RecordFormatUtils.SUMMARY_PREFIX_BYTES) {
            throw Exceptions.newIOException("Truncated END summary");
        }

        long frameCount = content.getLong();
        long firstFrame = content.getLong();
        long lastFrame = content.getLong();
        int terminationCode = content.getInt();
        int flags = content.getInt();
        long missingFrames = content.getLong();
        long unavailableSamples = content.getLong();
        long tornObservations = content.getLong();
        int rangeCount = content.getInt();
        int reserved = content.getInt();

        if (terminationCode < 0 || terminationCode >= TERMINATIONS.length || (flags & ~RecordFormatUtils.SUMMARY_REQUIREMENTS_SATISFIED_FLAG) != 0 ||
            reserved != 0 || rangeCount < 0 || (long) rangeCount * RecordFormatUtils.MISSING_RANGE_BYTES != content.remaining()) {
            throw Exceptions.newIOException("Invalid END summary values or bounds");
        }

        RecordSummary expected = this.quality.summarize(TERMINATIONS[terminationCode]);

        if (frameCount != expected.getFrameCount() || firstFrame != expected.getFirstFrame() || lastFrame != expected.getLastFrame() ||
            missingFrames != expected.getMissingFrameCount() || unavailableSamples != expected.getUnavailableSampleCount() ||
            tornObservations != expected.getTornObservationCount() || rangeCount != expected.getMissingRanges().size() ||
            flags != (expected.isRequirementsSatisfied() ? RecordFormatUtils.SUMMARY_REQUIREMENTS_SATISFIED_FLAG : 0)) {
            throw Exceptions.newIOException("END summary disagrees with observed frames");
        }

        List<FrameRange> ranges = expected.getMissingRanges();

        for (FrameRange range : ranges) {
            if (content.getLong() != range.getFirstFrame() || content.getLong() != range.getLastFrame()) {
                throw Exceptions.newIOException("END missing ranges disagree with observed frames");
            }
        }

        return expected;
    }


    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * <p>
     * As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     *
     * @throws IOException if an I/O error occurs
     * @implSpec Closes the owned input stream; the reader is marked closed first.
     */
    @Override
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            this.input.close();
        }
    }
}
