package org.fogofwarlabs.screcorder.binary;

import org.jetbrains.annotations.NotNull;

/**
 * Reads little-endian values from byte arrays: the byte order of every StarCraft file format and native structure.
 * <p>
 * The readers do not check bounds beyond the array access itself.
 */
public final class LittleEndianUtils {
    private static final int BYTE_MASK = 0xFF;
    private static final long DWORD_MASK = 0xFFFFFFFFL;


    /**
     * Reads an unsigned little-endian WORD.
     *
     * @param data   the bytes
     * @param offset the offset of the WORD
     * @return the unsigned WORD at {@code offset}
     */
    public static int readUnsigned16(byte @NotNull [] data, int offset) {
        return (data[offset] & BYTE_MASK) | (data[offset + 1] & BYTE_MASK) << Byte.SIZE;
    }

    /**
     * Reads a little-endian DWORD.
     *
     * @param data   the bytes
     * @param offset the offset of the DWORD
     * @return the DWORD at {@code offset} as a signed {@code int}
     */
    public static int readInt32(byte @NotNull [] data, int offset) {
        return readUnsigned16(data, offset) | readUnsigned16(data, offset + Short.BYTES) << Short.SIZE;
    }

    /**
     * Reads an unsigned little-endian DWORD.
     *
     * @param data   the bytes
     * @param offset the offset of the DWORD
     * @return the unsigned DWORD at {@code offset}
     */
    public static long readUnsigned32(byte @NotNull [] data, int offset) {
        return readInt32(data, offset) & DWORD_MASK;
    }

    /**
     * Reads an unsigned little-endian value of up to eight bytes.
     *
     * @param data   the bytes
     * @param offset the offset of the value
     * @param length the value width in bytes, {@code 0} through {@code 8}
     * @return the unsigned value of {@code length} bytes at {@code offset}
     */
    public static long readUnsigned(byte @NotNull [] data, int offset, int length) {
        long value = 0;

        for (int index = length - 1; index >= 0; index--) {
            value = value << Byte.SIZE | (data[offset + index] & (long) BYTE_MASK);
        }

        return value;
    }


    private LittleEndianUtils() {
    }
}
