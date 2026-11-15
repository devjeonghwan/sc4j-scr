package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.text.TblFile;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ImageIds;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks resource smoke selection, overlay positions and interrupted initialization.
 */
@RequiresInstallation
final class IscriptGasOverlayTest {
    /**
     * The script offset of the Vespene Geyser's {@code creategasoverlays} instruction.
     */
    private static final int CREATE_GAS_OVERLAYS_OFFSET = 8370;
    /**
     * The opcode of {@code creategasoverlays}.
     */
    private static final int CREATE_GAS_OVERLAYS_OPCODE = 0x38;
    /**
     * The byte offset of the frame offset DWORDs in a special overlay file.
     */
    private static final int OVERLAY_FRAME_OFFSETS_OFFSET = 8;
    /**
     * The bytes of one overlay position: the horizontal offset byte followed by the vertical offset byte.
     */
    private static final int OVERLAY_POSITION_SIZE = 2;
    /**
     * The resources of a geyser that is not depleted.
     */
    private static final int REMAINING_RESOURCES = 5000;
    /**
     * The capacity of an image pool with room for the smoke.
     */
    private static final int IMAGE_POOL_CAPACITY = 4;
    /**
     * The capacity of an image pool that the parent image exhausts.
     */
    private static final int EXHAUSTED_IMAGE_POOL_CAPACITY = 1;
    /**
     * The horizontal offset of the geyser image.
     */
    private static final int PARENT_OFFSET_X = 120;
    /**
     * The vertical offset of the geyser image.
     */
    private static final int PARENT_OFFSET_Y = 127;
    /**
     * The instruction limit of the interrupted sprite execution.
     */
    private static final int INSTRUCTION_LIMIT = 1;
    /**
     * The image count of the geyser sprite with its smoke.
     */
    private static final int IMAGES_WITH_SMOKE = 2;
    /**
     * The image type of the first smoke of a geyser that has resources.
     */
    private static final int GEYSER_SMOKE_IMAGE_TYPE = 430;
    /**
     * The image type of the first smoke of a depleted geyser.
     */
    private static final int DEPLETED_GEYSER_SMOKE_IMAGE_TYPE = 435;
    /**
     * The resources of a depleted geyser.
     */
    private static final int DEPLETED_RESOURCES = 0;
    /**
     * The Init animation.
     */
    private static final int INIT_ANIMATION = 0;
    /**
     * The image list position of the geyser image below its smoke.
     */
    private static final int PARENT_IMAGE_POSITION = 1;
    /**
     * The image count of the geyser sprite without smoke.
     */
    private static final int PARENT_ONLY_IMAGE_COUNT = 1;
    /**
     * The image flag of a horizontally flipped frame.
     */
    private static final int HORIZONTALLY_FLIPPED_FLAG = 0x02;
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
     * The sprite type of the geyser sprite, which the smoke does not depend on.
     */
    private static final int SPRITE_TYPE = 0;
    /**
     * The capacity of the unit pool of the resource unit.
     */
    private static final int UNIT_POOL_CAPACITY = 1;
    /**
     * The pool index of the resource unit.
     */
    private static final int UNIT_POOL_INDEX = 1;
    /**
     * The installed image name table that names the special overlay files.
     */
    private static final @NotNull String IMAGE_NAMES_FILE = "arr/images.tbl";
    /**
     * The installed folder of the special overlay files.
     */
    private static final @NotNull String OVERLAY_FOLDER = "unit/";

    private static @Nullable GameData gameData;

    private static byte @Nullable [] specialOverlay;
    private static int overlayIndex;

