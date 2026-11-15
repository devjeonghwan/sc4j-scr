package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.NativeCodeConstants;
import org.jetbrains.annotations.NotNull;

/**
 * A 4-byte code-constant region of the classic type-7 records: the region keeps its own DWORD, which starts as the
 * executable's constant ({@link NativeCodeConstants}) and which a write replaces for the rest of the game.
 */
public final class ClassicCodeConstantRegion implements ClassicRegion {
    private final int base;
    private final @NotNull NativeCodeConstants constants;

    private boolean written;
    private int storedValue;


    /**
     * Creates a {@link ClassicCodeConstantRegion} instance.
     *
     * @param base      the classic base, one of the 4-byte type-7 bases
     * @param constants the constants read from the executable
     */
    public ClassicCodeConstantRegion(int base, @NotNull NativeCodeConstants constants) {
        this.base      = base;
        this.constants = constants;

        this.written     = false;
        this.storedValue = 0;
    }


    /**
     * @return the classic base address
     */
    @Override
    public int getBase() {
        return this.base;
    }

    /**
     * @return the size in bytes
     */
    @Override
    public int getSize() {
        return Integer.BYTES;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     */
    @Override
    public int readByte(int offset) {
        int value = this.written ? this.storedValue : this.constants.getValue(this.base);

        return value >>> (offset * Byte.SIZE) & BYTE_MASK;
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     */
    @Override
    public void writeByte(int offset, int value) {
        int shift = offset * Byte.SIZE;

        int previous = this.written ? this.storedValue : this.constants.getValue(this.base);

        this.storedValue = LittleEndianUtils.replaceByte(previous, shift, value);
        this.written     = true;
    }

    /**
     * Reads an aligned little-endian DWORD; regions whose domain values are DWORDs override it to read them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @return the DWORD
     */
    @Override
    public int readDword(int offset) {
        if (offset == 0) {
            return this.written ? this.storedValue : this.constants.getValue(this.base);
        }

        return ClassicRegion.super.readDword(offset);
    }

    /**
     * Stores a little-endian DWORD; regions whose domain values are DWORDs override it to store them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @param value  the DWORD
     */
    @Override
    public void writeDword(int offset, int value) {
        if (offset != 0) {
            ClassicRegion.super.writeDword(offset, value);

            return;
        }

        this.storedValue = value;
        this.written     = true;
    }
}
