package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.IntUnaryOperator;

/**
 * A generic host region over a Remastered table of BYTEs that a domain object holds: reads and writes copy the host
 * bytes, so an EUD write changes the table the game reads.
 */
public final class ClassicHostByteTableRegion implements ClassicRegion {
    /**
     * The classic address of the damage-overlay bytes, one per images.dat entry, which the image loading fills and the
     * setup of a unit's damage overlays reads.
     */
    public static final int IMAGE_DAMAGE_OVERLAYS = 0x5240D0;


    /**
     * Stores one byte of the table.
     */
    @FunctionalInterface
    public interface ByteWriter {
        /**
         * Stores the byte at an index.
         *
         * @param index the byte index
         * @param value the byte in the low eight bits
         */
        void write(int index, int value);
    }


    /**
     * Creates a {@link ClassicHostByteTableRegion} instance over a write-ignored host BYTE table.
     *
     * @param base   the classic base
     * @param size   the size in bytes
     * @param reader reads the byte at an index
     * @return the created {@link ClassicHostByteTableRegion} instance
     */
    public static @NotNull ClassicHostByteTableRegion createWriteIgnoredRegion(int base, int size, @NotNull IntUnaryOperator reader) {
        return new ClassicHostByteTableRegion(base, size, reader);
    }

    /**
     * Creates a {@link ClassicHostByteTableRegion} instance for the damage-overlay region {@link #IMAGE_DAMAGE_OVERLAYS} over the game's
     * damage-overlay counts.
     *
     * @param gameData the game's data
     * @return the created {@link ClassicHostByteTableRegion} instance over {@link GameData#getImageDamageOverlayCount}
     */
    public static @NotNull ClassicHostByteTableRegion createImageDamageOverlayRegion(@NotNull GameData gameData) {
        return new ClassicHostByteTableRegion(IMAGE_DAMAGE_OVERLAYS, ImagesDat.IMAGE_COUNT, index -> {
            try {
                return gameData.getImageDamageOverlayCount(index);
            } catch (IOException exception) {
                throw Exceptions.newUncheckedIOException(exception, "The damage-overlay byte of image {0} cannot be computed.", index);
            }
        }, gameData::setImageDamageOverlayCount);
    }


    private final int base;
    private final int size;
    private final @NotNull IntUnaryOperator reader;
    private final @NotNull ByteWriter writer;
    private final boolean writeIgnored;


    /**
     * Creates a {@link ClassicHostByteTableRegion} instance whose writes change the host table.
     *
     * @param base   the classic base
     * @param size   the size in bytes
     * @param reader reads the byte at an index
     * @param writer stores the byte at an index
     */
    public ClassicHostByteTableRegion(int base, int size, @NotNull IntUnaryOperator reader, @NotNull ByteWriter writer) {
        this.base         = base;
        this.size         = size;
        this.reader       = reader;
        this.writer       = writer;
        this.writeIgnored = false;
    }

    private ClassicHostByteTableRegion(int base, int size, @NotNull IntUnaryOperator reader) {
        this.base         = base;
        this.size         = size;
        this.reader       = reader;
        this.writer       = (index, value) -> {
            throw Exceptions.newIllegalStateException("A write-ignored region is never written.");
        };
        this.writeIgnored = true;
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
     *
     * @return {@code true} if writes are ignored, {@code false} otherwise
     */
    @Override
    public boolean isWriteIgnored() {
        return this.writeIgnored;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     */
    @Override
    public int readByte(int offset) {
        return this.reader.applyAsInt(offset) & BYTE_MASK;
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     */
    @Override
    public void writeByte(int offset, int value) {
        this.writer.write(offset, value & BYTE_MASK);
    }
}
