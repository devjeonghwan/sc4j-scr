package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.engine.entity.pool.GenerationTiming;
import org.fogofwarlabs.sc4j.engine.entity.pool.SlotList;
import org.fogofwarlabs.sc4j.engine.entity.pool.SlotPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * Orders the free unit slots and advances their generations as the native unit pool does.
 * <p>
 * Every list write is a raw link write, so a free list that EUD writes corrupted is relinked exactly as natively: the
 * size of the engine free list is not kept. The free list starts as {@code 1, capacity, capacity - 1, ..., 2}; a commit
 * moves the head to the next slot, relinks both neighbors and replaces the tail only when the slot is the tail; a freed
 * slot goes right after the head, or becomes head and tail of an empty list with its links unchanged. The generation
 * advances modulo {@code 32} when a slot is initialized, before its commit, and a release keeps it.
 */
enum UnitSlotPolicy implements SlotPolicy {
    /**
     * The native unit slot policy.
     */
    INSTANCE;

    /**
     * The second pool slot, the tail of the initial free list.
     */
    private static final int SECOND_SLOT = 2;

    /**
     * @return when the pool advances the generation of a slot: {@link GenerationTiming#ON_INITIALIZE}
     */
    @Override
    public @NotNull GenerationTiming getGenerationTiming() {
        return GenerationTiming.ON_INITIALIZE;
    }

    /**
     * Returns the generation that follows a generation; the pool keeps the low {@code 16} bits of the result.
     *
     * @param generation the current generation
     * @return the next generation, {@link UnitHandle#nextGeneration(int)}
     */
    @Override
    public int advanceGeneration(int generation) {
        return UnitHandle.nextGeneration(generation);
    }


    /**
     * Links every slot of a new pool into the empty free list: {@code 1, capacity, capacity - 1, ..., 2}, with slot
     * {@code 2} as the tail when it exists.
     *
     * @param freeSlots the empty free list
     * @param capacity  the pool capacity; the slots are {@code 1} up to it
     */
    @Override
    public void initialize(@NotNull SlotList freeSlots, int capacity) {
        freeSlots.setFirst(capacity == 0 ? SlotList.NONE : 1);
        freeSlots.setLast(Math.min(capacity, SECOND_SLOT));

        if (capacity > 1) {
            freeSlots.setNext(1, capacity);
            freeSlots.setPrevious(capacity, 1);

            for (int index = capacity; index > SECOND_SLOT; index--) {
                freeSlots.setNext(index, index - 1);
                freeSlots.setPrevious(index - 1, index);
            }
        }
    }

    /**
     * Unlinks the first free slot when an allocation commits it: the head becomes the slot's next link, the tail becomes
     * its previous link only when the slot is the tail, both neighbors are relinked and the slot's links are cleared.
     *
     * @param freeSlots the free list
     * @param index     the first free slot
     */
    @Override
    public void unlink(@NotNull SlotList freeSlots, int index) {
        int previous = freeSlots.getPrevious(index);
        int next = freeSlots.getNext(index);

        freeSlots.setFirst(next);

        if (freeSlots.getLast() == index) {
            freeSlots.setLast(previous);
        }

        if (previous != SlotList.NONE) {
            freeSlots.setNext(previous, next);
        }

        if (next != SlotList.NONE) {
            freeSlots.setPrevious(next, previous);
        }

        freeSlots.setNext(index, SlotList.NONE);
        freeSlots.setPrevious(index, SlotList.NONE);
    }

    /**
     * Links a freed slot into the free list: right after the head, becoming the tail when the head was the tail; into an
     * empty list the slot becomes head and tail with its links unchanged.
     *
     * @param freeSlots the free list
     * @param index     the freed slot, not linked
     */
    @Override
    public void release(@NotNull SlotList freeSlots, int index) {
        int first = freeSlots.getFirst();

        if (first == SlotList.NONE) {
            freeSlots.setFirst(index);
            freeSlots.setLast(index);

            return;
        }

        if (freeSlots.getLast() == first) {
            freeSlots.setLast(index);
        }

        int following = freeSlots.getNext(first);

        freeSlots.setPrevious(index, first);
        freeSlots.setNext(index, following);

        if (following != SlotList.NONE) {
            freeSlots.setPrevious(following, index);
        }

        freeSlots.setNext(first, index);
    }
}
