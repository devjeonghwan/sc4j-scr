package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.ProductionStatistics;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccountingAssert;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMapScenario;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.fogofwarlabs.sc4j.scr.game.visibility.SightCoverage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Checks unit completion: captured completion cases, worker vitals, constructed buildings, neutral mineral fields and placed workers.
 */
@RequiresInstallation
final class UnitCompletionTest {
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    private static final @NotNull String FIRST_PHASE_DIGEST = "48bc024e30ce8bae06679202e97bad052ff45310ee8fb1cb5cb37bef4e670eee";
    private static final @NotNull String ACCOUNTING_DIGEST = "1acb2417a4404e32d7205bd60fce1a46381fa2fe43c30a8bb8f92241e808068f";
    private static final @NotNull String FINAL_COMPLETION_DIGEST = "6e6887ff61e465d36ff118f73e294de0754b1ce936542ef3a8922094225a72b1";

    private static final int MAP_DIMENSION = 64;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int INSTRUCTION_LIMIT = 512;
    private static final int COMPLETION_INSTRUCTION_LIMIT = 1024;
    private static final int WORKER_IMAGE_CAPACITY = 4;
    private static final int IMAGE_CAPACITY = 16;
    private static final int ORDER_CAPACITY = 16;
    private static final int FINAL_ORDER_CAPACITY = 4;
    private static final int RANDOM_SEED = 0x12345678;
    private static final int FILL_DWORD = 0xA5A5A5A5;
    private static final int REFERENCE_WIDTH = Long.BYTES;

    private static final int CURRENT_SPEED_OFFSET = 0x50;
    private static final int OWNER_OFFSET = 0x68;
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int HIGHLIGHTED_ORDER_COUNT_OFFSET = 0xC8;
    private static final int CURRENT_BUTTON_SET_OFFSET = 0xD8;
    private static final int REMAINING_BUILD_TIME_OFFSET = 0xF0;
    private static final int STATUS_OFFSET = 0x140;
    private static final int PATH_COLLISION_FLAGS_OFFSET = 0x191;
    private static final int LOCKDOWN_TIMER_OFFSET = 0x1A3;
    private static final int STASIS_TIMER_OFFSET = 0x1A5;
    private static final int MAELSTROM_TIMER_OFFSET = 0x1B4;
    private static final int AIR_STRENGTH_OFFSET = 0x1D0;
    private static final int GROUND_STRENGTH_OFFSET = 0x1D2;
    private static final int SPRITE_REFERENCE_OFFSET = 0x18;
    /**
     * The sprite, order target, subunit, first and last order and trailing references that the final completion capture clears.
     */
    private static final int @NotNull [] CLEARED_REFERENCE_OFFSETS = {0x18, 0x80, 0xA0, 0xA8, 0xB0, 0x1C8};

    private static final int BUILDING_ABILITY_FLAG = 1;
    /**
     * The unit types of the first phase capture that it skips.
     */
    private static final int FIRST_SKIPPED_TYPE = 203;
    private static final int LAST_SKIPPED_TYPE = 213;
    private static final int @NotNull [] FIRST_PHASE_STATUSES = {0, 0x10000, 0x10400};
    private static final int @NotNull [] REMAINING_TIMES = {0, 1};
    private static final int CAPTURE_SPRITE_X = (short) 0x8123;
    private static final int CREATION_X = 321;
    private static final int CREATION_Y = 654;
    private static final int OWNER = 3;
    private static final int MAXIMUM_SPEED = 0x7FFFFFFF;
    private static final int FIRST_PHASE_RANDOM_SOURCE = 36;
    private static final int FIRST_PHASE_CASE_COUNT = 732;

    private static final int SHIELD_FRACTION_BITS = 8;
    private static final int COMPLETED_STATUS = 1;
    private static final int SPRITE_HIDDEN_FLAG = 0x20;
    private static final int NEUTRAL_OWNER = 11;
    /**
     * The button set of a unit without buttons, the type identifier {@code None}.
     */
    private static final int NO_BUTTON_SET = 228;

