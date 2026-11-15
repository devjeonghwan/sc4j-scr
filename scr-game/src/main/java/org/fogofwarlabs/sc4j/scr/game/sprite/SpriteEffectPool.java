package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Retains persistent sprite-effect slots and their intrusive free and active lists.
 * <p>
 * This establishes storage only. The startup sprite allocation must still execute before {@code THG2}.
 */
public final class SpriteEffectPool {
    /**
     * The life that allocation stores in a slot before sprite creation.
     */
    private static final int INITIAL_LIFE = 1;

    private final @NotNull SpriteEffect @NotNull [] spriteEffects;
    private final int @NotNull [] previousIndices;
    private final int @NotNull [] nextIndices;
    private final boolean @NotNull [] allocated;
    private final boolean @NotNull [] active;

    private int firstFreeIndex;
    private int lastFreeIndex;
    private int firstActiveIndex;
    private int lastActiveIndex;
    private int allocatedCount;

    /**
     * Creates a {@link SpriteEffectPool} instance in the original free-list insertion order.
     *
     * @param capacity the established positive runtime capacity
     * @throws IllegalArgumentException if the capacity is not positive
     */
    public SpriteEffectPool(int capacity) {
        if (capacity <= 0) {
            throw Exceptions.newIllegalArgumentException("The sprite effect capacity must be positive.");
        }

        this.spriteEffects   = new SpriteEffect[capacity];
        this.previousIndices = new int[capacity];
        this.nextIndices     = new int[capacity];
        this.allocated       = new boolean[capacity];
        this.active          = new boolean[capacity];

        this.firstFreeIndex   = 0;
        this.lastFreeIndex    = capacity == 1 ? 0 : 1;
        this.firstActiveIndex = -1;
        this.lastActiveIndex  = -1;
        this.allocatedCount   = 0;

        Arrays.fill(this.previousIndices, -1);
        Arrays.fill(this.nextIndices, -1);

        for (int poolIndex = 0; poolIndex < capacity; poolIndex++) {
            this.spriteEffects[poolIndex] = new SpriteEffect(poolIndex);

            if (poolIndex > 0) {
                int followingIndex = this.nextIndices[0];

                this.previousIndices[poolIndex] = 0;
                this.nextIndices[poolIndex]     = followingIndex;

                if (followingIndex >= 0) {
                    this.previousIndices[followingIndex] = poolIndex;
                }

                this.nextIndices[0] = poolIndex;
            }
        }
    }

    /**
     * @return the configured storage capacity
     */
    public int getCapacity() {
        return this.spriteEffects.length;
    }

    /**
     * @param poolIndex the storage index
     * @return the retained storage object
     */
    public @NotNull SpriteEffect getSpriteEffect(int poolIndex) {
        return this.spriteEffects[poolIndex];
    }

    /**
     * @param poolIndex the storage index
     * @return the preceding intrusive link, or {@code -1}
     */
    public int getPreviousIndex(int poolIndex) {
        return this.previousIndices[poolIndex];
    }

    /**
     * @param poolIndex the storage index
     * @return the following intrusive link, or {@code -1}
     */
    public int getNextIndex(int poolIndex) {
        return this.nextIndices[poolIndex];
    }

    /**
     * @param poolIndex the storage index
     * @return {@code true} if the slot is allocated, {@code false} otherwise
     */
    public boolean isAllocated(int poolIndex) {
        return this.allocated[poolIndex];
    }

    /**
     * @param poolIndex the storage index
     * @return {@code true} if the slot belongs to the active list, {@code false} otherwise
     */
    public boolean isActive(int poolIndex) {
        return this.active[poolIndex];
    }

    /**
     * @return the free head index, or {@code -1}
     */
    public int getFirstFreeIndex() {
        return this.firstFreeIndex;
    }

    /**
     * @return the free tail index, or {@code -1}
     */
    public int getLastFreeIndex() {
        return this.lastFreeIndex;
    }

    /**
     * @return the active head index, or {@code -1}
     */
    public int getFirstActiveIndex() {
        return this.firstActiveIndex;
    }

    /**
     * @return the active tail index, or {@code -1}
     */
    public int getLastActiveIndex() {
        return this.lastActiveIndex;
    }

    /**
     * @return the number of slots removed from the free list
     */
    public int getAllocatedCount() {
        return this.allocatedCount;
    }


    /**
     * Zeroes every slot before a saved game's sprite effects are read into it, as the sprite effect list reader clears the array.
     */
    public void clear() {
        for (SpriteEffect spriteEffect : this.spriteEffects) {
            spriteEffect.setSprite(null);
            spriteEffect.setLife(0);
        }

        Arrays.fill(this.allocated, false);
        Arrays.fill(this.active, false);
        Arrays.fill(this.previousIndices, -1);
        Arrays.fill(this.nextIndices, -1);

        this.allocatedCount   = 0;
        this.firstFreeIndex   = -1;
        this.lastFreeIndex    = -1;
        this.firstActiveIndex = -1;
        this.lastActiveIndex  = -1;
    }

    /**
     * Restores a saved sprite effect's fields into its slot.
     *
     * @param poolIndex the storage index
     * @param life      the life DWORD
     * @param sprite    the sprite, or {@code null}
     */
    public void restoreSavedSpriteEffect(int poolIndex, int life, @Nullable Sprite sprite) {
        SpriteEffect spriteEffect = this.spriteEffects[poolIndex];

        spriteEffect.setLife(life);
        spriteEffect.setSprite(sprite);
    }

