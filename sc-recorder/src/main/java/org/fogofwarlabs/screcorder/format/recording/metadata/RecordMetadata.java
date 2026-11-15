package org.fogofwarlabs.screcorder.format.recording.metadata;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Stores capture provenance and explicit coverage requirements.
 * <p>
 * Missing source metadata does not imply verified capture.
 */
public final class RecordMetadata {
    /**
     * Requires consecutive observations within observed or configured frame boundaries.
     */
    public static final int REQUIRE_NO_GAPS = 1;

    /**
     * Requires every selected sample observation to be available.
     */
    public static final int REQUIRE_AVAILABLE_SAMPLES = 2;

    /**
     * Requires marker-stable evidence for every frame observation.
     */
    public static final int REQUIRE_MARKER_STABLE = 4;

    /**
     * Requires known and actually observed first and last frame boundaries.
     */
    public static final int REQUIRE_EXPECTED_BOUNDARIES = 8;

    private static final int COVERAGE_MASK = REQUIRE_NO_GAPS | REQUIRE_AVAILABLE_SAMPLES | REQUIRE_MARKER_STABLE | REQUIRE_EXPECTED_BOUNDARIES;

    private static final int MAXIMUM_PRODUCER_BYTES = 4096;
    private static final int DIGEST_BYTES = 32;
    private static final int EXPECTED_FRAMES_BYTES = Long.BYTES * 2;
    private static final int ENTRY_HEADER_BYTES = 8;
    private static final int REQUIRED_FLAG = 1;
    private static final int SHA256_ALGORITHM = 1;

    private static final int KNOWN_TAG_COUNT = 9;
    private static final int PRODUCER_VERSION_TAG = 1;
    private static final int EXECUTABLE_DIGEST_TAG = 2;
    private static final int REQUESTED_REPLAY_DIGEST_TAG = 3;
    private static final int LOADED_REPLAY_EVIDENCE_TAG = 4;
    private static final int SAMPLING_METHOD_TAG = 5;
    private static final int POLLING_INTERVAL_TAG = 6;
    private static final int COVERAGE_REQUIREMENTS_TAG = 7;
    private static final int DIGEST_ALGORITHM_TAG = 8;
    private static final int EXPECTED_FRAMES_TAG = 9;

    /**
     * Builds {@link RecordMetadata} instances.
     */
    public static final class Builder {
        private @Nullable String producerVersion;
        private byte @Nullable [] executableDigest;
        private byte @Nullable [] requestedReplayDigest;
        private @Nullable LoadedReplayEvidence loadedReplayEvidence;
        private @Nullable SamplingMethod samplingMethod;
        private long pollingIntervalNanoseconds;
        private int coverageRequirements;
        private boolean digestEnabled;
        private long expectedFirstFrame;
        private long expectedLastFrame;

        /**
         * Creates a {@link Builder} instance with unspecified provenance and frame boundaries.
         */
        public Builder() {
            this.producerVersion            = null;
            this.executableDigest           = null;
            this.requestedReplayDigest      = null;
            this.loadedReplayEvidence       = null;
            this.samplingMethod             = null;
            this.pollingIntervalNanoseconds = -1;
            this.coverageRequirements       = 0;
            this.digestEnabled              = true;
            this.expectedFirstFrame         = -1;
            this.expectedLastFrame          = -1;
        }

        /**
         * Sets the producing tool version.
         *
         * @param producerVersion the version label
         * @return this instance for chaining
         * @throws IllegalArgumentException if the version is not valid Unicode or its UTF-8 form exceeds the supported length
         */
        public @NotNull Builder setProducerVersion(@NotNull String producerVersion) {
            try {
                ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                                                           .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(producerVersion));

                if (encoded.remaining() > MAXIMUM_PRODUCER_BYTES) {
                    throw Exceptions.newIllegalArgumentException("Producer version exceeds {0} bytes", MAXIMUM_PRODUCER_BYTES);
                }
            } catch (CharacterCodingException exception) {
                throw Exceptions.newIllegalArgumentException("Producer version contains malformed Unicode");
            }

