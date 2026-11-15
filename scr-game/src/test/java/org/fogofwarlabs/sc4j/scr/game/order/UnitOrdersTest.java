package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
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
 * Checks acquisition ranges, guard targeting and primary and timer order updates.
 */
@RequiresInstallation
final class UnitOrdersTest {
    private static final int MAP_DIMENSION = 8;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int INSTRUCTION_LIMIT = 10000;
    /**
     * The classic upgrades; an upgrade identifier from this count on takes effect only with the expansion enabled.
     */
    private static final int CLASSIC_UPGRADE_COUNT = 46;
    /**
     * The acquisition range bonuses of the upgraded unit types of the acquisition range test, in their order.
     */
    private static final int @NotNull [] UPGRADE_RANGE_BONUSES = {1, 3, 3, 1, 2};
    /**
     * The acquisition range bonus of the Fenix Dragoon hero and of the other heroes of the acquisition range test with the expansion enabled.
     */
    private static final int FENIX_DRAGOON_RANGE_BONUS = 2;
    private static final int HERO_RANGE_BONUS = 3;
    /**
     * The capacity and the pool index of the single unit of the acquisition and guard tests.
     */
    private static final int SINGLE_UNIT_CAPACITY = 1;
    private static final int UNIT_INDEX = 1;

    private static final int WORKER_MAP_DIMENSION = 32;
    private static final int WORKER_MAP_TILE_COUNT = WORKER_MAP_DIMENSION * WORKER_MAP_DIMENSION;
    private static final int WORKER_TEST_UNIT_CAPACITY = 4;
    private static final int WORKER_POSITION = 256;
    /**
     * The position next to the worker, inside its acquisition range.
     */
    private static final int NEAR_POSITION = 280;
    /**
     * The position away from the worker, outside its acquisition range.
     */
    private static final int DISTANT_POSITION = 768;
    /**
     * The owner of the enemies of the worker.
     */
    private static final int ENEMY_OWNER = 1;
    /**
     * The visibility flags that show a sprite to the owner of the worker, player {@code 0}.
     */
    private static final int WORKER_OWNER_VISIBILITY = 1;
    /**
     * The pool indices of the worker, of the neutral Mineral Field, of the hidden enemy and of the distant enemy.
     */
    private static final int WORKER_INDEX = 1;
    private static final int NEUTRAL_INDEX = 2;
    private static final int HIDDEN_ENEMY_INDEX = 3;
    private static final int DISTANT_ENEMY_INDEX = 4;
    /**
     * The Player Guard order, which a guarding unit keeps without a target.
     */
    private static final int PLAYER_GUARD_ORDER = 3;
    /**
     * The order timer and the primary order countdown after a guard update without a target.
     */
    private static final int GUARD_ORDER_TIMER = 15;
    private static final int GUARD_ORDER_COUNTDOWN = 8;

    /**
     * The SHA-256 digests of the primary order capture and of the unit order wrapper capture.
     */
    private static final @NotNull String PRIMARY_ORDERS_CAPTURE_HASH = "5cae2a04a228a57faae23680b31ea59ab84c78dac357a072defe24d2e81437cf";
    private static final @NotNull String UNIT_ORDER_WRAPPER_CAPTURE_HASH = "a7d084b60dfddcee9151d9dc0327bf1a4ab229c29787cf821d518c1231e250f1";
    /**
     * The size of the captured unit record.
     */
    private static final int UNIT_RECORD_SIZE = 488;
    /**
     * The captured cases of each order or unit type.
     */
    private static final int CASE_COUNT = 256;
    /**
     * The index of the random caller whose count each captured case compares.
     */
    private static final int RANDOM_COUNT_INDEX = 29;
    /**
     * The random calls of all primary order cases.
     */
    private static final int PRIMARY_ORDER_RANDOM_CALLS = 16;
    /**
     * The pool index of the captured unit.
     */
    private static final int CAPTURE_UNIT_INDEX = 1;

