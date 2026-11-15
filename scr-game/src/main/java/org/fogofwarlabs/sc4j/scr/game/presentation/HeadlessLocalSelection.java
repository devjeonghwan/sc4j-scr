package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Retains local selection pool indices for headless games and probes, including direct EUD reads and writes.
 */
public final class HeadlessLocalSelection implements LocalSelection {
    private final int @NotNull [] slots;

    /**
     * Creates a {@link HeadlessLocalSelection} instance with empty selection slots.
     */
    public HeadlessLocalSelection() {
        this.slots = new int[CAPACITY];
    }

    /**
     * @param position the slot from {@code 0} through {@code 11}
     * @return the pool index of the unit in the slot, or {@code 0} for an empty slot
     */
    @Override
    public int getSelectedPoolIndex(int position) {
        return this.slots[position];
    }

    /**
     * Sets one local selection slot directly without a command or sound; the console shows it on the next refresh.
     *
     * @param position  the slot from {@code 0} through {@code 11}
     * @param poolIndex the pool index of the unit to store, or {@code 0} for an empty slot
     */
    @Override
    public void setSelectedPoolIndexRaw(int position, int poolIndex) {
        this.slots[position] = poolIndex;
    }


    /**
     * Adds the second offspring of a hatched egg to the first free local selection slot when the egg was selected; leaves a full selection unchanged
     * and sends no sound.
     *
     * @param egg    the unit that hatched (the first offspring keeps the egg's unit)
     * @param second the second offspring
     */
    @Override
    public void addHatchedUnit(@NotNull Unit egg, @NotNull Unit second) {
        int firstFreePosition = -1;
        boolean selected = false;

        for (int position = 0; position < CAPACITY; position++) {
            if (this.slots[position] == egg.getIndex()) {
                selected = true;
            }

            if (this.slots[position] == EMPTY_SLOT && firstFreePosition < 0) {
                firstFreePosition = position;
            }
        }

        if (selected && firstFreePosition >= 0) {
            this.slots[firstFreePosition] = second.getIndex();
        }
    }

    /**
     * Removes a retired or hidden unit from the local selection array, as the lifecycle deselection clears it from every selection
     * ({@link UnitSelection#removeUnit}), and a selected unit the local player stops seeing or detecting; nothing is sent.
     * <p>
     * The units before the first empty slot move down over the unit's slots and the rest of those slots become empty.
     *
     * @param unit the unit
     */
    @Override
    public void removeUnit(@NotNull Unit unit) {
        LocalSelection.removePoolIndex(this.slots, unit.getIndex());
    }
}
