package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

/**
 * One DWORD region of the local view ({@link LocalView}): the screen and moved screen positions (4 bytes each) and the
 * mouse position (8 bytes, X then Y), and the drawn screen tile WORDs (2 bytes each).
 */
public final class ClassicViewRegion implements ClassicRegion {
    /**
     * Creates {@link ClassicViewRegion} instances for the seven regions of the local view.
     *
     * @param view the local view
     * @return the seven regions of the view
     */
    public static @NotNull ClassicViewRegion @NotNull [] create(@NotNull LocalView view) {
        return new ClassicViewRegion[]{
                new ClassicViewRegion(LocalView.PREVIOUS_SCREEN_X_ADDRESS, Integer.BYTES, view),
                new ClassicViewRegion(LocalView.PREVIOUS_SCREEN_Y_ADDRESS, Integer.BYTES, view),
                new ClassicViewRegion(LocalView.SCREEN_X_ADDRESS, Integer.BYTES, view),
                new ClassicViewRegion(LocalView.SCREEN_Y_ADDRESS, Integer.BYTES, view),
                new ClassicViewRegion(LocalView.MOUSE_ADDRESS, 2 * Integer.BYTES, view),
                new ClassicViewRegion(LocalView.DRAWN_TILE_X_ADDRESS, Short.BYTES, view),
                new ClassicViewRegion(LocalView.DRAWN_TILE_Y_ADDRESS, Short.BYTES, view)
        };
    }


    private final int base;
    private final int size;
    private final @NotNull LocalView view;


    private ClassicViewRegion(int base, int size, @NotNull LocalView view) {
        this.base = base;
        this.size = size;
        this.view = view;
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
        return this.size;
    }

    /**
     * Tells whether the address space accepts writes without calling the region or changing its state.
     * <p>
     * The four screen positions and the two drawn tile WORDs ignore writes; the mouse position is written.
     *
     * @return {@code true} if writes are ignored, {@code false} otherwise (the mouse position)
     */
    @Override
    public boolean isWriteIgnored() {
        return this.base != LocalView.MOUSE_ADDRESS;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     * @throws UnsupportedOperationException if the interface did not supply the view
     */
    @Override
    public int readByte(int offset) {
        return this.view.read(this.base + (offset & ~DWORD_OFFSET_MASK)) >>> ((offset & DWORD_OFFSET_MASK) * Byte.SIZE) & BYTE_MASK;
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     * @throws UnsupportedOperationException if the interface did not supply the view
     */
    @Override
    public void writeByte(int offset, int value) {
        int address = this.base + (offset & ~DWORD_OFFSET_MASK);
        int shift = (offset & DWORD_OFFSET_MASK) * Byte.SIZE;

        this.view.write(address, LittleEndianUtils.replaceByte(this.view.read(address), shift, value));
    }

    /**
     * Reads an aligned little-endian DWORD; regions whose domain values are DWORDs override it to read them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @return the DWORD
     * @throws UnsupportedOperationException if the interface did not supply the view
     */
    @Override
    public int readDword(int offset) {
        if ((offset & DWORD_OFFSET_MASK) != 0 || this.size < Integer.BYTES) {
            return ClassicRegion.super.readDword(offset);
        }

        return this.view.read(this.base + offset);
    }

    /**
     * Stores a little-endian DWORD; regions whose domain values are DWORDs override it to store them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @param value  the DWORD
     * @throws UnsupportedOperationException if the interface did not supply the view
     */
    @Override
    public void writeDword(int offset, int value) {
        if ((offset & DWORD_OFFSET_MASK) != 0) {
            ClassicRegion.super.writeDword(offset, value);

            return;
        }

        this.view.write(this.base + offset, value);
    }
}
