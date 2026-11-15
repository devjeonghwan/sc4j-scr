package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

/**
 * Rejects every access to a classic region whose host is not represented, including the host reason in the failure.
 */
public final class ClassicUnrepresentedRegion implements ClassicRegion {
    private final int base;
    private final int size;
    private final @NotNull String host;


    /**
     * Creates a {@link ClassicUnrepresentedRegion} instance.
     *
     * @param base the classic base
     * @param size the size in bytes
     * @param host the host contents and why they are not represented
     */
    public ClassicUnrepresentedRegion(int base, int size, @NotNull String host) {
        this.base = base;
        this.size = size;
        this.host = host;
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
     * Reads the unsigned byte at an offset; this region always fails.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return no value; the read always fails
     * @throws UnsupportedOperationException if called, naming the host that is not represented
     */
    @Override
    public int readByte(int offset) {
        throw this.createUnmappedAccessException(offset);
    }

    /**
     * Stores one byte, changing the domain state that holds it; this region always fails.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     * @throws UnsupportedOperationException if called, naming the host that is not represented
     */
    @Override
    public void writeByte(int offset, int value) {
        throw this.createUnmappedAccessException(offset);
    }

    private @NotNull UnsupportedOperationException createUnmappedAccessException(int offset) {
        return Exceptions.newUnsupportedOperationException("EUD address {0} is {1}, which this emulation does not represent.",
                                                           String.format("0x%08X", this.base + offset),
                                                           this.host);
    }
}
