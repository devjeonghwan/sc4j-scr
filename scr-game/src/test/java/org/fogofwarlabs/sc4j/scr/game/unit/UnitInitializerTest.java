package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
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
import java.util.HexFormat;

/**
 * Verifies unit initialization: the marine setup without a committed pool slot, the role state reset, the walking speed recalculation, the motion
 * fields, the native maximum energy and construction increment cases and the worker sprite at the requested position and owner.
 */
@RequiresInstallation
final class UnitInitializerTest {
    /**
     * The horizontal and vertical position of the unit that the role state test initializes.
     */
    private static final int POSITION = 100;
    /**
     * The interpreter instruction limit of the role state test.
     */
    private static final int INSTRUCTION_LIMIT = 512;
    /**
     * The Spider Mine count a previous unit type left.
     */
    private static final int PREVIOUS_SPIDER_MINE_COUNT = 3;
    /**
     * The nonzero gathering byte and silo ready DWORD a previous unit type left.
     */
    private static final int PREVIOUS_ROLE_VALUE = 1;

    private static final int MAP_DIMENSION = 64;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int IMAGE_CAPACITY = 2;
    /**
     * The image capacity of the worker initialization test.
     */
    private static final int WORKER_IMAGE_CAPACITY = 8;
    private static final int CREATION_X = 321;
    private static final int CREATION_Y = 654;
    private static final int OWNER = 3;
    private static final int FIXED_POINT_BITS = 8;
    private static final int MARINE_TOP_SPEED = 1024;
    private static final int MARINE_ENERGY = 12800;
    private static final int MARINE_INITIAL_ORDER = 188;
    private static final int MARINE_IMAGE_WAIT = 61;

    private static final int MOVEMENT_DESTINATION_X_OFFSET = 0x20;
    private static final int MOVEMENT_DESTINATION_Y_OFFSET = 0x22;
    private static final int MOVEMENT_WAYPOINT_OFFSET = 0x30;
    private static final int MOVEMENT_CONTROL_OFFSET = 0x3F;
    private static final int FIXED_POSITION_X_OFFSET = 0x44;
    private static final int FIXED_POSITION_Y_OFFSET = 0x48;
    private static final int CURRENT_SPEED_OFFSET = 0x50;
    private static final int VELOCITY_X_OFFSET = 0x58;
    private static final int MOVEMENT_DIRECTION_OFFSET = 0x62;
    private static final int OWNER_OFFSET = 0x68;
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int ENERGY_OFFSET = 0xE6;
    private static final int STATUS_OFFSET = 0x140;
    private static final int SPEED_INCREASE_OFFSET = 0x1A1;
    private static final int SPEED_DECREASE_OFFSET = 0x1A2;

    /**
     * The speed increase, speed decrease and status DWORD of each walking speed case.
     */
    private static final int @NotNull [] @NotNull [] SPEED_MODIFIERS = {
            {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0}, {0, 0, 0x10000000}, {1, 1, 0x10000000}, {0, 1, 0x10000000}
    };
    private static final int @NotNull [] ORDINARY_SPEEDS = {1024, 3072, 384, 1024, 3072, 3072, 1024};
    private static final int @NotNull [] SPECIAL_SPEEDS = {1024, 3414, 384, 1024, 3414, 3414, 1024};
    /**
     * The first unit type whose upgraded walking speed uses the special speed table.
     */
    private static final int FIRST_SPECIAL_SPEED_TYPE = 70;
    private static final int WALKING_MOVEMENT_CONTROL = 2;
    private static final int SPRITE_INSTRUCTION_LIMIT = 32;

