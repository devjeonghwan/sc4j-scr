package org.fogofwarlabs.sc4j.scr.game.map;

import org.fogofwarlabs.sc4j.scr.core.GameSettings;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkSection;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkThingy;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.GameLoader;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that {@code THG2} records create map sprites, failed allocations and filtered records in record order.
 */
@RequiresInstallation
final class MapSpriteEffectLoaderTest {
    /**
     * The name of the section of map sprites and doodad units.
     */
    private static final @NotNull String THINGY_SECTION = "THG2";
    /**
     * The bytes of a CHK section header: the name and the size DWORD.
     */
    private static final int SECTION_HEADER_SIZE = 8;
    /**
     * The number of records in each synthetic {@code THG2} section.
     */
    private static final int SYNTHETIC_SECTION_RECORD_COUNT = 2;
    /**
     * The declared size of each synthetic {@code THG2} section.
     */
    private static final int SYNTHETIC_SECTION_SIZE = SYNTHETIC_SECTION_RECORD_COUNT * ChkThingy.ENTRY_SIZE;
    /**
     * The number of synthetic {@code THG2} sections.
     */
    private static final int SYNTHETIC_SECTION_COUNT = 2;
    /**
     * The number of records in all synthetic {@code THG2} sections.
     */
    private static final int SYNTHETIC_RECORD_COUNT = SYNTHETIC_SECTION_COUNT * SYNTHETIC_SECTION_RECORD_COUNT;
    /**
     * The value of the unused byte of each synthetic {@code THG2} record.
     */
    private static final int UNUSED_RECORD_BYTE = 255;
    /**
     * The record flags of a synthetic sprite with the post-creation effect bit, which a sprite record ignores.
     */
    private static final int SPRITE_EFFECT_BIT_RECORD_FLAGS = 0x9000;
    /**
     * The record flags of a synthetic sprite without the post-creation effect bit.
     */
    private static final int SPRITE_RECORD_FLAGS = 0x1000;
    /**
     * The record flags of a synthetic unit with the post-creation effect.
     */
    private static final int UNIT_EFFECT_RECORD_FLAGS = 0x8000;
    /**
     * The victory condition ID of the synthetic settings, nonzero so that the setup does not use the map settings.
     */
    private static final int NON_MAP_SETTINGS_VICTORY = 1;
    /**
     * The sprite effect, sprite and image pool capacities of the synthetic section tests.
     */
    private static final int SYNTHETIC_THINGY_CAPACITY = 3;
    private static final int SYNTHETIC_SPRITE_CAPACITY = 3;
    private static final int SYNTHETIC_IMAGE_CAPACITY = 8;
    /**
     * The iscript instruction bound of the replay map test.
     */
    private static final int INSTRUCTION_LIMIT = 1024;
    /**
     * The iscript instruction bound of the synthetic section and post-creation effect tests.
     */
    private static final int SYNTHETIC_INSTRUCTION_LIMIT = 128;
    /**
     * The game random seed of the replay map test.
     */
    private static final int RANDOM_SEED = 0x12345678;
    /**
     * The index of the image capacity in the replay limits.
     */
    private static final int IMAGE_LIMIT_INDEX = 0;
    /**
     * The index of the sprite capacity in the replay limits.
     */
    private static final int SPRITE_LIMIT_INDEX = 1;
    /**
     * The index of the standalone sprite effect capacity in the replay limits.
     */
    private static final int THINGY_LIMIT_INDEX = 2;
    /**
     * The number of primary sprite effect updates the replay map test runs.
     */
    private static final int SPRITE_EFFECT_UPDATE_COUNT = 7;
    /**
     * The highest sprite type identifier whose map sprites in the replay map carry two images.
     */
    private static final int LAST_TWO_IMAGE_SPRITE_TYPE = 30;
    /**
     * The local vision comparison mask of the replay map test: the bit of player {@code 0}.
     */
    private static final int LOCAL_PLAYER_MASK = 1;
    /**
     * The unit memory offset of the status flags DWORD.
     */
    private static final int STATUS_OFFSET = 0x140;
    /**
     * The unit memory offset of the Lockdown timer byte.
     */
    private static final int LOCKDOWN_TIMER_OFFSET = 0x1A3;
    /**
     * The unit memory offset of the Stasis Field timer byte.
     */
    private static final int STASIS_TIMER_OFFSET = 0x1A5;
    /**
     * The unit memory offset of the Maelstrom timer byte.
     */
    private static final int MAELSTROM_TIMER_OFFSET = 0x1B4;

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
    void load_should_create_all_84_map_sprites_after_startup_allocation() throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT)));
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData);

        ChkFile mapFile = ChkFile.parseTolerant(mapData);
        int[] capacities = replay.getLimits().getEffectiveValues();
        Game game = Game.create(gameData,
                                new GameMap(mapFile.getWidth(), mapFile.getHeight(),
                                            mapFile.getTileset(),
                                            new int[mapFile.getWidth() * mapFile.getHeight()],
                                            new int[mapFile.getWidth() * mapFile.getHeight()]),
                                new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeSpriteEffectPool(replay.getLimits().getThingyCapacity());
        game.getSpriteSystem().initializeSpritePool(capacities[SPRITE_LIMIT_INDEX]);
        game.getSpriteSystem().initializeImagePool(capacities[IMAGE_LIMIT_INDEX]);
        game.getIscriptExecutor().initializeSpriteEffects(INSTRUCTION_LIMIT);

        List<MapSpriteEffectLoader.Creation> events = new ArrayList<>();
        List<ChkThingy> records = mapFile.getThingies();
        int recordIndex = 0;

        for (ChkSection section : mapFile.getSections(THINGY_SECTION)) {
            int recordCount = section.getDeclaredSize() / ChkThingy.ENTRY_SIZE;

            List<ChkThingy> occurrenceRecords = records.subList(recordIndex, recordIndex + recordCount);
            GameSettings occurrenceSettings = replay.getHeader().getSettings();

            events.addAll(MapSpriteEffectLoader.load(new UnitCreationContext(false, false, false, false, true, 0), game,
                                                     section,
                                                     occurrenceRecords,
                                                     occurrenceSettings,
                                                     true,
                                                     INSTRUCTION_LIMIT));

            recordIndex += recordCount;
        }

        Assertions.assertEquals(84, events.size());

        SpriteEffectPool spriteEffects = game.getSpriteSystem().getSpriteEffectPool();

        Assertions.assertNotNull(spriteEffects);

        Assertions.assertEquals(85, spriteEffects.getAllocatedCount());

        SpritePool sprites = game.getSpriteSystem().getSpritePool();

        Assertions.assertNotNull(sprites);

        Assertions.assertEquals(85, sprites.getAllocatedCount());

        int imageCount = 1;

        for (int eventIndex = 0; eventIndex < events.size(); eventIndex++) {
            MapSpriteEffectLoader.Creation event = events.get(eventIndex);
            ChkThingy record = records.get(eventIndex);

            Assertions.assertSame(record, event.getRecord());
            Assertions.assertEquals(record.getSourceOffset(), event.getSourceOffset());
            Assertions.assertEquals(GameLoader.InitializationRoute.THG2_SPRITE_CONSTRUCTOR, event.getRoute());
            Assertions.assertEquals(capacities[THINGY_LIMIT_INDEX] - 1 - eventIndex, event.getThingyPoolIndex());
            Assertions.assertEquals(0, event.getUnitPoolIndex());

            Sprite sprite = spriteEffects.getSpriteEffect(event.getThingyPoolIndex()).getSprite();

            Assertions.assertEquals(capacities[SPRITE_LIMIT_INDEX] - 1 - eventIndex, sprite.getPoolIndex());
            Assertions.assertEquals(record.getTypeIdentifier(), sprite.getSpriteTypeId());
            Assertions.assertEquals(record.getHorizontal(), sprite.getX());
            Assertions.assertEquals(record.getVertical(), sprite.getY());
            Assertions.assertEquals(record.getOwner(), sprite.getOwner());

            int expectedImages = record.getTypeIdentifier() <= LAST_TWO_IMAGE_SPRITE_TYPE ? 2 : 1;

            Assertions.assertEquals(expectedImages, sprite.getImages().size());

            SpriteImage programImage = sprite.getMainImage();

            Assertions.assertNotNull(programImage);

            Assertions.assertEquals(31164, programImage.getProgramCounter());
            Assertions.assertEquals(124, sprite.getMainImage().getWait());

            imageCount += expectedImages;

            Assertions.assertEquals(RANDOM_SEED, event.getRandomStateBefore());
            Assertions.assertEquals(RANDOM_SEED, event.getRandomStateAfter());
            Assertions.assertEquals(0, event.getRandomCallsBefore());
            Assertions.assertEquals(0, event.getRandomCallsAfter());
        }

        SpriteImagePool images = game.getSpriteSystem().getImagePool();

        Assertions.assertNotNull(images);

        Assertions.assertEquals(imageCount, images.getAllocatedCount());
        Assertions.assertEquals(0, spriteEffects.getFirstActiveIndex());
        Assertions.assertEquals(capacities[THINGY_LIMIT_INDEX] - 84, spriteEffects.getNextIndex(0));
        Assertions.assertEquals(capacities[THINGY_LIMIT_INDEX] - 1, spriteEffects.getLastActiveIndex());

        SightCoverage sightCoverage = new SightCoverage(mapFile.getWidth());

        sightCoverage.setSharedVisionMasks(new int[PlayerTable.PLAYER_SLOTS]);
        game.setSightCoverage(sightCoverage);
        game.getLocalVision().setComparisonMask(LOCAL_PLAYER_MASK);

        for (int invocation = 0; invocation < SPRITE_EFFECT_UPDATE_COUNT; invocation++) {
            game.getIscriptExecutor().updatePrimarySpriteEffects(INSTRUCTION_LIMIT);

            int poolIndex = spriteEffects.getFirstActiveIndex();
            int activeCount = 0;

            while (poolIndex >= 0) {
                Sprite sprite = spriteEffects.getSpriteEffect(poolIndex).getSprite();

                Assertions.assertNotNull(sprite);

                SpriteImage programImage = sprite.getMainImage();

                Assertions.assertNotNull(programImage);

                Assertions.assertEquals(31164, programImage.getProgramCounter());
                Assertions.assertEquals(123 - invocation, sprite.getMainImage().getWait());

                for (SpriteImage image : sprite.getImages()) {
                    if (image != sprite.getMainImage()) {
                        Assertions.assertEquals(31252, image.getProgramCounter());
                        Assertions.assertEquals(0, image.getWait());
                    }
                }

                activeCount++;
                poolIndex = spriteEffects.getNextIndex(poolIndex);
            }

            Assertions.assertEquals(85, activeCount);
            Assertions.assertEquals(imageCount, images.getAllocatedCount());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }

        Assertions.assertFalse(game.hasInitializedWorldState());
    }

    @Test
    void load_should_handle_repeated_sections_failed_and_filtered_records() throws IOException {
        ByteBuffer source = ByteBuffer.allocate(SYNTHETIC_SECTION_COUNT * (SECTION_HEADER_SIZE + SYNTHETIC_SECTION_SIZE))
                                      .order(ByteOrder.LITTLE_ENDIAN);

        source.put(THINGY_SECTION.getBytes(StandardCharsets.US_ASCII)).putInt(SYNTHETIC_SECTION_SIZE);
        source.putShort((short) 16).putShort((short) 32).putShort((short) 0).put((byte) 3).put((byte) UNUSED_RECORD_BYTE)
              .putShort((short) SPRITE_EFFECT_BIT_RECORD_FLAGS);
        source.putShort((short) 17).putShort((short) 1).putShort((short) 1).put((byte) 7).put((byte) UNUSED_RECORD_BYTE)
              .putShort((short) SPRITE_EFFECT_BIT_RECORD_FLAGS);
        source.put(THINGY_SECTION.getBytes(StandardCharsets.US_ASCII)).putInt(SYNTHETIC_SECTION_SIZE);
        source.putShort((short) 18).putShort((short) 2).putShort((short) 2).put((byte) 2).put((byte) UNUSED_RECORD_BYTE)
              .putShort((short) SPRITE_RECORD_FLAGS);
        source.putShort((short) 0).putShort((short) 0).putShort((short) 0).put((byte) 0).put((byte) UNUSED_RECORD_BYTE)
              .putShort((short) UNIT_EFFECT_RECORD_FLAGS);

        ChkFile mapFile = ChkFile.parseTolerant(source.array());
        GameSettings settings = new GameSettings(NON_MAP_SETTINGS_VICTORY, 0, 0, 0, new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS], 0);
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.getSpriteSystem().initializeSpriteEffectPool(SYNTHETIC_THINGY_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(SYNTHETIC_SPRITE_CAPACITY);
        game.getSpriteSystem().initializeImagePool(SYNTHETIC_IMAGE_CAPACITY);
        game.getIscriptExecutor().initializeSpriteEffects(SYNTHETIC_INSTRUCTION_LIMIT);

        ChkSection firstSection = mapFile.getSections(THINGY_SECTION).getFirst();
        List<ChkThingy> firstRecords = mapFile.getThingies().subList(0, SYNTHETIC_SECTION_RECORD_COUNT);

        List<MapSpriteEffectLoader.Creation> first = MapSpriteEffectLoader.load(new UnitCreationContext(false, false, false, false, true, 0), game,
                                                                                firstSection,
                                                                                firstRecords,
                                                                                settings,
                                                                                true,
                                                                                SYNTHETIC_INSTRUCTION_LIMIT);

        Assertions.assertEquals(-1, first.getFirst().getThingyPoolIndex());
        Assertions.assertEquals(1, first.get(1).getThingyPoolIndex());

        ChkSection secondSection = mapFile.getSections(THINGY_SECTION).get(1);
        List<ChkThingy> secondRecords = mapFile.getThingies().subList(SYNTHETIC_SECTION_RECORD_COUNT, SYNTHETIC_RECORD_COUNT);

        List<MapSpriteEffectLoader.Creation> second = MapSpriteEffectLoader.load(new UnitCreationContext(false, false, false, false, true, 0), game,
                                                                                 secondSection,
                                                                                 secondRecords,
                                                                                 settings,
                                                                                 true,
                                                                                 SYNTHETIC_INSTRUCTION_LIMIT);

        Assertions.assertEquals(2, second.getFirst().getThingyPoolIndex());
        Assertions.assertEquals(GameLoader.InitializationRoute.THG2_FILTERED, second.get(1).getRoute());
        Assertions.assertEquals(36, second.getFirst().getSourceOffset());
        Assertions.assertEquals(46, second.get(1).getSourceOffset());

        SpriteEffectPool spriteEffects = game.getSpriteSystem().getSpriteEffectPool();

        Assertions.assertNotNull(spriteEffects);

        Assertions.assertEquals(3, spriteEffects.getAllocatedCount());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            ChkSection failureSection = mapFile.getSections(THINGY_SECTION).getFirst();
            List<ChkThingy> failureRecords = mapFile.getThingies().subList(SYNTHETIC_SECTION_RECORD_COUNT, SYNTHETIC_RECORD_COUNT);

            MapSpriteEffectLoader.load(new UnitCreationContext(false, false, false, false, true, 0), game,
                                       failureSection,
                                       failureRecords,
                                       settings,
                                       true,
                                       SYNTHETIC_INSTRUCTION_LIMIT);
        });
    }

    @Test
    void apply_post_creation_effect_should_return_early_for_disabled_or_blocked_units() {
        for (int blockedField : new int[]{STATUS_OFFSET, LOCKDOWN_TIMER_OFFSET, STASIS_TIMER_OFFSET, MAELSTROM_TIMER_OFFSET}) {
            Unit unit = UnitTestUtils.createUnit(1);

            if (blockedField == STATUS_OFFSET) {
                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, UnitStatusFlags.DISABLED);
            } else {
                UnitMemoryScenario.of(unit).writeMemoryByte(blockedField, 1);
            }

            Assertions.assertDoesNotThrow(() -> {
                Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

                MapSpriteEffectLoader.applyPostCreationEffect(game, unit, SYNTHETIC_INSTRUCTION_LIMIT);
            });
            Assertions.assertEquals(blockedField == STATUS_OFFSET ? 0x400 : 0, unit.getStatus());
        }
    }
}
