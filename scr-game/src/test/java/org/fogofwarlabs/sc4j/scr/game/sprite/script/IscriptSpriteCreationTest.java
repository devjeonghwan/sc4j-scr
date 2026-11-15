package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Checks sprite registration, construction images and completion animations.
 */
@RequiresInstallation
final class IscriptSpriteCreationTest {
    /**
     * The width and height of the test map in tiles.
     */
    private static final int MAP_DIMENSION = 64;
    /**
     * The tile count of the test map.
     */
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    /**
     * The tileset count.
     */
    private static final int TILESET_COUNT = 8;
    /**
     * The tileset of the test maps outside the geyser test.
     */
    private static final int TILESET = 0;
    /**
     * The width and height of the map of the failed creation and ending tests in tiles.
     */
    private static final int SMALL_MAP_DIMENSION = 1;
    /**
     * The tile count of the map of the failed creation and ending tests.
     */
    private static final int SMALL_MAP_TILE_COUNT = SMALL_MAP_DIMENSION * SMALL_MAP_DIMENSION;
    /**
     * The seed of the game random generator.
     */
    private static final int RANDOM_SEED = 0;
    /**
     * The capacity of the sprite pool of the fog copy test.
     */
    private static final int FOG_SPRITE_POOL_CAPACITY = 3;
    /**
     * The capacity of the image pool of the fog copy test.
     */
    private static final int FOG_IMAGE_POOL_CAPACITY = 16;
    /**
     * The capacity of the sprite pool of the marine test.
     */
    private static final int MARINE_SPRITE_POOL_CAPACITY = 2;
    /**
     * The capacity of the image pool of the marine test.
     */
    private static final int MARINE_IMAGE_POOL_CAPACITY = 4;
    /**
     * The capacity of the image pool of the construction tests.
     */
    private static final int CONSTRUCTION_IMAGE_POOL_CAPACITY = 8;
    /**
     * The capacity of a sprite pool with room for a single sprite.
     */
    private static final int SINGLE_SPRITE_POOL_CAPACITY = 1;
    /**
     * The capacity of the unit pool of the marine test.
     */
    private static final int UNIT_POOL_CAPACITY = 1;
    /**
     * The pool index of the unit context.
     */
    private static final int UNIT_POOL_INDEX = 1;
    /**
     * The capacity of the sprite pool of the failed creation test.
     */
    private static final int FAILED_SPRITE_POOL_CAPACITY = 3;
    /**
     * The capacity of an image pool with room for a single image.
     */
    private static final int SINGLE_IMAGE_POOL_CAPACITY = 1;
    /**
     * The capacity of an image pool with room for the marine and its shadow.
     */
    private static final int SHADOW_IMAGE_POOL_CAPACITY = 2;
    /**
     * The instruction limit of every iscript call of the fog copy, construction image, geyser and Built animation tests.
     */
    private static final int INSTRUCTION_LIMIT = 512;
    /**
     * The instruction limit of the marine creation and execution.
     */
    private static final int MARINE_INSTRUCTION_LIMIT = 32;
    /**
     * The instruction limit of the failed sprite creations.
     */
    private static final int FAILED_INSTRUCTION_LIMIT = 8;
    /**
     * The instruction limit of the marine creation and the execution that reaches {@code end}.
     */
    private static final int END_INSTRUCTION_LIMIT = 16;
    /**
     * The horizontal position of the mineral field.
     */
    private static final int MINERAL_POSITION_X = 320;
    /**
     * The vertical position of the mineral field.
     */
    private static final int MINERAL_POSITION_Y = 640;
    /**
     * The frame base of the mineral field's main image.
     */
    private static final int MINERAL_FRAME_BASE = 2;
    /**
     * The visibility flags of a sprite visible to every player.
     */
    private static final int ALL_PLAYERS_VISIBILITY = 255;
    /**
     * The visibility flags of a sprite visible to no player.
     */
    private static final int NO_PLAYERS_VISIBILITY = 0;
    /**
     * The image count of the mineral field sprite, and so of its fog copy.
     */
    private static final int MINERAL_IMAGE_COUNT = 2;
    /**
     * The image type of the first image of the third mineral field type.
     */
    private static final int MINERAL_FIRST_IMAGE = 351;
    /**
     * The flags of the first copied image: redraw, full iscript and clickable.
     */
    private static final int MINERAL_FIRST_IMAGE_FLAGS = 49;
    /**
     * The image type of the last image of the third mineral field type.
     */
    private static final int MINERAL_LAST_IMAGE = 352;
    /**
     * The flags of the last copied image: redraw.
     */
    private static final int MINERAL_LAST_IMAGE_FLAGS = 1;
    /**
     * The allocated sprites of the fog copy test: the mineral field and its copy.
     */
    private static final int FOG_SPRITE_COUNT = 2;
    /**
     * The horizontal position of the created unit sprites.
     */
    private static final int POSITION_X = 321;
    /**
     * The vertical position of the created unit sprites.
     */
    private static final int POSITION_Y = 654;
    /**
     * The owner of the created unit sprites.
     */
    private static final int OWNER = 3;
    /**
     * The elevation level of the marine sprite.
     */
    private static final int MARINE_ELEVATION = 4;
    /**
     * The image count of a sprite with its main image and one shadow or other second image: the marine, and the built sprites without a Nexus
     * overlay.
     */
    private static final int IMAGES_WITH_SHADOW = 2;
    /**
     * The marine image type.
     */
    private static final int MARINE_IMAGE = 239;
    /**
     * The wait the marine's Init script draws.
     */
    private static final int MARINE_INIT_WAIT = 62;
    /**
     * The marine's wait after the immediate update.
     */
    private static final int MARINE_NEXT_WAIT = 61;
    /**
     * The sprite row of {@link #POSITION_Y}.
     */
    private static final int SPRITE_ROW = 20;
    /**
     * The random call site of {@code waitrand}.
     */
    private static final int WAIT_RANDOM_SOURCE = 3;
    /**
     * The random calls of a marine creation: the {@code waitrand} of the marine's Init script.
     */
    private static final int MARINE_INIT_RANDOM_CALLS = 1;
    /**
     * The registration value of the created marine sprite.
     */
    private static final int MARINE_REGISTRATION_VALUE = 0;
    /**
     * The byte capacity of one case vector.
     */
    private static final int VECTOR_SIZE = 256;
    /**
     * The unit record offset of the unit type WORD.
     */
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    /**
     * The unit record offset of the damage overlay count BYTE.
     */
    private static final int DAMAGE_OVERLAY_COUNT_OFFSET = 0xEB;
    /**
     * The construction mode that uses the sprite's own image.
     */
    private static final int SPRITE_IMAGE_MODE = 0;
    /**
     * The construction mode that prefers the construction image.
     */
    private static final int CONSTRUCTION_IMAGE_MODE = 1;
    /**
     * The animation force that ignores the sprite's nonbreaking flag.
     */
    private static final int FORCED = 1;
    /**
     * The animation force that honors the sprite's nonbreaking flag.
     */
    private static final int UNFORCED = 0;
    /**
     * The sprite flag of a selection circle.
     */
    private static final int SELECTION_CIRCLE_FLAG = 0x01;
    /**
     * The sprite flag of the first team selection circle.
     */
    private static final int TEAM_SELECTION_CIRCLE_1_FLAG = 0x02;
    /**
     * The sprite flag of the second team selection circle.
     */
    private static final int TEAM_SELECTION_CIRCLE_2_FLAG = 0x04;
    /**
     * The sprite flag of a selected sprite.
     */
    private static final int SELECTED_FLAG = 0x08;
    /**
     * The sprite flag of a running nonbreaking code block.
     */
    private static final int NO_BREAK_FLAG = 0x80;
    /**
     * The sprite flags that the construction image keeps or clears: the selection circle, both team selection circles and the selected flag.
     */
    private static final int SELECTION_OVERLAY_FLAGS = 0x0F;
    /**
     * The field of a selection flag case that holds the sprite flags before the construction image.
     */
    private static final int CASE_INITIAL_FLAGS = 0;
    /**
     * The field of a selection flag case that holds the selection flags after the construction image.
     */
    private static final int CASE_RESTORED_FLAGS = 1;
    /**
     * The geyser image type.
     */
    private static final int GEYSER_IMAGE = 344;
    /**
     * The wait the geyser's Init script sets.
     */
    private static final int GEYSER_INIT_WAIT = 14;
    /**
     * The image type that the construction image mode gives the geyser.
     */
    private static final int GEYSER_CONSTRUCTION_IMAGE = 325;
    /**
     * The geyser's program counter after its Built animation.
     */
    private static final int GEYSER_BUILT_PROGRAM_COUNTER = 8370;
    /**
     * The hatchery's program counter after its forced Built animation.
     */
    private static final int HATCHERY_BUILT_PROGRAM_COUNTER = 19180;
    /**
     * The hatchery's wait after its forced Built animation.
     */
    private static final int HATCHERY_BUILT_WAIT = 0;
    /**
     * The random calls of the hatchery's forced Built animation.
     */
    private static final int HATCHERY_BUILT_RANDOM_CALLS = 1;
    /**
     * The program counter of the waiting script after the replacement Init.
     */
    private static final int WAITING_PROGRAM_COUNTER = 31164;
    /**
     * The wait left after the replacement Init.
     */
    private static final int WAITING_WAIT = 124;
    /**
     * The wait left after the first sprite execution.
     */
    private static final int NEXT_WAITING_WAIT = 123;
    /**
     * The image count of the Nexus sprite with its overlay.
     */
    private static final int IMAGES_WITH_OVERLAY = 3;
    /**
     * The image list position of the Nexus main image, after its overlay.
     */
    private static final int NEXUS_MAIN_POSITION = 1;
    /**
     * The overlay image type that the forced Built animation gives the Nexus.
     */
    private static final int NEXUS_OVERLAY_IMAGE = 181;
    /**
     * The remapping draw function.
     */
    private static final int REMAPPING_MODIFIER = 9;
    /**
     * The color remapping of the Nexus overlay.
     */
    private static final int OVERLAY_COLOR_REMAPPING = 3;
    /**
     * The sprite executions after the forced Built animation.
     */
    private static final int EXECUTION_COUNT = 7;
    /**
     * A sprite type outside {@code sprites.dat}.
     */
    private static final int MISSING_SPRITE_TYPE = 65535;
    /**
     * The horizontal position of the missing sprite.
     */
    private static final int MISSING_POSITION_X = 32;
    /**
     * The horizontal position of the sprite without image storage.
     */
    private static final int EXHAUSTED_POSITION_X = 4;
    /**
     * The vertical position of the sprite without image storage.
     */
    private static final int EXHAUSTED_POSITION_Y = 5;
    /**
     * The owner of the sprite without image storage.
     */
    private static final int EXHAUSTED_OWNER = 7;
    /**
     * The first sprite slot.
     */
    private static final int FIRST_SLOT = 0;
    /**
     * The second sprite slot.
     */
    private static final int SECOND_SLOT = 1;
    /**
     * The third sprite slot.
     */
    private static final int THIRD_SLOT = 2;
    /**
     * The horizontal and vertical position of the ending marine.
     */
    private static final int ENDING_POSITION = 1;
    /**
     * The iscript whose Death animation reaches {@code end}.
     */
    private static final int END_SCRIPT = 33;
    /**
     * The byte distance from the Death entry of {@link #END_SCRIPT} to its {@code end} instruction.
     */
    private static final int END_DISTANCE = 2;
    /**
     * The byte size of the {@code end} instruction.
     */
    private static final int END_INSTRUCTION_SIZE = 1;
    /**
     * The wait of the shadow before the execution.
     */
    private static final int SHADOW_WAIT = 2;
    /**
     * The wait of the shadow after the execution.
     */
    private static final int SHADOW_NEXT_WAIT = 1;
    /**
     * The image list position of the marine's shadow, after the marine image.
     */
    private static final int SHADOW_POSITION = 1;
    /**
     * The image count after the marine image ends: the shadow.
     */
    private static final int REMAINING_IMAGE_COUNT = 1;
    /**
     * The Death animation.
     */
    private static final int DEATH_ANIMATION = 1;
    /**
     * The SHA-256 digest of the construction image replacement cases.
     */
    private static final @NotNull String CONSTRUCTION_CASES_HASH = "807277b972c1e326190c95164d26ad6d15d00945883ab2b172b486f4f0ae1311";
    /**
     * The digest algorithm of the construction image replacement cases.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";

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
    void copy_fog_sprite_should_copy_mineral_images_without_executing_their_scripts() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeSpritePool(FOG_SPRITE_POOL_CAPACITY);
        game.getSpriteSystem().initializeImagePool(FOG_IMAGE_POOL_CAPACITY);

        Assertions.assertNotNull(gameData);

        int spriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3));
        Sprite source = game.getIscriptExecutor().createSprite(spriteType,
                                                               MINERAL_POSITION_X, MINERAL_POSITION_Y,
                                                               PlayerTable.NEUTRAL_PLAYER_ID,
                                                               null,
                                                               INSTRUCTION_LIMIT);

        Assertions.assertNotNull(source);

        source.setVisibilityFlags(NO_PLAYERS_VISIBILITY);

        SpriteImage sourceMain = source.getMainImage();

        Assertions.assertNotNull(sourceMain);

        sourceMain.setFrameIndex(MINERAL_FRAME_BASE);

        Sprite copy = game.getIscriptExecutor().copyFogSprite(source, INSTRUCTION_LIMIT);

        Assertions.assertNotNull(copy);
        Assertions.assertNotSame(source, copy);
        Assertions.assertEquals(source.getX(), copy.getX());
        Assertions.assertEquals(source.getY(), copy.getY());
        Assertions.assertEquals(ALL_PLAYERS_VISIBILITY, copy.getVisibilityFlags());
        Assertions.assertEquals(source.getImages().size(), copy.getImages().size());
        Assertions.assertEquals(MINERAL_IMAGE_COUNT, copy.getImages().size());
        Assertions.assertEquals(MINERAL_FIRST_IMAGE, copy.getImages().getFirst().getImageTypeId());
        Assertions.assertEquals(MINERAL_FIRST_IMAGE_FLAGS, copy.getImages().getFirst().getFlags());
        Assertions.assertEquals(MINERAL_LAST_IMAGE, copy.getImages().getLast().getImageTypeId());
        Assertions.assertEquals(MINERAL_LAST_IMAGE_FLAGS, copy.getImages().getLast().getFlags());

        SpriteImage observedMainImage = copy.getMainImage();

        Assertions.assertNotNull(observedMainImage);

        Assertions.assertEquals(MINERAL_FRAME_BASE, observedMainImage.getFrameIndex());

        for (int imageIndex = 0; imageIndex < source.getImages().size(); imageIndex++) {
            SpriteImage original = source.getImages().get(imageIndex);
            SpriteImage copied = copy.getImages().get(imageIndex);

            Assertions.assertNotSame(original, copied);
            Assertions.assertEquals(original.getImageTypeId(), copied.getImageTypeId());
            Assertions.assertEquals(original.getDrawnFrameIndex(), copied.getDrawnFrameIndex());
            Assertions.assertEquals(0, copied.getProgramCounter());
            Assertions.assertEquals(0, copied.getWait());
            Assertions.assertTrue(copied.hasGraphics(), "A copied image takes the graphics of its image type, which a save keeps.");
        }

        SpritePool observedSpritePool = game.getSpriteSystem().getSpritePool();

        Assertions.assertNotNull(observedSpritePool);

        Assertions.assertEquals(FOG_SPRITE_COUNT, observedSpritePool.getAllocatedCount());

        int expectedImageCount = source.getImages().size() + copy.getImages().size();

        SpriteImagePool observedImagePool = game.getSpriteSystem().getImagePool();

        Assertions.assertNotNull(observedImagePool);

        Assertions.assertEquals(expectedImageCount, observedImagePool.getAllocatedCount());
    }

    @Test
    void marine_sprite_creation_uses_unit_context_then_runs_the_immediate_update() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeSpritePool(MARINE_SPRITE_POOL_CAPACITY);
        game.getSpriteSystem().initializeImagePool(MARINE_IMAGE_POOL_CAPACITY);

        Unit unit = new UnitPool(UNIT_POOL_CAPACITY).getUnit(UNIT_POOL_INDEX);

        Assertions.assertNotNull(gameData);

        int spriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", ScrUnitIds.TERRAN_MARINE));
        Sprite sprite = game.getIscriptExecutor().createSprite(spriteType, POSITION_X, POSITION_Y, OWNER, unit, MARINE_INSTRUCTION_LIMIT);

        Assertions.assertNotNull(sprite);
        Assertions.assertEquals(FIRST_SLOT, sprite.getPoolIndex());
        Assertions.assertEquals(POSITION_X, sprite.getX());
        Assertions.assertEquals(POSITION_Y, sprite.getY());
        Assertions.assertEquals(OWNER, sprite.getOwner());
        Assertions.assertEquals(MARINE_ELEVATION, sprite.getElevationLevel());
        Assertions.assertEquals(ALL_PLAYERS_VISIBILITY, sprite.getVisibilityFlags());
        Assertions.assertEquals(0, sprite.getFlags());
        Assertions.assertTrue(sprite.getWidth() > 0);
        Assertions.assertTrue(sprite.getHeight() > 0);
        Assertions.assertEquals(IMAGES_WITH_SHADOW, sprite.getImages().size());

        SpriteImage mainImage = sprite.getMainImage();

        Assertions.assertNotNull(mainImage);
        Assertions.assertEquals(MARINE_IMAGE, mainImage.getImageTypeId());
        Assertions.assertEquals(MARINE_INIT_WAIT, mainImage.getWait());

        SpritePool observedSpritePool = game.getSpriteSystem().getSpritePool();

        Assertions.assertNotNull(observedSpritePool);

        Assertions.assertEquals(SPRITE_ROW, observedSpritePool.getRowIndex(FIRST_SLOT));
        Assertions.assertTrue(observedSpritePool.isRegistered(FIRST_SLOT));
        Assertions.assertEquals(MARINE_REGISTRATION_VALUE, observedSpritePool.getRegistrationValue(FIRST_SLOT));
        Assertions.assertEquals(MARINE_INIT_RANDOM_CALLS, game.getRandom().getCallCount(WAIT_RANDOM_SOURCE));

        Assertions.assertTrue(game.getIscriptExecutor().executeSprite(sprite, unit, MARINE_INSTRUCTION_LIMIT));

        Assertions.assertEquals(MARINE_NEXT_WAIT, mainImage.getWait());
        Assertions.assertEquals(MARINE_INIT_RANDOM_CALLS, game.getRandom().getCallCount(WAIT_RANDOM_SOURCE));
    }

    @Test
    void matches_native_construction_image_replacement_cases() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer vector = ByteBuffer.allocate(VECTOR_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int unitType : new int[]{
                ScrUnitIds.TERRAN_MARINE,
                ScrUnitIds.TERRAN_COMMAND_CENTER,
                ScrUnitIds.ZERG_HATCHERY,
                ScrUnitIds.PROTOSS_NEXUS,
                ScrUnitIds.RESOURCE_MINERAL_FIELD,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_2,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3
        }) {
            for (int constructionMode : new int[]{SPRITE_IMAGE_MODE, CONSTRUCTION_IMAGE_MODE}) {
                Game game = Game.create(gameData,
                                        new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]),
                                        new LcgRandom(RANDOM_SEED));

                game.getSpriteSystem().initializeSpritePool(SINGLE_SPRITE_POOL_CAPACITY);
                game.getSpriteSystem().initializeImagePool(CONSTRUCTION_IMAGE_POOL_CAPACITY);

                Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);

                Assertions.assertNotNull(gameData);

                int spriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", unitType));
                Sprite sprite = game.getIscriptExecutor().createSprite(spriteType, POSITION_X, POSITION_Y, OWNER, unit, INSTRUCTION_LIMIT);

                unit.setSprite(sprite);

                Assertions.assertNotNull(sprite);

                int width = sprite.getWidth();
                int height = sprite.getHeight();

                game.getIscriptExecutor().initializeConstructionImage(unit, constructionMode, INSTRUCTION_LIMIT);

                Assertions.assertSame(sprite, unit.getSpriteOrNull());
                Assertions.assertEquals(width, sprite.getWidth());
                Assertions.assertEquals(height, sprite.getHeight());

                vector.clear();
                vector.putInt(unitType).putInt(constructionMode);

                SpriteImage observedMainImage = sprite.getMainImage();

                Assertions.assertNotNull(observedMainImage);

                vector.putInt(observedMainImage.getIndex());
                vector.putInt(game.getRandom().getState()).putInt(game.getRandom().getTotalRandomCounts());
                vector.putInt(UnitMemoryScenario.of(unit).readMemoryUnsignedByte(DAMAGE_OVERLAY_COUNT_OFFSET));

                SpriteImagePool pool = game.getSpriteSystem().getImagePool();

                Assertions.assertNotNull(pool);

                vector.putInt(pool.getCapacity() - pool.getAllocatedCount());

                for (int poolIndex = pool.getFirstFreeIndex(); poolIndex >= 0; poolIndex = pool.getNextFreeIndex(poolIndex)) {
                    vector.putInt(poolIndex);
                }

                vector.putInt(sprite.getImages().size());

                for (SpriteImage image : sprite.getImages()) {
                    vector.putInt(image.getIndex()).putInt(image.getImageTypeId()).putInt(image.getModifier());
                    vector.putInt(image.getFrameIndexOffset()).putInt(image.getFlags()).putInt(image.getProgramCounter());
                    vector.putInt(image.getWait()).putInt(image.getFrameIndex());
                }

                digest.update(vector.array(), 0, vector.position());
            }
        }

        Assertions.assertEquals(CONSTRUCTION_CASES_HASH, HexFormat.of().formatHex(digest.digest()));
    }

    /**
     * Checks that the construction image keeps the selection and team selection circle flags and keeps the selected flag only beside a selection
     * circle.
     */
    @Test
    void initialize_construction_image_should_restore_selection_overlay_flags() {
        int[][] cases = {
                {SELECTION_CIRCLE_FLAG | SELECTED_FLAG, SELECTION_CIRCLE_FLAG | SELECTED_FLAG},
                {SELECTION_CIRCLE_FLAG, SELECTION_CIRCLE_FLAG},
                {SELECTED_FLAG, 0},
                {TEAM_SELECTION_CIRCLE_1_FLAG | SELECTED_FLAG, TEAM_SELECTION_CIRCLE_1_FLAG},
                {TEAM_SELECTION_CIRCLE_2_FLAG | SELECTION_CIRCLE_FLAG, TEAM_SELECTION_CIRCLE_2_FLAG | SELECTION_CIRCLE_FLAG}
        };

        for (int[] flags : cases) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

            game.getSpriteSystem().initializeSpritePool(SINGLE_SPRITE_POOL_CAPACITY);
            game.getSpriteSystem().initializeImagePool(CONSTRUCTION_IMAGE_POOL_CAPACITY);

            Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.TERRAN_COMMAND_CENTER);