    private static final int STATISTIC_COUNT = 48;
    private static final int STATISTICS_BYTE_COUNT = 2 * STATISTIC_COUNT * Integer.BYTES;
    private static final int ACCUMULATORS_OFFSET = STATISTIC_COUNT * Integer.BYTES;
    private static final int FILL_SAMPLE = 0x55555555;
    private static final float FILL_ACCUMULATOR = -3.5f;
    private static final int @NotNull [] ACCOUNTING_STATUSES = {0, 0x40000000};
    private static final int @NotNull [] ACCOUNTING_OWNERS = {0, NEUTRAL_OWNER};
    private static final int @NotNull [] ACCOUNTING_CHANGES = {-1, 1, Integer.MAX_VALUE, Integer.MIN_VALUE};
    private static final int ACCOUNTING_CASE_COUNT = 7296;

    private static final int @NotNull [] PLAYER_TYPES = {0, 1, 3, 7};
    private static final int @NotNull [] FINAL_STATUSES = {2, 0x40000002};
    private static final int NOTHING_ORDER = 23;
    private static final int BUILT_ANIMATION = 16;
    private static final int SPRITE_FLAGS_SAMPLE = 0x80;
    private static final int FINAL_CASE_COUNT = 56;

    private static final int BUILDING_X = 320;
    private static final int BUILDING_Y = 640;
    private static final int COMPLETED_COUNTER_OFFSET = 0x5CF4;
    private static final int COUNTER_PLAYER_SLOTS = 12;
    private static final int WORD_MASK = 0xFFFF;

    private static final int MINIATURE_TILES_PER_TILE = 16;
    private static final int WALKABLE_MINIATURE_FLAG = 1;
    private static final int VISIBILITY_FLAGS = 0xFFFF;
    private static final int WORKER_TILE_X = 10;
    private static final int WORKER_TILE_Y = 20;
    private static final int FIRST_PLAYER_VISIBILITY_FLAGS = 0x101;
    /**
     * The sprite visibility of a sprite that only player {@code 0} sees.
     */
    private static final int FIRST_PLAYER_SPRITE_VISIBILITY = 1;
    /**
     * The path collision flag of a ground unit at ground elevation.
     */
    private static final int GROUND_ELEVATION_PATH_FLAG = 1;
    private static final int EDGES_PER_UNIT = 2;

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

    /**
     * Returns the offset of the completed unit counter of a unit type and player in the captured accounting context.
     *
     * @param unitType the unit type
     * @param owner    the player
     * @return the byte offset of the counter
     */
    private static int getCompletedCounterOffset(int unitType, int owner) {
        return COMPLETED_COUNTER_OFFSET + (unitType * COUNTER_PLAYER_SLOTS + owner) * Integer.BYTES;
    }

    @Test
    void initialize_before_placement_should_write_the_empty_button_set_of_a_disabled_unit() {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setUnitId(ScrUnitIds.NONE);
        unit.setStatus(UnitStatusFlags.DISABLED);
        unit.setCurrentButtonSet(ScrUnitIds.TERRAN_MARINE);

        game.getUnitCompletion().initializeBeforePlacement(new UnitCreationContext(false, false, false, false, false, 0), unit, 1);

        Assertions.assertEquals(NO_BUTTON_SET, unit.getCurrentButtonSet());
    }

    @Test
    void matches_native_initialize_before_placement_cases() throws Exception {
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_COMPLETION_FIRST_PHASE);

