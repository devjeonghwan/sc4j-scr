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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks image insertion, script initialization and image removal.
 */
@RequiresInstallation
final class IscriptOverlayTest {
    /**
     * The capacity of the image pool.
     */
    private static final int IMAGE_POOL_CAPACITY = 16;
    /**
     * The marine image type.
     */
    private static final int MARINE_IMAGE = 239;
    /**
     * The marine shadow image type.
     */
    private static final int SHADOW_IMAGE = 240;
    /**
     * The iscript of the marine.
     */
    private static final int MARINE_SCRIPT = 78;
    /**
     * The iscript of the marine shadow.
     */
    private static final int SHADOW_SCRIPT = 275;
    /**
     * The instruction limit of a marine traversal.
     */
    private static final int INSTRUCTION_LIMIT = 32;
    /**
     * The instruction limit of a single instruction.
     */
    private static final int SINGLE_INSTRUCTION_LIMIT = 1;
    /**
     * The shadow draw function.
     */
    private static final int SHADOW_MODIFIER = 10;
    /**
     * The image flag that requests a redraw.
     */
    private static final int REDRAW_FLAG = 0x01;
    /**
     * The image flag of a horizontally flipped frame.
     */
    private static final int HORIZONTALLY_FLIPPED_FLAG = 0x02;
    /**
     * The image flag of an image with directional frames.
     */
    private static final int DIRECTIONAL_FRAMES_FLAG = 0x08;
    /**
     * The image flag of an image that runs every animation of its script.
     */
    private static final int USES_FULL_ISCRIPT_FLAG = 0x10;
    /**
     * The program counter advance of the shadow's Init script.
     */
    private static final int SHADOW_INIT_ADVANCE = 2;
    /**
     * The frame index the marine's Init script sets.
     */
    private static final int MARINE_INIT_FRAME = 68;
    /**
     * The wait the marine's Init script draws.
     */
    private static final int MARINE_INIT_WAIT = 62;
    /**
     * The marine's wait after the next traversal.
     */
    private static final int MARINE_NEXT_WAIT = 61;
    /**
     * The random call site of {@code waitrand}.
     */
    private static final int WAIT_RANDOM_SOURCE = 3;
    /**
     * The image count of a marine sprite with its shadow.
     */
    private static final int IMAGES_WITH_SHADOW = 2;
    /**
     * The image type of the executing image.
     */
    private static final int EXECUTING_IMAGE = 17;
    /**
     * The iscript whose Init script attaches an underlay.
     */
    private static final int UNDERLAY_SCRIPT = 11;
    /**
     * The underlay image type that the Init script of {@link #UNDERLAY_SCRIPT} attaches.
     */
    private static final int UNDERLAY_IMAGE = 18;
    /**
     * The horizontal offset of the executing image.
     */
    private static final int EXECUTING_OFFSET_X = 120;
    /**
     * The vertical offset of the executing image.
     */
    private static final int EXECUTING_OFFSET_Y = 127;
    /**
     * The frame offset of the executing image.
     */
    private static final int EXECUTING_FRAME_OFFSET = 7;
    /**
     * The instruction limit of the underlay execution.
     */
    private static final int UNDERLAY_INSTRUCTION_LIMIT = 2;
    /**
     * The image count of the sprite with the attached underlay.
     */
    private static final int IMAGES_WITH_UNDERLAY = 4;
    /**
     * The horizontal offset of the underlay.
     */
    private static final int UNDERLAY_OFFSET_X = 120;
    /**
     * The vertical offset of the underlay, wrapped to a signed byte.
     */
    private static final int UNDERLAY_OFFSET_Y = -122;
    /**
     * The shadow's program counter when the shared instruction budget runs out.
     */
    private static final int SHADOW_INTERRUPTED_PROGRAM_COUNTER = 31250;
    /**
     * The shadow's program counter after its Init script.
     */
    private static final int SHADOW_INIT_PROGRAM_COUNTER = 31252;
    /**
     * The frame base of the main image.
     */
    private static final int MAIN_FRAME_BASE = 5;
    /**
     * The frame offset of the main image.
     */
    private static final int MAIN_FRAME_OFFSET = 3;
    /**
     * The frame base of the shadow before it follows the main image.
     */
    private static final int SHADOW_FRAME_BASE = 7;
    /**
     * The frame offset of the shadow before it follows the main image.
     */
    private static final int SHADOW_FRAME_OFFSET = 1;
    /**
     * The instruction limit of a shadow traversal that follows the main image.
     */
    private static final int FOLLOW_INSTRUCTION_LIMIT = 3;
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
     * The wait of the image after the ending image.
     */
    private static final int WAITING_IMAGE_WAIT = 1;
    /**
     * The instruction limit of the traversal that removes the ending image.
     */
    private static final int ENDING_INSTRUCTION_LIMIT = 2;
    /**
     * The Init animation.
     */
    private static final int INIT_ANIMATION = 0;
    /**
     * The Death animation.
     */
    private static final int DEATH_ANIMATION = 1;
    /**
     * The random calls of the marine's Init script: its {@code waitrand}.
     */
    private static final int MARINE_INIT_RANDOM_CALLS = 1;
    /**
     * The image list position of the marine's shadow, after the marine image.
     */
    private static final int SHADOW_POSITION = 1;
    /**
     * The image list position of the main image of the underlay test.
     */
    private static final int MAIN_POSITION = 0;
    /**
     * The image list position of the executing image of the underlay test.
     */
    private static final int EXECUTING_POSITION = 1;
    /**
     * The image list position of the attached underlay, after the executing image.
     */
    private static final int UNDERLAY_POSITION = 2;
    /**
     * The image list position of the trailing image of the underlay test.
     */
    private static final int TRAILING_POSITION = 3;
    /**
     * The image count after the ending image is removed: the waiting image.
     */
    private static final int REMAINING_IMAGE_COUNT = 1;
    /**
     * The image type of the ending and waiting images, which the removal does not depend on.
     */
    private static final int PLAIN_IMAGE_TYPE = 0;
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
     * The sprite type of the owning sprites, which the image scripts do not depend on.
     */
    private static final int SPRITE_TYPE = 0;

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

