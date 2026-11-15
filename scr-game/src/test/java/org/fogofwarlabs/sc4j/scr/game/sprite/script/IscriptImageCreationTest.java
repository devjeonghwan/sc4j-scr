package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPoolScenario;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks image allocation, initialization effects, draw-function modifier data and exhaustion.
 */
@RequiresInstallation
final class IscriptImageCreationTest {
    /**
     * The marine image type, whose Init script creates its shadow.
     */
    private static final int MARINE_IMAGE = 239;
    /**
     * The marine shadow image type.
     */
    private static final int SHADOW_IMAGE = 240;
    /**
     * An image type outside {@code images.dat}.
     */
    private static final int MISSING_IMAGE_TYPE = 65535;
    /**
     * The capacity of an image pool with room for a single image.
     */
    private static final int SINGLE_IMAGE_POOL_CAPACITY = 1;
    /**
     * The capacity of an image pool with room for the marine and its shadow.
     */
    private static final int SHADOW_IMAGE_POOL_CAPACITY = 2;
    /**
     * The capacity of the unit pool.
     */
    private static final int UNIT_POOL_CAPACITY = 1;
    /**
     * The instruction limit of an image creation.
     */
    private static final int INSTRUCTION_LIMIT = 8;
    /**
     * The instruction limit of a marine creation whose shadow's Init also runs.
     */
    private static final int CHILD_INSTRUCTION_LIMIT = 16;
    /**
     * The instruction limit of a single instruction.
     */
    private static final int SINGLE_INSTRUCTION_LIMIT = 1;
    /**
     * The initial horizontal offset byte {@code 255}, a signed {@code -1}.
     */
    private static final int WRAPPED_OFFSET_X = 255;
    /**
     * The signed value of {@link #WRAPPED_OFFSET_X}.
     */
    private static final int SIGNED_OFFSET_X = -1;
    /**
     * The initial vertical offset byte {@code 128}, a signed {@code -128}.
     */
    private static final int WRAPPED_OFFSET_Y = 128;
    /**
     * The signed value of {@link #WRAPPED_OFFSET_Y}.
     */
    private static final int SIGNED_OFFSET_Y = -128;
    /**
     * The post-Init direction, which mirrors to frame offset {@code 15} with a horizontal flip.
     */
    private static final int DIRECTION = 17;
    /**
     * The frame offset of {@link #DIRECTION}.
     */
    private static final int DIRECTION_FRAME_OFFSET = 15;
    /**
     * The shadow draw function.
     */
    private static final int SHADOW_MODIFIER = 10;
    /**
     * The flags of the created shadow: redraw, horizontally flipped and directional frames.
     */
    private static final int SHADOW_FLAGS = 11;
    /**
     * The frame base the marine's Init script sets.
     */
    private static final int MARINE_INIT_FRAME_BASE = 68;
    /**
     * The wait the marine's Init script draws.
     */
    private static final int MARINE_INIT_WAIT = 62;
    /**
     * The random call site of {@code waitrand}.
     */
    private static final int WAIT_RANDOM_SOURCE = 3;
    /**
     * The shadow's program counter after its Init script.
     */
    private static final int SHADOW_INIT_PROGRAM_COUNTER = 31252;
    /**
     * The shadow's program counter when the shared instruction budget runs out.
     */
    private static final int SHADOW_INTERRUPTED_PROGRAM_COUNTER = 31250;
    /**
     * The image count of a marine sprite with its shadow.
     */
    private static final int IMAGES_WITH_SHADOW = 2;
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
     * The byte size of the {@code end} instruction.
     */
    private static final int END_INSTRUCTION_SIZE = 1;
    /**
     * The horizontal offset of the reused image.
     */
    private static final int REUSED_OFFSET_X = 4;
    /**
     * The vertical offset of the reused image.
     */
    private static final int REUSED_OFFSET_Y = 5;
    /**
     * The Death animation.
     */
    private static final int DEATH_ANIMATION = 1;
    /**
     * The image list position of the marine's shadow, after the marine image.
     */
    private static final int SHADOW_POSITION = 1;
    /**
     * The frame offset of the marine's shadow: the post-Init direction applies to the marine image only.
     */
    private static final int SHADOW_FRAME_OFFSET = 0;
    /**
     * The random calls of a marine creation: the {@code waitrand} of the marine's Init script.
     */
    private static final int MARINE_INIT_RANDOM_CALLS = 1;
    /**
     * The width and height of the test map in tiles.
     */
    private static final int MAP_DIMENSION = 1;
    /**
     * The tile count of the test map.
     */
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    /**
     * The tileset of the test map.
     */
    private static final int TILESET = 0;
    /**
     * The seed of the game random generator.
     */
    private static final int RANDOM_SEED = 0;
    /**
     * The sprite type of the owning sprites, which the image creation does not depend on.
     */
    private static final int SPRITE_TYPE = 0;
    /**
     * The {@code images.dat} column block of the Draw Function bytes, after the GRP, turns, clickable, full iscript and draw if cloaked blocks.
     */
    private static final int DRAW_FUNCTION_BLOCK = 5;
    /**
     * The {@code thingy\tCureLrg.grp} image type whose draw function the test rewrites.
     */
    private static final int MODIFIER_IMAGE_TYPE = 969;
    /**
     * The sprite owner, which the player color draw function keeps as its modifier data.
     */
    private static final int OWNER = 3;
    /**
     * The last draw function.
     */
    private static final int LAST_DRAW_FUNCTION = 17;
    /**
     * The draw function that keeps the owner as its modifier data.
     */
    private static final int PLAYER_COLOR_DRAW_FUNCTION = 14;
    /**
     * The draw function of the warp-in flash.
     */
    private static final int WARP_FLASH_DRAW_FUNCTION = 17;
    /**
     * The initial modifier data of the warp-in flash: delay {@code 2}, brightness {@code 0x30}.
     */
    private static final int WARP_FLASH_START_DATA = 0x0230;
    /**
     * The initial modifier data of the other draw functions.
     */
    private static final int CLEARED_MODIFIER_DATA = 0;
    /**
     * The draw function that copies the color remapping of the image type.
     */
    private static final int REMAPPING_DRAW_FUNCTION = 9;
    /**
     * The capacity of the image pool of the draw-function cases.
     */
    private static final int IMAGE_POOL_CAPACITY = 4;
    /**
     * The instruction limit of the created image's Init script in the draw-function cases.
     */
    private static final int MODIFIER_INSTRUCTION_LIMIT = 64;

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
    void create_image_should_require_pool_and_report_exhaustion_before_type_lookup_or_init() throws IOException {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);

