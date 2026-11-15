package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

/**
 * The classic key states over the {@link LocalKeyStates} input.
 * <p>
 * A DWORD read at the offsets {@code 0x30} through {@code 0x38} serves four live key bytes from {@link LocalKeyStates} at
 * keys {@code offset + index}; other reads serve the key-state bytes. A DWORD write at those offsets is ignored; other
 * writes store into the key-state bytes.
 */
public final class ClassicKeyStateRegion implements ClassicRegion {
    /**
     * The classic address of the key states.
     */
    public static final int BASE = 0x596A18;

    /**
     * The first DWORD offset that serves live key bytes.
     */
    private static final int ASYNC_FIRST_OFFSET = 0x30;
    /**
     * The last DWORD offset that serves live key bytes.
     */
    private static final int ASYNC_LAST_OFFSET = 0x38;


    private final @NotNull LocalKeyStates keys;


    /**
     * Creates a {@link ClassicKeyStateRegion} instance.
     *
     * @param keys the local key states
     */
    public ClassicKeyStateRegion(@NotNull LocalKeyStates keys) {
        this.keys = keys;
    }


    /**
     * @return the classic base address
     */
    @Override
    public int getBase() {
        return BASE;
    }

    /**
     * @return the size in bytes
     */
    @Override
    public int getSize() {
        return LocalKeyStates.SIZE;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     * @throws UnsupportedOperationException if the key states are unknown
     */
    @Override
    public int readByte(int offset) {
        return this.keys.getState(offset);
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     * @throws UnsupportedOperationException if the key states are unknown
     */
    @Override
    public void writeByte(int offset, int value) {
        this.keys.setState(offset, value);
    }

    /**
     * Reads an aligned little-endian DWORD; regions whose domain values are DWORDs override it to read them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @return the DWORD
     * @throws UnsupportedOperationException if the key states are unknown
     */
    @Override
    public int readDword(int offset) {
        if (offset >= ASYNC_FIRST_OFFSET && offset <= ASYNC_LAST_OFFSET) {
            int value = 0;

            for (int index = 0; index < Integer.BYTES; index++) {
                value |= this.keys.getAsyncPressed(offset + index) << (index * Byte.SIZE);
            }

            return value;
        }

        return ClassicRegion.super.readDword(offset);
    }

    /**
     * Stores a little-endian DWORD; regions whose domain values are DWORDs override it to store them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @param value  the DWORD
     * @throws UnsupportedOperationException if the key states are unknown
     */
    @Override
    public void writeDword(int offset, int value) {
        if (offset >= ASYNC_FIRST_OFFSET && offset <= ASYNC_LAST_OFFSET) {
            return;
        }

        ClassicRegion.super.writeDword(offset, value);
    }
}