        Assertions.assertEquals(FIRST_PHASE_DIGEST, HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(fixture)));

        ByteBuffer expected = ByteBuffer.wrap(fixture).order(ByteOrder.LITTLE_ENDIAN);
        byte[] expectedUnit = new byte[UnitMemoryCheck.SIZE];
        int caseCount = 0;

        for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
            Assertions.assertNotNull(gameData);

            if ((gameData.getUnits().get("Special Ability Flags", unitType) & BUILDING_ABILITY_FLAG) != 0 ||
                unitType >= FIRST_SKIPPED_TYPE && unitType <= LAST_SKIPPED_TYPE) {
                continue;
            }

            for (int status : FIRST_PHASE_STATUSES) {
                for (int remainingTime : REMAINING_TIMES) {
                    Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
                    Unit unit = UnitTestUtils.createUnit(1);

                    for (int offset = 0; offset < UnitMemoryCheck.SIZE; offset += Integer.BYTES) {
                        UnitMemoryScenario.of(unit).writeMemoryInteger(offset, FILL_DWORD);
                    }

                    UnitMemoryScenario.of(unit).clearMemoryBytes(SPRITE_REFERENCE_OFFSET, REFERENCE_WIDTH);
                    unit.setSprite(SpriteScenario.createSprite(0, CAPTURE_SPRITE_X, CREATION_Y, 0));
                    UnitMemoryScenario.of(unit).writeMemoryInteger(CURRENT_SPEED_OFFSET, MAXIMUM_SPEED);
                    UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                    UnitMemoryScenario.of(unit).writeMemoryWord(REMAINING_BUILD_TIME_OFFSET, remainingTime);
                    UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);
                    UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, OWNER);
                    UnitMemoryScenario.of(unit).writeMemoryByte(LOCKDOWN_TIMER_OFFSET, 0);
                    UnitMemoryScenario.of(unit).writeMemoryByte(STASIS_TIMER_OFFSET, 0);
                    UnitMemoryScenario.of(unit).writeMemoryByte(MAELSTROM_TIMER_OFFSET, 0);

                    game.getUnitCompletion().initializeBeforePlacement(new UnitCreationContext(false, false, false, false, false, 0), unit,
                                                                       INSTRUCTION_LIMIT);

                    expected.get(expectedUnit);

                    UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit, "First completion type " + unitType);

                    Assertions.assertEquals(expected.getInt(), game.getRandom().getState());
                    Assertions.assertEquals(expected.getInt(), game.getRandom().getTotalRandomCounts());
                    Assertions.assertEquals(game.getRandom().getTotalRandomCounts(), game.getRandom().getCallCount(FIRST_PHASE_RANDOM_SOURCE));

                    caseCount++;
                }
            }
        }

        Assertions.assertEquals(FIRST_PHASE_CASE_COUNT, caseCount);
        Assertions.assertFalse(expected.hasRemaining());
    }

    @Test
    void initialize_before_placement_should_restore_worker_vitals_without_completing() {
        for (int unitType : new int[]{ScrUnitIds.TERRAN_SCV, ScrUnitIds.ZERG_DRONE, ScrUnitIds.PROTOSS_PROBE}) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(1);
            game.getSpriteSystem().initializeSpritePool(1);
            game.getSpriteSystem().initializeImagePool(WORKER_IMAGE_CAPACITY);
            game.initializeExecutionScope();

            UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
            UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
            UnitCreationContext creationInputs = new UnitCreationContext(false, false, false, false, false, 0);

            Unit unit = storageConstructor.create(creationInputs, accounting, unitType, CREATION_X, CREATION_Y, OWNER, 0, INSTRUCTION_LIMIT);

            Assertions.assertNotNull(unit);

            game.getUnitCompletion().initializeBeforePlacement(new UnitCreationContext(false, false, false, false, true, OWNER), unit,
                                                               INSTRUCTION_LIMIT);

            Assertions.assertNotNull(gameData);
            Assertions.assertEquals(gameData.getUnits().get("Hit Points", unitType), unit.getHitPoints());
            Assertions.assertEquals(gameData.getUnits().get("Shield Amount", unitType) << SHIELD_FRACTION_BITS, unit.getShieldPoints());
            Assertions.assertEquals(0, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(REMAINING_BUILD_TIME_OFFSET));
            Assertions.assertEquals(unitType, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(CURRENT_BUTTON_SET_OFFSET));
            Assertions.assertEquals(0, unit.getStatus() & COMPLETED_STATUS);

            Sprite sprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(sprite);
            Assertions.assertEquals(SPRITE_HIDDEN_FLAG, sprite.getFlags() & SPRITE_HIDDEN_FLAG);
            Assertions.assertFalse(game.hasInitializedWorldState());
        }
    }

    @Test
    void matches_native_update_completed_cases() throws Exception {
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_COMPLETION_ACCOUNTING, ACCOUNTING_DIGEST);

        try (InputStream capture = new ByteArrayInputStream(fixture)) {
            ByteBuffer snapshot = ByteBuffer.allocate(UnitAccountingAssert.CONTEXT_BYTE_COUNT + STATISTICS_BYTE_COUNT).order(ByteOrder.LITTLE_ENDIAN);
            int[] initialCounters = new int[UnitAccountingAssert.CONTEXT_BYTE_COUNT / Integer.BYTES];
            int[] initialSamples = new int[ProductionStatistics.VALUE_COUNT];
            float[] initialAccumulators = new float[ProductionStatistics.VALUE_COUNT];

            Arrays.fill(initialCounters, FILL_DWORD);
            Arrays.fill(initialSamples, FILL_SAMPLE);
            Arrays.fill(initialAccumulators, FILL_ACCUMULATOR);

            int caseCount = 0;

            for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
                for (int status : ACCOUNTING_STATUSES) {
                    for (int owner : ACCOUNTING_OWNERS) {
                        for (int change : ACCOUNTING_CHANGES) {
                            for (boolean countProduction : new boolean[]{false, true}) {
                                UnitAccounting accounting = UnitAccountingAssert.fromCapture(initialCounters);
                                ProductionStatistics statistics = new ProductionStatistics(true, initialSamples, initialAccumulators);
                                Unit unit = UnitTestUtils.createUnit(1);

                                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);
                                UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, owner);

                                Assertions.assertNotNull(gameData);

                                accounting.updateCompleted(unit, gameData.getUnitTypes(), change, countProduction, statistics);

                                Assertions.assertEquals(snapshot.capacity(), capture.readNBytes(snapshot.array(), 0, snapshot.capacity()));

                                UnitAccountingAssert.assertKnownCounters(snapshot, accounting);

                                for (int index = 0; index < STATISTIC_COUNT; index++) {
                                    Assertions.assertEquals(snapshot.getInt(UnitAccountingAssert.CONTEXT_BYTE_COUNT + index * Integer.BYTES),
                                                            statistics.getSampleValue(index));
                                }

                                for (int index = 0; index < STATISTIC_COUNT; index++) {
                                    Assertions.assertEquals(snapshot.getInt(UnitAccountingAssert.CONTEXT_BYTE_COUNT + ACCUMULATORS_OFFSET +
                                                                            index * Integer.BYTES),
                                                            Float.floatToRawIntBits(statistics.getAccumulatedValue(index)));
                                }

                                caseCount++;
                            }
                        }
                    }
                }
            }

            Assertions.assertEquals(-1, capture.read());
            Assertions.assertEquals(ACCOUNTING_CASE_COUNT, caseCount);
        }
    }

    @Test
    void matches_native_complete_after_placement_cases() throws Exception {
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_FINAL_COMPLETION, FINAL_COMPLETION_DIGEST);

        try (InputStream capture = new ByteArrayInputStream(fixture)) {
            ByteBuffer counters = ByteBuffer.allocate(UnitAccountingAssert.CONTEXT_BYTE_COUNT).order(ByteOrder.LITTLE_ENDIAN);
            int caseCount = 0;

            for (int unitType : new int[]{
                    ScrUnitIds.TERRAN_COMMAND_CENTER,
                    ScrUnitIds.ZERG_HATCHERY,
                    ScrUnitIds.PROTOSS_NEXUS,
                    ScrUnitIds.RESOURCE_MINERAL_FIELD,
                    ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_2,
                    ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3,
                    ScrUnitIds.RESOURCE_VESPENE_GEYSER
            }) {
                for (int playerType : PLAYER_TYPES) {
                    for (int status : FINAL_STATUSES) {
                        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

                        game.initializeOrderPool(FINAL_ORDER_CAPACITY);

                        int[] initialCounters = new int[UnitAccountingAssert.CONTEXT_BYTE_COUNT / Integer.BYTES];

                        Arrays.fill(initialCounters, FILL_DWORD);

                        UnitAccounting accounting = UnitAccountingAssert.fromCapture(initialCounters);

                        game.getPlayerTable().setUnitAccounting(accounting);

                        Assertions.assertNotNull(gameData);

                        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));

                        Unit unit = UnitTestUtils.createUnit(1);

                        for (int offset = 0; offset < UnitMemoryCheck.SIZE; offset += Integer.BYTES) {
                            UnitMemoryScenario.of(unit).writeMemoryInteger(offset, FILL_DWORD);
                        }

                        for (int offset : CLEARED_REFERENCE_OFFSETS) {
                            UnitMemoryScenario.of(unit).clearMemoryBytes(offset, REFERENCE_WIDTH);
                        }

                        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                        UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);
                        UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, NEUTRAL_OWNER);
                        UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, NOTHING_ORDER);
                        UnitMemoryScenario.of(unit).writeMemoryByte(HIGHLIGHTED_ORDER_COUNT_OFFSET, 0);

                        Sprite sprite = SpriteScenario.createSprite(0, 0, 0, 0);
                        SpriteImage image = SpriteScenario.createImage(0, 0, 0);

                        image.setAnimationType(BUILT_ANIMATION);
                        sprite.setMainImage(image);
                        sprite.setFlags(SPRITE_FLAGS_SAMPLE);
                        unit.setSprite(sprite);

                        game.getUnitCompletion().completeAfterPlacement(new UnitCreationContext(false, false, false, false, false, 0),
                                                                        unit,
                                                                        playerType,
                                                                        COMPLETION_INSTRUCTION_LIMIT);

                        UnitDiagnosticAssert.assertDomainEquals(capture.readNBytes(UnitMemoryCheck.SIZE), unit);

                        Assertions.assertEquals(counters.capacity(), capture.readNBytes(counters.array(), 0, counters.capacity()));

                        UnitAccountingAssert.assertKnownCounters(counters, accounting);

                        Assertions.assertEquals(capture.read(), sprite.getFlags());

                        OrderPool orderPool = game.getOrderPool();

                        Assertions.assertNotNull(orderPool);
                        Assertions.assertEquals(0, orderPool.getAllocatedCount());

                        caseCount++;
                    }
                }
            }

            Assertions.assertEquals(-1, capture.read());
            Assertions.assertEquals(FINAL_CASE_COUNT, caseCount);
        }
    }

    @Test
    void constructed_buildings_complete_with_accounting_orders_and_strength() throws IOException {
        for (int unitType : new int[]{
                ScrUnitIds.TERRAN_COMMAND_CENTER,
                ScrUnitIds.ZERG_HATCHERY,
                ScrUnitIds.PROTOSS_NEXUS,
                ScrUnitIds.RESOURCE_MINERAL_FIELD,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_2,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3,
                ScrUnitIds.RESOURCE_VESPENE_GEYSER
        }) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]),
                                    new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(1);
            game.getSpriteSystem().initializeSpritePool(1);
            game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);
            game.initializeOrderPool(ORDER_CAPACITY);
            game.initializeExecutionScope();
            game.initializeUnitSpatialIndex();

            UnitPool registryPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(registryPool);

            game.getUnitSystem().setUnitRegistry(new UnitRegistry(registryPool));

            Assertions.assertNotNull(gameData);

            game.setFoliage(gameData.createFoliage(MAP_DIMENSION, 0));

            UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

            accounting.initializeForGame(null);
            game.getPlayerTable().setUnitAccounting(accounting);
            game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));

            UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
            UnitCreationContext creationInputs = new UnitCreationContext(false, true, true, false, false, 0);

            Unit unit = storageConstructor.create(creationInputs, accounting, unitType, BUILDING_X, BUILDING_Y, NEUTRAL_OWNER, 0,
                                                  COMPLETION_INSTRUCTION_LIMIT);

            Assertions.assertNotNull(unit);

            game.getUnitCompletion().initializeBeforePlacement(new UnitCreationContext(false, true, false, false, true, 0), unit,
                                                               COMPLETION_INSTRUCTION_LIMIT);

            Assertions.assertEquals(gameData.getUnits().get("Hit Points", unitType), unit.getHitPoints());
            Assertions.assertEquals(gameData.getUnits().get("Shield Amount", unitType) << SHIELD_FRACTION_BITS, unit.getShieldPoints());
            Assertions.assertEquals(0, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(REMAINING_BUILD_TIME_OFFSET));
            Assertions.assertEquals(unitType, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(CURRENT_BUTTON_SET_OFFSET));
            Assertions.assertEquals(0, unit.getStatus() & COMPLETED_STATUS);
            Assertions.assertFalse(game.hasInitializedWorldState());

            int randomCountBeforeCompletion = game.getRandom().getTotalRandomCounts();

            game.getUnitCompletion().completeAfterPlacement(new UnitCreationContext(false, false, false, false, false, 0), unit, 0,
                                                            COMPLETION_INSTRUCTION_LIMIT);

            Assertions.assertEquals(COMPLETED_STATUS, unit.getStatus() & COMPLETED_STATUS);
            Assertions.assertEquals(1, UnitAccountingAssert.readCounter(accounting, getCompletedCounterOffset(unitType, NEUTRAL_OWNER)));
            Assertions.assertEquals(gameData.getUnits().get("Human AI Idle", unitType),
                                    UnitMemoryScenario.of(unit).readMemoryUnsignedByte(PRIMARY_ORDER_OFFSET));
            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertNull(unit.getLastOrder());

            OrderPool orderPool = game.getOrderPool();

            Assertions.assertNotNull(orderPool);
            Assertions.assertEquals(0, orderPool.getAllocatedCount());
            Assertions.assertEquals(randomCountBeforeCompletion, game.getRandom().getTotalRandomCounts());

            UnitStrengthTable strengthTable = game.getUnitStrengthTable();

            Assertions.assertNotNull(strengthTable);
            Assertions.assertEquals(strengthTable.calculate(unit, 0, gameData.getUnitTypes()) & WORD_MASK,
                                    UnitMemoryScenario.of(unit).readMemoryUnsignedWord(AIR_STRENGTH_OFFSET));
            Assertions.assertEquals(strengthTable.calculate(unit, 1, gameData.getUnitTypes()) & WORD_MASK,
                                    UnitMemoryScenario.of(unit).readMemoryUnsignedWord(GROUND_STRENGTH_OFFSET));
            Assertions.assertFalse(game.hasInitializedWorldState());
        }
    }

    @Test
    void try_complete_should_complete_neutral_mineral_fields() throws IOException {
        for (int unitType : new int[]{
                ScrUnitIds.RESOURCE_MINERAL_FIELD,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_2,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3
        }) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]),
                                    new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(1);
            game.getSpriteSystem().initializeSpritePool(1);
            game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);
            game.initializeOrderPool(ORDER_CAPACITY);
            game.initializeExecutionScope();
            game.initializeUnitSpatialIndex();

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            game.getUnitSystem().setUnitRegistry(new UnitRegistry(pool));

            Assertions.assertNotNull(gameData);

            game.setFoliage(gameData.createFoliage(MAP_DIMENSION, 0));

            UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

            accounting.initializeForGame(null);
            game.getPlayerTable().setUnitAccounting(accounting);
            game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));

            UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
            UnitCreationContext creationInputs = new UnitCreationContext(false, true, true, false, false, 0);

            Unit unit = storageConstructor.create(creationInputs, accounting, unitType, BUILDING_X, BUILDING_Y, NEUTRAL_OWNER, 0,
                                                  COMPLETION_INSTRUCTION_LIMIT);

            Assertions.assertNotNull(unit);
            Assertions.assertTrue(game.getUnitStorageConstructor().tryComplete(new UnitCreationContext(false, true, true, false, false, 0), unit, 0,
                                                                               COMPLETION_INSTRUCTION_LIMIT));
            Assertions.assertEquals(COMPLETED_STATUS, unit.getStatus() & COMPLETED_STATUS);
            Assertions.assertEquals(gameData.getUnits().get("Hit Points", unitType), unit.getHitPoints());
            Assertions.assertEquals(gameData.getUnits().get("Human AI Idle", unitType),
                                    UnitMemoryScenario.of(unit).readMemoryUnsignedByte(PRIMARY_ORDER_OFFSET));
            Assertions.assertEquals(1, UnitAccountingAssert.readCounter(accounting, getCompletedCounterOffset(unitType, NEUTRAL_OWNER)));
            Assertions.assertEquals(1, pool.getAllocatedCount());
            Assertions.assertEquals(1, pool.getGeneration(1));
            Assertions.assertEquals(BUILDING_X, unit.getPositionX());
            Assertions.assertEquals(BUILDING_Y, unit.getPositionY());
            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertNull(game.getGameContext().getLastErrorBytes());
            Assertions.assertFalse(game.hasInitializedWorldState());
        }
    }

    @Test
    void try_complete_should_place_workers_reveal_their_sight_and_move_them_to_completed_list() throws IOException {
        Assertions.assertNotNull(gameData);

        TerrainTileset terrain = gameData.getTerrainTileset(0);
        int walkableTile = -1;

        for (int tileIdentifier = 0; tileIdentifier < terrain.getGroupCount() * MINIATURE_TILES_PER_TILE; tileIdentifier++) {
            boolean walkable = true;

            for (int miniatureIndex = 0; miniatureIndex < MINIATURE_TILES_PER_TILE; miniatureIndex++) {
                walkable &= (terrain.getMiniatureFlags(tileIdentifier, miniatureIndex) & WALKABLE_MINIATURE_FLAG) != 0;
            }

            if (walkable) {
                walkableTile = tileIdentifier;

                break;
            }
        }

        Assertions.assertTrue(walkableTile >= 0);

        for (int unitType : new int[]{ScrUnitIds.TERRAN_SCV, ScrUnitIds.ZERG_DRONE, ScrUnitIds.PROTOSS_PROBE}) {
            int[] tileIdentifiers = new int[MAP_TILE_COUNT];

            Arrays.fill(tileIdentifiers, walkableTile);

            int[] flags = terrain.expandTileFlags(tileIdentifiers);

            for (int index = 0; index < flags.length; index++) {
                flags[index] |= VISIBILITY_FLAGS;
            }

            GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, tileIdentifiers, flags);

            GameMapScenario.setOverlays(map, new int[MAP_TILE_COUNT]);

            Game game = Game.create(gameData, map, new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(1);
            game.getSpriteSystem().initializeSpritePool(1);
            game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);
            game.initializeOrderPool(ORDER_CAPACITY);
            game.initializeExecutionScope();
            game.initializeUnitSpatialIndex();

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            game.getUnitSystem().setUnitRegistry(new UnitRegistry(pool));
            game.setFoliage(gameData.createFoliage(MAP_DIMENSION, 0));
            game.getPathSystem().setTerrainContours(TerrainContours.create(map, terrain));
            game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));
            game.getPlayerTable().setProductionStatistics(new ProductionStatistics(false,
                                                                                   new int[ProductionStatistics.VALUE_COUNT],
                                                                                   new float[ProductionStatistics.VALUE_COUNT]));

            SightCoverage fog = new SightCoverage(MAP_DIMENSION);
            int[] sharing = new int[PlayerTable.PLAYER_SLOTS];

            for (int playerIndex = 0; playerIndex < sharing.length; playerIndex++) {
                sharing[playerIndex] = 1 << playerIndex;
            }

            fog.setSharedVisionMasks(sharing);
            game.setSightCoverage(fog);
            game.getLocalVision().setComparisonMask(1);

            UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

            accounting.initializeForGame(null);
            game.getPlayerTable().setUnitAccounting(accounting);

            UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
            UnitCreationContext creationInputs = new UnitCreationContext(false, true, true, false, false, 0);

            Unit unit = storageConstructor.create(creationInputs, accounting, unitType, BUILDING_X, BUILDING_Y, 0, 0, COMPLETION_INSTRUCTION_LIMIT);

            Assertions.assertNotNull(unit);
            Assertions.assertEquals(1, pool.getFirstCreatedIndex(false));

            UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

            Assertions.assertNotNull(spatialIndex);
            Assertions.assertEquals(0, spatialIndex.getCount());

            Sprite sprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(sprite);
            Assertions.assertEquals(SPRITE_HIDDEN_FLAG, sprite.getFlags() & SPRITE_HIDDEN_FLAG);
            Assertions.assertTrue(game.getUnitStorageConstructor().tryComplete(new UnitCreationContext(false, true, true, false, true, 0), unit, 0,
                                                                               COMPLETION_INSTRUCTION_LIMIT));
            Assertions.assertEquals(0, pool.getFirstCreatedIndex(false));
            Assertions.assertEquals(0, pool.getLastCreatedIndex(false));
            Assertions.assertEquals(1, pool.getFirstCreatedIndex(true));
            Assertions.assertEquals(1, pool.getLastCreatedIndex(true));
            Assertions.assertEquals(EDGES_PER_UNIT, spatialIndex.getCount());

            Sprite completedSprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(completedSprite);
            Assertions.assertEquals(0, completedSprite.getFlags() & SPRITE_HIDDEN_FLAG);
            Assertions.assertEquals(FIRST_PLAYER_SPRITE_VISIBILITY, completedSprite.getVisibilityFlags());
            Assertions.assertEquals(0, map.getTileFlags(WORKER_TILE_X, WORKER_TILE_Y) & FIRST_PLAYER_VISIBILITY_FLAGS);
            Assertions.assertEquals(COMPLETED_STATUS, unit.getStatus() & COMPLETED_STATUS);
            Assertions.assertEquals(GROUND_ELEVATION_PATH_FLAG,
                                    UnitMemoryScenario.of(unit).readMemoryUnsignedByte(PATH_COLLISION_FLAGS_OFFSET) & GROUND_ELEVATION_PATH_FLAG);
            Assertions.assertEquals(gameData.getUnits().get("Hit Points", unitType), unit.getHitPoints());
            Assertions.assertEquals(gameData.getUnits().get("Human AI Idle", unitType),
                                    UnitMemoryScenario.of(unit).readMemoryUnsignedByte(PRIMARY_ORDER_OFFSET));
            Assertions.assertEquals(1, UnitAccountingAssert.readCounter(accounting, getCompletedCounterOffset(unitType, 0)));
            Assertions.assertFalse(game.hasInitializedWorldState());
        }
    }
}
