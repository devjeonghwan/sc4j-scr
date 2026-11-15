package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.visibility.SightCoverage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Checks startup effects, effect updates and primary effect traversal.
 */
@RequiresInstallation
final class IscriptSpriteEffectCreationTest {
    /**
     * The SHA-256 digest of the decompressed primary effect traversal capture.
     */
    private static final @NotNull String TRAVERSAL_CAPTURE_HASH = "21e5bc1305ab05bdefe36150527ea65d037072fcc2bdb62ed1633ab1ad435bbc";
    /**
     * The SHA-256 digest of the images of the startup and decoration effects after their creation.
     */
    private static final @NotNull String DECORATION_CREATION_HASH = "11228368c5e5fc6bb32b3dd84c16978f5d2449043cd3ee87f644560d0ac74683";
    /**
     * The SHA-256 digest of the images of the startup and decoration effects at every snapshot.
     */
    private static final @NotNull String DECORATION_SNAPSHOT_HASH = "b3e7aed9c88c9073cb85fbc6479963a0b88999996f34eb8a7503102feffd6661";
    /**
     * The digest algorithm of the decoration image digests.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    /**
     * The life of an allocated effect before its sprite is attached.
     */
    private static final int INITIAL_LIFE = 1;
    /**
     * The capacity of the sprite effect and sprite pools of the update test.
     */
    private static final int UPDATE_POOL_CAPACITY = 2;
    /**
     * The capacity of the image pool of the update test.
     */
    private static final int UPDATE_IMAGE_POOL_CAPACITY = 4;
    /**
     * The capacity of the secondary effect pool.
     */
    private static final int SECONDARY_POOL_CAPACITY = 3;
    /**
     * The instruction limit of the startup effect initialization and the sprite effect updates of the update test.
     */
    private static final int INSTRUCTION_LIMIT = 512;
    /**
     * The wait of the startup effect's image after its Init.
     */
    private static final int STARTUP_INIT_WAIT = 124;
    /**
     * The wait of the startup effect's image after the first update.
     */
    private static final int FIRST_UPDATE_WAIT = 123;
    /**
     * The wait of the startup effect's image after the second update.
     */
    private static final int SECOND_UPDATE_WAIT = 122;
    /**
     * The wait of the startup effect's image after the third update.
     */
    private static final int THIRD_UPDATE_WAIT = 121;
    /**
     * The iscript whose Death animation reaches {@code end}.
     */
    private static final int END_SCRIPT = 33;
    /**
     * The byte distance from the Death entry of {@link #END_SCRIPT} to its {@code end} instruction.
     */
    private static final int END_DISTANCE = 2;
    /**
     * The opcode of {@code end}.
     */
    private static final int END_OPCODE = 0x16;
    /**
     * The Death animation.
     */
    private static final int DEATH_ANIMATION = 1;
    /**
     * The sprite types the traversal cases rotate through, the last one outside {@code sprites.dat}.
     */
    private static final int @NotNull [] TRAVERSAL_SPRITE_TYPES = {16, 129, 130, 385, 386, 480, 481, 65535};
    /**
     * The byte capacity of one case vector.
     */
    private static final int VECTOR_CAPACITY = 256;
    /**
     * The number of sprite type rotations of the traversal cases.
     */
    private static final int ROTATION_COUNT = 8;
    /**
     * The number of masks of the ending effects of the traversal cases, one bit per slot.
     */
    private static final int ENDING_MASK_COUNT = 64;
    /**
     * The width and height of the traversal map in tiles.
     */
    private static final int TRAVERSAL_MAP_DIMENSION = 2;
    /**
     * The tile count of the traversal map.
     */
    private static final int TRAVERSAL_MAP_TILE_COUNT = TRAVERSAL_MAP_DIMENSION * TRAVERSAL_MAP_DIMENSION;
    /**
     * The flags of every tile of the traversal map.
     */
    private static final int TRAVERSAL_TILE_FLAGS = 0xAB00BBAA;
    /**
     * The capacity of the effect, sprite and image pools of the traversal cases.
     */
    private static final int TRAVERSAL_POOL_CAPACITY = 6;
    /**
     * The vision of player {@code 0}.
     */
    private static final int FIRST_PLAYER_VISION = 1;
    /**
     * The number of effects that every traversal case allocates.
     */
    private static final int TRAVERSAL_EFFECT_COUNT = 5;
    /**
     * The horizontal and vertical position of the traversal sprites.
     */
    private static final int TRAVERSAL_POSITION = 32;
    /**
     * The width of the traversal sprites.
     */
    private static final int TRAVERSAL_SPRITE_WIDTH = 32;
    /**
     * The height of the traversal sprites.
     */
    private static final int TRAVERSAL_SPRITE_HEIGHT = 64;
    /**
     * The registration value of the traversal sprites.
     */
    private static final int REGISTRATION_VALUE = 0x12345678;
    /**
     * The sprite row of the traversal sprites.
     */
    private static final int TRAVERSAL_ROW = 1;
    /**
     * The image type of the traversal images.
     */
    private static final int TRAVERSAL_IMAGE = 445;
    /**
     * The instruction limit of the traversal.
     */
    private static final int TRAVERSAL_INSTRUCTION_LIMIT = 16;
    /**
     * The slot the traversal cases leave unallocated.
     */
    private static final int UNALLOCATED_SLOT = 1;
    /**
     * The DWORD fields before the slots of a traversal record.
     */
    private static final int TRAVERSAL_HEADER_WORDS = 6;
    /**
     * The captured DWORD fields of one slot of a traversal record.
     */
    private static final int TRAVERSAL_SLOT_WORDS = 6;
    /**
     * The DWORD fields after the slots of a traversal record.
     */
    private static final int TRAVERSAL_TAIL_WORDS = 0;
    /**
     * The owner of the traversal sprites.
     */
    private static final int TRAVERSAL_OWNER = 0;
    /**
     * The image flag that requests a redraw.
     */
    private static final int REDRAW_FLAG = 0x01;
    /**
     * The draw function of the traversal images.
     */
    private static final int NORMAL_MODIFIER = 0;
    /**
     * The wait of a traversal image that reaches {@code end} in the traversal.
     */
    private static final int ENDING_WAIT = 0;
    /**
     * The wait of a traversal image that keeps waiting in the traversal.
     */
    private static final int WAITING_WAIT = 1;
    /**
     * The row index of a sprite outside the sprite rows.
     */
    private static final int NO_ROW = -1;
    /**
     * The width and height of the decoration map in tiles.
     */
    private static final int DECORATION_MAP_DIMENSION = 64;
    /**
     * The tile count of the decoration map.
     */
    private static final int DECORATION_MAP_TILE_COUNT = DECORATION_MAP_DIMENSION * DECORATION_MAP_DIMENSION;
    /**
     * The seed of the decoration game's random generator.
     */
    private static final int DECORATION_RANDOM_SEED = 0x12345678;
    /**
     * The capacity of the effect and sprite pools of the decoration test.
     */
    private static final int DECORATION_POOL_CAPACITY = 64;
    /**
     * The capacity of the image pool of the decoration test.
     */
    private static final int DECORATION_IMAGE_POOL_CAPACITY = 128;
    /**
     * The instruction limit of the decoration test.
     */
    private static final int DECORATION_INSTRUCTION_LIMIT = 128;
    /**
     * The cursor marker sprite type, the sprite of the startup effect.
     */
    private static final int STARTUP_SPRITE = 318;
    /**
     * The sprite flag of a hidden sprite.
     */
    private static final int HIDDEN_FLAG = 0x20;
    /**
     * The visibility flags of a sprite visible to no player.
     */
    private static final int NO_PLAYERS_VISIBILITY = 0;
    /**
     * The case of the startup effect, before the decoration effects.
     */
    private static final int STARTUP_CASE = 0;
    /**
     * The number of effects: the startup effect and {@code 40} decoration effects.
     */
    private static final int EFFECT_COUNT = 41;
    /**
     * The sprite type before the first decoration sprite type.
     */
    private static final int DECORATION_SPRITE_BASE = 15;
    /**
     * The horizontal position of the decoration effects.
     */
    private static final int DECORATION_POSITION_X = 321;
    /**
     * The vertical position of the decoration effects.
     */
    private static final int DECORATION_POSITION_Y = 654;
    /**
     * The owner of the decoration effects, wider than the owner byte.
     */
    private static final int DECORATION_OWNER = 0x123;
    /**
     * The owner byte that the decoration sprites keep.
     */
    private static final int STORED_OWNER = 0x23;
    /**
     * The sprite row of {@link #DECORATION_POSITION_Y}.
     */
    private static final int DECORATION_ROW = 20;
    /**
     * The number of snapshots of every decoration effect, with an execution between consecutive snapshots.
     */
    private static final int SNAPSHOT_COUNT = 8;
    /**
     * The capacity of the effect and sprite pools of the failed creation test.
     */
    private static final int FAILED_POOL_CAPACITY = 3;
    /**
     * The capacity of an image pool with room for a single image.
     */
    private static final int SINGLE_IMAGE_POOL_CAPACITY = 1;
    /**
     * The horizontal position of the failed effect.
     */
    private static final int FAILED_POSITION_X = 32;
    /**
     * The instruction limit of the failed creations.
     */
    private static final int FAILED_INSTRUCTION_LIMIT = 16;
    /**
     * The first effect slot.
     */
    private static final int FIRST_SLOT = 0;
    /**
     * The second effect slot.
     */
    private static final int SECOND_SLOT = 1;
    /**
     * The third effect slot.
     */
    private static final int THIRD_SLOT = 2;
    /**
     * The width and height of the map of the update and failed creation tests in tiles.
     */
    private static final int SMALL_MAP_DIMENSION = 1;
    /**
     * The tile count of the map of the update and failed creation tests.
     */
    private static final int SMALL_MAP_TILE_COUNT = SMALL_MAP_DIMENSION * SMALL_MAP_DIMENSION;
    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;
    /**
     * The seed of the game random generator outside the decoration test.
     */
    private static final int RANDOM_SEED = 0;
    /**
     * The frame of the update phase.
     */
    private static final int UPDATE_FRAME = 0;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }


    @Test
    void update_sprite_effects_should_update_primary_before_guard_and_require_visibility_context() {
        Game game = Game.create(gameData,
                                new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, TILESET, new int[SMALL_MAP_TILE_COUNT]),
                                new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeSpriteEffectPool(UPDATE_POOL_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(UPDATE_POOL_CAPACITY);
        game.getSpriteSystem().initializeImagePool(UPDATE_IMAGE_POOL_CAPACITY);

        SpriteEffectPool secondaryPool = new SpriteEffectPool(SECONDARY_POOL_CAPACITY);

        game.getIscriptExecutor().initializeSpriteEffects(INSTRUCTION_LIMIT);

        SpriteEffect primary = game.getSpriteSystem().getStartupSpriteEffect();

        Assertions.assertNotNull(primary);

        Sprite observedSprite = primary.getSpriteOrNull();

        Assertions.assertNotNull(observedSprite);

        SpriteImage image = observedSprite.getMainImage();

        Assertions.assertNotNull(image);

        Assertions.assertEquals(STARTUP_INIT_WAIT, image.getWait());

        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> game.getIscriptExecutor().updateSpriteEffects(secondaryPool, INSTRUCTION_LIMIT));

        Assertions.assertEquals(FIRST_UPDATE_WAIT, image.getWait());

        game.initializeExecutionScope();

        GameExecutionScope initializationScope = game.getExecutionScope();

        Assertions.assertNotNull(initializationScope);

        initializationScope.finishInitialization();
        initializationScope.beginFramePhase(UPDATE_FRAME);

        try {
            game.getIscriptExecutor().updateSpriteEffects(secondaryPool, INSTRUCTION_LIMIT);

            Assertions.assertEquals(SECOND_UPDATE_WAIT, image.getWait());
            Assertions.assertEquals(-1, secondaryPool.getFirstActiveIndex());
            Assertions.assertEquals(0, secondaryPool.getAllocatedCount());

            SpriteEffect secondary = secondaryPool.allocate();

            Assertions.assertNotNull(secondary);

            SpritePool observedSpritePool = game.getSpriteSystem().getSpritePool();

            Assertions.assertNotNull(observedSpritePool);

            secondaryPool.finishInitialization(secondary, observedSpritePool.allocate());

            Assertions.assertThrows(IllegalStateException.class,
                                    () -> game.getIscriptExecutor().updateSpriteEffects(secondaryPool, INSTRUCTION_LIMIT));

            Assertions.assertEquals(THIRD_UPDATE_WAIT, image.getWait());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        } finally {
            initializationScope.endFramePhase();
        }
    }

    @Test
    void matches_native_primary_sprite_effect_traversal_cases() throws IOException, NoSuchAlgorithmException {
        Assertions.assertNotNull(gameData);

        int endOffset = gameData.getIscript().getAnimationOffset(END_SCRIPT, DEATH_ANIMATION) + END_DISTANCE;

        Assertions.assertEquals(END_OPCODE, gameData.getIscript().decodeInstruction(endOffset).getOpcode());

        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_SPRITE_EFFECT_PRIMARY_LOOP, TRAVERSAL_CAPTURE_HASH);
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer vector = ByteBuffer.allocate(VECTOR_CAPACITY).order(ByteOrder.LITTLE_ENDIAN);

        for (int rotation = 0; rotation < ROTATION_COUNT; rotation++) {
            for (int endingMask = 0; endingMask < ENDING_MASK_COUNT; endingMask++) {
                int[] tiles = new int[TRAVERSAL_MAP_TILE_COUNT];

                Arrays.fill(tiles, TRAVERSAL_TILE_FLAGS);

                Game game = Game.create(gameData,
                                        new GameMap(TRAVERSAL_MAP_DIMENSION, TRAVERSAL_MAP_DIMENSION, TILESET, new int[TRAVERSAL_MAP_TILE_COUNT], tiles),
                                        new LcgRandom(RANDOM_SEED));

                game.getSpriteSystem().initializeSpriteEffectPool(TRAVERSAL_POOL_CAPACITY);
                game.getSpriteSystem().initializeSpritePool(TRAVERSAL_POOL_CAPACITY);
                game.getSpriteSystem().initializeImagePool(TRAVERSAL_POOL_CAPACITY);

                SightCoverage coverage = new SightCoverage(TRAVERSAL_MAP_DIMENSION);

                coverage.setSharedVisionMasks(new int[PlayerTable.PLAYER_SLOTS]);
                game.setSightCoverage(coverage);
                game.getLocalVision().setComparisonMask(FIRST_PLAYER_VISION);

                SpriteEffectPool effectPool = game.getSpriteSystem().getSpriteEffectPool();

                Assertions.assertNotNull(effectPool);

                SpritePool spritePool = game.getSpriteSystem().getSpritePool();

                Assertions.assertNotNull(spritePool);

                SpriteImagePool imagePool = game.getSpriteSystem().getImagePool();

                Assertions.assertNotNull(imagePool);

                for (int allocationIndex = 0; allocationIndex < TRAVERSAL_EFFECT_COUNT; allocationIndex++) {
                    SpriteEffect effect = effectPool.allocate();
                    Sprite sprite = spritePool.allocate();
                    SpriteImage image = imagePool.allocate();

                    Assertions.assertNotNull(effect);
                    Assertions.assertNotNull(sprite);
                    Assertions.assertNotNull(image);

                    int poolIndex = effect.getPoolIndex();
                    int spriteType = TRAVERSAL_SPRITE_TYPES[(poolIndex + rotation) % TRAVERSAL_SPRITE_TYPES.length];

                    sprite.initializeNativeStorage(spriteType, TRAVERSAL_POSITION, TRAVERSAL_POSITION, TRAVERSAL_OWNER, true);
                    sprite.setNativeDimensions(TRAVERSAL_SPRITE_WIDTH, TRAVERSAL_SPRITE_HEIGHT);
                    spritePool.register(sprite, REGISTRATION_VALUE);
                    spritePool.attachToRow(sprite, TRAVERSAL_ROW);
                    image.initializeNativeStorage(TRAVERSAL_IMAGE, 0, 0, REDRAW_FLAG, NORMAL_MODIFIER);
                    image.setProgramCounter(endOffset);
                    image.setWait((endingMask & (1 << poolIndex)) != 0 ? ENDING_WAIT : WAITING_WAIT);
                    sprite.insertTailImage(image);
                    sprite.setMainImage(image);

                    effectPool.finishInitialization(effect, sprite);
                }

                game.getIscriptExecutor().updatePrimarySpriteEffects(TRAVERSAL_INSTRUCTION_LIMIT);

                vector.clear();
                vector.putInt(rotation).putInt(endingMask);
                vector.putInt(effectPool.getFirstFreeIndex()).putInt(effectPool.getLastFreeIndex());
                vector.putInt(effectPool.getFirstActiveIndex()).putInt(effectPool.getLastActiveIndex());

                for (int poolIndex = 0; poolIndex < TRAVERSAL_POOL_CAPACITY; poolIndex++) {
                    SpriteEffect effect = effectPool.getSpriteEffect(poolIndex);
                    Sprite sprite = spritePool.getSprite(poolIndex);
                    Sprite effectSprite = effect.getSpriteOrNull();

                    vector.putInt(effectPool.getPreviousIndex(poolIndex)).putInt(effectPool.getNextIndex(poolIndex));
                    vector.putInt(effect.getLife());
                    vector.putInt(effectSprite == null ? -1 : effectSprite.getPoolIndex());
                    vector.putInt(sprite.getVisibilityFlags());

                    if (poolIndex != UNALLOCATED_SLOT) {
                        boolean ended = (endingMask & (1 << poolIndex)) != 0;

                        Assertions.assertEquals(!ended, spritePool.isAllocated(poolIndex));
                        Assertions.assertEquals(!ended, effectPool.isActive(poolIndex));
                        Assertions.assertEquals(ended ? NO_ROW : TRAVERSAL_ROW, spritePool.getRowIndex(poolIndex));
                        Assertions.assertNotNull(sprite.getMainImage());
                        Assertions.assertTrue(spritePool.isRegistered(poolIndex));
                        Assertions.assertEquals(REGISTRATION_VALUE, spritePool.getRegistrationValue(poolIndex));
                    }
                }

                SpriteEffectAssert.assertRecord(expected, vector,
                                                TRAVERSAL_HEADER_WORDS, TRAVERSAL_POOL_CAPACITY, TRAVERSAL_SLOT_WORDS, TRAVERSAL_TAIL_WORDS);

                Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
            }
        }

        Assertions.assertFalse(expected.hasRemaining());
    }

    @Test
    void startup_and_forty_decoration_effects_match_expected_image_digests() throws NoSuchAlgorithmException {
        Game game = Game.create(gameData,
                                new GameMap(DECORATION_MAP_DIMENSION, DECORATION_MAP_DIMENSION, TILESET, new int[DECORATION_MAP_TILE_COUNT]),
                                new LcgRandom(DECORATION_RANDOM_SEED));

        game.getSpriteSystem().initializeSpriteEffectPool(DECORATION_POOL_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(DECORATION_POOL_CAPACITY);
        game.getSpriteSystem().initializeImagePool(DECORATION_IMAGE_POOL_CAPACITY);

        game.getIscriptExecutor().initializeSpriteEffects(DECORATION_INSTRUCTION_LIMIT);

        SpriteEffect startup = game.getSpriteSystem().getStartupSpriteEffect();

        Assertions.assertNotNull(startup);
        Assertions.assertEquals(FIRST_SLOT, startup.getPoolIndex());
        Assertions.assertEquals(STARTUP_SPRITE, startup.getSprite().getSpriteTypeId());
        Assertions.assertEquals(HIDDEN_FLAG, startup.getSprite().getFlags());
        Assertions.assertEquals(NO_PLAYERS_VISIBILITY, startup.getSprite().getVisibilityFlags());

        SpriteEffectPool effectPool = game.getSpriteSystem().getSpriteEffectPool();
        SpritePool spritePool = game.getSpriteSystem().getSpritePool();
        SpriteImagePool imagePool = game.getSpriteSystem().getImagePool();

        Assertions.assertNotNull(effectPool);
        Assertions.assertNotNull(spritePool);
        Assertions.assertNotNull(imagePool);

        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        MessageDigest invocationDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer vector = ByteBuffer.allocate(VECTOR_CAPACITY).order(ByteOrder.LITTLE_ENDIAN);

        for (int caseIndex = 0; caseIndex < EFFECT_COUNT; caseIndex++) {
            int spriteType = caseIndex == STARTUP_CASE ? STARTUP_SPRITE : caseIndex + DECORATION_SPRITE_BASE;
            SpriteEffect effect = caseIndex == STARTUP_CASE
                                  ? startup
                                  : game.getIscriptExecutor().createSpriteEffect(spriteType,
                                                                                 DECORATION_POSITION_X, DECORATION_POSITION_Y,
                                                                                 DECORATION_OWNER,
                                                                                 DECORATION_INSTRUCTION_LIMIT);

            Assertions.assertNotNull(effect);

            Sprite sprite = effect.getSprite();

            if (caseIndex > STARTUP_CASE) {
                Assertions.assertEquals(STORED_OWNER, sprite.getOwner());
                Assertions.assertEquals(effect.getPoolIndex(), effectPool.getNextIndex(FIRST_SLOT));
                Assertions.assertEquals(DECORATION_ROW, spritePool.getRowIndex(sprite.getPoolIndex()));
            }

            vector.clear();
            vector.putInt(spriteType);
            vector.putInt(imagePool.getFirstFreeIndex());
            vector.putInt(game.getRandom().getState()).putInt(game.getRandom().getTotalRandomCounts());
            vector.putInt(sprite.getImages().size());

            for (SpriteImage image : sprite.getImages()) {
                vector.putInt(image.getIndex()).putInt(image.getImageTypeId()).putInt(image.getModifier());
                vector.putInt(image.getFrameIndexOffset()).putInt(image.getFlags()).putInt(image.getProgramCounter());
                vector.putInt(image.getWait()).putInt(image.getFrameIndex());
            }

            digest.update(vector.array(), 0, vector.position());

            for (int invocation = 0; invocation < SNAPSHOT_COUNT; invocation++) {
                vector.clear();
                vector.putInt(invocation).putInt(spriteType);
                vector.putInt(imagePool.getFirstFreeIndex());
                vector.putInt(game.getRandom().getState()).putInt(game.getRandom().getTotalRandomCounts());
                vector.putInt(sprite.getImages().size());

                for (SpriteImage image : sprite.getImages()) {
                    vector.putInt(image.getIndex()).putInt(image.getImageTypeId()).putInt(image.getModifier());
                    vector.putInt(image.getFrameIndexOffset()).putInt(image.getFlags()).putInt(image.getProgramCounter());
                    vector.putInt(image.getWait()).putInt(image.getFrameIndex());
                }

                invocationDigest.update(vector.array(), 0, vector.position());

                if (invocation < SNAPSHOT_COUNT - 1) {
                    Assertions.assertTrue(game.getIscriptExecutor().executeSprite(sprite, null, DECORATION_INSTRUCTION_LIMIT));
                }
            }
        }

        Assertions.assertEquals(DECORATION_CREATION_HASH, HexFormat.of().formatHex(digest.digest()));
        Assertions.assertEquals(DECORATION_SNAPSHOT_HASH, HexFormat.of().formatHex(invocationDigest.digest()));

        Assertions.assertEquals(EFFECT_COUNT, effectPool.getAllocatedCount());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void sprite_effect_creation_failure_keeps_pool_order_and_startup_rejects_missing_sprite() {
        Game game = Game.create(gameData,
                                new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, TILESET, new int[SMALL_MAP_TILE_COUNT]),
                                new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeSpriteEffectPool(FAILED_POOL_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(FAILED_POOL_CAPACITY);
        game.getSpriteSystem().initializeImagePool(SINGLE_IMAGE_POOL_CAPACITY);

        Assertions.assertNull(game.getIscriptExecutor().createSpriteEffect(STARTUP_SPRITE, FAILED_POSITION_X, 0, 0, FAILED_INSTRUCTION_LIMIT));

        SpriteEffectPool pool = game.getSpriteSystem().getSpriteEffectPool();

        Assertions.assertNotNull(pool);

        Assertions.assertEquals(THIRD_SLOT, pool.getFirstFreeIndex());
        Assertions.assertEquals(FIRST_SLOT, pool.getNextIndex(THIRD_SLOT));
        Assertions.assertEquals(SECOND_SLOT, pool.getNextIndex(FIRST_SLOT));
        Assertions.assertEquals(INITIAL_LIFE, pool.getSpriteEffect(FIRST_SLOT).getLife());
        Assertions.assertNull(pool.getSpriteEffect(FIRST_SLOT).getSpriteOrNull());
        Assertions.assertEquals(0, pool.getAllocatedCount());

        SpriteImagePool observedImagePool = game.getSpriteSystem().getImagePool();

        Assertions.assertNotNull(observedImagePool);

        observedImagePool.allocate();

        Assertions.assertThrows(IllegalStateException.class, () -> game.getIscriptExecutor().initializeSpriteEffects(FAILED_INSTRUCTION_LIMIT));

        Assertions.assertNull(game.getSpriteSystem().getStartupSpriteEffect());
        Assertions.assertEquals(0, pool.getAllocatedCount());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }
}
