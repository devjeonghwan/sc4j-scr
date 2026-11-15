package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The emulation's copy of the {@code stat_txt} string table, which the emulation builds when it starts: it loads
 * {@code rez\stat_txt.tbl}, allocates the file size plus a spare of {@code 0x6000} bytes rounded down to a DWORD, copies the file to
 * the start and leaves the rest zero, and registers the pointer regions to the buffer. Reads and writes reach every byte of
 * the buffer; a write also puts the region on the emulator's modified list. The end of a trigger pass commits the list:
 * this region's commit clears the flag and makes the buffer the game's {@code stat_txt} table.
 */
public final class ClassicStatTextRegion implements ClassicRegion {
    /**
     * The classic address of the buffer.
     */
    public static final int BASE = 0x19184660;
    /**
     * The installed table the emulation copies.
     */
    public static final @NotNull String FILE = "rez/stat_txt.tbl";
    /**
     * The zero bytes the buffer holds after the file.
     */
    private static final int SPARE = 0x6000;


    /**
     * Creates a {@link ClassicStatTextRegion} instance over a copy of the installed table.
     *
     * @param gameData the installed game data
     * @return the created {@link ClassicStatTextRegion} instance
     * @throws UncheckedIOException if the installed table cannot be read
     */
    public static @NotNull ClassicStatTextRegion load(@NotNull GameData gameData) {
        try {
            return new ClassicStatTextRegion(gameData.readInstalledFile(FILE));
        } catch (IOException exception) {
            throw Exceptions.newUncheckedIOException(exception, "The EUD stat_txt copy needs {0}.", FILE);
        }
    }


    private final byte @NotNull [] bytes;

    private boolean modified;


    /**
     * Creates a {@link ClassicStatTextRegion} instance over a copy of the table bytes.
     *
     * @param file the installed table bytes
     */
    public ClassicStatTextRegion(byte @NotNull [] file) {
        this.bytes = new byte[file.length + SPARE & -Integer.BYTES];

        System.arraycopy(file, 0, this.bytes, 0, Math.min(file.length, this.bytes.length));

        this.modified = false;
    }


    /**
     * @return {@code true} if a write is not yet committed, {@code false} otherwise
     */
    public boolean isModified() {
        return this.modified;
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
        return this.bytes.length;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     */
    @Override
    public int readByte(int offset) {
        return this.bytes[offset] & BYTE_MASK;
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     */
    @Override
    public void writeByte(int offset, int value) {
        this.bytes[offset] = (byte) value;
        this.modified      = true;
    }

    /**
     * Commits a written buffer as the game's {@code stat_txt} table; does nothing without a write.
     *
     * @param world the world whose table the buffer becomes
     */
    public void commit(@NotNull Game world) {
        if (!this.modified) {
            return;
        }

        this.modified = false;

        world.getGameContext().setStatTextTable(this.bytes);
    }
}