            this.producerVersion = producerVersion;

            return this;
        }

        /**
         * Sets the executable SHA-256 digest.
         *
         * @param executableDigest the digest bytes
         * @return this instance for chaining
         * @throws IllegalArgumentException if the digest does not contain {@code 32} bytes
         */
        public @NotNull Builder setExecutableDigest(byte @NotNull [] executableDigest) {
            if (executableDigest.length != DIGEST_BYTES) {
                throw Exceptions.newIllegalArgumentException("Executable digest must contain {0} bytes", DIGEST_BYTES);
            }

            this.executableDigest = executableDigest.clone();

            return this;
        }

        /**
         * Sets the requested replay SHA-256 digest.
         *
         * @param requestedReplayDigest the digest bytes
         * @return this instance for chaining
         * @throws IllegalArgumentException if the digest does not contain {@code 32} bytes
         */
        public @NotNull Builder setRequestedReplayDigest(byte @NotNull [] requestedReplayDigest) {
            if (requestedReplayDigest.length != DIGEST_BYTES) {
                throw Exceptions.newIllegalArgumentException("Replay digest must contain {0} bytes", DIGEST_BYTES);
            }

            this.requestedReplayDigest = requestedReplayDigest.clone();

            return this;
        }

        /**
         * Sets loaded replay header evidence.
         *
         * @param loadedReplayEvidence the available header evidence
         * @return this instance for chaining
         */
        public @NotNull Builder setLoadedReplayEvidence(@NotNull LoadedReplayEvidence loadedReplayEvidence) {
            this.loadedReplayEvidence = loadedReplayEvidence;

            return this;
        }

        /**
         * Sets how sample values were obtained.
         *
         * @param samplingMethod the sampling method
         * @return this instance for chaining
         */
        public @NotNull Builder setSamplingMethod(@NotNull SamplingMethod samplingMethod) {
            this.samplingMethod = samplingMethod;

            return this;
        }

        /**
         * Sets the monotonic polling interval.
         *
         * @param pollingIntervalNanoseconds the nonnegative interval
         * @return this instance for chaining
         * @throws IllegalArgumentException if the interval is negative
         */
        public @NotNull Builder setPollingIntervalNanoseconds(long pollingIntervalNanoseconds) {
            if (pollingIntervalNanoseconds < 0) {
                throw Exceptions.newIllegalArgumentException("Polling interval must be nonnegative");
            }

            this.pollingIntervalNanoseconds = pollingIntervalNanoseconds;

            return this;
        }

        /**
         * Sets the collection quality requirements.
         *
         * @param coverageRequirements the combination of defined requirement flags
         * @return this instance for chaining
         * @throws IllegalArgumentException if an undefined requirement flag is set
         */
        public @NotNull Builder setCoverageRequirements(int coverageRequirements) {
            if ((coverageRequirements & ~COVERAGE_MASK) != 0) {
                throw Exceptions.newIllegalArgumentException("Unknown coverage requirements: {0}", coverageRequirements);
            }

            this.coverageRequirements = coverageRequirements;

            return this;
        }

        /**
         * Sets whether canonical SHA-256 sample digests are supported by this file.
         *
         * @param digestEnabled whether digest observations are permitted
         * @return this instance for chaining
         */
        public @NotNull Builder setDigestEnabled(boolean digestEnabled) {
            this.digestEnabled = digestEnabled;

            return this;
        }

        /**
         * Sets the known expected frame boundaries.
         *
         * @param expectedFirstFrame the first frame, or {@code -1} when unknown
         * @param expectedLastFrame  the last frame, or {@code -1} when unknown
         * @return this instance for chaining
         * @throws IllegalArgumentException if a boundary is below {@code -1} or a known last frame precedes the first frame
         */
        public @NotNull Builder setExpectedFrames(long expectedFirstFrame, long expectedLastFrame) {
            if (expectedFirstFrame < -1 || expectedLastFrame < -1 || (expectedLastFrame >= 0 && expectedLastFrame < expectedFirstFrame)) {
                throw Exceptions.newIllegalArgumentException("Invalid expected frame boundaries: {0}..{1}", expectedFirstFrame, expectedLastFrame);
            }

            this.expectedFirstFrame = expectedFirstFrame;
            this.expectedLastFrame  = expectedLastFrame;

            return this;
        }


