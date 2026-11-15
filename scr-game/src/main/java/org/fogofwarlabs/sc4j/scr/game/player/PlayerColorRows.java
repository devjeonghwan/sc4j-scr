package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Retains the player color remapping rows: twelve rows of the eight {@code game/tunit.pcx} palette indices of each player, then one
 * {@code game/tminimap.pcx} index per player. The game start fills them from each player's color (the slots past the playable ones by their own
 * index); an EUD write changes them, and the unit team color (the palette entry of the row's first byte unless the custom color table is active), the
 * unit remapping and the minimap follow.
 */
public final class PlayerColorRows {
    /**
     * The palette indices of one unit color row.
     */
    public static final int UNIT_ROW = 8;
    /**
     * The bytes of the block: twelve unit rows, then twelve minimap indices.
     */
    public static final int SIZE = PlayerTable.PLAYER_SLOTS * UNIT_ROW + PlayerTable.PLAYER_SLOTS;

    /**
     * The bytes of the unit rows of every player slot; the minimap indices follow them.
     */
    private static final int UNIT_ROWS_SIZE = PlayerTable.PLAYER_SLOTS * UNIT_ROW;

    /**
     * Creates a {@link PlayerColorRows} instance filled from the players' colors, as the game start does.
     *
     * @param gameData the game data (the player color tables)
     * @param players  the player table
     * @return the created {@link PlayerColorRows} instance, or {@code null} if a player's color lies outside the supplied {@code tunit.pcx} rows
     * @throws UncheckedIOException if {@code tunit.pcx} or {@code tminimap.pcx} cannot be read
     */
    public static @Nullable PlayerColorRows create(@NotNull GameData gameData, @NotNull PlayerTable players) {
        int[][] colorTables;

        try {
            colorTables = gameData.getPlayerColorTables();
        } catch (IOException exception) {
            throw Exceptions.newUncheckedIOException(exception, "The player color rows need tunit.pcx and tminimap.pcx.");
        }

        PlayerColorRows rows = new PlayerColorRows();

        for (int slot = 0; slot < PlayerTable.PLAYER_SLOTS; slot++) {
            Player player = slot < PlayerTable.PLAYABLE_PLAYER_SLOTS ? players.getPlayer(slot) : null;
            int color = player != null ? player.getColor() : slot;

            if (color < 0 || color >= colorTables.length) {
                return null;
            }

            for (int index = 0; index < UNIT_ROW; index++) {
                rows.bytes[slot * UNIT_ROW + index] = (byte) colorTables[color][index];
            }

            rows.bytes[UNIT_ROWS_SIZE + slot] = (byte) colorTables[color][UNIT_ROW];
        }

        return rows;
    }

    private final byte @NotNull [] bytes;

    /**
     * Creates a {@link PlayerColorRows} instance with zeroed rows, which {@link #create} or a restored saved game fills.
     */
    PlayerColorRows() {
        this.bytes = new byte[SIZE];
    }

    /**
     * @param player the player slot
     * @param index  the remapped color {@code 0..7} (palette indices {@code 8..15} of a unit frame)
     * @return the palette index
     */
    public int getUnitColor(int player, int index) {
        return Byte.toUnsignedInt(this.bytes[player * UNIT_ROW + index]);
    }

    /**
     * @param player the player slot
     * @return the minimap palette index
     */
    public int getMinimapColor(int player) {
        return Byte.toUnsignedInt(this.bytes[UNIT_ROWS_SIZE + player]);
    }


    /**
     * Reads a byte of the block, as an EUD read of the game context does.
     *
     * @param offset the byte offset in the block
     * @return the byte
     */
    public int readByte(int offset) {
        return Byte.toUnsignedInt(this.bytes[offset]);
    }

    /**
     * Writes a byte of the block, as an EUD write of the game context does.
     *
     * @param offset the byte offset in the block
     * @param value  the byte in the low eight bits
     */
    public void writeByte(int offset, int value) {
        this.bytes[offset] = (byte) value;
    }

    /**
     * Copies the unit rows of every player slot.
     *
     * @param destination the array receiving the {@link #UNIT_ROW} palette indices of every player slot
     */
    public void copyUnitRows(byte @NotNull [] destination) {
        System.arraycopy(this.bytes, 0, destination, 0, UNIT_ROWS_SIZE);
    }
}