    /**
     * Rebuilds both lists after a saved game's sprite effects were read: the slots without a sprite form the free list and the slots with one the
     * active list, each in ascending slot order (the first slot head and tail, every later one appended after the tail); the saved links are not
     * kept.
     */
    public void rebuildLists() {
        for (int poolIndex = 0; poolIndex < this.spriteEffects.length; poolIndex++) {
            boolean hasSprite = this.spriteEffects[poolIndex].getSpriteOrNull() != null;
            int lastIndex = hasSprite ? this.lastActiveIndex : this.lastFreeIndex;

            this.previousIndices[poolIndex] = lastIndex;
            this.nextIndices[poolIndex]     = -1;

            if (lastIndex >= 0) {
                this.nextIndices[lastIndex] = poolIndex;
            }

            if (hasSprite) {
                if (lastIndex < 0) {
                    this.firstActiveIndex = poolIndex;
                }

                this.lastActiveIndex      = poolIndex;
                this.allocated[poolIndex] = true;
                this.active[poolIndex]    = true;
                this.allocatedCount++;
            } else {
                if (lastIndex < 0) {
                    this.firstFreeIndex = poolIndex;
                }

                this.lastFreeIndex = poolIndex;
            }
        }
    }

    /**
     * Removes the free head and stores life {@code 1} before sprite initialization.
     *
     * @return the allocated slot, or {@code null} when exhausted
     */
    public @Nullable SpriteEffect allocate() {
        return this.allocate(true);
    }

    /**
     * Removes the free head with the lifetime-write timing selected by the caller.
     *
     * @param initializeLife {@code true} to store life before sprite creation, {@code false} to leave it to the caller
     * @return the allocated slot, or {@code null} when exhausted
     */
    public @Nullable SpriteEffect allocate(boolean initializeLife) {
        if (this.firstFreeIndex < 0) {
            return null;
        }

        int poolIndex = this.firstFreeIndex;

        this.firstFreeIndex = this.nextIndices[poolIndex];

        if (this.firstFreeIndex < 0) {
            this.lastFreeIndex = -1;
        } else {
            this.previousIndices[this.firstFreeIndex] = -1;
        }

        this.previousIndices[poolIndex] = -1;
        this.nextIndices[poolIndex]     = -1;
        this.allocated[poolIndex]       = true;
        this.allocatedCount++;

        if (initializeLife) {
            this.spriteEffects[poolIndex].setLife(INITIAL_LIFE);
        }

        return this.spriteEffects[poolIndex];
    }

    /**
     * Stores the sprite result and links the slot after the head of the active list, or of the free list when sprite initialization failed; an empty
     * list takes the slot as its head and tail.
     *
     * @param spriteEffect the detached allocated storage
     * @param sprite       the sprite initialization result, or {@code null} on failure
     * @throws IllegalStateException if the sprite effect is not detached allocated storage of this pool
     */
    public void finishInitialization(@NotNull SpriteEffect spriteEffect, @Nullable Sprite sprite) {
        int poolIndex = spriteEffect.getPoolIndex();

        if (poolIndex < 0 || poolIndex >= this.spriteEffects.length || this.spriteEffects[poolIndex] != spriteEffect ||
            !this.allocated[poolIndex] || this.active[poolIndex]) {
            throw Exceptions.newIllegalStateException("Sprite effect initialization requires detached allocated storage.");
        }

        spriteEffect.setSprite(sprite);

        int firstIndex = sprite == null ? this.firstFreeIndex : this.firstActiveIndex;
        int followingIndex = firstIndex < 0 ? -1 : this.nextIndices[firstIndex];

        this.previousIndices[poolIndex] = firstIndex;
        this.nextIndices[poolIndex]     = followingIndex;

        if (firstIndex >= 0) {
            this.nextIndices[firstIndex] = poolIndex;
        }

        if (followingIndex >= 0) {
            this.previousIndices[followingIndex] = poolIndex;
        }

        if (sprite == null) {
            if (firstIndex < 0) {
                this.firstFreeIndex = poolIndex;
            }

            if (followingIndex < 0) {
                this.lastFreeIndex = poolIndex;
            }

            this.allocated[poolIndex] = false;
            this.allocatedCount--;
        } else {
            if (firstIndex < 0) {
                this.firstActiveIndex = poolIndex;
            }

            if (followingIndex < 0) {
                this.lastActiveIndex = poolIndex;
            }

            this.active[poolIndex] = true;
        }
    }

    /**
     * Clears the expired sprite and transfers an active sprite effect after the free head.
     *
     * @param spriteEffect the active storage whose sprite has no remaining images
     * @throws IllegalStateException if the sprite effect is not active storage of this pool
     */
    public void recycle(@NotNull SpriteEffect spriteEffect) {
        int poolIndex = spriteEffect.getPoolIndex();

        if (poolIndex < 0 || poolIndex >= this.spriteEffects.length || this.spriteEffects[poolIndex] != spriteEffect || !this.active[poolIndex]) {
            throw Exceptions.newIllegalStateException("Sprite effect retirement requires active storage.");
        }

        int previousIndex = this.previousIndices[poolIndex];
        int followingIndex = this.nextIndices[poolIndex];

        if (this.firstActiveIndex == poolIndex) {
            this.firstActiveIndex = followingIndex;
        }

        if (this.lastActiveIndex == poolIndex) {
            this.lastActiveIndex = previousIndex;
        }

        if (previousIndex >= 0) {
            this.nextIndices[previousIndex] = followingIndex;
        }

        if (followingIndex >= 0) {
            this.previousIndices[followingIndex] = previousIndex;
        }

        this.previousIndices[poolIndex] = -1;
        this.nextIndices[poolIndex]     = -1;
        this.active[poolIndex]          = false;

        this.finishInitialization(spriteEffect, null);
    }
}
