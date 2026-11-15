package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.presentation.LocalSelection;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalInterfaceState;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * Serves classic unit-pointer DWORDs through list and selection owners, with a shadow value for each slot.
 * Reads convert represented pool indices to classic unit-table addresses. Writes retain the shadow: zero clears the
 * pointer and keeps its shadow flag; an exact unit entry stores its index and clears the flag; other values leave
 * the owner unchanged. Raw pointer replacements do not relink lists or recompute selection counts.
 */
public final class ClassicUnitPointerRegion implements ClassicRegion {
    /**
     * The classic address of the owner-list heads.
     */
    public static final int OWNER_LISTS = 0x6283F8;
    /**
     * The classic address of the unit-list heads.
     */
    public static final int UNIT_LISTS = 0x628428;
    /**
     * The classic address of the local selection.
     */
    public static final int LOCAL_SELECTION = 0x6284B8;
    /**
     * The classic address of the player selections.
     */
    public static final int PLAYER_SELECTIONS = 0x6284E8;

    /**
     * The unit-list slot of the first hidden unit.
     */
    private static final int FIRST_HIDDEN_SLOT = 0;
    /**
     * The unit-list slot of the last dying unit.
     */
    private static final int LAST_DEAD_SLOT = 1;
    /**
     * The unit-list slot of the first active unit.
     */
    private static final int FIRST_ACTIVE_SLOT = 2;
    /**
     * The unit-list slot of the first map revealer.
     */
    private static final int FIRST_REVEALER_SLOT = 3;
    /**
     * The unit-list slot of the first free slot.
     */
    private static final int FIRST_FREE_SLOT = 4;
    /**
     * The heads behind the six list DWORDs, in slot order; the sixth is the free list's tail.
     */
    private static final UnitPool.@NotNull ListHead @NotNull [] HEADS = {
            UnitPool.ListHead.FIRST_HIDDEN, UnitPool.ListHead.LAST_DEAD, UnitPool.ListHead.FIRST_ACTIVE,
            UnitPool.ListHead.FIRST_REVEALER, UnitPool.ListHead.FIRST_FREE, UnitPool.ListHead.LAST_FREE
    };


    /**
     * Stores a pool index into one host pointer.
     */
    @FunctionalInterface
    private interface SlotWriter {
        /**
         * Stores the pool index into the host pointer of a slot.
         *
         * @param slot      the pointer slot of the run
         * @param poolIndex the one-based pool index, or {@code 0} for none
         */
        void write(int slot, int poolIndex);
    }


    /**
     * Creates a {@link ClassicUnitPointerRegion} instance for the unit-list heads {@link #UNIT_LISTS}: first hidden unit, last dying unit, first
     * active unit, first map revealer, first free slot and last free slot (the tail that the initialization clears and the free-list insertion and
     * removal then keep, {@link UnitPool#getLastFreeIndex()}).
     *
     * @param pool the unit pool of at least 1700 slots
     * @return the created {@link ClassicUnitPointerRegion} instance
     */
    public static @NotNull ClassicUnitPointerRegion createUnitListRegion(@NotNull UnitPool pool) {
        return new ClassicUnitPointerRegion(pool,
                                            UNIT_LISTS, HEADS.length,
                                            slot -> switch (slot) {
                                                case FIRST_HIDDEN_SLOT -> pool.getFirstCreatedIndex(false);
                                                case LAST_DEAD_SLOT -> pool.getLastDeadIndex();
                                                case FIRST_ACTIVE_SLOT -> pool.getFirstCreatedIndex(true);
                                                case FIRST_REVEALER_SLOT -> pool.getFirstRevealerIndex();
                                                case FIRST_FREE_SLOT -> pool.getFirstFreeIndex();
                                                default -> pool.getLastFreeIndex();
                                            },
                                            (slot, poolIndex) -> pool.setListHeadRaw(HEADS[slot], poolIndex));
    }

    /**
     * Creates a {@link ClassicUnitPointerRegion} instance for the owner-list heads {@link #OWNER_LISTS}: the first unit of each owner's list (the
     * game start clears the twelve pointers; storage initialization prepends).
     *
     * @param pool the unit pool of at least 1700 slots
     * @return the created {@link ClassicUnitPointerRegion} instance
     */
    public static @NotNull ClassicUnitPointerRegion createOwnerListRegion(@NotNull UnitPool pool) {
        return new ClassicUnitPointerRegion(pool, OWNER_LISTS, PlayerTable.PLAYER_SLOTS, pool::getFirstOwnerIndex, pool::setFirstOwnerIndexRaw);
    }

