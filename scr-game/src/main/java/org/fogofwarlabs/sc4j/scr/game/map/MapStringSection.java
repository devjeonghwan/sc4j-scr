package org.fogofwarlabs.sc4j.scr.game.map;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.scr.data.format.text.LegacyTextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Holds the map string section the loader keeps ({@code STR } or {@code STRx}) as mutable bytes.
 * <p>
 * Trigger text resolves through it, and the EUD emulation serves it as one region (the pointee of the classic string pointer), so a write through the
 * emulation changes the text later actions show.
 */
public final class MapStringSection {
    /**
     * The size of the string count and of an offset entry of a {@code STR } section (WORDs).
     */
    private static final int ENTRY_SIZE = 2;
    /**
     * The size of the string count and of an offset entry of a {@code STRx} section (DWORDs).
     */
    private static final int EXTENDED_ENTRY_SIZE = 4;
    /**
     * The index of the first string; string indices are one-based.
     */
    private static final int FIRST_STRING_INDEX = 1;
    /**
     * The log2 of the byte span that shares one change stamp.
     */
    private static final int CHANGE_BLOCK_SHIFT = 6;
    /**
     * The log2 of the byte span that shares one coarse change stamp, checked before its blocks.
     */
    private static final int CHANGE_GROUP_SHIFT = 10;
    /**
     * The mask of one unsigned byte.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * Little-endian DWORD access to {@link #bytes} at any offset.
     */
    private static final @NotNull VarHandle DWORD = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    private final byte @NotNull [] bytes;
    private final boolean extended;

    /**
     * The number of byte writes that changed a byte so far; such a write stamps its block and group with the new count.
     */
    private long changeCount;
    /**
     * The change count of the last change in each block, {@code 0} for a block never changed.
     */
    private final long @NotNull [] blockChangeCounts;
    /**
     * The change count of the last change in each group of blocks.
     */
    private final long @NotNull [] groupChangeCounts;

    /**
     * Creates a {@link MapStringSection} instance with a copy of a section payload and no changes.
     *
     * @param payload  the section payload, copied
     * @param extended whether the section is {@code STRx} (DWORD count and offsets)
     */
    public MapStringSection(byte @NotNull [] payload, boolean extended) {
        this.bytes    = payload.clone();
        this.extended = extended;

        this.changeCount       = 0;
        this.blockChangeCounts = new long[(this.bytes.length >>> CHANGE_BLOCK_SHIFT) + 1];
        this.groupChangeCounts = new long[(this.bytes.length >>> CHANGE_GROUP_SHIFT) + 1];
    }

    /**
     * @return {@code true} if the section is {@code STRx} (DWORD count and offsets), {@code false} otherwise
     */
    public boolean isExtended() {
        return this.extended;
    }

    /**
     * @return the section size in bytes
     */
    public int getSize() {
        return this.bytes.length;
    }

    /**
     * @return the number of byte writes that changed a byte so far, which only grows
     */
    public long getChangeCount() {
        return this.changeCount;
    }

    /**
     * Resolves a string as the loader's lookup: no string for a section shorter than one entry, an index below {@code 1} or past the count, an offset
     * entry past the section or an offset at or past the section size; otherwise the bytes up to the first NUL.
     *
     * @param stringIndex the one-based string index
     * @return the string bytes, or {@code null} when the lookup finds no string
     */
    public byte @Nullable [] getStringBytes(int stringIndex) {
        int entrySize = this.extended ? EXTENDED_ENTRY_SIZE : ENTRY_SIZE;

        if (this.bytes.length < entrySize || stringIndex < FIRST_STRING_INDEX) {
            return null;
        }

        long count = this.extended ? LittleEndianUtils.readUnsigned32(this.bytes, 0) : LittleEndianUtils.readUnsigned16(this.bytes, 0);
        long entryOffset = (long) stringIndex * entrySize;

        if (stringIndex > count || entryOffset + entrySize > this.bytes.length) {
            return null;
        }

        long start = this.extended ? LittleEndianUtils.readUnsigned32(this.bytes, (int) entryOffset)
                                   : LittleEndianUtils.readUnsigned16(this.bytes, (int) entryOffset);

        if (start >= this.bytes.length) {
            return null;
        }

        int end = (int) start;

        while (end < this.bytes.length && this.bytes[end] != 0) {
            end++;
        }

        byte[] result = new byte[end - (int) start];

        System.arraycopy(this.bytes, (int) start, result, 0, result.length);

        return result;
    }

