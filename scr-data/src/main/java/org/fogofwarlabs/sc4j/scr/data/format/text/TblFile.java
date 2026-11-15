package org.fogofwarlabs.sc4j.scr.data.format.text;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.text.TerminatedStringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Represents a parsed StarCraft TBL string table.
 */
public final class TblFile {
    /**
     * The string count WORD at the start of the file; the offset WORDs follow it.
     */
    private static final int HEADER_SIZE = Short.BYTES;
    private static final int OFFSET_SIZE = Short.BYTES;

    /**
     * Creates a {@link TblFile} instance from the given TBL file content.
     *
     * @param data the TBL file content
     * @return the created {@link TblFile} instance
     * @throws IOException if the TBL table is malformed
     */
    public static @NotNull TblFile parse(byte @NotNull [] data) throws IOException {
        if (data.length < HEADER_SIZE) {
            throw Exceptions.newIOException("The TBL file is too small: {0}", data.length);
        }

        int stringCount = LittleEndianUtils.readUnsigned16(data, 0);
        int tableEnd = HEADER_SIZE + stringCount * OFFSET_SIZE;

        if (tableEnd > data.length) {
            throw Exceptions.newIOException("The TBL offset table exceeds the file size.");
        }

        int[] offsets = new int[stringCount];

        for (int stringIndex = 0; stringIndex < stringCount; stringIndex++) {
            offsets[stringIndex] = LittleEndianUtils.readUnsigned16(data, HEADER_SIZE + stringIndex * OFFSET_SIZE);

            if (offsets[stringIndex] >= data.length && offsets[stringIndex] != 0) {
                throw Exceptions.newIOException("The TBL string offset is out of bounds at index {0}", stringIndex);
            }
        }

        return new TblFile(data, offsets);
    }


    private final byte @NotNull [] data;
    private final int @NotNull [] offsets;

    /**
     * Creates a {@link TblFile} instance.
     *
     * @param data    the TBL file content
     * @param offsets the string offsets
     */
    private TblFile(byte @NotNull [] data, int @NotNull [] offsets) {
        this.data    = data;
        this.offsets = offsets;
    }

    /**
     * @return the number of strings
     */
    public int getStringCount() {
        return this.offsets.length;
    }

    /**
     * Returns the raw bytes of the string at the given TBL index.
     *
     * @param stringIndex the TBL string index
     * @return the bytes up to the null terminator
     * @throws IndexOutOfBoundsException if the string index is out of range
     */
    public byte @NotNull [] getStringBytes(int stringIndex) {
        int stringOffset = this.offsets[stringIndex];

        if (stringOffset == 0) {
            return new byte[0];
        }

        byte[] stringBytes = new byte[TerminatedStringUtils.getTextLength(this.data, stringOffset)];

        System.arraycopy(this.data, stringOffset, stringBytes, 0, stringBytes.length);

        return stringBytes;
    }

    /**
     * Returns the string at the given TBL index using ISO-8859-1 as a lossless byte mapping.
     *
     * @param stringIndex the TBL string index
     * @return the string value
     */
    public @NotNull String getString(int stringIndex) {
        return this.getString(stringIndex, StandardCharsets.ISO_8859_1);
    }

    /**
     * Returns the string at the given TBL index using the given charset.
     *
     * @param stringIndex the TBL string index
     * @param charset     the text charset
     * @return the string value
     */
    public @NotNull String getString(int stringIndex, @NotNull Charset charset) {
        return new String(this.getStringBytes(stringIndex), charset);
    }
}
