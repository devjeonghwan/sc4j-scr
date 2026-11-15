package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.fogofwarlabs.sc4j.base.binary.BigEndianUtils;
import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Represents the loaded CASC index ({@code .idx}) files.
 * <p>
 * Each index entry maps the first 9 bytes of an encoding key to the location of the
 * file data inside a {@code data.XXX} file. The offset points to a 30-byte local header
 * that is followed by the BLTE container.
 */
public final class CascIndex {
    private static final int BYTE_MASK = 0xFF;
    /**
     * The length of an index file name {@code BBVVVVVVVV.idx}: bucket, version and extension.
     */
    private static final int FILE_NAME_LENGTH = 14;
    private static final int FILE_NAME_BUCKET_END = 2;
    private static final int FILE_NAME_VERSION_END = 10;
    private static final int HEXADECIMAL_RADIX = 16;

    private static final int GUARDED_BLOCK_SIZE = 8;
    private static final int GUARDED_BLOCK_SIZE_FIELD_SIZE = 4;
    private static final int HEADER_SIZE = 16;
    private static final int HEADER_PADDING = 8;
    private static final int ENTRY_SIZE = 18;
    private static final int KEY_SIZE = 9;

    private static final int HEADER_REVISION_SIZE = 2;
    private static final int HEADER_BUCKET_OFFSET = 2;
    private static final int HEADER_FLAGS_OFFSET = 3;
    private static final int HEADER_SPAN_SIZE_BYTES_OFFSET = 4;
    private static final int HEADER_SPAN_OFFSET_BYTES_OFFSET = 5;
    private static final int HEADER_KEY_BYTES_OFFSET = 6;
    private static final int HEADER_SEGMENT_BITS_OFFSET = 7;

    private static final int INDEX_REVISION = 7;
    private static final int SPAN_SIZE_BYTES = 4;
    private static final int SPAN_OFFSET_BYTES = 5;
    private static final int SEGMENT_BITS = 30;

    /**
     * Represents a single index entry.
     */
    public static final class Entry {
        private final int dataFileIndex;
        private final long offset;
        private final long size;

        /**
         * Creates an {@link Entry} instance.
         *
         * @param dataFileIndex the index of the {@code data.XXX} file
         * @param offset        the offset of the local header inside the data file
         * @param size          the size of the local header plus the compressed data
         */
        private Entry(int dataFileIndex, long offset, long size) {
            this.dataFileIndex = dataFileIndex;
            this.offset        = offset;
            this.size          = size;
        }

        /**
         * @return the index of the {@code data.XXX} file
         */
        public int getDataFileIndex() {
            return this.dataFileIndex;
        }

        /**
         * @return the offset of the local header inside the data file
         */
        public long getOffset() {
            return this.offset;
        }

        /**
         * @return the size of the local header plus the compressed data
         */
        public long getSize() {
            return this.size;
        }
    }

    /**
     * Loads the newest index file of every bucket from the given directory.
     *
     * @param indexDirectory the directory that holds the {@code .idx} files
     * @return the loaded {@link CascIndex} instance
     * @throws IOException if an index file cannot be read
     */
    public static @NotNull CascIndex loadFrom(@NotNull Path indexDirectory) throws IOException {
        Map<Integer, Path> newestIndexFiles = new HashMap<>();

        try (DirectoryStream<Path> indexFiles = Files.newDirectoryStream(indexDirectory, "*.idx")) {
            for (Path indexFile : indexFiles) {
                String fileName = indexFile.getFileName().toString();

                if (fileName.length() != FILE_NAME_LENGTH) {
                    continue;
                }

                int bucket = Integer.parseInt(fileName.substring(0, FILE_NAME_BUCKET_END), HEXADECIMAL_RADIX);
                int version = Integer.parseInt(fileName.substring(FILE_NAME_BUCKET_END, FILE_NAME_VERSION_END), HEXADECIMAL_RADIX);
                Path previousFile = newestIndexFiles.get(bucket);

                if (previousFile == null ||
                    version > Integer.parseInt(previousFile.getFileName().toString().substring(FILE_NAME_BUCKET_END, FILE_NAME_VERSION_END),
                                               HEXADECIMAL_RADIX)) {
                    newestIndexFiles.put(bucket, indexFile);
                }
            }
        }

        Map<String, Entry> entries = new HashMap<>();

        for (Map.Entry<Integer, Path> indexFile : newestIndexFiles.entrySet()) {
            loadEntries(indexFile.getKey(), indexFile.getValue(), entries);
        }

        return new CascIndex(entries);
    }

