package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.fogofwarlabs.sc4j.base.binary.BigEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.IntStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Decodes BLTE containers.
 * <p>
 * Supports the chunk modes {@code N} (uncompressed) and {@code Z} (zlib).
 * The modes {@code E} (encrypted) and {@code F} (frame) are rejected.
 */
public final class BlteUtils {
    private static final int ARRAY_SIZE_MARGIN = 8;
    private static final int CHUNK_MODE_SIZE = Byte.BYTES;
    private static final int INFLATE_OVERFLOW_PROBE_SIZE = Byte.BYTES;

    private static final byte @NotNull [] MAGIC = {'B', 'L', 'T', 'E'};
    private static final int MAGIC_SIZE = MAGIC.length;
    private static final int HEADER_SIZE_FIELD_SIZE = 4;
    private static final int CHUNK_TABLE_HEADER_SIZE = 4;
    /**
     * The offset of the low WORD of the 24-bit chunk count inside the chunk table header.
     */
    private static final int CHUNK_COUNT_OFFSET = 2;
    private static final int CHUNK_INFO_SIZE = 24;
    /**
     * The offset of the decoded size inside a chunk info record; the compressed size is at offset {@code 0}.
     */
    private static final int CHUNK_DECODED_SIZE_OFFSET = 4;
    /**
     * The largest decoded size an array can hold on common JVMs.
     */
    private static final int MAXIMUM_DECODED_SIZE = Integer.MAX_VALUE - ARRAY_SIZE_MARGIN;

    /**
     * The decoded size and chunk count from which the chunks of one container inflate in parallel.
     */
    private static final int PARALLEL_MINIMUM_SIZE = 4 << 20;
    private static final int PARALLEL_MINIMUM_CHUNKS = 4;

    private static final int INFLATE_INITIAL_CAPACITY = 4096;
    private static final int INFLATE_BUFFER_SIZE = 8192;

    private static final byte MODE_UNCOMPRESSED = 'N';
    private static final byte MODE_ZLIB = 'Z';

    /**
     * Decodes the given BLTE container.
     *
     * @param blteData the BLTE container including the {@code BLTE} magic
     * @return the decoded content
     * @throws IOException if the container is malformed or holds an unsupported chunk mode
     */
    public static byte @NotNull [] decode(byte @NotNull [] blteData) throws IOException {
        return decode(blteData, 0, blteData.length);
    }

    /**
     * Decodes a BLTE container stored inside a larger array, such as a CASC data file record after its local header. A
     * container with a chunk table is decoded straight into one array of its declared total size, without intermediate
     * chunk copies.
     *
     * @param data   the array holding the container
     * @param offset the first byte of the {@code BLTE} magic
     * @param length the container length in bytes
     * @return the decoded content
     * @throws IOException               if the container is malformed or holds an unsupported chunk mode
     * @throws IndexOutOfBoundsException if {@code offset} and {@code length} do not describe a range inside {@code data}
     */
    public static byte @NotNull [] decode(byte @NotNull [] data, int offset, int length) throws IOException {
        if (length < MAGIC_SIZE + HEADER_SIZE_FIELD_SIZE + CHUNK_MODE_SIZE) {
            throw Exceptions.newIOException("The BLTE container is too small: {0}", length);
        }

        for (int index = 0; index < MAGIC_SIZE; index++) {
            if (data[offset + index] != MAGIC[index]) {
                throw Exceptions.newIOException("The BLTE container has an invalid magic.");
            }
        }

        int end = offset + length;
        int headerSize = BigEndianUtils.readInt32(data, offset + MAGIC_SIZE);

        if (headerSize == 0) {
            int chunkOffset = offset + MAGIC_SIZE + HEADER_SIZE_FIELD_SIZE;

            return decodeChunk(data, chunkOffset, end - chunkOffset);
        }

        if (headerSize > length) {
            throw Exceptions.newIOException("The BLTE header size exceeds the container size: {0}", headerSize);
        }

        int chunkCount = BigEndianUtils.readUnsigned16(data, offset + MAGIC_SIZE + HEADER_SIZE_FIELD_SIZE + CHUNK_COUNT_OFFSET);
        int chunkTableOffset = offset + MAGIC_SIZE + HEADER_SIZE_FIELD_SIZE + CHUNK_TABLE_HEADER_SIZE;
        long decodedSize = 0;

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int decompressedSize = BigEndianUtils.readInt32(data, chunkTableOffset + chunkIndex * CHUNK_INFO_SIZE + CHUNK_DECODED_SIZE_OFFSET);

            if (decompressedSize < 0) {
                throw Exceptions.newIOException("The BLTE chunk has an invalid decoded size: {0}", chunkIndex);
            }

            decodedSize += decompressedSize;
        }

