package org.fogofwarlabs.sc4j.scr.client.world;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Orders sprites for drawing, and ranks them for pointer picking and selection replacement, by one unsigned {@code 32}-bit key.
 * <p>
 * The key packs, from the high bits down, the elevation level, the vertical position as a signed {@code 16}-bit value (only up to elevation
 * {@code 4}, otherwise {@code 0}), the turret flag and the reserved WORD: {@code ((elevation << 13 | y) << 5 | turret) << 9 | word}. It keeps only
 * the low {@code 32} bits, so elevations from {@code 32} wrap and a negative position fills the high bits. A larger key is drawn later and wins a
 * pick or a selection slot.
 * <p>
 * {@link #collect} walks the tile rows from four rows above the camera's top row through the camera row plus the view height in pixels and four, at
 * most the last map row. It pushes every sprite of a row list that the view sees into a binary max-heap and drains the heap: sprites are drawn in
 * ascending key order, equal keys in the order the heap releases them. The heap storage is reused and grows when needed.
 * <p>
 * The view sees a sprite by its visibility flags only; the hidden sprite flag is not tested. A game view tests the flags against the local comparison
 * mask. A replay or observer view tests them against the replay vision mask and also sees the {@code sprites.dat} entries {@code 1..128} and
 * {@code 387..479} whatever their visibility, and every neutral sprite while the entire map is revealed ({@link LocalVision}).
 */
public final class SpriteDrawOrder {
    private static final int MAXIMUM_POSITION_ELEVATION = 4;
    private static final int POSITION_BITS = 13;
    private static final int FLAG_BITS = 5;
    private static final int RESERVED_WORD_SHIFT = 9;

    /**
     * The first {@code sprites.dat} entry of the lower range a replay or observer view always sees.
     */
    private static final int FIRST_LOWER_REPLAY_SPRITE = 1;
    /**
     * The last {@code sprites.dat} entry of the lower range a replay or observer view always sees.
     */
    private static final int LAST_LOWER_REPLAY_SPRITE = 0x80;
    /**
     * The first {@code sprites.dat} entry of the upper range a replay or observer view always sees.
     */
    private static final int FIRST_UPPER_REPLAY_SPRITE = 0x183;
    /**
     * The last {@code sprites.dat} entry of the upper range a replay or observer view always sees.
     */
    private static final int LAST_UPPER_REPLAY_SPRITE = 0x1DF;

    /**
     * The tile rows collected above the camera's top row, and added below the view height.
     */
    private static final int ROW_MARGIN = 4;
    /**
     * The heap index of the root; index {@code 0} is unused.
     */
    private static final int HEAP_ROOT = 1;
    private static final int INITIAL_CAPACITY = 256;
    private static final int CAPACITY_GROWTH_FACTOR = 2;

    /**
     * Computes the unsigned {@code 32}-bit key that orders a sprite for drawing, picking and selection replacement.
     *
     * @param sprite the sprite
     * @return the key; compare keys with {@link Integer#compareUnsigned}
     */
    public static int computeKey(@NotNull Sprite sprite) {
        int elevationLevel = sprite.getElevationLevel();
        int position = elevationLevel <= MAXIMUM_POSITION_ELEVATION ? (short) sprite.getY() : 0;

        return ((sprite.getFlags() & Sprite.FLAG_TURRET) | (elevationLevel << POSITION_BITS | position) << FLAG_BITS) << RESERVED_WORD_SHIFT |
               sprite.getReservedWord();
    }

    /**
     * Tests whether the view sees a sprite: by its visibility flags only, with the replay or observer rule when it applies.
     *
     * @param sprite           the sprite
     * @param replayOrObserver whether the view is a replay or an observer view
     * @param viewMask         the players whose vision the view shows ({@link LocalVision#getViewMask})
     * @param mapRevealed      whether the replay reveals the entire map
     * @return {@code true} if the sprite is collected for drawing, {@code false} otherwise
     */
    private static boolean isSeen(@NotNull Sprite sprite, boolean replayOrObserver, int viewMask, boolean mapRevealed) {
        if ((sprite.getVisibilityFlags() & viewMask) != 0) {
            return true;
        }

        if (!replayOrObserver) {
            return false;
        }

        int spriteTypeId = sprite.getSpriteTypeId();

        return mapRevealed && sprite.getOwner() == PlayerTable.NEUTRAL_PLAYER_ID ||
               spriteTypeId >= FIRST_LOWER_REPLAY_SPRITE && spriteTypeId <= LAST_LOWER_REPLAY_SPRITE ||
               spriteTypeId >= FIRST_UPPER_REPLAY_SPRITE && spriteTypeId <= LAST_UPPER_REPLAY_SPRITE;
    }

    private int @NotNull [] heapKeys;
    private int @NotNull [] heapPoolIndices;
    private int heapCount;

    private int @NotNull [] orderedPoolIndices;
    private int orderedCount;

    /**
     * Creates a {@link SpriteDrawOrder} instance with reusable heap and order storage.
     */
    SpriteDrawOrder() {
        this.heapKeys        = new int[INITIAL_CAPACITY + HEAP_ROOT];
        this.heapPoolIndices = new int[INITIAL_CAPACITY + HEAP_ROOT];
        this.heapCount       = 0;

        this.orderedPoolIndices = new int[INITIAL_CAPACITY];
        this.orderedCount       = 0;
    }

    /**
     * @param orderIndex the non-negative position in draw order below the last collected count
     * @return the sprite pool index drawn at that position
     * @throws IndexOutOfBoundsException if the position is negative or not below the last collected count
     */
    int getPoolIndex(int orderIndex) {
        if (orderIndex < 0 || orderIndex >= this.orderedCount) {
            throw Exceptions.newIndexOutOfBoundsException("Sprite draw order index {0} is outside {1}.", orderIndex, this.orderedCount);
        }

        return this.orderedPoolIndices[orderIndex];
    }


    /**
     * Collects the sprites the view sees in the tile rows around the camera and orders them for drawing.
     *
     * @param sprites    the game sprite pool
     * @param cameraRow  the tile row of the camera's top edge
     * @param viewHeight the view height in pixels
     * @param mapHeight  the map height in tiles
     * @param vision     the vision of the local view
     * @return the number of ordered sprites
     */
    int collect(@NotNull SpritePool sprites, int cameraRow, int viewHeight, int mapHeight, @NotNull LocalVision vision) {
        boolean replayOrObserver = vision.isReplayOrObserver();
        int viewMask = vision.getViewMask();
        boolean mapRevealed = replayOrObserver && vision.isRevealed();
        int firstRow = Math.max(0, cameraRow - ROW_MARGIN);
        int lastRow = cameraRow + viewHeight + ROW_MARGIN;

        if (lastRow >= mapHeight) {
            lastRow = mapHeight - 1;
        }

        this.heapCount = 0;

        for (int row = firstRow; row <= lastRow; row++) {
            for (int poolIndex = sprites.getFirstRowIndex(row); poolIndex >= 0; poolIndex = sprites.getNextRowIndex(poolIndex)) {
                Sprite sprite = sprites.getSprite(poolIndex);

                if (isSeen(sprite, replayOrObserver, viewMask, mapRevealed)) {
                    this.push(computeKey(sprite), poolIndex);
                }
            }
        }

        this.drain();

        return this.orderedCount;
    }

    /**
     * Adds a sprite at the bottom of the heap and moves it up past every parent with a smaller key.
     *
     * @param key       the sprite's order key ({@link #computeKey})
     * @param poolIndex the sprite pool index
     */
    private void push(int key, int poolIndex) {
        if (this.heapCount + HEAP_ROOT == this.heapKeys.length) {
            this.heapKeys        = Arrays.copyOf(this.heapKeys, this.heapKeys.length * CAPACITY_GROWTH_FACTOR);
            this.heapPoolIndices = Arrays.copyOf(this.heapPoolIndices, this.heapPoolIndices.length * CAPACITY_GROWTH_FACTOR);
        }

        this.heapCount++;

        int position = this.heapCount;

        while (position > HEAP_ROOT) {
            int parent = position >> 1;

            if (Integer.compareUnsigned(key, this.heapKeys[parent]) <= 0) {
                break;
            }

            this.heapKeys[position]        = this.heapKeys[parent];
            this.heapPoolIndices[position] = this.heapPoolIndices[parent];

            position = parent;
        }

        this.heapKeys[position]        = key;
        this.heapPoolIndices[position] = poolIndex;
    }

    /**
     * Releases the heap root by root into the draw order from its end, so the order starts with the smallest key. After each release the last entry
     * moves down from the root past every larger child, preferring the right child only when it is larger.
     */
    private void drain() {
        int count = this.heapCount;

        if (count > this.orderedPoolIndices.length) {
            this.orderedPoolIndices = new int[this.heapKeys.length];
        }

        for (int orderIndex = count - 1; orderIndex >= 0; orderIndex--) {
            int size = this.heapCount - 1;
            int movedKey = this.heapKeys[this.heapCount];
            int movedPoolIndex = this.heapPoolIndices[this.heapCount];
            int position = HEAP_ROOT;

            this.orderedPoolIndices[orderIndex] = this.heapPoolIndices[HEAP_ROOT];

            while (position <= size >> 1) {
                int child = position << 1;

                if (child < size && Integer.compareUnsigned(this.heapKeys[child], this.heapKeys[child + 1]) < 0) {
                    child++;
                }

                if (Integer.compareUnsigned(this.heapKeys[child], movedKey) <= 0) {
                    break;
                }

                this.heapKeys[position]        = this.heapKeys[child];
                this.heapPoolIndices[position] = this.heapPoolIndices[child];

                position = child;
            }

            this.heapKeys[position]        = movedKey;
            this.heapPoolIndices[position] = movedPoolIndex;

            this.heapCount = size;
        }

        this.orderedCount = count;
    }
}
