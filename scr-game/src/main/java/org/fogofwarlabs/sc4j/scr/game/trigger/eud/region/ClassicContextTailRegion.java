package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

/**
 * The game context tail: the bytes of the game context after the game-data block, served over the live context. The
 * context starts zeroed; in the tail only these bytes are written by the game itself:
 * <ul>
 *     <li>the twelve lobby color bytes ({@link GameContext#getContextLobbyColors()}): the lobby sets them, the game start
 *     resets them unless the custom colors stay, and replay loading resets them, so single player and replays show the
 *     reset value twelve times and a network game with custom colors the resolved lobby colors;</li>
 *     <li>the saved custom colors that a game save writes; no game is saved during a game here, so they keep their zero
 *     (or what EUD writes).</li>
 * </ul>
 * Every other byte is the world's context storage ({@link GameContext#getContextStorage()}): zero at the start, it keeps
 * what EUD writes and what a saved game holds, in every game mode.
 */
public final class ClassicContextTailRegion implements ClassicRegion {
    /**
     * The classic address of the tail.
     */
    static final int BASE = 0x58F44C;
    /**
     * The mask of an unsigned BYTE.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The context offset of the tail, the end of the game-data block.
     */
    private static final int CONTEXT_OFFSET = ClassicAddressSpace.GAME_DATA_SIZE;
    /**
     * The size of the tail, up to the end of the game context.
     */
    static final int SIZE = GameContext.CONTEXT_SIZE - CONTEXT_OFFSET;
    /**
     * The offset in the tail of the lobby color bytes.
     */
    private static final int LOBBY_COLORS = 0x2E;


    private final byte @NotNull [] lobbyColors;
    /**
     * The world's context storage, indexed by context offset.
     */
    private final byte @NotNull [] storage;


    /**
     * Creates a {@link ClassicContextTailRegion} instance.
     *
     * @param world the world whose lobby color bytes the region serves
     */
    public ClassicContextTailRegion(@NotNull Game world) {
        this.lobbyColors = world.getGameContext().getContextLobbyColors();
        this.storage     = world.getGameContext().getContextStorage();
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
        return SIZE;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     */
    @Override
    public int readByte(int offset) {
        if (offset >= LOBBY_COLORS && offset < LOBBY_COLORS + this.lobbyColors.length) {
            return this.lobbyColors[offset - LOBBY_COLORS] & BYTE_MASK;
        }

        return this.storage[CONTEXT_OFFSET + offset] & BYTE_MASK;
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     */
    @Override
    public void writeByte(int offset, int value) {
        if (offset >= LOBBY_COLORS && offset < LOBBY_COLORS + this.lobbyColors.length) {
            this.lobbyColors[offset - LOBBY_COLORS] = (byte) value;
        } else {
            this.storage[CONTEXT_OFFSET + offset] = (byte) value;
        }
    }
}
