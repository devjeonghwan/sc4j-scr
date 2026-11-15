package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks worker carrying overlays, the direction they are created with, the instruction limit check and image removal.
 */
@RequiresInstallation
final class IscriptCarryingImageTest {
    /**
     * The width and height of the test map in tiles.
     */
    private static final int MAP_DIMENSION = 8;
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
    private static final int RANDOM_SEED = 1;
    /**
     * The capacity of the image pool.
     */
    private static final int IMAGE_POOL_CAPACITY = 16;
    /**
     * The capacity of the worker's unit pool.
     */
    private static final int UNIT_POOL_CAPACITY = 1;
    /**
     * The pool index of the worker.
     */
    private static final int UNIT_POOL_INDEX = 1;
    /**
     * The horizontal position of the worker sprite.
     */
    private static final int SPRITE_POSITION_X = 100;
    /**
     * The vertical position of the worker sprite.
     */
    private static final int SPRITE_POSITION_Y = 100;
    /**
     * The instruction limit of the carried image's creation scripts.
     */
    private static final int INSTRUCTION_LIMIT = 10000;
    /**
     * An instruction limit that is not positive.
     */
    private static final int NON_POSITIVE_INSTRUCTION_LIMIT = 0;
    /**
     * The owner of the carried powerup.
     */
    private static final int POWERUP_OWNER = 0;
    /**
     * The frame bases of the worker's main image whose special overlay offsets the carried image follows.
     */
    private static final int @NotNull [] MAIN_FRAME_BASES = {0, 1, 8, 16};
    /**
     * The heading byte that the worker sprite's direction update applies.
     */
    private static final int HEADING = 0;
    /**
     * The special overlay coordinate pair that the carried image follows.
     */
    private static final int OVERLAY_INDEX = 0;
    /**
     * The image type of the carried mineral chunk.
     */
    private static final int MINERAL_CHUNK_IMAGE = 397;
    /**
     * The image flag of a horizontally flipped frame.
     */
    private static final int HORIZONTALLY_FLIPPED_FLAG = 0x02;
    /**
     * The image flag of an image that follows the special overlay offsets of its main image.
     */
    private static final int USES_SPECIAL_OFFSET_FLAG = 0x80;
    /**
     * The image count and the allocated image count after the carried image is removed: the main image.
     */
    private static final int REMAINING_IMAGE_COUNT = 1;
    /**
     * The flipped frame direction of the worker's main image before the carried image is created.
     */
    private static final int MAIN_FRAME_DIRECTION = 7;
    /**
     * The heading steps of one frame direction.
     */
    private static final int HEADING_PER_FRAME_DIRECTION = 8;
    /**
     * The frame direction of the worker's next direction change.
     */
    private static final int TURNED_DIRECTION = 7;
    /**
     * The frame direction that a carried image is created with.
     */
    private static final int CREATED_DIRECTION = 0;

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
    void mineral_carry_image_uses_installed_scripts_and_follows_special_offsets_until_removed() throws IOException {
        for (int workerType : new int[]{ScrUnitIds.TERRAN_SCV, ScrUnitIds.ZERG_DRONE, ScrUnitIds.PROTOSS_PROBE}) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

            game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);

            Assertions.assertNotNull(gameData);

            int flingyType = gameData.getUnits().get("Graphics", workerType);
            int spriteType = gameData.getFlingy().get("Sprite", flingyType);
            int imageType = gameData.getSprites().get("Image", spriteType);
            Sprite sprite = SpriteScenario.createSprite(game, spriteType, SPRITE_POSITION_X, SPRITE_POSITION_Y, 0);
            SpriteImage main = SpriteScenario.createImage(game, imageType, 0, 0);

            sprite.insertHeadImage(main);
            sprite.setMainImage(main);

            Unit worker = new UnitPool(UNIT_POOL_CAPACITY).getUnit(UNIT_POOL_INDEX);

            worker.setUnitId(workerType);
            worker.setSprite(sprite);

            game.getIscriptExecutor().addMineralCarryImage(worker, false, INSTRUCTION_LIMIT);

            SpriteImage carrying = null;

