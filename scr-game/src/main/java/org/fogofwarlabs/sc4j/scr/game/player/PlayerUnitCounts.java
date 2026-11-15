package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.table.PlayerProgressTable;
import org.fogofwarlabs.sc4j.engine.entity.column.IntColumn;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedUnitTypes;
import org.fogofwarlabs.sc4j.scr.content.ScrUnits;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serves a counter for every unit type and player slot, such as the unit counts, deaths and kills of the game data, in the native layout over a
 * store of the engine content: the unit counts or the completed unit counts of a {@link PlayerProgressTable}, or a per-player extension column
 * ({@link PlayerProgressColumns}). The view holds no counter of its own.
 * <p>
 * The native layout orders the counters {@code type * PLAYER_SLOTS + player}; {@link #getByNativeIndex(int)} and
 * {@link #setByNativeIndex(int, int)} serve that linear index to the EUD address space and map it to the player-major store.
 * <p>
 * The count tables lie back to back in the game context, and the game indexes them without a bound: an index past the end of the table, such as the
 * row of unit type {@code 228}, reads and writes the DWORDs that follow the table, which the linked {@link ContextDwordTable} provides. All counter
 * accesses go through that rule.
 * <p>
 * The counters of the added unit types ({@link ScrAddedUnitTypes}) lie outside the native layout: {@link #get(int, int)} and the other type
 * accessors serve an added type from the store's entries after the native ones, so the native table, its EUD memory and its successor stay as
 * they are. Without added types every access is the native one.
 */
public abstract class PlayerUnitCounts implements ContextDwordTable {
    /**
     * The number of counters: every unit type for every player slot.
     */
    public static final int NATIVE_COUNT = UnitsDat.UNIT_COUNT * PlayerTable.PLAYER_SLOTS;

    /**
     * The multiplier that folds each counter into the state hash.
     */
    private static final int HASH_MULTIPLIER = 31;

    /**
     * Serves the unit counts or the completed unit counts of a progress table.
     */
    private static final class ProgressUnitCounts extends PlayerUnitCounts {
        private final @NotNull PlayerProgressTable progress;
        private final boolean completed;

        /**
         * Creates a {@link ProgressUnitCounts} instance.
         *
         * @param progress  the progress table
         * @param completed whether the view serves the completed unit counts
         */
        private ProgressUnitCounts(@NotNull PlayerProgressTable progress, boolean completed) {
            super(progress.getUnitTypeCount() - ScrUnits.COUNT);

            this.progress  = progress;
            this.completed = completed;
        }

        /**
         * Reads the stored counter of a player and unit type.
         *
         * @param player   the player slot
         * @param unitType the unit type
         * @return the counter
         */
        @Override
        int readCount(int player, int unitType) {
            return this.completed ? this.progress.getCompletedUnitCount(player, unitType) : this.progress.getUnitCount(player, unitType);
        }

        /**
         * Writes the stored counter of a player and unit type.
         *
         * @param player   the player slot
         * @param unitType the unit type
         * @param value    the counter
         */
        @Override
        void writeCount(int player, int unitType, int value) {
            if (this.completed) {
                this.progress.setCompletedUnitCount(player, unitType, value);
            } else {
                this.progress.setUnitCount(player, unitType, value);
            }
        }

        /**
         * Reads the stored counter of a player and added unit type: the progress table's entry of the type, after the native types.
         *
         * @param player the player slot
         * @param added  the added type, from {@code 0}
         * @return the counter
         */
        @Override
        int readAddedCount(int player, int added) {
            return this.readCount(player, ScrUnits.COUNT + added);
        }

        /**
         * Writes the stored counter of a player and added unit type: the progress table's entry of the type, after the native types.
         *
         * @param player the player slot
         * @param added  the added type, from {@code 0}
         * @param value  the counter
         */
        @Override
        void writeAddedCount(int player, int added, int value) {
            this.writeCount(player, ScrUnits.COUNT + added, value);
        }
    }

    /**
     * Serves a per-player extension column of the unit types, indexed {@code player * UNIT_COUNT + unitType}.
     */
    private static final class ColumnUnitCounts extends PlayerUnitCounts {
        private final @NotNull IntColumn column;

        /**
         * Creates a {@link ColumnUnitCounts} instance.
         *
         * @param column the column
         */
        private ColumnUnitCounts(@NotNull IntColumn column) {
            super(Math.max(0, (column.getLength() - NATIVE_COUNT) / PlayerTable.PLAYER_SLOTS));

            this.column = column;
        }

        /**
         * Reads the stored counter of a player and unit type.
         *
         * @param player   the player slot
         * @param unitType the unit type
         * @return the counter
         */
        @Override
        int readCount(int player, int unitType) {
            return this.column.get(player * UnitsDat.UNIT_COUNT + unitType);
        }

        /**
         * Writes the stored counter of a player and unit type.
         *
         * @param player   the player slot
         * @param unitType the unit type
         * @param value    the counter
         */
        @Override
        void writeCount(int player, int unitType, int value) {
            this.column.set(player * UnitsDat.UNIT_COUNT + unitType, value);
        }

        /**
         * Reads the stored counter of a player and added unit type, at {@code NATIVE_COUNT + added * PLAYER_SLOTS + player}.
         *
         * @param player the player slot
         * @param added  the added type, from {@code 0}
         * @return the counter
         */
        @Override
        int readAddedCount(int player, int added) {
            return this.column.get(NATIVE_COUNT + added * PlayerTable.PLAYER_SLOTS + player);
        }

        /**
         * Writes the stored counter of a player and added unit type, at {@code NATIVE_COUNT + added * PLAYER_SLOTS + player}.
         *
         * @param player the player slot
         * @param added  the added type, from {@code 0}
         * @param value  the counter
         */
        @Override
        void writeAddedCount(int player, int added, int value) {
            this.column.set(NATIVE_COUNT + added * PlayerTable.PLAYER_SLOTS + player, value);
        }
    }

    /**
     * Creates a {@link PlayerUnitCounts} instance over the unit counts of a progress table, every unit complete or not.
     *
     * @param progress the progress table
     * @return the created {@link PlayerUnitCounts} instance
     */
    public static @NotNull PlayerUnitCounts ofUnitCounts(@NotNull PlayerProgressTable progress) {
        return new ProgressUnitCounts(progress, false);
    }

    /**
     * Creates a {@link PlayerUnitCounts} instance over the completed unit counts of a progress table.
     *
     * @param progress the progress table
     * @return the created {@link PlayerUnitCounts} instance
     */
    public static @NotNull PlayerUnitCounts ofCompletedUnitCounts(@NotNull PlayerProgressTable progress) {
        return new ProgressUnitCounts(progress, true);
    }

    /**
     * Creates a {@link PlayerUnitCounts} instance over a column of one counter per player and unit type, indexed
     * {@code player * UNIT_COUNT + unitType}.
     *
     * @param column the column, at least {@link #NATIVE_COUNT} values long; the values after them hold the added unit types, one per player slot
     * @return the created {@link PlayerUnitCounts} instance
     */
    public static @NotNull PlayerUnitCounts ofColumn(@NotNull IntColumn column) {
        return new ColumnUnitCounts(column);
    }

    /**
     * The number of added unit types the store holds counters for.
     */
    private final int addedCount;

    /**
     * The DWORDs that follow the table in the game context, or {@code null} while they are not linked.
     */
    private @Nullable ContextDwordTable successor;

    /**
     * Creates a {@link PlayerUnitCounts} instance with no linked successor.
     *
     * @param addedCount the number of added unit types the store holds counters for
     */
    private PlayerUnitCounts(int addedCount) {
        this.addedCount = addedCount;

        this.successor = null;
    }

    /**
     * Returns the counter of a unit type and player slot.
     *
     * @param unitType the unit type
     * @param player   the player slot
     * @return the counter
     * @throws UnsupportedOperationException if the counter lies past the table and no successor is linked
     */
    public int get(int unitType, int player) {
        int added = unitType - ScrAddedUnitTypes.FIRST_ID;

        if (added >= 0 && added < this.addedCount) {
            return this.readAddedCount(player, added);
        }

        return this.getByNativeIndex(unitType * PlayerTable.PLAYER_SLOTS + player);
    }

    /**
     * Sets one unit-type and player counter.
     *
     * @param unitType the unit type
     * @param player   the player slot
     * @param value    the replacement counter
     * @throws UnsupportedOperationException if the counter lies past the table and no successor is linked
     */
    void set(int unitType, int player, int value) {
        int added = unitType - ScrAddedUnitTypes.FIRST_ID;

        if (added >= 0 && added < this.addedCount) {
            this.writeAddedCount(player, added, value);

            return;
        }

        this.setByNativeIndex(unitType * PlayerTable.PLAYER_SLOTS + player, value);
    }

    /**
     * Returns a DWORD by its native linear index.
     * <p>
     * The index is {@code type * PLAYER_SLOTS + player}; an index past the table reads the linked successor.
     *
     * @param index the DWORD index from the start of the table
     * @return the DWORD
     * @throws UnsupportedOperationException if the index lies past the table and no successor is linked
     */
    @Override
    public int getByNativeIndex(int index) {
        if (index < NATIVE_COUNT) {
            return this.readCount(index % PlayerTable.PLAYER_SLOTS, index / PlayerTable.PLAYER_SLOTS);
        }

        return this.requireSuccessor(index).getByNativeIndex(index - NATIVE_COUNT);
    }

    /**
     * Sets a DWORD by its native linear index.
     * <p>
     * The index is {@code type * PLAYER_SLOTS + player}; an index past the table writes the linked successor.
     *
     * @param index the DWORD index from the start of the table
     * @param value the new DWORD
     * @throws UnsupportedOperationException if the index lies past the table and no successor is linked
     */
    @Override
    public void setByNativeIndex(int index, int value) {
        if (index < NATIVE_COUNT) {
            this.writeCount(index % PlayerTable.PLAYER_SLOTS, index / PlayerTable.PLAYER_SLOTS, value);

            return;
        }

        this.requireSuccessor(index).setByNativeIndex(index - NATIVE_COUNT, value);
    }

    /**
     * Sets the DWORDs that follow the table in the game context.
     *
     * @param successor the following DWORDs
     */
    void setSuccessor(@NotNull ContextDwordTable successor) {
        this.successor = successor;
    }


    /**
     * Adds to one counter with integer overflow.
     *
     * @param unitType the unit type
     * @param player   the player slot
     * @param change   the signed counter change
     * @throws UnsupportedOperationException if the counter lies past the table and no successor is linked
     */
    void add(int unitType, int player, int change) {
        int added = unitType - ScrAddedUnitTypes.FIRST_ID;

        if (added >= 0 && added < this.addedCount) {
            this.writeAddedCount(player, added, this.readAddedCount(player, added) + change);

            return;
        }

        int index = unitType * PlayerTable.PLAYER_SLOTS + player;

        this.setByNativeIndex(index, this.getByNativeIndex(index) + change);
    }

    /**
     * Increments one counter with integer overflow.
     *
     * @param unitType the unit type
     * @param player   the player slot
     * @throws UnsupportedOperationException if the counter lies past the table and no successor is linked
     */
    void increment(int unitType, int player) {
        this.add(unitType, player, 1);
    }

    /**
     * Clears every counter of the table; the successor keeps its DWORDs.
     */
    void clear() {
        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
                this.writeCount(player, unitType, 0);
            }

            for (int added = 0; added < this.addedCount; added++) {
                this.writeAddedCount(player, added, 0);
            }
        }
    }

    /**
     * Copies every counter of the table into another table.
     *
     * @param destination the receiving table
     */
    void copyTo(@NotNull PlayerUnitCounts destination) {
        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
                destination.writeCount(player, unitType, this.readCount(player, unitType));
            }

            for (int added = 0; added < Math.min(this.addedCount, destination.addedCount); added++) {
                destination.writeAddedCount(player, added, this.readAddedCount(player, added));
            }
        }
    }

    /**
     * Folds every counter of the table into a state hash, in native order, then the counters of the added unit types.
     *
     * @param hash the preceding hash
     * @return the hash including every counter
     */
    int computeStateHash(int hash) {
        for (int index = 0; index < NATIVE_COUNT; index++) {
            hash = HASH_MULTIPLIER * hash + this.getByNativeIndex(index);
        }

        for (int added = 0; added < this.addedCount; added++) {
            for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
                hash = HASH_MULTIPLIER * hash + this.readAddedCount(player, added);
            }
        }

        return hash;
    }

    /**
     * Reads the stored counter of a player and unit type.
     *
     * @param player   the player slot
     * @param unitType the unit type
     * @return the counter
     */
    abstract int readCount(int player, int unitType);

    /**
     * Writes the stored counter of a player and unit type.
     *
     * @param player   the player slot
     * @param unitType the unit type
     * @param value    the counter
     */
    abstract void writeCount(int player, int unitType, int value);

    /**
     * Reads the stored counter of a player and added unit type.
     *
     * @param player the player slot
     * @param added  the added type, from {@code 0}
     * @return the counter
     */
    abstract int readAddedCount(int player, int added);

    /**
     * Writes the stored counter of a player and added unit type.
     *
     * @param player the player slot
     * @param added  the added type, from {@code 0}
     * @param value  the counter
     */
    abstract void writeAddedCount(int player, int added, int value);

    /**
     * Returns the linked successor for an index past the table.
     *
     * @param index the native linear index past the table
     * @return the successor
     * @throws UnsupportedOperationException if no successor is linked
     */
    private @NotNull ContextDwordTable requireSuccessor(int index) {
        ContextDwordTable linked = this.successor;

        if (linked == null) {
            throw Exceptions.newUnsupportedOperationException("Count index {0} lies past the table without linked DWORDs after it.", index);
        }

        return linked;
    }
}
