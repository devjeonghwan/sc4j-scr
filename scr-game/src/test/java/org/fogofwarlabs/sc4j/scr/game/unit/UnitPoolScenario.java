package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

/**
 * Commits unit pool slots and sets free slot generations through the construction lifecycle, for tests that isolate later game operations from
 * full construction.
 */
public final class UnitPoolScenario {
    /**
     * The largest five-bit generation; {@link #allocate} advances once more, a full wrap.
     */
    private static final int MAXIMUM_GENERATION = 31;

    /**
     * Commits the first available slot using the construction lifecycle.
     *
     * @param pool the unit pool
     * @return the committed pool index, or {@code 0} if the pool is full
     */
    public static int allocate(@NotNull UnitPool pool) {
        int poolIndex = pool.getFirstFreeIndex();

        if (poolIndex == 0) {
            return 0;
        }

        Unit unit = pool.getUnit(poolIndex);

        pool.advanceGeneration(unit);
        pool.commitInitializedUnit(unit);

        return poolIndex;
    }

    /**
     * Sets a free slot's generation through the pool lifecycle used by construction.
     *
     * @param pool       the unit pool
     * @param poolIndex  the free slot index
     * @param generation the five-bit generation
     * @throws IllegalArgumentException if the generation is not a five-bit value or the slot is allocated
     * @throws IllegalStateException    if the slot is absent from the free list
     */
    public static void setFreeGeneration(@NotNull UnitPool pool, int poolIndex, int generation) {
        if (generation < 0 || generation > MAXIMUM_GENERATION || pool.isAllocated(poolIndex)) {
            throw Exceptions.newIllegalArgumentException("Generation fixture requires a free slot and a five-bit value.");
        }

        int[] heldIndices = new int[pool.getCapacity()];
        int heldCount = 0;

        while (pool.getFirstFreeIndex() != poolIndex) {
            int firstIndex = pool.getFirstFreeIndex();

            if (firstIndex == 0) {
                throw Exceptions.newIllegalStateException("The fixture slot is absent from the free list.");
            }

            Unit first = pool.getUnit(firstIndex);

            // A full wrap preserves unrelated slots' generations while they are held aside.
            for (int advance = 0; advance < MAXIMUM_GENERATION; advance++) {
                pool.advanceGeneration(first);
            }

            heldIndices[heldCount++] = allocate(pool);
        }

        Unit unit = pool.getUnit(poolIndex);

        while (pool.getGeneration(poolIndex) != generation) {
            pool.advanceGeneration(unit);
        }

        for (int heldIndex = 0; heldIndex < heldCount; heldIndex++) {
            pool.recycleFinalized(heldIndices[heldIndex]);
        }
    }


    private UnitPoolScenario() {
    }
}