        /**
         * Builds a {@link RecordMetadata} instance.
         *
         * @return the built {@link RecordMetadata} instance
         */
        public @NotNull RecordMetadata build() {
            return new RecordMetadata(this);
        }
    }


    /**
     * Creates a {@link RecordMetadata} instance by decoding a complete bounded little-endian buffer.
     *
     * @param input the metadata buffer, consumed through its limit
     * @return the created {@link RecordMetadata} instance
     * @throws IOException if metadata is truncated, malformed or unsupported
     */
    public static @NotNull RecordMetadata decode(@NotNull ByteBuffer input) throws IOException {
        Builder builder = new Builder().setDigestEnabled(false);
        boolean[] knownTags = new boolean[KNOWN_TAG_COUNT + 1];

        try {
            while (input.hasRemaining()) {
                if (input.remaining() < ENTRY_HEADER_BYTES) {
                    throw Exceptions.newIOException("Truncated metadata entry");
                }

                int tag = Short.toUnsignedInt(input.getShort());
                int flags = Short.toUnsignedInt(input.getShort());
                int payloadBytes = input.getInt();

                if ((flags & ~REQUIRED_FLAG) != 0 || payloadBytes < 0 || payloadBytes > input.remaining()) {
                    throw Exceptions.newIOException("Invalid metadata entry size or flags");
                }

                ByteBuffer payload = input.slice(input.position(), payloadBytes).order(ByteOrder.LITTLE_ENDIAN);

                input.position(input.position() + payloadBytes);

                if (tag < PRODUCER_VERSION_TAG || tag > KNOWN_TAG_COUNT) {
                    if ((flags & REQUIRED_FLAG) != 0) {
                        throw Exceptions.newIOException("Unknown required metadata tag: {0}", tag);
                    }

                    continue;
                }

                if (knownTags[tag]) {
                    throw Exceptions.newIOException("Duplicate metadata tag: {0}", tag);
                }

                knownTags[tag] = true;

                switch (tag) {
                    case PRODUCER_VERSION_TAG -> {
                        if (payloadBytes > MAXIMUM_PRODUCER_BYTES) {
                            throw Exceptions.newIOException("Producer metadata is too long");
                        }

                        String producer = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                                                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(payload).toString();

                        builder.setProducerVersion(producer);
                    }

                    case EXECUTABLE_DIGEST_TAG, REQUESTED_REPLAY_DIGEST_TAG -> {
                        if (payloadBytes != DIGEST_BYTES) {
                            throw Exceptions.newIOException("Invalid source SHA-256 digest length");
                        }

                        byte[] digest = new byte[DIGEST_BYTES];

                        payload.get(digest);

                        if (tag == EXECUTABLE_DIGEST_TAG) {
                            builder.setExecutableDigest(digest);
                        } else {
                            builder.setRequestedReplayDigest(digest);
                        }
                    }

                    case LOADED_REPLAY_EVIDENCE_TAG -> {
                        if (payloadBytes != Byte.BYTES) {
                            throw Exceptions.newIOException("Invalid loaded replay evidence length");
                        }

                        int code = Byte.toUnsignedInt(payload.get());

                        if (code >= LoadedReplayEvidence.values().length) {
                            throw Exceptions.newIOException("Unknown loaded replay evidence: {0}", code);
                        }

                        builder.setLoadedReplayEvidence(LoadedReplayEvidence.values()[code]);
                    }

                    case SAMPLING_METHOD_TAG -> {
                        if (payloadBytes != Short.BYTES) {
                            throw Exceptions.newIOException("Invalid sampling method length");
                        }

                        int code = Short.toUnsignedInt(payload.getShort());

                        if (code >= SamplingMethod.values().length) {
                            throw Exceptions.newIOException("Unknown sampling method: {0}", code);
                        }

                        builder.setSamplingMethod(SamplingMethod.values()[code]);
                    }

                    case POLLING_INTERVAL_TAG -> {
                        if (payloadBytes != Long.BYTES) {
                            throw Exceptions.newIOException("Invalid polling interval length");
                        }

                        builder.setPollingIntervalNanoseconds(payload.getLong());
                    }

                    case COVERAGE_REQUIREMENTS_TAG -> {
                        if (payloadBytes != Integer.BYTES) {
                            throw Exceptions.newIOException("Invalid coverage requirement length");
                        }

                        builder.setCoverageRequirements(payload.getInt());
                    }

                    case DIGEST_ALGORITHM_TAG -> {
                        if (payloadBytes != Short.BYTES || payload.getShort() != SHA256_ALGORITHM) {
                            throw Exceptions.newIOException("Unsupported digest algorithm");
                        }

                        builder.setDigestEnabled(true);
                    }

                    case EXPECTED_FRAMES_TAG -> {
                        if (payloadBytes != EXPECTED_FRAMES_BYTES) {
                            throw Exceptions.newIOException("Invalid expected boundary metadata length");
                        }

                        builder.setExpectedFrames(payload.getLong(), payload.getLong());
                    }

                    default -> throw Exceptions.newIOException("Unsupported metadata tag: {0}", tag);
                }

                if (payload.hasRemaining()) {
                    throw Exceptions.newIOException("Trailing metadata entry bytes");
                }
            }

            return builder.build();
        } catch (CharacterCodingException | IllegalArgumentException exception) {
            throw Exceptions.newIOException(exception, "Invalid metadata values");
        }
    }

    private static void writeEntryHeader(@NotNull ByteBuffer output, int tag, int payloadBytes) {
        output.putShort((short) tag).putShort((short) REQUIRED_FLAG).putInt(payloadBytes);
    }


    private final @Nullable String producerVersion;
    private final byte @Nullable [] executableDigest;
    private final byte @Nullable [] requestedReplayDigest;
    private final @Nullable LoadedReplayEvidence loadedReplayEvidence;
    private final @Nullable SamplingMethod samplingMethod;
    private final long pollingIntervalNanoseconds;
    private final int coverageRequirements;
    private final boolean digestEnabled;
    private final long expectedFirstFrame;
    private final long expectedLastFrame;

    private RecordMetadata(@NotNull Builder builder) {
        this.producerVersion            = builder.producerVersion;
        this.executableDigest           = builder.executableDigest == null ? null : builder.executableDigest.clone();
        this.requestedReplayDigest      = builder.requestedReplayDigest == null ? null : builder.requestedReplayDigest.clone();
        this.loadedReplayEvidence       = builder.loadedReplayEvidence;
        this.samplingMethod             = builder.samplingMethod;
        this.pollingIntervalNanoseconds = builder.pollingIntervalNanoseconds;
        this.coverageRequirements       = builder.coverageRequirements;
        this.digestEnabled              = builder.digestEnabled;
        this.expectedFirstFrame         = builder.expectedFirstFrame;
        this.expectedLastFrame          = builder.expectedLastFrame;
    }

    /**
     * @return the producer version, or {@code null} when unspecified
     */
    public @Nullable String getProducerVersion() {
        return this.producerVersion;
    }

    /**
     * @return an executable digest copy, or {@code null} when unspecified
     */
    public byte @Nullable [] getExecutableDigest() {
        return this.executableDigest == null ? null : this.executableDigest.clone();
    }

    /**
     * @return a replay digest copy, or {@code null} when unspecified
     */
    public byte @Nullable [] getRequestedReplayDigest() {
        return this.requestedReplayDigest == null ? null : this.requestedReplayDigest.clone();
    }

    /**
     * @return loaded replay header evidence, or {@code null} when unspecified
     */
    public @Nullable LoadedReplayEvidence getLoadedReplayEvidence() {
        return this.loadedReplayEvidence;
    }

    /**
     * @return the sampling method, or {@code null} when unspecified
     */
    public @Nullable SamplingMethod getSamplingMethod() {
        return this.samplingMethod;
    }

    /**
     * @return the polling interval, or {@code -1} when unspecified
     */
    public long getPollingIntervalNanoseconds() {
        return this.pollingIntervalNanoseconds;
    }

    /**
     * @return the collection quality requirement flags
     */
    public int getCoverageRequirements() {
        return this.coverageRequirements;
    }

    /**
     * @return {@code true} if canonical sample digests are permitted, {@code false} otherwise
     */
    public boolean isDigestEnabled() {
        return this.digestEnabled;
    }

    /**
     * @return the expected first frame, or {@code -1} when unknown
     */
    public long getExpectedFirstFrame() {
        return this.expectedFirstFrame;
    }

    /**
     * @return the expected last frame, or {@code -1} when unknown
     */
    public long getExpectedLastFrame() {
        return this.expectedLastFrame;
    }


    /**
     * Encodes metadata as little-endian entries.
     *
     * @return a newly allocated canonical metadata payload
     */
    public byte @NotNull [] encode() {
        byte[] producerBytes = this.producerVersion == null ? null : this.producerVersion.getBytes(StandardCharsets.UTF_8);
        int payloadBytes = ENTRY_HEADER_BYTES + Integer.BYTES + ENTRY_HEADER_BYTES + EXPECTED_FRAMES_BYTES;
        payloadBytes += producerBytes == null ? 0 : ENTRY_HEADER_BYTES + producerBytes.length;
        payloadBytes += this.executableDigest == null ? 0 : ENTRY_HEADER_BYTES + DIGEST_BYTES;
        payloadBytes += this.requestedReplayDigest == null ? 0 : ENTRY_HEADER_BYTES + DIGEST_BYTES;
        payloadBytes += this.loadedReplayEvidence == null ? 0 : ENTRY_HEADER_BYTES + Byte.BYTES;
        payloadBytes += this.samplingMethod == null ? 0 : ENTRY_HEADER_BYTES + Short.BYTES;
        payloadBytes += this.pollingIntervalNanoseconds < 0 ? 0 : ENTRY_HEADER_BYTES + Long.BYTES;
        payloadBytes += this.digestEnabled ? ENTRY_HEADER_BYTES + Short.BYTES : 0;

        ByteBuffer output = ByteBuffer.allocate(payloadBytes).order(ByteOrder.LITTLE_ENDIAN);

        if (producerBytes != null) {
            writeEntryHeader(output, PRODUCER_VERSION_TAG, producerBytes.length);
            output.put(producerBytes);
        }

        if (this.executableDigest != null) {
            writeEntryHeader(output, EXECUTABLE_DIGEST_TAG, DIGEST_BYTES);
            output.put(this.executableDigest);
        }

        if (this.requestedReplayDigest != null) {
            writeEntryHeader(output, REQUESTED_REPLAY_DIGEST_TAG, DIGEST_BYTES);
            output.put(this.requestedReplayDigest);
        }

        if (this.loadedReplayEvidence != null) {
            writeEntryHeader(output, LOADED_REPLAY_EVIDENCE_TAG, Byte.BYTES);
            output.put((byte) this.loadedReplayEvidence.getCode());
        }

        if (this.samplingMethod != null) {
            writeEntryHeader(output, SAMPLING_METHOD_TAG, Short.BYTES);
            output.putShort((short) this.samplingMethod.getCode());
        }

        if (this.pollingIntervalNanoseconds >= 0) {
            writeEntryHeader(output, POLLING_INTERVAL_TAG, Long.BYTES);
            output.putLong(this.pollingIntervalNanoseconds);
        }

        writeEntryHeader(output, COVERAGE_REQUIREMENTS_TAG, Integer.BYTES);
        output.putInt(this.coverageRequirements);

        if (this.digestEnabled) {
            writeEntryHeader(output, DIGEST_ALGORITHM_TAG, Short.BYTES);
            output.putShort((short) SHA256_ALGORITHM);
        }

        writeEntryHeader(output, EXPECTED_FRAMES_TAG, EXPECTED_FRAMES_BYTES);
        output.putLong(this.expectedFirstFrame).putLong(this.expectedLastFrame);

        return output.array();
    }
}