    @BeforeAll
    static void loadInstalledData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());

        TblFile names = TblFile.parse(gameData.readInstalledFile(IMAGE_NAMES_FILE));
        int resourceIndex = gameData.getImages().get("Special Overlay", ImageIds.VESPENE_GEYSER);

        specialOverlay = gameData.readInstalledFile(OVERLAY_FOLDER + names.getString(resourceIndex - 1).replace('\\', '/'));

        Assertions.assertEquals(CREATE_GAS_OVERLAYS_OPCODE, gameData.getIscript().decodeInstruction(CREATE_GAS_OVERLAYS_OFFSET).getOpcode());

        overlayIndex = gameData.getIscript().decodeInstruction(CREATE_GAS_OVERLAYS_OFFSET).getOperands()[0];
    }

    @AfterAll
    static void closeInstalledData() {
        if (gameData != null) {
            gameData.close();
        }
    }


    @Test
    void execute_sprite_should_select_resource_smoke_and_attach_it_above_before_child_init() throws IOException {
        Assertions.assertNotNull(specialOverlay);

        ByteBuffer contents = ByteBuffer.wrap(specialOverlay).order(ByteOrder.LITTLE_ENDIAN);
        int coordinateOffset = contents.getInt(OVERLAY_FRAME_OFFSETS_OFFSET) + overlayIndex * OVERLAY_POSITION_SIZE;

        for (int resources : new int[]{DEPLETED_RESOURCES, REMAINING_RESOURCES}) {
            for (boolean flipped : new boolean[]{false, true}) {
                Game game = Game.create(gameData,
                                        new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]),
                                        new LcgRandom(RANDOM_SEED));

                game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);

                Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
                SpriteImage parent = SpriteScenario.createImage(game, ImageIds.VESPENE_GEYSER, PARENT_OFFSET_X, PARENT_OFFSET_Y);

                sprite.insertHeadImage(parent);
                sprite.setMainImage(parent);
                parent.setProgramCounter(CREATE_GAS_OVERLAYS_OFFSET);
                parent.setFlags(flipped ? HORIZONTALLY_FLIPPED_FLAG : 0);

                Unit resource = new UnitPool(UNIT_POOL_CAPACITY).getUnit(UNIT_POOL_INDEX);

                resource.setUnitId(ScrUnitIds.RESOURCE_VESPENE_GEYSER);
                resource.setResources(resources);

                SpriteImagePool observedImagePool = game.getSpriteSystem().getImagePool();

                Assertions.assertNotNull(observedImagePool);

                int nextFreeIndex = observedImagePool.getFirstFreeIndex();
                IllegalStateException failure = Assertions.assertThrows(
                        IllegalStateException.class,
                        () -> game.getIscriptExecutor().executeSprite(sprite, resource, INSTRUCTION_LIMIT)
                );

                Assertions.assertTrue(failure.getMessage().contains("instruction limit"));
                Assertions.assertEquals(IMAGES_WITH_SMOKE, sprite.getImages().size());

                SpriteImage smoke = sprite.getImages().getFirst();

                Assertions.assertEquals(nextFreeIndex, smoke.getIndex());
                Assertions.assertSame(parent, sprite.getImages().get(PARENT_IMAGE_POSITION));
                Assertions.assertSame(parent, sprite.getMainImage());

                int imageType = (resources == DEPLETED_RESOURCES ? DEPLETED_GEYSER_SMOKE_IMAGE_TYPE : GEYSER_SMOKE_IMAGE_TYPE) + overlayIndex;

                Assertions.assertEquals(imageType, smoke.getImageTypeId());
                Assertions.assertEquals((byte) (PARENT_OFFSET_X + (flipped ? -specialOverlay[coordinateOffset] : specialOverlay[coordinateOffset])),
                                        smoke.getOffsetX());
                Assertions.assertEquals((byte) (PARENT_OFFSET_Y + specialOverlay[coordinateOffset + 1]), smoke.getOffsetY());

                Assertions.assertNotNull(gameData);

                Assertions.assertEquals(gameData.getIscript().getAnimationOffset(gameData.getImages().get("Iscript", imageType), INIT_ANIMATION),
                                        smoke.getProgramCounter());
                Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
            }
        }
    }

    @Test
    void execute_sprite_should_not_allocate_smoke_for_non_resource_unit_or_exhausted_pool() {
        for (int unitType : new int[]{ScrUnitIds.TERRAN_MARINE, ScrUnitIds.RESOURCE_VESPENE_GEYSER}) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

            game.getSpriteSystem().initializeImagePool(unitType == ScrUnitIds.RESOURCE_VESPENE_GEYSER ? EXHAUSTED_IMAGE_POOL_CAPACITY
                                                                                                      : IMAGE_POOL_CAPACITY);

            Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
            SpriteImage parent = SpriteScenario.createImage(game, ImageIds.VESPENE_GEYSER, 0, 0);

            sprite.insertHeadImage(parent);
            sprite.setMainImage(parent);
            parent.setProgramCounter(CREATE_GAS_OVERLAYS_OFFSET);

            Unit unit = new UnitPool(UNIT_POOL_CAPACITY).getUnit(UNIT_POOL_INDEX);

            unit.setUnitId(unitType);
            unit.setResources(REMAINING_RESOURCES);

            IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
                                                                    () -> game.getIscriptExecutor().executeSprite(sprite, unit, INSTRUCTION_LIMIT));

            Assertions.assertTrue(failure.getMessage().contains("instruction limit"));
            Assertions.assertEquals(PARENT_ONLY_IMAGE_COUNT, sprite.getImages().size());
            Assertions.assertSame(parent, sprite.getImages().getFirst());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }
    }
}