    /**
     * @param stringIndex the one-based string index
     * @return the decoded string, empty when the lookup finds no string
     */
    public @NotNull String getString(int stringIndex) {
        byte[] text = this.getStringBytes(stringIndex);

        return text == null ? "" : LegacyTextUtils.decode(text, 0, text.length);
    }

    /**
     * Copies the section bytes, as EUD writes left them.
     *
     * @return a copy of the section bytes
     */
    public byte @NotNull [] toByteArray() {
        return this.bytes.clone();
    }

    /**
     * Finds the first byte of a range that may have changed after a change count was observed. Changes are tracked per block of
     * {@code 1 << CHANGE_BLOCK_SHIFT} bytes, so the answer is the first range byte in a block with a change.
     *
     * @param offset      the first byte offset
     * @param length      the number of bytes, at least {@code 1}
     * @param changeCount a value of {@link #getChangeCount()} observed earlier
     * @return the index in the range of that byte, or {@code -1} if the range holds the bytes it held at that count
     */
    public int findChangeSince(int offset, int length, long changeCount) {
        int lastBlock = (offset + length - 1) >>> CHANGE_BLOCK_SHIFT;
        int block = offset >>> CHANGE_BLOCK_SHIFT;

        while (block <= lastBlock) {
            int group = block >>> (CHANGE_GROUP_SHIFT - CHANGE_BLOCK_SHIFT);

            if (this.groupChangeCounts[group] <= changeCount) {
                block = group + 1 << (CHANGE_GROUP_SHIFT - CHANGE_BLOCK_SHIFT);
            } else if (this.blockChangeCounts[block] <= changeCount) {
                block++;
            } else {
                return Math.max(offset, block << CHANGE_BLOCK_SHIFT) - offset;
            }
        }

        return -1;
    }

    /**
     * Reads one byte of the section.
     *
     * @param offset the byte offset
     * @return the unsigned byte
     */
    public int readByte(int offset) {
        return Byte.toUnsignedInt(this.bytes[offset]);
    }

    /**
     * Reads a little-endian DWORD of the section.
     *
     * @param offset the offset of four bytes inside the section
     * @return the little-endian DWORD
     */
    public int readDword(int offset) {
        return (int) DWORD.get(this.bytes, offset);
    }

    /**
     * Copies a range of the section.
     *
     * @param offset            the first byte offset
     * @param destination       the receiving array
     * @param destinationOffset the first index written
     * @param length            the number of bytes
     */
    public void read(int offset, byte @NotNull [] destination, int destinationOffset, int length) {
        System.arraycopy(this.bytes, offset, destination, destinationOffset, length);
    }

    /**
     * Stores one byte; a byte that changes stamps its block and group with the next change count.
     *
     * @param offset the byte offset
     * @param value  the byte in the low eight bits
     */
    public void writeByte(int offset, int value) {
        if (this.bytes[offset] == (byte) value) {
            return;
        }

        this.bytes[offset] = (byte) value;
        this.changeCount++;
        this.blockChangeCounts[offset >>> CHANGE_BLOCK_SHIFT] = this.changeCount;
        this.groupChangeCounts[offset >>> CHANGE_GROUP_SHIFT] = this.changeCount;
    }

    /**
     * Stores a little-endian DWORD as four {@link #writeByte} calls in address order do.
     *
     * @param offset the offset of four bytes inside the section
     * @param value  the DWORD
     */
    public void writeDword(int offset, int value) {
        int changed = (int) DWORD.get(this.bytes, offset) ^ value;

        if (changed == 0) {
            return;
        }

        DWORD.set(this.bytes, offset, value);

        for (int index = 0; index < Integer.BYTES; index++) {
            if ((changed >>> index * Byte.SIZE & BYTE_MASK) == 0) {
                continue;
            }

            this.changeCount++;
            this.blockChangeCounts[offset + index >>> CHANGE_BLOCK_SHIFT] = this.changeCount;
            this.groupChangeCounts[offset + index >>> CHANGE_GROUP_SHIFT] = this.changeCount;
        }
    }
}