    private static final int HIT_POINTS_OFFSET = 0x10;
    private static final int OWNER_OFFSET = 0x68;
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int COOLDOWN_TIMERS_OFFSET = 0x70;
    private static final int LAST_COOLDOWN_TIMER_OFFSET = 0x73;
    private static final int SHIELD_POINTS_OFFSET = 0x88;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int PRIMARY_ORDER_COUNTDOWN_OFFSET = 0xC9;
    private static final int SPELL_TARGET_FLAGS_OFFSET = 0xD6;
    private static final int SECONDARY_ORDER_OFFSET = 0xEA;
    private static final int STATUS_OFFSET = 0x140;
    private static final int SECONDARY_ORDER_STATE_OFFSET = 0x146;
    private static final int MOVEMENT_DESTINATION_COUNTDOWN_OFFSET = 0x147;
    private static final int SECONDARY_ORDER_POSITION_OFFSET = 0x150;
    private static final int HEALING_STATE_OFFSET = 0x193;
    private static final int REMAINING_LIFETIME_OFFSET = 0x19C;
    private static final int STATUS_CYCLE_ELAPSED_OFFSET = 0x1B2;

    /**
     * The owner of the captured units.
     */
    private static final int CAPTURE_OWNER = 2;
    /**
     * The values that mark captured unit fields before an update.
     */
    private static final int COOLDOWN_TIMERS_MARKER = 0xAA;
    private static final int SPELL_TARGET_FLAGS_MARKER = 0xBB;
    private static final int HEALING_STATE_MARKER = 0x7E;
    private static final int SECONDARY_ORDER_STATE_MARKER = 0xAA;
    /**
     * The unclassified value at the secondary order position that the update keeps.
     */
    private static final int SECONDARY_ORDER_POSITION_MARKER = 0x12345678;
    /**
     * The random state of the first captured case and the random state step between consecutive cases.
     */
    private static final int RANDOM_STATE_BASE = 0x12345678;
    private static final int RANDOM_STATE_STEP = 0x9E3779B9;
    /**
     * The period of the primary order countdown of the captured cases.
     */
    private static final int ORDER_COUNTDOWN_PERIOD = 17;
    /**
     * The player types that the primary order cases cycle through.
     */
    private static final int PLAYER_TYPE_PERIOD = 4;
    /**
     * The case index bits of the alternate unit type or status, of the selected sprite flag and of the hidden sprite flag.
     */
    private static final int ALTERNATE_CASE_BIT = 1;
    private static final int SELECTED_CASE_BIT = 4;
    private static final int HIDDEN_CASE_BIT = 2;
    /**
     * The fraction bits of the shield points.
     */
    private static final int SHIELD_FRACTION_BITS = 8;
    /**
     * The period of the shield offsets of the captured cases.
     */
    private static final int SHIELD_CASE_PERIOD = 5;
    /**
     * The middle shield offset index of the captured cases, which keeps the shield amount.
     */
    private static final int SHIELD_CASE_MIDDLE = 2;
    /**
     * The shield offset between consecutive shield cases.
     */
    private static final int SHIELD_CASE_STEP = 5;
    /**
     * The period of the cases without a remaining lifetime.
     */
    private static final int NO_LIFETIME_PERIOD = 3;
    /**
     * The shortest remaining lifetime and the period of the remaining lifetimes of the other cases.
     */
    private static final int MINIMUM_LIFETIME = 2;
    private static final int LIFETIME_PERIOD = 8;
    /**
     * The step between the odd case factors of consecutive cooldown timer bytes.
     */
    private static final int TIMER_FACTOR_STEP = 2;

    /**
     * The sprite position of the units of the hatchery test and of the captured unit order cases.
     */
    private static final int CAPTURE_SPRITE_POSITION = 64;