    private static final int LAST_WORD = 65535;
    private static final int SIGN_WORD = 32768;
    private static final int WAYPOINT_SAMPLE = 456;
    private static final int CURRENT_SPEED_SAMPLE = 789;
    private static final int VELOCITY_SAMPLE = -123;
    private static final int DIRECTION_SAMPLE = 254;
    private static final int MOTION_DIRECTION = 511;
    private static final int EXPECTED_FIXED_POSITION_X = -256;
    private static final int EXPECTED_FIXED_POSITION_Y = -8388608;
    private static final int EXPECTED_HEADING = 255;
    private static final int PACKED_WAYPOINT = 0x00020001;

    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    private static final @NotNull String CONSTRUCTION_DIGEST = "6bb4e9931b02f89bf52ffc03d0e346721263ee099364448d534f0d2ad8448fb2";
    private static final @NotNull String ENERGY_DIGEST = "a66cc98e2f19b9fe4fbde64a0a418558ea225468f178296169497e2b29b9921e";
    private static final int @NotNull [] UPGRADE_LEVELS = {0, 1, 255};
    private static final int PLAYER_COUNT = 12;
    private static final int UPGRADE_COUNT = 61;
    private static final int @NotNull [] CONSTRUCTION_STATUSES = {0, 2, -1};
    private static final int FILL_DWORD = 0xA5A5A5A5;

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
    void initialize_should_set_up_a_marine_without_committing_its_pool_slot() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);
        game.getSpriteSystem().initializeSpritePool(1);
        game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, ScrUnitIds.TERRAN_MARINE, CREATION_X, CREATION_Y, OWNER, INSTRUCTION_LIMIT));
        Assertions.assertEquals(0, unit.getUnitId());
        Assertions.assertEquals(OWNER, unit.getOwnerIndex());
        Assertions.assertEquals(CREATION_X, unit.getPositionX());
        Assertions.assertEquals(CREATION_Y, unit.getPositionY());
        Assertions.assertEquals(CREATION_X << FIXED_POINT_BITS, UnitMemoryScenario.of(unit).readMemoryInteger(FIXED_POSITION_X_OFFSET));
        Assertions.assertEquals(CREATION_Y << FIXED_POINT_BITS, UnitMemoryScenario.of(unit).readMemoryInteger(FIXED_POSITION_Y_OFFSET));
        Assertions.assertEquals(MARINE_TOP_SPEED, unit.getTopSpeed());
        Assertions.assertEquals(1, unit.getHitPoints());
        Assertions.assertEquals(MARINE_ENERGY, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(ENERGY_OFFSET));
        Assertions.assertEquals(MARINE_INITIAL_ORDER, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(PRIMARY_ORDER_OFFSET));

        SpritePool spritePool = game.getSpriteSystem().getSpritePool();

        Assertions.assertNotNull(spritePool);
        Assertions.assertEquals(1, spritePool.getRegistrationValue(0));
        Assertions.assertEquals(0, unitPool.getAllocatedCount());
        Assertions.assertEquals(0, unitPool.getGeneration(1));
        Assertions.assertEquals(1, game.getRandom().getTotalRandomCounts());

        Sprite mainImageSprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(mainImageSprite);

        SpriteImage initializedImage = mainImageSprite.getMainImage();

        Assertions.assertNotNull(initializedImage);
        Assertions.assertEquals(MARINE_IMAGE_WAIT, initializedImage.getWait());
        Assertions.assertFalse(game.hasInitializedWorldState());
    }

    @Test
    void initialize_should_reset_the_role_state_and_keep_the_resource_and_link_state() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);
        game.getSpriteSystem().initializeSpritePool(1);
        game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);
        Unit linked = UnitTestUtils.createUnit(2);
        int marineType = ScrUnitIds.TERRAN_MARINE;

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);
        unit.setSpiderMineCount(PREVIOUS_SPIDER_MINE_COUNT);
        unit.setCarriedPowerup(linked);
        unit.setTargetResource(linked, POSITION, POSITION);
        unit.setHangarParent(linked);
        unit.setAddonBuildType(ScrUnitIds.TERRAN_COMSAT_STATION);
        unit.setResourceBeingGatheredByte(PREVIOUS_ROLE_VALUE);
        unit.setFirstGatheringWorker(linked);
        unit.setGatherTarget(linked);
        unit.setPowerupCarrier(linked);
        unit.setSiloNuke(linked);
        unit.setSiloReadyDword(PREVIOUS_ROLE_VALUE);
        unit.setNydusExit(linked);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, marineType, POSITION, POSITION, 0, INSTRUCTION_LIMIT));

        Assertions.assertEquals(0, unit.getSpiderMineCount());
        Assertions.assertNull(unit.getCarriedPowerup());
        Assertions.assertNull(unit.getTargetResourceUnit());
        Assertions.assertNull(unit.getHangarParent());
        Assertions.assertEquals(0, unit.getAddonBuildType());

        Assertions.assertEquals(PREVIOUS_ROLE_VALUE, unit.getResourceBeingGatheredByte());
        Assertions.assertSame(linked, unit.getFirstGatheringWorker());
        Assertions.assertSame(linked, unit.getGatherTarget());
        Assertions.assertSame(linked, unit.getPowerupCarrier());
        Assertions.assertSame(linked, unit.getSiloNuke());
        Assertions.assertEquals(PREVIOUS_ROLE_VALUE, unit.getSiloReadyDword());
        Assertions.assertSame(linked, unit.getNydusExit());
    }

    @Test
    void recalculate_walking_speed_should_match_native_speeds_and_keep_the_animation() {
        for (int unitType : new int[]{
                ScrUnitIds.TERRAN_MARINE,
                ScrUnitIds.TERRAN_SCV,
                ScrUnitIds.PROTOSS_SCOUT,
                ScrUnitIds.HERO_MOJO,
                ScrUnitIds.HERO_ARTANIS
        }) {
            for (int modifierIndex = 0; modifierIndex < SPEED_MODIFIERS.length; modifierIndex++) {
                Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

                game.getSpriteSystem().initializeSpritePool(1);
                game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);

                Unit unit = UnitTestUtils.createUnit(1);

                Assertions.assertNotNull(gameData);

                int spriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", 0));
                Sprite sprite = game.getIscriptExecutor().createSprite(spriteType, CREATION_X, CREATION_Y, OWNER, unit, SPRITE_INSTRUCTION_LIMIT);

                Assertions.assertNotNull(sprite);

                unit.setSprite(sprite);
                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_CONTROL_OFFSET, WALKING_MOVEMENT_CONTROL);
                UnitMemoryScenario.of(unit).writeMemoryByte(SPEED_INCREASE_OFFSET, SPEED_MODIFIERS[modifierIndex][0]);
                UnitMemoryScenario.of(unit).writeMemoryByte(SPEED_DECREASE_OFFSET, SPEED_MODIFIERS[modifierIndex][1]);
                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, SPEED_MODIFIERS[modifierIndex][2]);

                SpriteImage imageBeforeSpeedChange = sprite.getMainImage();

                Assertions.assertNotNull(imageBeforeSpeedChange);

                int programCounter = imageBeforeSpeedChange.getProgramCounter();
                int wait = imageBeforeSpeedChange.getWait();
                int randomState = game.getRandom().getState();

                game.getUnitInitializer().recalculateWalkingSpeed(unit, INSTRUCTION_LIMIT);

                Assertions.assertEquals((unitType < FIRST_SPECIAL_SPEED_TYPE ? ORDINARY_SPEEDS : SPECIAL_SPEEDS)[modifierIndex], unit.getTopSpeed());

                SpriteImage imageAfterSpeedChange = sprite.getMainImage();

                Assertions.assertNotNull(imageAfterSpeedChange);
                Assertions.assertEquals(programCounter, imageAfterSpeedChange.getProgramCounter());
                Assertions.assertEquals(wait, imageAfterSpeedChange.getWait());
                Assertions.assertEquals(randomState, game.getRandom().getState());
                Assertions.assertEquals(1, game.getRandom().getTotalRandomCounts());
                Assertions.assertEquals(0, unit.getNextSpeed());
                Assertions.assertEquals(0, unit.getCurrentSpeed());
            }
        }
    }

    @Test
    void initialize_motion_fields_should_keep_the_fields_it_does_not_overwrite() {
        Unit unit = UnitTestUtils.createUnit(1);

        UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_X_OFFSET, LAST_WORD);
        UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_Y_OFFSET, SIGN_WORD);

        Unit target = UnitTestUtils.createUnit(2);

        unit.setMovementTargetUnit(target);
        UnitMemoryScenario.of(unit).writeMemoryInteger(MOVEMENT_WAYPOINT_OFFSET, WAYPOINT_SAMPLE);
        UnitMemoryScenario.of(unit).writeMemoryInteger(CURRENT_SPEED_OFFSET, CURRENT_SPEED_SAMPLE);
        UnitMemoryScenario.of(unit).writeMemoryInteger(VELOCITY_X_OFFSET, VELOCITY_SAMPLE);
        UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_DIRECTION_OFFSET, DIRECTION_SAMPLE);

        Assertions.assertNotNull(gameData);

        int flingyType = gameData.getUnits().get("Graphics", ScrUnitIds.TERRAN_SCV);

        UnitInitializer.initializeMotionFields(unit, gameData.getFlingyTypes(), flingyType, LAST_WORD, SIGN_WORD, MOTION_DIRECTION);

        Assertions.assertEquals(EXPECTED_FIXED_POSITION_X, UnitMemoryScenario.of(unit).readMemoryInteger(FIXED_POSITION_X_OFFSET));
        Assertions.assertEquals(EXPECTED_FIXED_POSITION_Y, UnitMemoryScenario.of(unit).readMemoryInteger(FIXED_POSITION_Y_OFFSET));
        Assertions.assertEquals(0, unit.getMovementFlags());
        Assertions.assertSame(target, unit.getMovementTargetUnit());
        Assertions.assertEquals(WAYPOINT_SAMPLE, UnitMemoryScenario.of(unit).readMemoryInteger(MOVEMENT_WAYPOINT_OFFSET));
        Assertions.assertEquals(CURRENT_SPEED_SAMPLE, unit.getCurrentSpeed());
        Assertions.assertEquals(VELOCITY_SAMPLE, unit.getVelocityX());
        Assertions.assertEquals(DIRECTION_SAMPLE, unit.getMovementDirection());
        Assertions.assertEquals(EXPECTED_HEADING, unit.getHeading());

        UnitInitializer.initializeMotionFields(unit, gameData.getFlingyTypes(), flingyType, 1, 2, 0);

        Assertions.assertEquals(1, unit.getMovementFlags());
        Assertions.assertNull(unit.getMovementTargetUnit());
        Assertions.assertEquals(PACKED_WAYPOINT, UnitMemoryScenario.of(unit).readMemoryInteger(MOVEMENT_WAYPOINT_OFFSET));
    }

    @Test
    void matches_native_maximum_energy_and_construction_increment_cases() throws Exception {
        MessageDigest energyDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_CONSTRUCTION_SCALARS);

        Assertions.assertEquals(CONSTRUCTION_DIGEST, HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(fixture)));

        ByteBuffer expected = ByteBuffer.wrap(fixture).order(ByteOrder.LITTLE_ENDIAN);
        byte[] expectedUnit = new byte[UnitMemoryCheck.SIZE];
        Unit unit = UnitTestUtils.createUnit(1);
        ByteBuffer value = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int level : UPGRADE_LEVELS) {
            Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
            PlayerUpgrades upgrades = PlayerProgressTestUtils.createPlayerUpgrades(true);

            game.getPlayerTable().setPlayerUpgrades(upgrades);

            for (int playerIndex = 0; playerIndex < PLAYER_COUNT; playerIndex++) {
                for (int upgrade = 0; upgrade < UPGRADE_COUNT; upgrade++) {
                    upgrades.setLevel(playerIndex, upgrade, level);
                }
            }

            for (int playerIndex = 0; playerIndex < PLAYER_COUNT; playerIndex++) {
                for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
                    UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                    UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, playerIndex);

                    Assertions.assertNotNull(gameData);

                    value.putInt(0, game.getUnitInitializer().getMaximumEnergy(unit, gameData.getUnitTypes()));

                    energyDigest.update(value.array());
                }
            }
        }

        for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
            for (int status : CONSTRUCTION_STATUSES) {
                for (int offset = 0; offset < UnitMemoryCheck.SIZE; offset += Integer.BYTES) {
                    UnitMemoryScenario.of(unit).writeMemoryInteger(offset, FILL_DWORD);
                }

                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);

                UnitInitializer.initializeConstructionIncrements(unit, gameData.getUnitTypes());

                expected.get(expectedUnit);

                UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit, "Construction type " + unitType);
            }
        }

        Assertions.assertEquals(ENERGY_DIGEST, HexFormat.of().formatHex(energyDigest.digest()));
        Assertions.assertFalse(expected.hasRemaining());
    }

    @Test
    void initialize_should_create_worker_sprite_at_requested_position_and_owner() {
        for (int unitType : new int[]{ScrUnitIds.TERRAN_SCV, ScrUnitIds.ZERG_DRONE, ScrUnitIds.PROTOSS_PROBE}) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(1);
            game.getSpriteSystem().initializeSpritePool(1);
            game.getSpriteSystem().initializeImagePool(WORKER_IMAGE_CAPACITY);
            game.initializeExecutionScope();

            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit unit = unitPool.getUnit(1);

            UnitStorageConstructor.clearBeforeInnerInitialization(unit);

            Assertions.assertTrue(game.getUnitInitializer().initialize(unit, unitType, CREATION_X, CREATION_Y, OWNER, INSTRUCTION_LIMIT));

            Assertions.assertEquals(unitType, unit.getUnitId());
            Assertions.assertEquals(OWNER, unit.getOwnerIndex());
            Assertions.assertEquals(CREATION_X, unit.getPositionX());
            Assertions.assertEquals(CREATION_Y, unit.getPositionY());

            Sprite sprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(sprite);
            Assertions.assertNotNull(sprite.getMainImage());

            SpritePool spritePool = game.getSpriteSystem().getSpritePool();

            Assertions.assertNotNull(spritePool);

            Assertions.assertEquals(1, spritePool.getAllocatedCount());
            Assertions.assertEquals(0, unitPool.getAllocatedCount());
            Assertions.assertFalse(game.hasInitializedWorldState());

            GameExecutionScope initializationScope = game.getExecutionScope();

            Assertions.assertNotNull(initializationScope);

            initializationScope.finishInitialization();
        }
    }
}