    private static void loadEntries(int bucket, @NotNull Path indexFile, @NotNull Map<String, Entry> entries) throws IOException {
        byte[] indexData = Files.readAllBytes(indexFile);

        // The file starts with a guarded block that wraps the 16-byte index header.
        int headerBlockSize = (int) LittleEndianUtils.readUnsigned(indexData, 0, GUARDED_BLOCK_SIZE_FIELD_SIZE);

        if (headerBlockSize != HEADER_SIZE) {
            throw Exceptions.newIOException("The index file has an invalid header block size: {0}", indexFile);
        }

        int headerOffset = GUARDED_BLOCK_SIZE;

        if (indexData.length < headerOffset + HEADER_SIZE) {
            throw Exceptions.newIOException("The index file is too small: {0}", indexFile);
        }

        int revision = (int) LittleEndianUtils.readUnsigned(indexData, headerOffset, HEADER_REVISION_SIZE);
        int bucketIndex = indexData[headerOffset + HEADER_BUCKET_OFFSET] & BYTE_MASK;
        int flags = indexData[headerOffset + HEADER_FLAGS_OFFSET] & BYTE_MASK;
        int spanSizeBytes = indexData[headerOffset + HEADER_SPAN_SIZE_BYTES_OFFSET] & BYTE_MASK;
        int spanOffsetBytes = indexData[headerOffset + HEADER_SPAN_OFFSET_BYTES_OFFSET] & BYTE_MASK;
        int keyBytes = indexData[headerOffset + HEADER_KEY_BYTES_OFFSET] & BYTE_MASK;
        int segmentBits = indexData[headerOffset + HEADER_SEGMENT_BITS_OFFSET] & BYTE_MASK;

        if (revision != INDEX_REVISION || bucketIndex != bucket || flags != 0) {
            throw Exceptions.newIOException("The index file has an invalid header: {0}", indexFile);
        }

        if (spanSizeBytes != SPAN_SIZE_BYTES || spanOffsetBytes != SPAN_OFFSET_BYTES || keyBytes != KEY_SIZE || segmentBits != SEGMENT_BITS) {
            throw Exceptions.newIOException("The index file has an unsupported entry layout: {0}", indexFile);
        }

        // A second guarded block wraps the entry array.
        int entriesBlockOffset = headerOffset + HEADER_SIZE + HEADER_PADDING;
        int entriesBlockSize = (int) LittleEndianUtils.readUnsigned(indexData, entriesBlockOffset, GUARDED_BLOCK_SIZE_FIELD_SIZE);
        int entriesOffset = entriesBlockOffset + GUARDED_BLOCK_SIZE;

        if (entriesOffset + entriesBlockSize > indexData.length) {
            throw Exceptions.newIOException("The index file has a truncated entry block: {0}", indexFile);
        }

        int entryCount = entriesBlockSize / ENTRY_SIZE;

        for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
            int entryOffset = entriesOffset + entryIndex * ENTRY_SIZE;
            byte[] key = new byte[KEY_SIZE];

            System.arraycopy(indexData, entryOffset, key, 0, KEY_SIZE);

            long indexAndOffset = BigEndianUtils.readUnsigned(indexData, entryOffset + KEY_SIZE, SPAN_OFFSET_BYTES);
            int dataFileIndex = (int) (indexAndOffset >>> SEGMENT_BITS);
            long offset = indexAndOffset & ((1L << SEGMENT_BITS) - 1);
            long size = LittleEndianUtils.readUnsigned(indexData, entryOffset + KEY_SIZE + SPAN_OFFSET_BYTES, SPAN_SIZE_BYTES);

            entries.put(HexFormat.of().formatHex(key), new Entry(dataFileIndex, offset, size));
        }
    }


    private final @NotNull Map<String, Entry> entries;

    /**
     * Creates a {@link CascIndex} instance.
     *
     * @param entries the index entries keyed by the hex of the 9-byte key prefix
     */
    private CascIndex(@NotNull Map<String, Entry> entries) {
        this.entries = entries;
    }


    /**
     * Finds the index entry of the given encoding key.
     *
     * @param encodingKey the 16-byte encoding key
     * @return the matching entry, or {@code null} if not found
     */
    public @Nullable Entry find(byte @NotNull [] encodingKey) {
        byte[] key = new byte[KEY_SIZE];

        System.arraycopy(encodingKey, 0, key, 0, KEY_SIZE);

        return this.entries.get(HexFormat.of().formatHex(key));
    }
}