            for (SpriteImage image : sprite.getImages()) {
                if (image.getImageTypeId() == MINERAL_CHUNK_IMAGE) {
                    carrying = image;
                }
            }

            Assertions.assertNotNull(carrying);
            Assertions.assertTrue((carrying.getFlags() & USES_SPECIAL_OFFSET_FLAG) != 0);
            Assertions.assertTrue(sprite.getImages().indexOf(carrying) < sprite.getImages().indexOf(main));

            for (int frame : MAIN_FRAME_BASES) {
                main.setFrameIndex(frame);

                for (boolean flipped : new boolean[]{false, true}) {
                    main.setFlags(flipped ? HORIZONTALLY_FLIPPED_FLAG : 0);

                    IscriptExecutor.applySpriteDirection(game, sprite, HEADING);

                    int coordinate = gameData.getImageSpecialOverlayOffset(imageType, frame, OVERLAY_INDEX);

                    Assertions.assertEquals((byte) (flipped ? -(short) coordinate : (short) coordinate), carrying.getOffsetX());
                    Assertions.assertEquals((byte) (coordinate >>> Short.SIZE), carrying.getOffsetY());
                }
            }

            game.getIscriptExecutor().removeCarryImages(worker);

            Assertions.assertEquals(REMAINING_IMAGE_COUNT, sprite.getImages().size());
            Assertions.assertSame(main, sprite.getImages().getFirst());

            SpriteImagePool observedImagePool = game.getSpriteSystem().getImagePool();

            Assertions.assertNotNull(observedImagePool);

            Assertions.assertEquals(REMAINING_IMAGE_COUNT, observedImagePool.getAllocatedCount());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void add_mineral_carry_image_should_create_carried_image_facing_direction_zero() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

            game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);

            int workerType = ScrUnitIds.PROTOSS_PROBE;
            int flingyType = gameData.getUnits().get("Graphics", workerType);
            int spriteType = gameData.getFlingy().get("Sprite", flingyType);
            int imageType = gameData.getSprites().get("Image", spriteType);
            Sprite sprite = SpriteScenario.createSprite(game, spriteType, SPRITE_POSITION_X, SPRITE_POSITION_Y, 0);
            SpriteImage main = SpriteScenario.createImage(game, imageType, 0, 0);

            sprite.insertHeadImage(main);
            sprite.setMainImage(main);
            main.setFrameIndexOffset(MAIN_FRAME_DIRECTION);
            main.setFlags(main.getFlags() | HORIZONTALLY_FLIPPED_FLAG);

            Unit worker = new UnitPool(UNIT_POOL_CAPACITY).getUnit(UNIT_POOL_INDEX);

            worker.setUnitId(workerType);
            worker.setSprite(sprite);

            game.getIscriptExecutor().addMineralCarryImage(worker, false, INSTRUCTION_LIMIT);

            SpriteImage carrying = null;

            for (SpriteImage image : sprite.getImages()) {
                if (image.getImageTypeId() == MINERAL_CHUNK_IMAGE) {
                    carrying = image;
                }
            }

            Assertions.assertNotNull(carrying);
            Assertions.assertEquals(CREATED_DIRECTION, carrying.getFrameIndexOffset(), "The carried image is created facing direction 0.");
            Assertions.assertEquals(0, carrying.getFlags() & HORIZONTALLY_FLIPPED_FLAG);

            IscriptExecutor.applySpriteDirection(game, sprite, TURNED_DIRECTION * HEADING_PER_FRAME_DIRECTION);

            Assertions.assertEquals(TURNED_DIRECTION, carrying.getFrameIndexOffset(), "The worker's next direction change turns the carried image.");
        }
    }

    @Test
    void add_powerup_carry_image_should_reject_a_non_positive_instruction_limit() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        IscriptExecutor executor = game.getIscriptExecutor();
        Unit worker = new UnitPool(UNIT_POOL_CAPACITY).getUnit(UNIT_POOL_INDEX);

        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> executor.addPowerupCarryImage(worker, MINERAL_CHUNK_IMAGE, POWERUP_OWNER, NON_POSITIVE_INSTRUCTION_LIMIT));
    }
}