            Assertions.assertNotNull(gameData);

            int spriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", ScrUnitIds.TERRAN_COMMAND_CENTER));
            Sprite sprite = game.getIscriptExecutor().createSprite(spriteType, POSITION_X, POSITION_Y, OWNER, unit, INSTRUCTION_LIMIT);

            unit.setSprite(sprite);

            Assertions.assertNotNull(sprite);

            sprite.setFlags(sprite.getFlags() | flags[CASE_INITIAL_FLAGS]);

            game.getIscriptExecutor().initializeConstructionImage(unit, CONSTRUCTION_IMAGE_MODE, INSTRUCTION_LIMIT);

            Assertions.assertEquals(flags[CASE_RESTORED_FLAGS],
                                    sprite.getFlags() & SELECTION_OVERLAY_FLAGS,
                                    "Flags 0x" + Integer.toHexString(flags[CASE_INITIAL_FLAGS]));
        }
    }

    @Test
    void geyser_sprite_uses_tileset_frames_through_construction_and_completion_animations() {
        for (int tileset = 0; tileset < TILESET_COUNT; tileset++) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, tileset, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

            game.getSpriteSystem().initializeSpritePool(SINGLE_SPRITE_POOL_CAPACITY);
            game.getSpriteSystem().initializeImagePool(CONSTRUCTION_IMAGE_POOL_CAPACITY);

            Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.RESOURCE_VESPENE_GEYSER);

            Assertions.assertNotNull(gameData);

            int spriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", ScrUnitIds.RESOURCE_VESPENE_GEYSER));
            Sprite sprite = game.getIscriptExecutor().createSprite(spriteType, POSITION_X, POSITION_Y, OWNER, unit, INSTRUCTION_LIMIT);

            unit.setSprite(sprite);

            Assertions.assertNotNull(sprite);

            SpriteImage observedMainImage = sprite.getMainImage();

            Assertions.assertNotNull(observedMainImage);

            Assertions.assertEquals(GEYSER_IMAGE, observedMainImage.getImageTypeId());
            Assertions.assertEquals(tileset, observedMainImage.getFrameIndex());
            Assertions.assertEquals(GEYSER_INIT_WAIT, observedMainImage.getWait());

            game.getIscriptExecutor().initializeConstructionImage(unit, CONSTRUCTION_IMAGE_MODE, INSTRUCTION_LIMIT);

            observedMainImage = sprite.getMainImage();

            Assertions.assertNotNull(observedMainImage);

            Assertions.assertEquals(GEYSER_CONSTRUCTION_IMAGE, observedMainImage.getImageTypeId());

            game.getIscriptExecutor().initializeConstructionImage(unit, SPRITE_IMAGE_MODE, INSTRUCTION_LIMIT);

            game.getIscriptExecutor().runSpriteAnimation(sprite, unit, IscriptExecutor.ANIMATION_BUILT, FORCED, INSTRUCTION_LIMIT);

            observedMainImage = sprite.getMainImage();

            Assertions.assertNotNull(observedMainImage);

            Assertions.assertEquals(GEYSER_IMAGE, observedMainImage.getImageTypeId());
            Assertions.assertEquals(tileset, observedMainImage.getFrameIndex());
            Assertions.assertEquals(GEYSER_BUILT_PROGRAM_COUNTER, observedMainImage.getProgramCounter());
            Assertions.assertEquals(GEYSER_INIT_WAIT, observedMainImage.getWait());
        }
    }

    @Test
    void matches_native_built_animation_force_and_image_insertion_cases() {
        for (int unitType : new int[]{
                ScrUnitIds.TERRAN_COMMAND_CENTER,
                ScrUnitIds.ZERG_HATCHERY,
                ScrUnitIds.PROTOSS_NEXUS,
                ScrUnitIds.RESOURCE_MINERAL_FIELD,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_2,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3
        }) {
            for (int force : new int[]{UNFORCED, FORCED}) {
                Game game = Game.create(gameData,
                                        new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]),
                                        new LcgRandom(RANDOM_SEED));

                game.getSpriteSystem().initializeSpritePool(SINGLE_SPRITE_POOL_CAPACITY);
                game.getSpriteSystem().initializeImagePool(CONSTRUCTION_IMAGE_POOL_CAPACITY);

                Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);

                Assertions.assertNotNull(gameData);

                int spriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", unitType));
                Sprite sprite = game.getIscriptExecutor().createSprite(spriteType, POSITION_X, POSITION_Y, OWNER, unit, INSTRUCTION_LIMIT);

                unit.setSprite(sprite);

                Assertions.assertNotNull(sprite);

                game.getIscriptExecutor().initializeConstructionImage(unit, SPRITE_IMAGE_MODE, INSTRUCTION_LIMIT);

                sprite.setFlags(NO_BREAK_FLAG);

                game.getIscriptExecutor().runSpriteAnimation(sprite, unit, IscriptExecutor.ANIMATION_BUILT, force, INSTRUCTION_LIMIT);

                boolean hatcheryExecuted = unitType == ScrUnitIds.ZERG_HATCHERY && force != UNFORCED;
                boolean nexusOverlayCreated = unitType == ScrUnitIds.PROTOSS_NEXUS && force != UNFORCED;

                SpriteImage observedMainImage = sprite.getMainImage();

                Assertions.assertNotNull(observedMainImage);

                Assertions.assertEquals(hatcheryExecuted ? HATCHERY_BUILT_PROGRAM_COUNTER : WAITING_PROGRAM_COUNTER,
                                        observedMainImage.getProgramCounter());
                Assertions.assertEquals(hatcheryExecuted ? HATCHERY_BUILT_WAIT : WAITING_WAIT, observedMainImage.getWait());
                Assertions.assertEquals(hatcheryExecuted ? HATCHERY_BUILT_RANDOM_CALLS : 0, game.getRandom().getTotalRandomCounts());
                Assertions.assertEquals(nexusOverlayCreated ? IMAGES_WITH_OVERLAY : IMAGES_WITH_SHADOW, sprite.getImages().size());
                Assertions.assertEquals(NO_BREAK_FLAG, sprite.getFlags());

                if (nexusOverlayCreated) {
                    Assertions.assertEquals(NEXUS_OVERLAY_IMAGE, sprite.getImages().getFirst().getImageTypeId());
                    Assertions.assertEquals(REMAPPING_MODIFIER, sprite.getImages().getFirst().getModifier());
                    Assertions.assertEquals(OVERLAY_COLOR_REMAPPING, sprite.getImages().getFirst().getColorRemappingIndex());
                    Assertions.assertEquals(WAITING_PROGRAM_COUNTER, sprite.getImages().getFirst().getProgramCounter());
                    Assertions.assertSame(sprite.getMainImage(), sprite.getImages().get(NEXUS_MAIN_POSITION));

                    for (int invocation = 0; invocation < EXECUTION_COUNT; invocation++) {
                        Assertions.assertTrue(game.getIscriptExecutor().executeSprite(sprite, unit, INSTRUCTION_LIMIT));

                        Assertions.assertEquals(IMAGES_WITH_OVERLAY, sprite.getImages().size());
                        Assertions.assertEquals(WAITING_PROGRAM_COUNTER, sprite.getImages().getFirst().getProgramCounter());
                        Assertions.assertEquals(NEXT_WAITING_WAIT - invocation, sprite.getImages().getFirst().getWait());
                        Assertions.assertEquals(OVERLAY_COLOR_REMAPPING, sprite.getImages().getFirst().getColorRemappingIndex());
                        Assertions.assertEquals(REMAPPING_MODIFIER, sprite.getImages().getFirst().getModifier());

                        observedMainImage = sprite.getMainImage();

                        Assertions.assertNotNull(observedMainImage);

                        Assertions.assertEquals(NEXT_WAITING_WAIT - invocation, observedMainImage.getWait());
                        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
                    }
                }
            }
        }
    }

    @Test
    void create_sprite_should_return_failed_storage_after_free_head_without_running_init() {
        Game game = Game.create(gameData,
                                new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, TILESET, new int[SMALL_MAP_TILE_COUNT]),
                                new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeSpritePool(FAILED_SPRITE_POOL_CAPACITY);
        game.getSpriteSystem().initializeImagePool(SINGLE_IMAGE_POOL_CAPACITY);

        Assertions.assertNull(game.getIscriptExecutor().createSprite(MISSING_SPRITE_TYPE, MISSING_POSITION_X, 0, 0, null, FAILED_INSTRUCTION_LIMIT));

        SpritePool pool = game.getSpriteSystem().getSpritePool();

        Assertions.assertNotNull(pool);

        Assertions.assertEquals(THIRD_SLOT, pool.getFirstFreeIndex());
        Assertions.assertEquals(FIRST_SLOT, pool.getNextFreeIndex(THIRD_SLOT));
        Assertions.assertEquals(SECOND_SLOT, pool.getNextFreeIndex(FIRST_SLOT));
        Assertions.assertEquals(0, pool.getAllocatedCount());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());

        SpriteImagePool observedImagePool = game.getSpriteSystem().getImagePool();

        Assertions.assertNotNull(observedImagePool);

        observedImagePool.allocate();

        Assertions.assertNotNull(gameData);

        int spriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", ScrUnitIds.TERRAN_MARINE));

        Assertions.assertNull(game.getIscriptExecutor().createSprite(spriteType,
                                                                     EXHAUSTED_POSITION_X, EXHAUSTED_POSITION_Y,
                                                                     EXHAUSTED_OWNER,
                                                                     null,
                                                                     FAILED_INSTRUCTION_LIMIT));

        Assertions.assertEquals(FIRST_SLOT, pool.getFirstFreeIndex());
        Assertions.assertEquals(THIRD_SLOT, pool.getNextFreeIndex(FIRST_SLOT));
        Assertions.assertEquals(EXHAUSTED_OWNER, pool.getSprite(THIRD_SLOT).getOwner());
        Assertions.assertFalse(pool.isRegistered(THIRD_SLOT));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_sprite_should_cache_next_image_before_end_and_keep_main_storage() throws IOException {
        Game game = Game.create(gameData,
                                new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, TILESET, new int[SMALL_MAP_TILE_COUNT]),
                                new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeSpritePool(SINGLE_SPRITE_POOL_CAPACITY);
        game.getSpriteSystem().initializeImagePool(SHADOW_IMAGE_POOL_CAPACITY);

        Assertions.assertNotNull(gameData);

        int spriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", ScrUnitIds.TERRAN_MARINE));
        Sprite sprite = game.getIscriptExecutor().createSprite(spriteType, ENDING_POSITION, ENDING_POSITION, 0, null, END_INSTRUCTION_LIMIT);

        Assertions.assertNotNull(sprite);

        SpriteImage mainImage = sprite.getMainImage();
        SpriteImage shadow = sprite.getImages().get(SHADOW_POSITION);

        Assertions.assertNotNull(mainImage);

        int endOffset = gameData.getIscript().getAnimationOffset(END_SCRIPT, DEATH_ANIMATION) + END_DISTANCE;

        mainImage.setProgramCounter(endOffset);
        mainImage.setWait(0);
        shadow.setWait(SHADOW_WAIT);

        Assertions.assertTrue(game.getIscriptExecutor().executeSprite(sprite, null, END_INSTRUCTION_LIMIT));

        Assertions.assertEquals(SHADOW_NEXT_WAIT, shadow.getWait());
        Assertions.assertEquals(REMAINING_IMAGE_COUNT, sprite.getImages().size());
        Assertions.assertSame(mainImage, sprite.getMainImage());
        Assertions.assertFalse(mainImage.hasGraphics());
        Assertions.assertEquals(endOffset + END_INSTRUCTION_SIZE, mainImage.getProgramCounter());
    }
}