        Assertions.assertThrows(IllegalStateException.class,
                                () -> game.getIscriptExecutor().createImage(sprite, null, SHADOW_IMAGE, 0, 0, 0, null, INSTRUCTION_LIMIT));

        game.getSpriteSystem().initializeImagePool(SINGLE_IMAGE_POOL_CAPACITY);

        Assertions.assertThrows(IllegalStateException.class, () -> game.getSpriteSystem().initializeImagePool(SINGLE_IMAGE_POOL_CAPACITY));

        SpriteImage image = game.getIscriptExecutor().createImage(sprite,
                                                                  null,
                                                                  SHADOW_IMAGE,
                                                                  WRAPPED_OFFSET_X, WRAPPED_OFFSET_Y,
                                                                  DIRECTION,
                                                                  null,
                                                                  INSTRUCTION_LIMIT);

        Assertions.assertNotNull(image);
        Assertions.assertSame(image, sprite.getMainImage());
        Assertions.assertEquals(SIGNED_OFFSET_X, image.getOffsetX());
        Assertions.assertEquals(SIGNED_OFFSET_Y, image.getOffsetY());
        Assertions.assertEquals(DIRECTION_FRAME_OFFSET, image.getFrameIndexOffset());
        Assertions.assertEquals(SHADOW_MODIFIER, image.getModifier());
        Assertions.assertEquals(SHADOW_FLAGS, image.getFlags());
        Assertions.assertTrue(image.hasGraphics());

