package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.scr.game.session.OutgoingTurnBuffer;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

/**
 * The generic host region over the local outgoing turn buffer: reads and writes copy the buffer bytes, so a map that
 * writes command bytes at the length ({@link #LENGTH}) and raises the length queues network commands of the local player
 * ({@link OutgoingTurnBuffer}).
 */
final class ClassicTurnBufferRegion implements ClassicRegion {
    /**
     * The classic address of the buffer.
     */
    static final int BASE = 0x654880;
    /**
     * The classic address of the buffer's length DWORD.
     */
    static final int LENGTH = 0x654AA0;


    private final @NotNull Game world;


    /**
     * Creates a {@link ClassicTurnBufferRegion} instance.
     *
     * @param world the world whose outgoing turn buffer the region serves
     */
    ClassicTurnBufferRegion(@NotNull Game world) {
        this.world = world;
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
        return OutgoingTurnBuffer.SIZE;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     */
    @Override
    public int readByte(int offset) {
        return this.world.getInterfaceLink().getOutgoingTurn().readByte(offset);
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     */
    @Override
    public void writeByte(int offset, int value) {
        this.world.getInterfaceLink().getOutgoingTurn().writeByte(offset, value);
    }
}
