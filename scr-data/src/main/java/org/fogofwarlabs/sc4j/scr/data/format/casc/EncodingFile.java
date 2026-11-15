package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.fogofwarlabs.sc4j.base.binary.BigEndianUtils;
import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Represents a parsed CASC encoding file.
 * <p>
 * The encoding file maps content keys to encoding keys. The layout follows the TACT
 * encoding table: a fixed header, an encoding specification string block, an array of
 * page headers, then the content key pages with variable-length entries. Only the
 * content key page table is loaded because the encoding key page table is not needed
 * for file extraction.
 */
public final class EncodingFile {
    private static final int BYTE_MASK = 0xFF;
    private static final int MAGIC_EN = 0x454E;
    private static final int KEY_SIZE = 16;
    private static final int HEADER_SIZE = 22;
    private static final int PAGE_HEADER_SIZE = 32;
    private static final int ENTRY_HEADER_SIZE = 6;

    private static final int HEADER_VERSION_OFFSET = 2;
    private static final int HEADER_CONTENT_KEY_SIZE_OFFSET = 3;
    private static final int HEADER_ENCODING_KEY_SIZE_OFFSET = 4;
    private static final int HEADER_CONTENT_PAGE_SIZE_OFFSET = 5;
    private static final int HEADER_CONTENT_PAGE_COUNT_OFFSET = 9;
    private static final int HEADER_SPECIFICATION_SIZE_OFFSET = 18;
    /**
     * The unit of the page size field: kilobytes.
     */
    private static final int PAGE_SIZE_UNIT = 1024;
    private static final int ENTRY_CONTENT_SIZE_OFFSET = 2;

    private static final int VERSION = 1;

    /**
     * Represents a single content key entry.
     */
    public static final class Entry {
        private final byte @NotNull [] encodingKey;
        private final long contentSize;

        /**
         * Creates an {@link Entry} instance.
         *
         * @param encodingKey the 16-byte encoding key
         * @param contentSize the decoded content size of the file
         */
        private Entry(byte @NotNull [] encodingKey, long contentSize) {
            this.encodingKey = encodingKey;
            this.contentSize = contentSize;
        }

        /**
         * @return the 16-byte encoding key
         */
        public byte @NotNull [] getEncodingKey() {
            return this.encodingKey;
        }

        /**
         * @return the decoded content size of the file
         */
        public long getContentSize() {
            return this.contentSize;
        }
    }

    /**
     * Parses the given encoding file content.
     *
     * @param content the BLTE-decoded encoding file content
     * @return the parsed {@link EncodingFile} instance
     * @throws IOException if the content is not a valid encoding file
     */
    public static @NotNull EncodingFile parse(byte @NotNull [] content) throws IOException {
        if (content.length < HEADER_SIZE) {
            throw Exceptions.newIOException("The encoding file is too small: {0}", content.length);
        }

        int magic = BigEndianUtils.readUnsigned16(content, 0);

        if (magic != MAGIC_EN) {
            throw Exceptions.newIOException("The encoding file has an invalid magic: 0x{0}", Integer.toHexString(magic));
        }

        int version = content[HEADER_VERSION_OFFSET] & BYTE_MASK;
        int hashSizeContentKey = content[HEADER_CONTENT_KEY_SIZE_OFFSET] & BYTE_MASK;
        int hashSizeEncodingKey = content[HEADER_ENCODING_KEY_SIZE_OFFSET] & BYTE_MASK;

        if (version != VERSION) {
            throw Exceptions.newIOException("Unsupported encoding file version: {0}", version);
        }

        if (hashSizeContentKey != KEY_SIZE || hashSizeEncodingKey != KEY_SIZE) {
            throw Exceptions.newIOException("Unsupported encoding key sizes: {0}, {1}", hashSizeContentKey, hashSizeEncodingKey);
        }

        int pageSizeContentKey = BigEndianUtils.readUnsigned16(content, HEADER_CONTENT_PAGE_SIZE_OFFSET) * PAGE_SIZE_UNIT;
        int pageCountContentKey = BigEndianUtils.readInt32(content, HEADER_CONTENT_PAGE_COUNT_OFFSET);
        int specificationBlockSize = BigEndianUtils.readInt32(content, HEADER_SPECIFICATION_SIZE_OFFSET);
        Map<String, Entry> entries = new HashMap<>();
        int pagesOffset = HEADER_SIZE + specificationBlockSize + pageCountContentKey * PAGE_HEADER_SIZE;

        for (int pageIndex = 0; pageIndex < pageCountContentKey; pageIndex++) {
            int pageOffset = pagesOffset + pageIndex * pageSizeContentKey;
            int pageEnd = Math.min(pageOffset + pageSizeContentKey, content.length);
            int entryOffset = pageOffset;

            while (entryOffset + ENTRY_HEADER_SIZE + KEY_SIZE <= pageEnd) {
                int encodingKeyCount = LittleEndianUtils.readUnsigned16(content, entryOffset);

                if (encodingKeyCount == 0) {
                    break;
                }

                long contentSize = BigEndianUtils.readInt32(content, entryOffset + ENTRY_CONTENT_SIZE_OFFSET);
                byte[] contentKey = new byte[KEY_SIZE];

                System.arraycopy(content, entryOffset + ENTRY_HEADER_SIZE, contentKey, 0, KEY_SIZE);

                int encodingKeysOffset = entryOffset + ENTRY_HEADER_SIZE + KEY_SIZE;

                if (encodingKeysOffset + (long) encodingKeyCount * KEY_SIZE > pageEnd) {
                    break;
                }

                byte[] encodingKey = new byte[KEY_SIZE];

                System.arraycopy(content, encodingKeysOffset, encodingKey, 0, KEY_SIZE);
                entries.put(HexFormat.of().formatHex(contentKey), new Entry(encodingKey, contentSize));

                entryOffset = encodingKeysOffset + encodingKeyCount * KEY_SIZE;
            }
        }

        return new EncodingFile(entries);
    }


    private final @NotNull Map<String, Entry> entries;

    /**
     * Creates an {@link EncodingFile} instance.
     *
     * @param entries the entries keyed by the hex of the content key
     */
    private EncodingFile(@NotNull Map<String, Entry> entries) {
        this.entries = entries;
    }


    /**
     * Finds the entry of the given content key.
     *
     * @param contentKey the 16-byte content key
     * @return the matching entry, or {@code null} if not found
     */
    public @Nullable Entry findEntry(byte @NotNull [] contentKey) {
        return this.entries.get(HexFormat.of().formatHex(contentKey));
    }
}