        if (decodedSize > MAXIMUM_DECODED_SIZE) {
            throw Exceptions.newIOException("The BLTE content is too large: {0}", decodedSize);
        }

        byte[] decodedContent = new byte[(int) decodedSize];
        int[] chunkDataOffsets = new int[chunkCount];
        int[] decodedOffsets = new int[chunkCount];
        int chunkDataOffset = offset + headerSize;
        int decodedOffset = 0;

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int compressedSize = BigEndianUtils.readInt32(data, chunkTableOffset + chunkIndex * CHUNK_INFO_SIZE);

            if (compressedSize < CHUNK_MODE_SIZE || chunkDataOffset + (long) compressedSize > end) {
                throw Exceptions.newIOException("The BLTE chunk exceeds the container size: {0}", chunkIndex);
            }

            chunkDataOffsets[chunkIndex] = chunkDataOffset;
            decodedOffsets[chunkIndex]   = decodedOffset;

            decodedOffset += BigEndianUtils.readInt32(data, chunkTableOffset + chunkIndex * CHUNK_INFO_SIZE + CHUNK_DECODED_SIZE_OFFSET);
            chunkDataOffset += compressedSize;
        }

        if (chunkCount >= PARALLEL_MINIMUM_CHUNKS && decodedSize >= PARALLEL_MINIMUM_SIZE) {
            // The chunks are independent streams writing disjoint ranges: a large file (an HD tileset or anim) inflates
            // them on the common pool.
            try {
                IntStream.range(0, chunkCount).parallel().forEach(chunkIndex -> {
                    try {
                        decodeChunkInto(null,
                                        data,
                                        chunkTableOffset, chunkIndex,
                                        chunkDataOffsets[chunkIndex],
                                        decodedContent, decodedOffsets[chunkIndex]);
                    } catch (IOException exception) {
                        throw Exceptions.newUncheckedIOException(exception);
                    }
                });
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }

            return decodedContent;
        }

        try (Inflater inflater = new Inflater()) {
            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                decodeChunkInto(inflater,
                                data,
                                chunkTableOffset, chunkIndex,
                                chunkDataOffsets[chunkIndex],
                                decodedContent, decodedOffsets[chunkIndex]);
            }
        }

        return decodedContent;
    }

    /**
     * Decodes one chunk of a chunk table into its place in the decoded content.
     *
     * @param inflater          an inflater to reuse, or {@code null} to use a new one
     * @param data              the array holding the container
     * @param chunkTableOffset  the offset of the first chunk info record
     * @param chunkIndex        the chunk index
     * @param chunkDataOffset   the offset of the chunk's mode byte
     * @param destination       the decoded content
     * @param destinationOffset the offset of the chunk inside the decoded content
     * @throws IOException if the chunk is malformed or has an unsupported mode
     */
    private static void decodeChunkInto(@Nullable Inflater inflater,
                                        byte @NotNull [] data,
                                        int chunkTableOffset, int chunkIndex,
                                        int chunkDataOffset,
                                        byte @NotNull [] destination, int destinationOffset) throws IOException {
        int chunkInfoOffset = chunkTableOffset + chunkIndex * CHUNK_INFO_SIZE;
        int compressedSize = BigEndianUtils.readInt32(data, chunkInfoOffset);
        int decompressedSize = BigEndianUtils.readInt32(data, chunkInfoOffset + CHUNK_DECODED_SIZE_OFFSET);
        byte mode = data[chunkDataOffset];

        if (mode == MODE_UNCOMPRESSED) {
            if (compressedSize - CHUNK_MODE_SIZE != decompressedSize) {
                throw Exceptions.newIOException("The BLTE uncompressed chunk does not match its decoded size: {0}", chunkIndex);
            }

            System.arraycopy(data, chunkDataOffset + CHUNK_MODE_SIZE, destination, destinationOffset, decompressedSize);
        } else if (mode == MODE_ZLIB) {
            if (inflater != null) {
                inflater.reset();
                inflateInto(inflater, data, chunkDataOffset + CHUNK_MODE_SIZE, compressedSize - CHUNK_MODE_SIZE,
                            destination, destinationOffset, decompressedSize);
            } else {
                try (Inflater chunkInflater = new Inflater()) {
                    inflateInto(chunkInflater, data, chunkDataOffset + CHUNK_MODE_SIZE, compressedSize - CHUNK_MODE_SIZE,
                                destination, destinationOffset, decompressedSize);
                }
            }
        } else {
            throw Exceptions.newIOException("Unsupported BLTE chunk mode: {0}", (char) mode);
        }
    }

    private static byte @NotNull [] decodeChunk(byte @NotNull [] data, int offset, int compressedSize) throws IOException {
        byte mode = data[offset];

        if (mode == MODE_UNCOMPRESSED) {
            byte[] decodedChunk = new byte[compressedSize - CHUNK_MODE_SIZE];

            System.arraycopy(data, offset + CHUNK_MODE_SIZE, decodedChunk, 0, decodedChunk.length);

            return decodedChunk;
        }

        if (mode == MODE_ZLIB) {
            return inflateChunk(data, offset + CHUNK_MODE_SIZE, compressedSize - CHUNK_MODE_SIZE);
        }

        throw Exceptions.newIOException("Unsupported BLTE chunk mode: {0}", (char) mode);
    }

    /**
     * Inflates one zlib chunk of a declared decoded size into its place in the decoded content.
     *
     * @param inflater          the inflater, reset by the caller
     * @param data              the array holding the chunk
     * @param offset            the first compressed byte
     * @param compressedSize    the compressed size in bytes
     * @param destination       the decoded content
     * @param destinationOffset the offset of the chunk inside the decoded content
     * @param decompressedSize  the declared decoded size in bytes
     * @throws IOException if the chunk is malformed, truncated or does not match its declared size
     */
    private static void inflateInto(@NotNull Inflater inflater,
                                    byte @NotNull [] data, int offset, int compressedSize,
                                    byte @NotNull [] destination, int destinationOffset, int decompressedSize) throws IOException {
        int produced = 0;

        inflater.setInput(data, offset, compressedSize);

        try {
            while (produced < decompressedSize) {
                int producedSize = inflater.inflate(destination, destinationOffset + produced, decompressedSize - produced);

                if (producedSize == 0) {
                    if (inflater.finished()) {
                        throw Exceptions.newIOException("The BLTE zlib chunk is shorter than its decoded size.");
                    }

                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw Exceptions.newIOException("The BLTE zlib chunk is truncated.");
                    }
                }

                produced += producedSize;
            }

            if (!inflater.finished() && inflater.inflate(new byte[INFLATE_OVERFLOW_PROBE_SIZE]) != 0) {
                throw Exceptions.newIOException("The BLTE zlib chunk exceeds its decoded size.");
            }
        } catch (DataFormatException exception) {
            throw Exceptions.newIOException(exception, "The BLTE zlib chunk is malformed.");
        }
    }

    private static byte @NotNull [] inflateChunk(byte @NotNull [] data, int offset, int compressedSize) throws IOException {
        ByteArrayOutputStream decodedChunk;

        try (Inflater inflater = new Inflater()) {
            decodedChunk = new ByteArrayOutputStream(INFLATE_INITIAL_CAPACITY);
            byte[] buffer = new byte[INFLATE_BUFFER_SIZE];

            inflater.setInput(data, offset, compressedSize);

            try {
                while (!inflater.finished()) {
                    int producedSize = inflater.inflate(buffer);

                    if (producedSize == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                        throw Exceptions.newIOException("The BLTE zlib chunk is truncated.");
                    }

                    decodedChunk.write(buffer, 0, producedSize);
                }
            } catch (DataFormatException exception) {
                throw Exceptions.newIOException(exception, "The BLTE zlib chunk is malformed.");
            }
        }

        return decodedChunk.toByteArray();
    }

    private BlteUtils() {
    }
}