    /**
     * The order capacity of the hatchery test.
     */
    private static final int HATCHERY_ORDER_CAPACITY = 32;
    /**
     * The current frame of the hatchery test, the first frame after the initial frame.
     */
    private static final int AFTER_INITIAL_FRAME = 1;
    /**
     * The pool index of the unit that the hatchery builds.
     */
    private static final int BUILD_UNIT_INDEX = 2;
    /**
     * The Spread Creep order, the secondary order that the idle activation of the hatchery sets.
     */
    private static final int SPREAD_CREEP_ORDER = 102;

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
    void get_target_acquisition_range_should_apply_upgrades_expansion_heroes_and_cloaked_ghosts() {
        for (boolean expansionEnabled : new boolean[]{false, true}) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));
            PlayerUpgrades upgrades = PlayerProgressTestUtils.createPlayerUpgrades(expansionEnabled);

            game.getPlayerTable().setPlayerUpgrades(upgrades);
            game.getUnitSystem().initializeUnitPool(SINGLE_UNIT_CAPACITY);

            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit unit = unitPool.getUnit(UNIT_INDEX);

            unit.setOwnerIndex(0);
            unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);

            int[] upgradedTypes = {
                    ScrUnitIds.TERRAN_MARINE,
                    ScrUnitIds.TERRAN_GOLIATH,
                    ScrUnitIds.TERRAN_GOLIATH_TURRET,
                    ScrUnitIds.ZERG_HYDRALISK,
                    ScrUnitIds.PROTOSS_DRAGOON
            };
            int[] upgradeIdentifiers = {
                    ScrUpgradeIds.U_238_SHELLS,
                    ScrUpgradeIds.CHARON_BOOSTERS,
                    ScrUpgradeIds.CHARON_BOOSTERS,
                    ScrUpgradeIds.GROOVED_SPINES,
                    ScrUpgradeIds.SINGULARITY_CHARGE
            };

            for (int typeIndex = 0; typeIndex < upgradedTypes.length; typeIndex++) {
                int unitType = upgradedTypes[typeIndex];

                unit.setUnitId(unitType);
                upgrades.setLevel(0, upgradeIdentifiers[typeIndex], 0);

                Assertions.assertNotNull(gameData);

                int baseRange = gameData.getUnits().get("Target Acquisition Range", unitType);

                Assertions.assertEquals(baseRange, game.getUnitOrders().getTargetAcquisitionRange(unit));

                upgrades.setLevel(0, upgradeIdentifiers[typeIndex], 1);

                int bonus = expansionEnabled || upgradeIdentifiers[typeIndex] < CLASSIC_UPGRADE_COUNT ? UPGRADE_RANGE_BONUSES[typeIndex] : 0;

                Assertions.assertEquals(baseRange + bonus, game.getUnitOrders().getTargetAcquisitionRange(unit));
            }

            for (int unitType : new int[]{
                    ScrUnitIds.HERO_ALAN_SCHEZAR, ScrUnitIds.HERO_ALAN_SCHEZAR_TURRET, ScrUnitIds.HERO_FENIX_DRAGOON
            }) {
                unit.setUnitId(unitType);

                int bonus = expansionEnabled ? unitType == ScrUnitIds.HERO_FENIX_DRAGOON ? FENIX_DRAGOON_RANGE_BONUS : HERO_RANGE_BONUS : 0;

                Assertions.assertEquals(gameData.getUnits().get("Target Acquisition Range", unitType) + bonus,
                                        game.getUnitOrders().getTargetAcquisitionRange(unit));
            }

            for (int unitType : new int[]{
                    ScrUnitIds.TERRAN_GHOST,
                    ScrUnitIds.HERO_SARAH_KERRIGAN,
                    ScrUnitIds.HERO_SAMIR_DURAN,
                    ScrUnitIds.HERO_ALEXEI_STUKOV,
                    ScrUnitIds.HERO_INFESTED_DURAN
            }) {
                unit.setUnitId(unitType);

                for (int status : new int[]{
                        UnitStatusFlags.REQUIRES_DETECTOR, UnitStatusFlags.CLOAKED, UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED
                }) {
                    unit.setStatus(status);
                    unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);

                    Assertions.assertEquals(0, game.getUnitOrders().getTargetAcquisitionRange(unit));

                    unit.setPrimaryOrder(ScrOrderIds.HOLD_POSITION);

                    Assertions.assertEquals(gameData.getUnits().get("Target Acquisition Range", unitType),
                                            game.getUnitOrders().getTargetAcquisitionRange(unit));
                }
            }
        }
    }

    @Test
    void update_primary_should_keep_zero_acquisition_guard_without_enemy_search_or_random() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(SINGLE_UNIT_CAPACITY);

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(UNIT_INDEX);

        for (int unitType : new int[]{
                ScrUnitIds.ZERG_LARVA, ScrUnitIds.ZERG_EGG, ScrUnitIds.ZERG_COCOON, ScrUnitIds.ZERG_LURKER_EGG
        }) {
            unit.setUnitId(unitType);
            unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);
            unit.setPrimaryOrderCountdown(0);
            unit.setCooldownTimers(0);

            Assertions.assertEquals(0, game.getUnitOrders().getTargetAcquisitionRange(unit));

            game.getUnitOrders().updatePrimary(unit, PlayerTypes.RESCUE_PASSIVE, INSTRUCTION_LIMIT);

            Assertions.assertEquals(PLAYER_GUARD_ORDER, unit.getPrimaryOrder());
            Assertions.assertEquals(GUARD_ORDER_TIMER, unit.getCooldownTimers() & Unit.ORDER_TIMER_MASK);
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void worker_guard_acquisition_checks_attack_angle_visibility_and_sprite_owner() {
        for (int workerType : new int[]{ScrUnitIds.TERRAN_SCV, ScrUnitIds.ZERG_DRONE, ScrUnitIds.PROTOSS_PROBE}) {
            Game game = Game.create(gameData, new GameMap(WORKER_MAP_DIMENSION, WORKER_MAP_DIMENSION, 0, new int[WORKER_MAP_TILE_COUNT]),
                                    new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(WORKER_TEST_UNIT_CAPACITY);
            game.initializeUnitSpatialIndex();
            game.getPlayerTable().setPlayerRelations(new PlayerRelations(new int[]{
                    PlayerTypes.HUMAN,
                    PlayerTypes.HUMAN,
                    PlayerTypes.NONE,
                    PlayerTypes.NONE,
                    PlayerTypes.NONE,
                    PlayerTypes.NONE,
                    PlayerTypes.NONE,
                    PlayerTypes.NONE,
                    PlayerTypes.NONE,
                    PlayerTypes.NONE,
                    PlayerTypes.NONE,
                    PlayerTypes.RESCUE_PASSIVE
            }, false, false, false));

            UnitPool workerPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(workerPool);

            Unit worker = workerPool.getUnit(WORKER_INDEX);

            worker.setUnitId(workerType);
            worker.setOwnerIndex(0);
            worker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.CAN_MOVE);
            worker.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);
            worker.setSprite(SpriteScenario.createSprite(0, WORKER_POSITION, WORKER_POSITION, 0));

            UnitPool neutralUnitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(neutralUnitPool);

            Unit neutral = neutralUnitPool.getUnit(NEUTRAL_INDEX);

            neutral.setUnitId(ScrUnitIds.RESOURCE_MINERAL_FIELD);
            neutral.setOwnerIndex(PlayerTable.NEUTRAL_PLAYER_ID);
            neutral.setSprite(SpriteScenario.createSprite(0, NEAR_POSITION, WORKER_POSITION, 0));

            Sprite neutralSprite = neutral.getSpriteOrNull();

            Assertions.assertNotNull(neutralSprite);

            neutralSprite.initializeNativeStorage(0, NEAR_POSITION, WORKER_POSITION, PlayerTable.NEUTRAL_PLAYER_ID, false);

            Sprite visibleNeutralSprite = neutral.getSpriteOrNull();

            Assertions.assertNotNull(visibleNeutralSprite);

            visibleNeutralSprite.setVisibilityFlags(WORKER_OWNER_VISIBILITY);

            UnitPool hiddenEnemyUnitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(hiddenEnemyUnitPool);

            Unit hiddenEnemy = hiddenEnemyUnitPool.getUnit(HIDDEN_ENEMY_INDEX);

            hiddenEnemy.setUnitId(ScrUnitIds.TERRAN_SCV);
            hiddenEnemy.setOwnerIndex(ENEMY_OWNER);
            hiddenEnemy.setSprite(SpriteScenario.createSprite(0, NEAR_POSITION, NEAR_POSITION, 0));

            UnitPool distantEnemyUnitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(distantEnemyUnitPool);

            Unit distantEnemy = distantEnemyUnitPool.getUnit(DISTANT_ENEMY_INDEX);

            distantEnemy.setUnitId(ScrUnitIds.TERRAN_SCV);
            distantEnemy.setOwnerIndex(ENEMY_OWNER);
            distantEnemy.setSprite(SpriteScenario.createSprite(0, DISTANT_POSITION, DISTANT_POSITION, 0));
            distantEnemy.getSpriteOrNull().setVisibilityFlags(WORKER_OWNER_VISIBILITY);

            for (int poolIndex = WORKER_INDEX; poolIndex <= WORKER_TEST_UNIT_CAPACITY; poolIndex++) {
                UnitSpatialIndex insertionIndex = game.getUnitSystem().getUnitSpatialIndex();

                Assertions.assertNotNull(insertionIndex);
                Assertions.assertNotNull(gameData);

                insertionIndex.insert(game.getUnitSystem().getUnitPool().getUnit(poolIndex), gameData.getUnitTypes());
            }

            game.getUnitOrders().updatePrimary(worker, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

            Assertions.assertEquals(GUARD_ORDER_TIMER, worker.getCooldownTimers() & Unit.ORDER_TIMER_MASK);
            Assertions.assertEquals(GUARD_ORDER_COUNTDOWN, worker.getPrimaryOrderCountdown());
            Assertions.assertEquals(PLAYER_GUARD_ORDER, worker.getPrimaryOrder());
            Assertions.assertNull(worker.getOrderTargetUnit());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());

            worker.setPrimaryOrderCountdown(0);
            worker.setCooldownTimers(0);
            hiddenEnemy.getSpriteOrNull().setVisibilityFlags(WORKER_OWNER_VISIBILITY);

            // A worker without the can-turn status only acquires targets inside its weapon attack angle.
            game.getUnitOrders().updatePrimary(worker, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

            Assertions.assertEquals(GUARD_ORDER_TIMER, worker.getCooldownTimers() & Unit.ORDER_TIMER_MASK);
            Assertions.assertEquals(PLAYER_GUARD_ORDER, worker.getPrimaryOrder());
            Assertions.assertNull(worker.getOrderTargetUnit());
            Assertions.assertNull(game.getUnitTargeting().findAcquireTarget(worker));

            worker.setStatus(worker.getStatus() | UnitStatusFlags.CAN_TURN);

            Assertions.assertSame(hiddenEnemy, game.getUnitTargeting().findAcquireTarget(worker));

            hiddenEnemy.getSpriteOrNull().setVisibilityFlags(0);
            neutral.getSpriteOrNull().initializeNativeStorage(0, NEAR_POSITION, WORKER_POSITION, ENEMY_OWNER, false);
            neutral.getSpriteOrNull().setVisibilityFlags(WORKER_OWNER_VISIBILITY);

            // The re-owned neutral is an enemy through its sprite owner, but its hidden sprite makes it unattackable.
            Assertions.assertNull(game.getUnitTargeting().findAcquireTarget(worker));

            Sprite hiddenNeutralSprite = neutral.getSpriteOrNull();

            Assertions.assertNotNull(hiddenNeutralSprite);

            hiddenNeutralSprite.setFlags(neutral.getSpriteOrNull().getFlags() & ~Sprite.FLAG_HIDDEN);

            Assertions.assertSame(neutral, game.getUnitTargeting().findAcquireTarget(worker));
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void matches_native_primary_order_update_cases() throws Exception {
        LcgRandom random = new LcgRandom(0);
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), random);
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_PRIMARY_ORDERS, PRIMARY_ORDERS_CAPTURE_HASH);
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        byte[] expectedUnit = new byte[UNIT_RECORD_SIZE];
        int totalCalls = 0;

        for (int order : new int[]{ScrOrderIds.GUARD,
                                   ScrOrderIds.PLAYER_GUARD,
                                   ScrOrderIds.NOTHING,
                                   ScrOrderIds.PICKUP_IDLE}) {
            for (int caseIndex = 0; caseIndex < CASE_COUNT; caseIndex++) {
                Unit unit = UnitTestUtils.createUnit(CAPTURE_UNIT_INDEX);

                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET,
                                                            (caseIndex & ALTERNATE_CASE_BIT) != 0
                                                            ? ScrUnitIds.HERO_JIM_RAYNOR_VULTURE
                                                            : ScrUnitIds.TERRAN_SCV);
                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, UnitStatusFlags.COMPLETED | UnitStatusFlags.CAN_MOVE);
                UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, CAPTURE_OWNER);
                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, order);
                UnitMemoryScenario.of(unit).writeMemoryByte(COOLDOWN_TIMERS_OFFSET, COOLDOWN_TIMERS_MARKER);
                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_COUNTDOWN_OFFSET, caseIndex % ORDER_COUNTDOWN_PERIOD);
                UnitMemoryScenario.of(unit).writeMemoryByte(SPELL_TARGET_FLAGS_OFFSET, SPELL_TARGET_FLAGS_MARKER);
                random.setState(RANDOM_STATE_BASE + caseIndex * RANDOM_STATE_STEP);

                game.getUnitOrders().updatePrimary(unit, caseIndex % PLAYER_TYPE_PERIOD, INSTRUCTION_LIMIT);

                expected.get(expectedUnit);

                UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit);

                Assertions.assertEquals(expected.getInt(), random.getState());
                Assertions.assertEquals(expected.getInt(), random.getTotalRandomCounts());
                Assertions.assertEquals(expected.getInt(), random.getCallCount(RANDOM_COUNT_INDEX));

                totalCalls += random.getTotalRandomCounts();
            }
        }

        Assertions.assertEquals(PRIMARY_ORDER_RANDOM_CALLS, totalCalls);
        Assertions.assertFalse(expected.hasRemaining());
    }

    @Test
    void update_primary_should_activate_hatchery_idle_and_secondary_order_after_the_initial_frame() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.initializeOrderPool(HATCHERY_ORDER_CAPACITY);
        game.setCurrentFrame(AFTER_INITIAL_FRAME);

        Unit unit = UnitTestUtils.createUnit(CAPTURE_UNIT_INDEX);
        Sprite sprite = SpriteScenario.createSprite(0, CAPTURE_SPRITE_POSITION, CAPTURE_SPRITE_POSITION, 0);
        SpriteImage image = SpriteScenario.createImage(0, 0, 0);

        sprite.insertTailImage(image);
        sprite.setMainImage(image);
        unit.setSprite(sprite);
        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.ZERG_HATCHERY);
        UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET,
                                                       UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING |
                                                       UnitStatusFlags.COLLISION | UnitStatusFlags.GROUND_UNIT);
        UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, ScrOrderIds.INIT_CREEP_GROWTH);
        UnitMemoryScenario.of(unit).writeMemoryByte(SECONDARY_ORDER_OFFSET, ScrOrderIds.NOTHING);
        UnitMemoryScenario.of(unit).writeMemoryInteger(SECONDARY_ORDER_POSITION_OFFSET, SECONDARY_ORDER_POSITION_MARKER);

        Unit buildUnit = UnitTestUtils.createUnit(BUILD_UNIT_INDEX);

        unit.setCurrentBuildUnit(buildUnit);
        UnitMemoryScenario.of(unit).writeMemoryByte(SECONDARY_ORDER_STATE_OFFSET, SECONDARY_ORDER_STATE_MARKER);

        game.getUnitOrders().updatePrimary(unit, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        Assertions.assertNotNull(gameData);

        Assertions.assertEquals(gameData.getUnits().get("Return to Idle", ScrUnitIds.ZERG_HATCHERY),
                                UnitMemoryScenario.of(unit).readMemoryUnsignedByte(PRIMARY_ORDER_OFFSET));
        Assertions.assertEquals(SPREAD_CREEP_ORDER, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(SECONDARY_ORDER_OFFSET));

        // This unclassified diagnostic value is not live secondary-order state.
        Assertions.assertEquals(SECONDARY_ORDER_POSITION_MARKER, UnitMemoryScenario.of(unit).readMemoryInteger(SECONDARY_ORDER_POSITION_OFFSET));
        Assertions.assertFalse(unit.hasCurrentBuildUnit());
        Assertions.assertEquals(0, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(SECONDARY_ORDER_STATE_OFFSET));

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(0, orders.getAllocatedCount());
    }

    @Test
    void matches_native_unit_order_update_cases() throws Exception {
        LcgRandom random = new LcgRandom(0);
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), random);
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_UNIT_ORDER_WRAPPER, UNIT_ORDER_WRAPPER_CAPTURE_HASH);
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        byte[] expectedUnit = new byte[UNIT_RECORD_SIZE];

        for (int unitType : new int[]{
                ScrUnitIds.ZERG_LARVA,
                ScrUnitIds.ZERG_DRONE,
                ScrUnitIds.ZERG_OVERLORD,
                ScrUnitIds.PROTOSS_PROBE,
                ScrUnitIds.ZERG_HATCHERY,
                ScrUnitIds.PROTOSS_NEXUS,
                ScrUnitIds.RESOURCE_MINERAL_FIELD,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_2,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3,
                ScrUnitIds.RESOURCE_VESPENE_GEYSER,
                ScrUnitIds.TERRAN_COMMAND_CENTER,
                ScrUnitIds.TERRAN_SCV,
                ScrUnitIds.ZERG_ZERGLING,
                ScrUnitIds.HERO_DEVOURING_ONE
        }) {
            for (int caseIndex = 0; caseIndex < CASE_COUNT; caseIndex++) {
                Unit unit = UnitTestUtils.createUnit(CAPTURE_UNIT_INDEX);
                Sprite sprite = SpriteScenario.createSprite(0, CAPTURE_SPRITE_POSITION, CAPTURE_SPRITE_POSITION, 0, 1);

                sprite.setFlags(((caseIndex & SELECTED_CASE_BIT) != 0 ? Sprite.FLAG_SELECTED : 0) |
                                ((caseIndex & HIDDEN_CASE_BIT) != 0 ? Sprite.FLAG_HIDDEN : 0));
                unit.setSprite(sprite);
                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET,
                                                               (caseIndex & ALTERNATE_CASE_BIT) != 0
                                                               ? UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING
                                                               : 0);

                UnitMemoryScenario memory = UnitMemoryScenario.of(unit);

                Assertions.assertNotNull(gameData);

                memory.writeMemoryInteger(HIT_POINTS_OFFSET, gameData.getUnits().get("Hit Points", unitType));
                UnitMemoryScenario.of(unit).writeMemoryInteger(SHIELD_POINTS_OFFSET,
                                                               (gameData.getUnits().get("Shield Amount", unitType) << SHIELD_FRACTION_BITS) +
                                                               (caseIndex % SHIELD_CASE_PERIOD - SHIELD_CASE_MIDDLE) * SHIELD_CASE_STEP);

                for (int offset = COOLDOWN_TIMERS_OFFSET; offset <= LAST_COOLDOWN_TIMER_OFFSET; offset++) {
                    UnitMemoryScenario.of(unit).writeMemoryByte(offset, caseIndex * ((offset - COOLDOWN_TIMERS_OFFSET) * TIMER_FACTOR_STEP + 1));
                }

                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_COUNTDOWN_OFFSET, caseIndex % ORDER_COUNTDOWN_PERIOD);
                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET,
                                                            unitType == ScrUnitIds.ZERG_DRONE ||
                                                            unitType == ScrUnitIds.PROTOSS_PROBE ||
                                                            unitType == ScrUnitIds.TERRAN_SCV
                                                            ? ScrOrderIds.GUARD
                                                            : unitType == ScrUnitIds.ZERG_OVERLORD
                                                              ? ScrOrderIds.PICKUP_IDLE
                                                              : ScrOrderIds.NOTHING);
                UnitMemoryScenario.of(unit).writeMemoryByte(SECONDARY_ORDER_OFFSET, ScrOrderIds.NOTHING);
                UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, CAPTURE_OWNER);
                UnitMemoryScenario.of(unit).writeMemoryByte(SPELL_TARGET_FLAGS_OFFSET, SPELL_TARGET_FLAGS_MARKER);
                UnitMemoryScenario.of(unit).writeMemoryByte(HEALING_STATE_OFFSET, HEALING_STATE_MARKER);
                UnitMemoryScenario.of(unit).writeMemoryByte(STATUS_CYCLE_ELAPSED_OFFSET, caseIndex);
                UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_DESTINATION_COUNTDOWN_OFFSET, caseIndex);
                UnitMemoryScenario.of(unit).writeMemoryWord(REMAINING_LIFETIME_OFFSET,
                                                            caseIndex % NO_LIFETIME_PERIOD == 0
                                                            ? 0
                                                            : MINIMUM_LIFETIME + caseIndex % LIFETIME_PERIOD);
                random.setState(RANDOM_STATE_BASE + caseIndex * RANDOM_STATE_STEP);

                game.getUnitOrders().update(unit, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

                Assertions.assertNull(unit.getSpriteOrNull());

                expected.get(expectedUnit);

                UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit);

                Assertions.assertEquals(expected.getInt(), random.getState());
                Assertions.assertEquals(expected.getInt(), random.getTotalRandomCounts());
                Assertions.assertEquals(expected.getInt(), random.getCallCount(RANDOM_COUNT_INDEX));
            }
        }

        Assertions.assertFalse(expected.hasRemaining());
    }
}