    /**
     * Creates a {@link ClassicUnitPointerRegion} instance for the interface selection copy {@link LocalInterfaceState#INTERFACE_SELECTION_ADDRESS}:
     * the twelve pointers that the interface copies from the local selection, a local input of {@link LocalInterfaceState}.
     *
     * @param pool  the unit pool of at least 1700 slots
     * @param state the local interface state
     * @return the created {@link ClassicUnitPointerRegion} instance
     */
    public static @NotNull ClassicUnitPointerRegion createInterfaceSelectionRegion(@NotNull UnitPool pool, @NotNull LocalInterfaceState state) {
        return new ClassicUnitPointerRegion(pool,
                                            LocalInterfaceState.INTERFACE_SELECTION_ADDRESS, LocalSelection.CAPACITY,
                                            state::getInterfaceSelectedPoolIndex,
                                            state::setInterfaceSelectedPoolIndexRaw);
    }

    /**
     * Creates a {@link ClassicUnitPointerRegion} instance for the player selections {@link #PLAYER_SELECTIONS}: the twelve selection pointers of
     * player {@code p} at {@code PLAYER_SELECTIONS + p * 48}.
     *
     * @param pool      the unit pool of at least 1700 slots
     * @param selection the player selection tables
     * @return the created {@link ClassicUnitPointerRegion} instance
     */
    public static @NotNull ClassicUnitPointerRegion createPlayerSelectionRegion(@NotNull UnitPool pool, @NotNull UnitSelection selection) {
        int perPlayer = UnitSelection.MAXIMUM_SELECTED;

        return new ClassicUnitPointerRegion(pool,
                                            PLAYER_SELECTIONS, PlayerTable.PLAYABLE_PLAYER_SLOTS * perPlayer,
                                            slot -> selection.getSelectedPoolIndex(slot / perPlayer, slot % perPlayer),
                                            (slot, poolIndex) -> selection.setSelectedPoolIndexRaw(slot / perPlayer, slot % perPlayer,
                                                                                                   poolIndex));
    }

    /**
     * Creates a {@link ClassicUnitPointerRegion} instance for the local selection {@link #LOCAL_SELECTION}: the twelve pointers of the local
     * interface's selection array, a local input the world's {@link LocalSelection} holds.
     *
     * @param pool      the unit pool of at least 1700 slots
     * @param selection supplies the world's current local selection
     * @return the created {@link ClassicUnitPointerRegion} instance
     */
    public static @NotNull ClassicUnitPointerRegion createLocalSelectionRegion(@NotNull UnitPool pool, @NotNull Supplier<LocalSelection> selection) {
        return new ClassicUnitPointerRegion(pool,
                                            LOCAL_SELECTION, LocalSelection.CAPACITY,
                                            slot -> selection.get().getSelectedPoolIndex(slot),
                                            (slot, poolIndex) -> selection.get().setSelectedPoolIndexRaw(slot, poolIndex));
    }


    private final @NotNull UnitPool pool;
    private final int base;
    private final @NotNull IntUnaryOperator reader;
    private final @NotNull SlotWriter writer;

    private final int @NotNull [] shadowValues;
    private final boolean @NotNull [] shadowSet;


    private ClassicUnitPointerRegion(@NotNull UnitPool pool, int base, int slotCount, @NotNull IntUnaryOperator reader, @NotNull SlotWriter writer) {
        this.pool   = pool;
        this.base   = base;
        this.reader = reader;
        this.writer = writer;

        this.shadowValues = new int[slotCount];
        this.shadowSet    = new boolean[slotCount];
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
        return this.shadowSet.length * Integer.BYTES;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     */
    @Override
    public int readByte(int offset) {
        return this.readByteFromDword(offset);
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     */
    @Override
    public void writeByte(int offset, int value) {
        this.writeByteToDword(offset, value);
    }

    /**
     * Reads an aligned little-endian DWORD; regions whose domain values are DWORDs override it to read them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @return the DWORD
     */
    @Override
    public int readDword(int offset) {
        if ((offset & DWORD_OFFSET_MASK) != 0) {
            return ClassicRegion.super.readDword(offset);
        }

        int slot = offset / Integer.BYTES;

        if (this.shadowSet[slot]) {
            return this.shadowValues[slot];
        }

        return ClassicUnitTableRegion.getEntryAddress(this.reader.applyAsInt(slot));
    }

    /**
     * Stores a little-endian DWORD; regions whose domain values are DWORDs override it to store them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @param value  the DWORD
     */
    @Override
    public void writeDword(int offset, int value) {
        if ((offset & DWORD_OFFSET_MASK) != 0) {
            ClassicRegion.super.writeDword(offset, value);

            return;
        }

        int slot = offset / Integer.BYTES;

        this.shadowValues[slot] = value;
        this.shadowSet[slot]    = true;

        int poolIndex = ClassicUnitTableRegion.findPoolIndex(value, this.pool);

        if (value != 0 && poolIndex == 0) {
            return;
        }

        this.writer.write(slot, poolIndex);

        if (poolIndex != 0) {
            this.shadowSet[slot] = false;
        }
    }
}
