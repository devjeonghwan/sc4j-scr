package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.registry.TableKind;
import org.fogofwarlabs.sc4j.engine.content.table.PlayerProgressTable;
import org.fogofwarlabs.sc4j.engine.entity.column.ByteColumn;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedUnitTypes;
import org.fogofwarlabs.sc4j.scr.content.ScrUnits;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.jetbrains.annotations.NotNull;

/**
 * Serves whether each player may produce each unit type in a map-settings game. The availability bytes live in an SC:R extension column of the
 * engine {@link PlayerProgressTable} ({@link PlayerProgressColumns#UNIT_AVAILABLE}), and its engine column holds whether each byte is nonzero;
 * this view holds no value of its own.
 * <p>
 * Every type starts allowed. The {@code PUNI} payload supplies player-specific bytes or the global default selected by each player's use-default row;
 * any nonzero stored byte allows production.
 */
public final class PlayerUnitAvailability {
    /**
     * The {@code PUNI} row of the global availability, in units of the unit type count.
     */
    private static final int SECTION_DEFAULT_ROW = PlayerTable.PLAYER_SLOTS;
    /**
     * The {@code PUNI} row of the players' use-default flags.
     */
    private static final int SECTION_USE_DEFAULT_ROW = SECTION_DEFAULT_ROW + 1;
    /**
     * The number of {@code PUNI} rows.
     */
    private static final int SECTION_ROWS = SECTION_USE_DEFAULT_ROW + PlayerTable.PLAYER_SLOTS;
    /**
     * The availability byte of a newly created storage: the unit type is allowed.
     */
    static final byte ALLOWED = 1;
    /**
     * The multiplier that folds each availability flag into the diagnostic hash.
     */
    private static final int HASH_MULTIPLIER = 31;

    private static void requireStoredEntry(int playerIndex, int unitType) {
        if (playerIndex < 0 || playerIndex >= PlayerTable.PLAYER_SLOTS || unitType < 0 || unitType >= UnitsDat.UNIT_COUNT) {
            throw Exceptions.newIllegalArgumentException("Unit availability access is outside its storage: {0}/{1}", playerIndex, unitType);
        }
    }


    /**
     * The table whose engine column holds whether each availability byte is nonzero.
     */
    private final @NotNull PlayerProgressTable progress;
    /**
     * The availability bytes of the game context; nonzero allows production.
     */
    private final @NotNull ByteColumn allowed;

    /**
     * Creates a {@link PlayerUnitAvailability} instance over a progress table and allows every unit type for every player in it.
     *
     * @param progress the progress table of the game, with the SC:R extension columns ({@link PlayerProgressColumns})
     */
    public PlayerUnitAvailability(@NotNull PlayerProgressTable progress) {
        this.progress = progress;
        this.allowed  = progress.getExtensionColumns(TableKind.UNIT).get(PlayerProgressColumns.UNIT_AVAILABLE);

        for (int index = 0; index < PlayerTable.PLAYER_SLOTS * UnitsDat.UNIT_COUNT; index++) {
            this.storeByte(index, ALLOWED);
        }
    }

    /**
     * Tests whether a player may produce a unit type. An added unit type ({@link ScrAddedUnitTypes}) has no availability byte: the map settings
     * and EUD do not reach it, and every player may produce it.
     *
     * @param playerIndex the player index
     * @param unitType    the {@code units.dat} identifier or an added unit type id
     * @return {@code true} if the player may produce the unit type, {@code false} otherwise
     * @throws IllegalArgumentException if the player or unit type is outside the stored ranges
     */
    public boolean isAllowed(int playerIndex, int unitType) {
        int added = unitType - ScrAddedUnitTypes.FIRST_ID;

        if (playerIndex >= 0 && playerIndex < PlayerTable.PLAYER_SLOTS && added >= 0 && added < this.progress.getUnitTypeCount() - ScrUnits.COUNT) {
            return true;
        }

        requireStoredEntry(playerIndex, unitType);

        return this.progress.isUnitAvailable(playerIndex, unitType);
    }


    /**
     * Reads a raw availability byte, as an EUD read of the game-data block does.
     *
     * @param playerIndex the player index
     * @param unitType    the {@code units.dat} identifier
     * @return the raw availability byte
     * @throws IllegalArgumentException if the player or unit type is outside the stored ranges
     */
    public int readByte(int playerIndex, int unitType) {
        requireStoredEntry(playerIndex, unitType);

        return Byte.toUnsignedInt(this.allowed.get(playerIndex * UnitsDat.UNIT_COUNT + unitType));
    }

    /**
     * Writes a raw availability byte, as an EUD write of the game-data block does.
     *
     * @param playerIndex the player index
     * @param unitType    the {@code units.dat} identifier
     * @param value       the byte in the low eight bits
     * @throws IllegalArgumentException if the player or unit type is outside the stored ranges
     */
    public void writeByte(int playerIndex, int unitType, int value) {
        requireStoredEntry(playerIndex, unitType);

        this.storeByte(playerIndex * UnitsDat.UNIT_COUNT + unitType, value);
    }

    /**
     * Applies a {@code PUNI} payload: one row per player slot, the global row and one use-default row per player slot, each row one byte per unit
     * type.
     *
     * @param payload the complete section payload
     * @throws UnsupportedOperationException if the payload is shorter than the section layout
     */
    public void applySection(byte @NotNull [] payload) {
        if (payload.length < UnitsDat.UNIT_COUNT * SECTION_ROWS) {
            throw Exceptions.newUnsupportedOperationException("Map unit availability is shorter than its section layout: {0}", payload.length);
        }

        for (int playerIndex = 0; playerIndex < PlayerTable.PLAYER_SLOTS; playerIndex++) {
            for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
                int playerOffset = playerIndex * UnitsDat.UNIT_COUNT + unitType;
                boolean useDefault = payload[UnitsDat.UNIT_COUNT * SECTION_USE_DEFAULT_ROW + playerOffset] != 0;

                this.storeByte(playerOffset, payload[useDefault ? UnitsDat.UNIT_COUNT * SECTION_DEFAULT_ROW + unitType : playerOffset]);
            }
        }
    }

    /**
     * Folds every availability flag into a diagnostic hash.
     *
     * @param hash the preceding diagnostic hash
     * @return the hash including every availability flag
     */
    public int computeStateHash(int hash) {
        for (int index = 0; index < PlayerTable.PLAYER_SLOTS * UnitsDat.UNIT_COUNT; index++) {
            hash = HASH_MULTIPLIER * hash + (this.allowed.get(index) != 0 ? 1 : 0);
        }

        return hash;
    }

    /**
     * Stores an availability byte and whether it is nonzero, the engine availability of the unit type.
     *
     * @param index the column index, {@code player * UNIT_COUNT + unitType}
     * @param value the byte in the low eight bits
     */
    private void storeByte(int index, int value) {
        this.allowed.set(index, (byte) value);
        this.progress.setUnitAvailable(index / UnitsDat.UNIT_COUNT, index % UnitsDat.UNIT_COUNT, (byte) value != 0);
    }
}
