package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.fogofwarlabs.sc4j.base.binary.BigEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.log.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Decompresses an MPQ sector using its compression mask.
 * Methods run in PKWare, bzip2, zlib, Huffman, sparse, stereo ADPCM and mono ADPCM order with the full sector capacity.
 */
final class MpqDecompressorUtils {
    /**
     * The failure result for a rejected sector.
     */
    static final int FAILED = -1;

    private static final int METHOD_HUFFMAN = 0x01;
    private static final int METHOD_ZLIB = 0x02;
    private static final int METHOD_PKWARE = 0x08;
    private static final int METHOD_BZIP2 = 0x10;
    private static final int METHOD_SPARSE = 0x20;
    private static final int METHOD_ADPCM_MONO = 0x40;
    private static final int METHOD_ADPCM_STEREO = 0x80;

    private static final int SPARSE_MINIMUM_SIZE = 5;
    private static final int SPARSE_HEADER_SIZE = 4;
    private static final int SPARSE_CHUNK_BASE = 0x20;
    private static final int SPARSE_COPY_FLAG = 0x80;
    private static final int SPARSE_LENGTH_MASK = 0x7F;
    /**
     * The unsupported mask bits rejected before running any method.
     */
    private static final int UNKNOWN_METHOD_BITS = 0x04;
    private static final int STEREO_CHANNELS = 2;
    private static final int MONO_CHANNELS = 1;
    private static final int BYTE_MASK = 0xFF;

    private static final @NotNull Logger LOGGER = Logger.of(MpqDecompressorUtils.class);

    /**
     * The ordered decompression methods.
     */
    private static final int @NotNull [] METHOD_ORDER = {
            METHOD_PKWARE, METHOD_BZIP2, METHOD_ZLIB, METHOD_HUFFMAN, METHOD_SPARSE, METHOD_ADPCM_STEREO, METHOD_ADPCM_MONO
    };

    /**
     * Decompresses one sector.
     *
     * @param input    the stored sector, starting at the method mask byte
     * @param output   the destination buffer
     * @param capacity the expected decompressed size of the sector
     * @return the number of bytes stored in {@code output}, or {@link #FAILED} if the sector cannot be decompressed
     * @throws IllegalStateException if the method order holds a method without a decoder
     */
    static int decompress(byte @NotNull [] input, byte @NotNull [] output, int capacity) {
        // Empty input produces an empty output.
        if (input.length == 0) {
            return 0;
        }

        if (Integer.compareUnsigned(capacity, input.length) < 0) {
            return FAILED;
        }

        if (capacity == input.length) {
            System.arraycopy(input, 0, output, 0, capacity);

            return capacity;
        }

        int methodMask = input[0] & BYTE_MASK;

        if ((methodMask & UNKNOWN_METHOD_BITS) != 0) {
            return FAILED;
        }

        byte[] data = Arrays.copyOfRange(input, Byte.BYTES, input.length);

        for (int method : METHOD_ORDER) {
            if ((methodMask & method) == 0) {
                continue;
            }

            byte[] methodOutput = switch (method) {
                case METHOD_PKWARE -> explode(data, capacity);
                case METHOD_ZLIB -> inflate(data, capacity);
                case METHOD_BZIP2 -> MpqBzip2.decompress(data, capacity);
                case METHOD_HUFFMAN -> MpqHuffman.decompress(data, capacity);
                case METHOD_SPARSE -> expandSparse(data, capacity);
                case METHOD_ADPCM_STEREO -> MpqAdpcmUtils.decompress(data, capacity, STEREO_CHANNELS);
                case METHOD_ADPCM_MONO -> MpqAdpcmUtils.decompress(data, capacity, MONO_CHANNELS);
                default -> throw Exceptions.newIllegalStateException("Unknown MPQ compression method: {0}", method);
            };

            if (methodOutput == null) {
                return FAILED;
            }

            data = methodOutput;
        }

        System.arraycopy(data, 0, output, 0, data.length);

        return data.length;
    }

    private static byte @Nullable [] explode(byte @NotNull [] data, int capacity) {
        // The mask-byte path uses a bounded output callback that silently drops bytes past the capacity.
        byte[] output = new byte[capacity];

        try {
            int storedCount = MpqExploderUtils.explodeInto(data, output, 0, capacity, false);

            return Arrays.copyOf(output, storedCount);
        } catch (IOException exception) {
            return null;
        }
    }

    private static byte @Nullable [] inflate(byte @NotNull [] data, int capacity) {
        // A zlib header is required, and the stream must reach its end within the destination bounds.
        try (Inflater inflater = new Inflater()) {
            byte[] output = new byte[capacity];

            inflater.setInput(data);

            try {
                int producedCount = 0;

                while (!inflater.finished()) {
                    int count = inflater.inflate(output, producedCount, capacity - producedCount);

                    producedCount += count;

                    if (count == 0 && (inflater.needsInput() || inflater.needsDictionary() || producedCount == capacity)) {
                        return null;
                    }
                }

                return Arrays.copyOf(output, producedCount);
            } catch (DataFormatException exception) {
                return null;
            }
        }
    }

    private static byte @Nullable [] expandSparse(byte @NotNull [] data, int capacity) {
        // A big-endian output size, then chunks of the low seven control bits plus 32 bytes, copied when the control's
        // high bit is set and zero otherwise. Output is capped by the declared size and destination capacity;
        // capacity and missing input bytes read as zero.
        if (data.length < SPARSE_MINIMUM_SIZE) {
            return null;
        }

        long declaredSize = BigEndianUtils.readUnsigned32(data, 0);

        if (declaredSize > capacity) {
            LOGGER.debug(() -> "MPQ compatibility: a sparse sector declares " + declaredSize + " bytes for a " + capacity + "-byte " +
                               "sector (the original writes past it); the output stops at the sector.");
        }

        byte[] output = new byte[(int) Math.min(declaredSize, capacity)];
        long remaining = declaredSize;
        int outputPosition = 0;
        int inputPosition = SPARSE_HEADER_SIZE;

        while (inputPosition < data.length) {
            int control = data[inputPosition++] & BYTE_MASK;
            int chunkSize = (int) Math.min((control & SPARSE_LENGTH_MASK) + SPARSE_CHUNK_BASE, remaining);

            if ((control & SPARSE_COPY_FLAG) != 0) {
                for (int index = 0; index < chunkSize; index++) {
                    int source = inputPosition + index;

                    if (outputPosition + index < output.length) {
                        output[outputPosition + index] = source < data.length ? data[source] : 0;
                    }
                }

                inputPosition += chunkSize;
            }

            outputPosition += chunkSize;
            remaining -= chunkSize;
        }

        return output;
    }


    private MpqDecompressorUtils() {
    }
}
