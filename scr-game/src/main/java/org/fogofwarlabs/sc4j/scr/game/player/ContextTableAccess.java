package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.state.GameContext;
import org.fogofwarlabs.sc4j.scr.game.state.GameContextBytes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Reads and writes the game-context byte that an entry of a per-player table addresses, for the upgrade and technology storages.
 * <p>
 * The table layout bounds neither the player nor the identifier: the player is an unsigned DWORD, so a player past the player slots (an owner byte
 * that EUD wrote) or an identifier past the table addresses another game-context byte by the same layout. The owning storage serves the bytes of its
 * own tables; the game-context view serves every other byte. A byte past the game context is not represented.
 */
final class ContextTableAccess {
    /**
     * The mask of the unsigned player DWORD widened to a QWORD.
     */
    private static final long DWORD_MASK = 0xFFFFFFFFL;

    /**
     * Returns the game-context offset of a byte entry: a classic identifier indexes the classic table by player rows of the classic entries, any
     * other identifier indexes the expansion table by player rows of the expansion entries. Neither index is bounded.
     *
     * @param classicOffset   the game-context offset of the classic table
     * @param classicCount    the classic entries of one player row
     * @param expansionOffset the game-context offset of the expansion table (the first expansion entry of player {@code 0})
     * @param expansionCount  the expansion entries of one player row
     * @param playerIndex     the player DWORD, unsigned
     * @param identifier      the entry identifier, nonnegative
     * @return the unsigned game-context offset
     */
    static long getEntryOffset(int classicOffset, int classicCount, int expansionOffset, int expansionCount, int playerIndex, int identifier) {
        long player = playerIndex & DWORD_MASK;

        return identifier < classicCount
               ? classicOffset + player * classicCount + identifier
               : expansionOffset - classicCount + player * expansionCount + identifier;
    }

    /**
     * Returns the game-context offset of the byte that holds an entry bit: one row of bit bytes per player, indexed by the identifier's byte. Neither
     * index is bounded.
     *
     * @param bitsOffset     the game-context offset of the bit table
     * @param bytesPerPlayer the bit bytes of one player row
     * @param playerIndex    the player DWORD, unsigned
     * @param identifier     the entry identifier, nonnegative
     * @return the unsigned game-context offset
     */
    static long getBitByteOffset(int bitsOffset, int bytesPerPlayer, int playerIndex, int identifier) {
        return bitsOffset + (playerIndex & DWORD_MASK) * bytesPerPlayer + (identifier >> PlayerTable.BIT_SET_BYTE_SHIFT);
    }

    /**
     * Checks that an addressed byte lies in the game context.
     *
     * @param contextOffset the unsigned game-context offset
     * @return the offset
     * @throws UnsupportedOperationException if the offset lies past the game context
     */
    private static int checkContextOffset(long contextOffset) {
        if (contextOffset >= GameContext.CONTEXT_SIZE) {
            throw Exceptions.newUnsupportedOperationException("Player table byte 0x{0} lies past the game context.", Long.toHexString(contextOffset));
        }

        return (int) contextOffset;
    }

    /**
     * The tables of the owning storage.
     */
    private final @NotNull GameContextBytes ownTables;
    /**
     * Tells whether a game-context offset lies in the tables of the owning storage.
     */
    private final @NotNull IntPredicate ownOffsets;
    /**
     * Supplies the view of the whole game context, or {@code null} for storage without a game context.
     */
    private final @Nullable Supplier<GameContextBytes> context;

    /**
     * Creates a {@link ContextTableAccess} instance for a storage of the game context.
     *
     * @param ownTables  the tables of the owning storage
     * @param ownOffsets tells whether a game-context offset lies in the tables of the owning storage
     * @param context    supplies the view of the whole game context as the world has it at the access, or {@code null} when no game context exists
     */
    ContextTableAccess(@NotNull GameContextBytes ownTables, @NotNull IntPredicate ownOffsets, @Nullable Supplier<GameContextBytes> context) {
        this.ownTables  = ownTables;
        this.ownOffsets = ownOffsets;
        this.context    = context;
    }

    /**
     * Reads the byte at an addressed game-context offset.
     *
     * @param contextOffset the unsigned game-context offset
     * @return the unsigned byte
     * @throws UnsupportedOperationException if the byte lies past the game context, or outside the owning storage's tables without a game context
     */
    int readByte(long contextOffset) {
        int offset = checkContextOffset(contextOffset);

        return this.getBytesAt(offset).readContextByte(offset);
    }

    /**
     * Writes the byte at an addressed game-context offset.
     *
     * @param contextOffset the unsigned game-context offset
     * @param value         the byte in the low eight bits
     * @throws UnsupportedOperationException if the byte lies past the game context, or outside the owning storage's tables without a game context
     */
    void writeByte(long contextOffset, int value) {
        int offset = checkContextOffset(contextOffset);

        this.getBytesAt(offset).writeContextByte(offset, value);
    }

    /**
     * Tests an entry bit in the byte at an addressed game-context offset.
     *
     * @param contextOffset the unsigned game-context offset of the bit byte
     * @param identifier    the entry identifier, whose low bits select the bit
     * @return {@code true} if the bit is set, {@code false} otherwise
     * @throws UnsupportedOperationException if the byte lies past the game context, or outside the owning storage's tables without a game context
     */
    boolean isBitSet(long contextOffset, int identifier) {
        return (this.readByte(contextOffset) >> (identifier & PlayerTable.BIT_SET_BIT_MASK) & 1) != 0;
    }

    /**
     * Sets or clears an entry bit in the byte at an addressed game-context offset, keeping the other bits of the byte.
     *
     * @param contextOffset the unsigned game-context offset of the bit byte
     * @param identifier    the entry identifier, whose low bits select the bit
     * @param set           whether the bit is set rather than cleared
     * @throws UnsupportedOperationException if the byte lies past the game context, or outside the owning storage's tables without a game context
     */
    void setBit(long contextOffset, int identifier, boolean set) {
        int offset = checkContextOffset(contextOffset);
        GameContextBytes bytes = this.getBytesAt(offset);
        int value = bytes.readContextByte(offset);
        int bit = 1 << (identifier & PlayerTable.BIT_SET_BIT_MASK);

        bytes.writeContextByte(offset, set ? value | bit : value & ~bit);
    }

    /**
     * Selects the bytes that serve a game-context offset.
     *
     * @param contextOffset a game-context offset
     * @return the owning storage's tables, or the view of the whole game context
     * @throws UnsupportedOperationException if the offset lies outside the owning storage's tables without a game context
     */
    private @NotNull GameContextBytes getBytesAt(int contextOffset) {
        if (this.ownOffsets.test(contextOffset)) {
            return this.ownTables;
        }

        if (this.context == null) {
            throw Exceptions.newUnsupportedOperationException("Player table byte 0x{0} needs a game context the storage does not have.",
                                                              Integer.toHexString(contextOffset));
        }

        return this.context.get();
    }
}
