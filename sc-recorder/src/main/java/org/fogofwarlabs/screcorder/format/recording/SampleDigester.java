package org.fogofwarlabs.screcorder.format.recording;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes the canonical digest of one sample, exactly as a {@code DIGEST} observation stores it.
 * <p>
 * The digest is SHA-256 over the ASCII domain {@code SCREC-STATE}, the format major and minor versions (u16 each),
 * the sample identifier (u32) and the canonical {@code VALUES} payload of the sample. A program that builds the same sample
 * values, for example from a simulation, obtains byte-identical digests and can compare them with a recording.
 * <p>
 * Instances reuse their buffers and are confined to one thread.
 */
public final class SampleDigester {
    /**
     * The digest length in bytes.
     */
    public static final int DIGEST_BYTES = RecordFormatUtils.DIGEST_BYTES;

    private static final int INITIAL_BUFFER_BYTES = 4096;
    private static final int VERSION_BYTES = Short.BYTES * 2;
    private static final byte @NotNull [] DIGEST_DOMAIN = "SCREC-STATE".getBytes(StandardCharsets.US_ASCII);

    private final @NotNull MessageDigest digest;
    private final byte @NotNull [] prefix;
    private byte @NotNull [] canonical;

    /**
     * Creates a {@link SampleDigester} instance.
     *
     * @throws IllegalStateException if SHA-256 is unavailable
     */
    public SampleDigester() {
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw Exceptions.newIllegalStateException(exception, "SHA-256 is unavailable");
        }

        this.prefix = new byte[DIGEST_DOMAIN.length + VERSION_BYTES + Integer.BYTES];
        System.arraycopy(DIGEST_DOMAIN, 0, this.prefix, 0, DIGEST_DOMAIN.length);
        this.canonical = new byte[INITIAL_BUFFER_BYTES];
    }


    /**
     * Computes the canonical digest of a sample.
     *
     * @param definition    the sample definition
     * @param sample        the sample values
     * @param <SampleValue> the sample type
     * @return a new {@code 32}-byte digest
     * @throws IOException if the sample cannot be encoded canonically
     */
    public <SampleValue extends Sample> byte @NotNull [] digest(@NotNull SampleDefinition<SampleValue> definition, @NotNull SampleValue sample) throws IOException {
        byte[] result = new byte[DIGEST_BYTES];

        this.digest(definition, sample, result, 0);

        return result;
    }

    /**
     * Computes the canonical digest of a sample into a caller-owned array.
     *
     * @param definition    the sample definition
     * @param sample        the sample values
     * @param destination   the destination array
     * @param offset        the destination offset of the {@code 32} digest bytes
     * @param <SampleValue> the sample type
     * @throws IOException if the sample cannot be encoded canonically
     */
    public <SampleValue extends Sample> void digest(@NotNull SampleDefinition<SampleValue> definition, @NotNull SampleValue sample,
                                                    byte @NotNull [] destination, int offset) throws IOException {
        int valueBytes = RecordFormatUtils.getValueBytes(definition, sample);

        this.canonical = RecordFormatUtils.growBuffer(this.canonical, valueBytes);

        ByteBuffer values = ByteBuffer.wrap(this.canonical, 0, valueBytes).slice().order(ByteOrder.LITTLE_ENDIAN);

        RecordFormatUtils.writeValues(definition, sample, values, valueBytes);

        ByteBuffer header = ByteBuffer.wrap(this.prefix).order(ByteOrder.LITTLE_ENDIAN);

        header.position(DIGEST_DOMAIN.length);
        header.putShort(RecordFormatUtils.FORMAT_MAJOR_VERSION).putShort(RecordFormatUtils.FORMAT_MINOR_VERSION).putInt(definition.getIdentifier());

        this.digest.reset();
        this.digest.update(this.prefix);
        this.digest.update(this.canonical, 0, valueBytes);

        try {
            this.digest.digest(destination, offset, DIGEST_BYTES);
        } catch (DigestException exception) {
            throw Exceptions.newIOException(exception, "Cannot store canonical sample digest");
        }
    }
}