        Assertions.assertNotNull(gameData);

        Assertions.assertSame(gameData.getGrpFile(SHADOW_IMAGE), image.getGraphics());

        long initialHash = game.computeStateHash();

        Assertions.assertNull(game.getIscriptExecutor().createImage(sprite, null, MISSING_IMAGE_TYPE, 0, 0, 0, null, INSTRUCTION_LIMIT));

        Assertions.assertEquals(initialHash, game.computeStateHash());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void create_image_should_run_child_init_before_parent_direction_and_honor_exhaustion() {
        for (int capacity : new int[]{SINGLE_IMAGE_POOL_CAPACITY, SHADOW_IMAGE_POOL_CAPACITY}) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

            game.getSpriteSystem().initializeImagePool(capacity);

            Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
            SpriteImage image = game.getIscriptExecutor().createImage(sprite, null, MARINE_IMAGE, 0, 0, DIRECTION, null, CHILD_INSTRUCTION_LIMIT);

            Assertions.assertNotNull(image);
            Assertions.assertEquals(capacity, sprite.getImages().size());
            Assertions.assertEquals(MARINE_INIT_FRAME_BASE, image.getFrameIndex());
            Assertions.assertEquals(DIRECTION_FRAME_OFFSET, image.getFrameIndexOffset());
            Assertions.assertEquals(MARINE_INIT_WAIT, image.getWait());

            if (capacity == SHADOW_IMAGE_POOL_CAPACITY) {
                SpriteImage shadow = sprite.getImages().get(SHADOW_POSITION);

                Assertions.assertEquals(SHADOW_IMAGE, shadow.getImageTypeId());
                Assertions.assertEquals(SHADOW_FRAME_OFFSET, shadow.getFrameIndexOffset());
                Assertions.assertEquals(SHADOW_INIT_PROGRAM_COUNTER, shadow.getProgramCounter());
            }

            Assertions.assertEquals(MARINE_INIT_RANDOM_CALLS, game.getRandom().getCallCount(WAIT_RANDOM_SOURCE));
        }
    }

    @Test
    void end_opcode_recycles_image_storage_but_keeps_main_pointer_and_next_counter() throws IOException {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeImagePool(SINGLE_IMAGE_POOL_CAPACITY);

        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = game.getIscriptExecutor().createImage(sprite, null, SHADOW_IMAGE, 0, 0, 0, null, INSTRUCTION_LIMIT);

        Assertions.assertNotNull(image);

        Assertions.assertNotNull(gameData);

        int endOffset = gameData.getIscript().getAnimationOffset(END_SCRIPT, DEATH_ANIMATION) + END_DISTANCE;

        Assertions.assertEquals(END_OPCODE, gameData.getIscript().decodeInstruction(endOffset).getOpcode());

        image.setProgramCounter(endOffset);

        Assertions.assertFalse(game.getIscriptExecutor().executeImagePreview(image, sprite, SINGLE_INSTRUCTION_LIMIT));

        Assertions.assertEquals(endOffset + END_INSTRUCTION_SIZE, image.getProgramCounter());
        Assertions.assertFalse(image.hasGraphics());
        Assertions.assertNull(image.getGraphics());
        Assertions.assertTrue(sprite.getImages().isEmpty());
        Assertions.assertSame(image, sprite.getMainImage());

        SpriteImage reused = game.getIscriptExecutor().createImage(sprite,
                                                                   null,
                                                                   SHADOW_IMAGE,
                                                                   REUSED_OFFSET_X, REUSED_OFFSET_Y,
                                                                   0,
                                                                   null,
                                                                   INSTRUCTION_LIMIT);

        Assertions.assertSame(image, reused);
        Assertions.assertTrue(image.hasGraphics());
        Assertions.assertSame(gameData.getGrpFile(SHADOW_IMAGE), image.getGraphics());
        Assertions.assertEquals(SHADOW_INIT_PROGRAM_COUNTER, image.getProgramCounter());
        Assertions.assertEquals(REUSED_OFFSET_X, image.getOffsetX());
    }

    @Test
    void create_image_should_share_instruction_budget_with_child_init_and_accept_unit_overlay() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeImagePool(SHADOW_IMAGE_POOL_CAPACITY);

        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);

        Assertions.assertThrows(IllegalStateException.class,
                                () -> game.getIscriptExecutor().createImage(sprite, null, MARINE_IMAGE, 0, 0, 0, null, SINGLE_INSTRUCTION_LIMIT));

        Assertions.assertEquals(IMAGES_WITH_SHADOW, sprite.getImages().size());
        Assertions.assertEquals(SHADOW_INTERRUPTED_PROGRAM_COUNTER, sprite.getImages().get(SHADOW_POSITION).getProgramCounter());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());

        Game unitGame = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

        unitGame.getSpriteSystem().initializeImagePool(SHADOW_IMAGE_POOL_CAPACITY);

        Sprite unitSprite = SpriteScenario.createSprite(unitGame, SPRITE_TYPE, 0, 0, 0);

        unitGame.getUnitSystem().initializeUnitPool(UNIT_POOL_CAPACITY);

        UnitPool unitPool = unitGame.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(UnitPoolScenario.allocate(unitPool));

        Assertions.assertNotNull(unitGame.getIscriptExecutor().createImage(unitSprite, null, MARINE_IMAGE, 0, 0, 0, unit, INSTRUCTION_LIMIT));

        Assertions.assertEquals(IMAGES_WITH_SHADOW, unitSprite.getImages().size());
        Assertions.assertEquals(MARINE_INIT_RANDOM_CALLS, unitGame.getRandom().getTotalRandomCounts());
    }

    @Test
    void create_image_should_initialize_modifier_data_for_every_draw_function() {
        for (int drawFunction = 0; drawFunction <= LAST_DRAW_FUNCTION; drawFunction++) {
            Assertions.assertNotNull(gameData);

            GameData copy = gameData.createGameCopy();

            copy.getImages().writeBlockByte(DRAW_FUNCTION_BLOCK, MODIFIER_IMAGE_TYPE, drawFunction);

            Assertions.assertEquals(drawFunction, copy.getImages().get("Draw Function", MODIFIER_IMAGE_TYPE));

            Game game = Game.create(copy, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

            game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);

            Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);

            sprite.setOwner(OWNER);

            SpriteImage image = game.getIscriptExecutor().createImage(sprite, null, MODIFIER_IMAGE_TYPE, 0, 0, 0, null, MODIFIER_INSTRUCTION_LIMIT);

            Assertions.assertNotNull(image, "draw function " + drawFunction);
            Assertions.assertEquals(drawFunction, image.getModifier(), "draw function " + drawFunction);

            int expectedData = switch (drawFunction) {
                case PLAYER_COLOR_DRAW_FUNCTION -> OWNER;
                case WARP_FLASH_DRAW_FUNCTION -> WARP_FLASH_START_DATA;
                default -> CLEARED_MODIFIER_DATA;
            };

            Assertions.assertEquals(expectedData, image.getModifierData1(), "modifier data of draw function " + drawFunction);

            if (drawFunction == REMAPPING_DRAW_FUNCTION) {
                Assertions.assertEquals(copy.getImages().get("Color Shift", MODIFIER_IMAGE_TYPE), image.getColorRemappingIndex());
            }
        }
    }
}
