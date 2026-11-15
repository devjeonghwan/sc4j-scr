package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

/**
 * The classic replay flag, a four-byte generic host region over the host's replay DWORD: nonzero while a replay plays.
 * Playback mode controls local presentation, so a write of another value is not represented.
 */
public final class ClassicReplayFlagRegion implements ClassicRegion {
    /**
     * The classic address of the replay flag.
     */
    public static final int BASE = 0x6D0F14;
    /**
     * The classic address of the game header.
     */
    public static final int GAME_HEADER_ADDRESS = 0x6D0F30;


    private final @NotNull Game world;


    /**
     * Creates a {@link ClassicReplayFlagRegion} instance.
     *
     * @param world the world whose playback mode the flag shows
     */
    public ClassicReplayFlagRegion(@NotNull Game world) {
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
        return offset == 0 && this.world.isReplayPlayback() ? 1 : 0;
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     * @throws UnsupportedOperationException if the write changes the byte
     */
    @Override
    public void writeByte(int offset, int value) {
        if ((value & BYTE_MASK) != this.readByte(offset)) {
            throw Exceptions.newUnsupportedOperationException("EUD write of {0} to the replay flag {1} is not represented.",
                                                              String.format("0x%02X", value & BYTE_MASK),
                                                              String.format("0x%08X", BASE + offset));
        }
    }
}
