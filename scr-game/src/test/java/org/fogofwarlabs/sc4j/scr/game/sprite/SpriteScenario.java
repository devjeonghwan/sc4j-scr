package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

/**
 * Creates pooled sprites and images for tests that do not run the complete sprite creation, and finds instruction sequences in script
 * contents.
 */
public final class SpriteScenario {
    /**
     * The capacity of a one-slot pool.
     */
    private static final int SINGLE_SLOT_CAPACITY = 1;
    /**
     * The sprite capacity created for a game that has no sprite pool yet.
     */
    private static final int GAME_SPRITE_POOL_CAPACITY = 32;
    /**
     * The image capacity created for a game that has no image pool yet.
     */
    private static final int GAME_IMAGE_POOL_CAPACITY = 512;
    /**
     * The owner player of the created sprites.
     */
    private static final int CREATED_SPRITE_OWNER = 0;
    /**
     * The sprite flags of the created sprites: none.
     */
    private static final int CREATED_SPRITE_FLAGS = 0;
    /**
     * The image flags of the created images: none.
     */
    private static final int CREATED_IMAGE_FLAGS = 0;
    /**
     * The draw function of the created images: the normal one.
     */
    private static final int NORMAL_MODIFIER = 0;
    /**
     * The first script content offset that {@link #findSequence} searches.
     */
    private static final int FIRST_SEARCH_OFFSET = 1;

    /**
     * Creates a {@link Sprite} instance in a new one-slot pool.
     *
     * @param type               the sprite type ID
     * @param horizontalPosition the horizontal position
     * @param verticalPosition   the vertical position
     * @param elevation          the elevation level
     * @return the created {@link Sprite} instance
     */
    public static @NotNull Sprite createSprite(int type, int horizontalPosition, int verticalPosition, int elevation) {
        Sprite sprite = new SpritePool(SINGLE_SLOT_CAPACITY).allocate();

        Assertions.assertNotNull(sprite);

        sprite.initializeNativeStorage(type, horizontalPosition, verticalPosition, CREATED_SPRITE_OWNER, false);
        sprite.setElevationLevel(elevation);
        sprite.setFlags(CREATED_SPRITE_FLAGS);

        return sprite;
    }

    /**
     * Creates a {@link Sprite} instance at the requested index of a new pool; the slots the pool hands out first stay allocated.
     *
     * @param type               the sprite type ID
     * @param horizontalPosition the horizontal position
     * @param verticalPosition   the vertical position
     * @param elevation          the elevation level
     * @param poolIndex          the requested pool index
     * @return the created {@link Sprite} instance
     */
    public static @NotNull Sprite createSprite(int type, int horizontalPosition, int verticalPosition, int elevation, int poolIndex) {
        SpritePool pool = new SpritePool(poolIndex + 1);
        Sprite sprite;

        do {
            sprite = pool.allocate();

            Assertions.assertNotNull(sprite);
        } while (sprite.getPoolIndex() != poolIndex);

        sprite.initializeNativeStorage(type, horizontalPosition, verticalPosition, CREATED_SPRITE_OWNER, false);
        sprite.setElevationLevel(elevation);
        sprite.setFlags(CREATED_SPRITE_FLAGS);

        return sprite;
    }

    /**
     * Creates a {@link Sprite} instance in the game's sprite pool, which is created first when the game has none, and attaches it to the row of its
     * vertical position.
     *
     * @param game               the game
     * @param type               the sprite type ID
     * @param horizontalPosition the horizontal position
     * @param verticalPosition   the vertical position
     * @param elevation          the elevation level
     * @return the created {@link Sprite} instance, allocated from the game's sprite pool
     * @throws AssertionError if the game's sprite pool is exhausted
     */
    public static @NotNull Sprite createSprite(@NotNull Game game, int type, int horizontalPosition, int verticalPosition, int elevation) {
        if (game.getSpriteSystem().getSpritePool() == null) {
            game.getSpriteSystem().initializeSpritePool(GAME_SPRITE_POOL_CAPACITY);
        }

        SpritePool observedSpritePool = game.getSpriteSystem().getSpritePool();

        Assertions.assertNotNull(observedSpritePool);

        Sprite sprite = observedSpritePool.allocate();

        if (sprite == null) {
            throw Exceptions.newAssertionError("The fixture sprite pool is exhausted.");
        }

        sprite.initializeNativeStorage(type, horizontalPosition, verticalPosition, CREATED_SPRITE_OWNER, false);
        sprite.setElevationLevel(elevation);
        sprite.setFlags(CREATED_SPRITE_FLAGS);

        observedSpritePool = game.getSpriteSystem().getSpritePool();

        Assertions.assertNotNull(observedSpritePool);

        observedSpritePool.attachToRow(sprite,
                                       Math.max(0, Math.min((short) verticalPosition / TerrainTileset.TILE_SIZE, game.getMap().getHeight() - 1)));

        return sprite;
    }

    /**
     * Creates a {@link SpriteImage} instance in a new one-slot pool.
     *
     * @param type             the image type ID
     * @param horizontalOffset the horizontal image offset
     * @param verticalOffset   the vertical image offset
     * @return the created {@link SpriteImage} instance
     */
    public static @NotNull SpriteImage createImage(int type, int horizontalOffset, int verticalOffset) {
        SpriteImage image = new SpriteImagePool(SINGLE_SLOT_CAPACITY).allocate();

        Assertions.assertNotNull(image);

        image.initializeNativeStorage(type, horizontalOffset, verticalOffset, CREATED_IMAGE_FLAGS, NORMAL_MODIFIER);

        return image;
    }

    /**
     * Creates a {@link SpriteImage} instance in the game's image pool, which is created first when the game has none.
     *
     * @param game             the game
     * @param type             the image type ID
     * @param horizontalOffset the horizontal image offset
     * @param verticalOffset   the vertical image offset
     * @return the created {@link SpriteImage} instance, allocated from the game's image pool
     * @throws AssertionError if the game's image pool is exhausted
     */
    public static @NotNull SpriteImage createImage(@NotNull Game game, int type, int horizontalOffset, int verticalOffset) {
        if (game.getSpriteSystem().getImagePool() == null) {
            game.getSpriteSystem().initializeImagePool(GAME_IMAGE_POOL_CAPACITY);
        }

        SpriteImagePool observedImagePool = game.getSpriteSystem().getImagePool();

        Assertions.assertNotNull(observedImagePool);

        SpriteImage image = observedImagePool.allocate();

        if (image == null) {
            throw Exceptions.newAssertionError("The fixture image pool is exhausted.");
        }

        image.initializeNativeStorage(type, horizontalOffset, verticalOffset, CREATED_IMAGE_FLAGS, NORMAL_MODIFIER);

        return image;
    }


    /**
     * Finds the first occurrence of a byte sequence in script contents, searching from offset {@code 1}.
     *
     * @param contents the script contents
     * @param sequence the bytes to find
     * @return the offset of the first match
     * @throws AssertionError if the contents do not contain the sequence
     */
    public static int findSequence(byte @NotNull [] contents, byte @NotNull [] sequence) {
        for (int offset = FIRST_SEARCH_OFFSET; offset <= contents.length - sequence.length; offset++) {
            int matched = 0;

            while (matched < sequence.length && contents[offset + matched] == sequence[matched]) {
                matched++;
            }

            if (matched == sequence.length) {
                return offset;
            }
        }

        throw Exceptions.newAssertionError("The installed script does not contain the required opcode byte fixture.");
    }

    private SpriteScenario() {
    }
}