    private static @NotNull Game createGame() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);

        return game;
    }


    @Test
    void marine_shadow_runs_its_initial_wait_then_follows_main_on_next_traversal() throws IOException {
        Game game = createGame();
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage marine = SpriteScenario.createImage(game, MARINE_IMAGE, 0, 0);

        marine.setFlags(USES_FULL_ISCRIPT_FLAG | DIRECTIONAL_FRAMES_FLAG);
        sprite.setMainImage(marine);
        sprite.insertTailImage(marine);

        Assertions.assertTrue(game.getIscriptExecutor().runImagePreviewAnimation(marine, sprite, MARINE_SCRIPT, INIT_ANIMATION, INSTRUCTION_LIMIT));

        Assertions.assertEquals(IMAGES_WITH_SHADOW, sprite.getImages().size());
        Assertions.assertSame(marine, sprite.getImages().getFirst());

        SpriteImage shadow = sprite.getImages().get(SHADOW_POSITION);

        Assertions.assertEquals(SHADOW_IMAGE, shadow.getImageTypeId());
        Assertions.assertEquals(SHADOW_MODIFIER, shadow.getModifier());
        Assertions.assertEquals(REDRAW_FLAG | DIRECTIONAL_FRAMES_FLAG, shadow.getFlags());
        Assertions.assertEquals(0, shadow.getDrawnFrameIndex());
        Assertions.assertEquals(0, shadow.getWait());

        Assertions.assertNotNull(gameData);

        Assertions.assertEquals(gameData.getIscript().getAnimationOffset(SHADOW_SCRIPT, INIT_ANIMATION) + SHADOW_INIT_ADVANCE,
                                shadow.getProgramCounter());
        Assertions.assertEquals(MARINE_INIT_FRAME, marine.getDrawnFrameIndex());
        Assertions.assertEquals(MARINE_INIT_WAIT, marine.getWait());
        Assertions.assertEquals(MARINE_INIT_RANDOM_CALLS, game.getRandom().getCallCount(WAIT_RANDOM_SOURCE));

        Assertions.assertTrue(game.getIscriptExecutor().executeSpritePreview(sprite, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MARINE_NEXT_WAIT, marine.getWait());
        Assertions.assertEquals(MARINE_INIT_FRAME, shadow.getDrawnFrameIndex());
        Assertions.assertEquals(0, shadow.getWait());
        Assertions.assertEquals(MARINE_INIT_RANDOM_CALLS, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_image_preview_should_attach_underlay_after_executing_image_and_wrap_offsets() throws IOException {
        Game game = createGame();
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage mainImage = SpriteScenario.createImage(game, MARINE_IMAGE, 0, 0);
        SpriteImage executingImage = SpriteScenario.createImage(game, EXECUTING_IMAGE, EXECUTING_OFFSET_X, EXECUTING_OFFSET_Y);
        SpriteImage trailingImage = SpriteScenario.createImage(game, MARINE_IMAGE, 0, 0);

        sprite.setMainImage(mainImage);
        sprite.insertTailImage(mainImage);
        sprite.insertTailImage(executingImage);
        sprite.insertTailImage(trailingImage);

        Assertions.assertNotNull(gameData);

        executingImage.setProgramCounter(gameData.getIscript().getAnimationOffset(UNDERLAY_SCRIPT, INIT_ANIMATION));
        executingImage.setFrameIndexOffset(EXECUTING_FRAME_OFFSET);
        executingImage.setFlags(HORIZONTALLY_FLIPPED_FLAG);

        Assertions.assertThrows(IllegalStateException.class,
                                () -> game.getIscriptExecutor().executeImagePreview(executingImage, sprite, UNDERLAY_INSTRUCTION_LIMIT));

        Assertions.assertEquals(IMAGES_WITH_UNDERLAY, sprite.getImages().size());
        Assertions.assertSame(mainImage, sprite.getImages().get(MAIN_POSITION));
        Assertions.assertSame(executingImage, sprite.getImages().get(EXECUTING_POSITION));

        SpriteImage underlay = sprite.getImages().get(UNDERLAY_POSITION);

        Assertions.assertSame(trailingImage, sprite.getImages().get(TRAILING_POSITION));
        Assertions.assertEquals(UNDERLAY_IMAGE, underlay.getImageTypeId());
        Assertions.assertEquals(UNDERLAY_OFFSET_X, underlay.getOffsetX());
        Assertions.assertEquals(UNDERLAY_OFFSET_Y, underlay.getOffsetY());
        Assertions.assertEquals(EXECUTING_FRAME_OFFSET, underlay.getFrameIndexOffset());
        Assertions.assertTrue((underlay.getFlags() & HORIZONTALLY_FLIPPED_FLAG) != 0);
    }

    @Test
    void run_image_preview_animation_should_share_limit_with_child_init_and_report_child_boundary() {
        Game game = createGame();
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage marine = SpriteScenario.createImage(game, MARINE_IMAGE, 0, 0);

        sprite.setMainImage(marine);
        sprite.insertTailImage(marine);

        IllegalStateException exception = Assertions.assertThrows(
                IllegalStateException.class,
                () -> game.getIscriptExecutor().runImagePreviewAnimation(marine, sprite, MARINE_SCRIPT, INIT_ANIMATION, SINGLE_INSTRUCTION_LIMIT)
        );

        Assertions.assertEquals(IMAGES_WITH_SHADOW, sprite.getImages().size());

        SpriteImage shadow = sprite.getImages().get(SHADOW_POSITION);

        Assertions.assertEquals(SHADOW_INTERRUPTED_PROGRAM_COUNTER, shadow.getProgramCounter());
        Assertions.assertTrue(exception.getMessage().contains("image 240"));
        Assertions.assertTrue(exception.getMessage().contains("offset 31250"));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_sprite_preview_should_skip_images_inserted_during_the_current_traversal() throws IOException {
        Game game = createGame();
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage marine = SpriteScenario.createImage(game, MARINE_IMAGE, 0, 0);

        Assertions.assertNotNull(gameData);

        marine.setProgramCounter(gameData.getIscript().getAnimationOffset(MARINE_SCRIPT, INIT_ANIMATION));
        sprite.setMainImage(marine);
        sprite.insertTailImage(marine);

        Assertions.assertTrue(game.getIscriptExecutor().executeSpritePreview(sprite, INSTRUCTION_LIMIT));

        Assertions.assertEquals(IMAGES_WITH_SHADOW, sprite.getImages().size());
        Assertions.assertEquals(MARINE_INIT_FRAME, marine.getDrawnFrameIndex());
        Assertions.assertEquals(0, sprite.getImages().get(SHADOW_POSITION).getDrawnFrameIndex());

        Assertions.assertTrue(game.getIscriptExecutor().executeSpritePreview(sprite, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MARINE_INIT_FRAME, sprite.getImages().get(SHADOW_POSITION).getDrawnFrameIndex());
    }

    @Test
    void execute_image_preview_should_keep_equal_frame_decomposition_and_copy_changes_on_follow() {
        Game game = createGame();
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage mainImage = SpriteScenario.createImage(game, MARINE_IMAGE, 0, 0);
        SpriteImage shadow = SpriteScenario.createImage(game, SHADOW_IMAGE, 0, 0);

        mainImage.setFrameIndex(MAIN_FRAME_BASE);
        mainImage.setFrameIndexOffset(MAIN_FRAME_OFFSET);
        shadow.setFrameIndex(SHADOW_FRAME_BASE);
        shadow.setFrameIndexOffset(SHADOW_FRAME_OFFSET);
        shadow.setProgramCounter(SHADOW_INIT_PROGRAM_COUNTER);
        sprite.setMainImage(mainImage);
        sprite.insertTailImage(mainImage);
        sprite.insertTailImage(shadow);

        Assertions.assertTrue(game.getIscriptExecutor().executeImagePreview(shadow, sprite, FOLLOW_INSTRUCTION_LIMIT));

        Assertions.assertEquals(SHADOW_FRAME_BASE, shadow.getFrameIndex());
        Assertions.assertEquals(SHADOW_FRAME_OFFSET, shadow.getFrameIndexOffset());
        Assertions.assertEquals(0, shadow.getFlags());

        mainImage.setFlags(HORIZONTALLY_FLIPPED_FLAG);

        Assertions.assertTrue(game.getIscriptExecutor().executeImagePreview(shadow, sprite, FOLLOW_INSTRUCTION_LIMIT));

        Assertions.assertEquals(MAIN_FRAME_BASE, shadow.getFrameIndex());
        Assertions.assertEquals(MAIN_FRAME_OFFSET, shadow.getFrameIndexOffset());
        Assertions.assertEquals(REDRAW_FLAG | HORIZONTALLY_FLIPPED_FLAG, shadow.getFlags());
    }

    @Test
    void execute_sprite_preview_should_remove_ended_images_without_skipping_their_successor() throws IOException {
        Game game = createGame();
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage endingImage = SpriteScenario.createImage(game, PLAIN_IMAGE_TYPE, 0, 0);
        SpriteImage waitingImage = SpriteScenario.createImage(game, PLAIN_IMAGE_TYPE, 0, 0);

        Assertions.assertNotNull(gameData);

        int deathOffset = gameData.getIscript().getAnimationOffset(END_SCRIPT, DEATH_ANIMATION);

        Assertions.assertEquals(END_OPCODE, gameData.getIscript().decodeInstruction(deathOffset + END_DISTANCE).getOpcode());

        endingImage.setProgramCounter(deathOffset + END_DISTANCE);
        waitingImage.setWait(WAITING_IMAGE_WAIT);
        sprite.insertTailImage(endingImage);
        sprite.insertTailImage(waitingImage);
        sprite.setMainImage(endingImage);

        Assertions.assertTrue(game.getIscriptExecutor().executeSpritePreview(sprite, ENDING_INSTRUCTION_LIMIT));

        Assertions.assertEquals(REMAINING_IMAGE_COUNT, sprite.getImages().size());
        Assertions.assertSame(waitingImage, sprite.getImages().getFirst());
        Assertions.assertEquals(0, waitingImage.getWait());
        Assertions.assertSame(endingImage, sprite.getMainImage());
        Assertions.assertNull(endingImage.getSprite());
    }
}
