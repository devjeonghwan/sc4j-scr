package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitTraits;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.ArrayDatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitCollisionRules;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitPositioning;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks unit registration, completion, the production record subscriber and the rejection of units of another pool.
 * <p>
 * The units.dat memory layout is the contract of the egg test: the game reads the build time of the empty type
 * {@code 228} from the WORD after the Build Time column, the Requirement Index of type {@code 0}, a value that replays
 * reproduce only with the same layout. The captured cases write their units by unit record offset.
 */
final class UnitRegistryTest {
    private static final int UNIT_CAPACITY = 8;
    /**
     * The unit capacity of a game, the capacity of the captured cases.
     */
    private static final int GAME_UNIT_CAPACITY = 1701;
    private static final int MAP_DIMENSION = 16;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;

    private static final int UNIT_RECORD_SIZE = 488;
    private static final int REMAINING_BUILD_TIME_OFFSET = 0xF0;
    /**
     * The values of the owner BYTE.
     */
    private static final int OWNER_VALUES = 1 << Byte.SIZE;
    /**
     * The bits of a generation that a free slot keeps.
     */
    private static final int GENERATION_MASK = 31;

    private static final int FIRST_OWNER = 0;
    private static final int SECOND_OWNER = 3;
    /**
     * The duration of one frame of the egg test, in milliseconds.
     */
    private static final int FRAME_DURATION = 42;
    private static final int EGG_POOL_INDEX = 5;
    private static final int EGG_REMAINING_BUILD_TIME = 10;

    private static final int REPLAY_BUS_NODE = 1;
    /**
     * A bit above the WORD of a record identifier or type, which the binding ignores.
     */
    private static final int RECORD_HIGH_WORD_BIT = 0x10000;
    private static final int RECORD_TYPE = 15;
    private static final int RECORD_FLAGS = 0xA5000001;
    private static final int SUBSCRIBER_ENABLED_FLAG = 0x80;

    /**
     * Reads the registry state its API exposes while no subscriber is bound: the phase flags, the production event
     * count, the subscriber flags and the pending production count of every owner.
     *
     * @param registry the registry
     * @return the state values
     */
    private static @NotNull List<@NotNull Object> readRegistryState(@NotNull UnitRegistry registry) {
        List<Object> state = new ArrayList<>();

        state.add(registry.isInitializationActive());
        state.add(registry.hasReplayOwners());
        state.add(registry.getProductionEventCount());
        state.add(registry.isReplaySubscriberBound());
        state.add(registry.hasPendingSubscriberEffects());

        for (int owner = 0; owner < PlayerTable.PLAYABLE_PLAYER_SLOTS; owner++) {
            state.add(registry.getPendingProductionCount(owner));
        }

        return state;
    }


    @Test
    void empty_owner_callbacks_keep_storage_and_pool_allocation() {
        UnitPool pool = new UnitPool(UNIT_CAPACITY);
        UnitRegistry registry = new UnitRegistry(pool);

        for (int owner = 0; owner < OWNER_VALUES; owner++) {
            Unit unit = pool.getUnit(1);

            UnitMemoryScenario.of(unit).writeMemoryByte(UnitMemoryScenario.OWNER_OFFSET, owner);
            UnitPoolScenario.setFreeGeneration(pool, 1, owner & GENERATION_MASK);
            UnitMemoryScenario.of(unit).writeMemoryInteger(UnitMemoryScenario.STATUS_OFFSET, owner);

            byte[] previousStorage = UnitMemoryScenario.of(unit).exportMemoryBytes();
            List<Object> registryState = readRegistryState(registry);

            registry.registerStoredUnit(unit, false);
            registry.registerStoredUnit(unit, true);
            registry.completeUnit(unit);

            Assertions.assertArrayEquals(previousStorage, UnitMemoryScenario.of(unit).exportMemoryBytes());
            Assertions.assertEquals(registryState, readRegistryState(registry));
            Assertions.assertEquals(0, pool.getAllocatedCount());
            Assertions.assertEquals(1, pool.getFirstFreeIndex());
        }
    }

    @Test
    void completed_predicate_precedes_handle_validation_but_completion_validates_the_handle() {
        UnitPool pool = new UnitPool(1);
        UnitRegistry registry = new UnitRegistry(pool);
        Unit foreignUnit = new UnitPool(1).getUnit(1);

        UnitMemoryScenario.of(foreignUnit).writeMemoryInteger(UnitMemoryScenario.STATUS_OFFSET, UnitStatusFlags.COMPLETED);

        Assertions.assertDoesNotThrow(() -> registry.registerStoredUnit(foreignUnit, false));
        Assertions.assertThrows(IllegalArgumentException.class, () -> registry.registerStoredUnit(foreignUnit, true));
        Assertions.assertThrows(IllegalArgumentException.class, () -> registry.completeUnit(foreignUnit));
    }

    @Test
    void foreign_units_are_rejected_by_registry_and_positioning_and_skipped_by_placement() {
        Unit incoming = new UnitPool(1).getUnit(1);
        UnitRegistry registry = new UnitRegistry(new UnitPool(1));
        UnitCollisionRules rules = UnitTestUtils.createCollisionRules();
        ScrUnitTypeView units = new ScrUnitTypeView(column -> new ArrayDatColumn(new int[0]),
                                                    ScrUnitTraits.createColumns(),
                                                    UnitsDat.REQUIREMENT_INDEX,
                                                    (column, index) -> 0);

        Assertions.assertThrows(IllegalArgumentException.class, () -> registry.registerStoredUnit(incoming, true));

        registry.finishInitialization();

        Assertions.assertThrows(IllegalStateException.class, () -> registry.registerStoredUnit(incoming, true));
        Assertions.assertFalse(rules.blocksPlacement(incoming, incoming, units));

        Unit candidate = new UnitPool(1).getUnit(1);

        candidate.setSprite(SpriteScenario.createSprite(0, 0, 0, 0));
        candidate.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);
        candidate.setStatus(UnitStatusFlags.NO_COLLIDE);

        Assertions.assertFalse(rules.blocksPlacement(candidate, incoming, units));

        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        UnitPositioning positioning = game.getUnitPositioning();
        int[] result = new int[1];

        Assertions.assertThrows(IllegalArgumentException.class, () -> positioning.find(incoming, 0, 0, result));
    }

    @Test
    void registry_rejects_registration_after_initialization_finishes() {
        UnitPool pool = new UnitPool(1);
        UnitRegistry registry = new UnitRegistry(pool);
        Unit unit = pool.getUnit(1);

        UnitMemoryScenario.of(unit).writeMemoryInteger(UnitMemoryScenario.STATUS_OFFSET, UnitStatusFlags.COMPLETED);

        Assertions.assertTrue(registry.isInitializationActive());

        registry.finishInitialization();

        Assertions.assertFalse(registry.isInitializationActive());
        Assertions.assertThrows(IllegalStateException.class, () -> registry.registerStoredUnit(unit, false));
        Assertions.assertThrows(IllegalStateException.class, () -> registry.completeUnit(unit));
    }

    @RequiresInstallation
    @Test
    void matches_native_production_record_cases() throws IOException {
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_REGISTRY_RECORDS);
        ByteBuffer input = ByteBuffer.wrap(fixture).order(ByteOrder.LITTLE_ENDIAN);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]);
            Game game = Game.create(gameData, map, new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(GAME_UNIT_CAPACITY);
            game.getPlayerTable().setPlayer(FIRST_OWNER, new Player(FIRST_OWNER, FIRST_OWNER, PlayerTypes.HUMAN, ScrRaceIds.ZERG, 0));
            game.getPlayerTable().setPlayer(SECOND_OWNER, new Player(SECOND_OWNER, SECOND_OWNER, PlayerTypes.HUMAN, ScrRaceIds.PROTOSS, 0));

            int count = input.getInt();

            for (int caseIndex = 0; caseIndex < count; caseIndex++) {
                int poolIndex = input.getInt();
                int unitType = input.getInt();
                int queuedType = input.getInt();
                int owner = input.getInt();
                int generation = input.getInt();
                int completed = input.getInt();
                boolean includeCompleted = input.getInt() != 0;
                int remaining = input.getInt();
                int duration = input.getInt();
                int expectedBefore = input.getInt();
                int expectedAfter = input.getInt();
                int expectedEvents = input.getInt();
                int expectedOwner = input.getInt();
                int expectedHandle = input.getInt();
                int expectedTotal = input.getInt();
                int expectedRemaining = input.getInt();
                int expectedType = input.getInt();

                Assertions.assertEquals(caseIndex, input.getInt());
                Assertions.assertEquals(0, input.getInt());

                UnitPool unitPool = game.getUnitSystem().getUnitPool();

                Assertions.assertNotNull(unitPool);

                UnitRegistry registry = new UnitRegistry(unitPool);

                registry.populateReplayOwners(game, duration);

                Assertions.assertThrows(IllegalArgumentException.class, () -> registry.bindReplaySubscriber(REPLAY_BUS_NODE + 1, RECORD_TYPE, 0));
                Assertions.assertFalse(registry.isReplaySubscriberBound());
                Assertions.assertTrue(registry.hasPendingSubscriberEffects());

                Unit unit = unitPool.getUnit(poolIndex);

                UnitMemoryScenario.of(unit).clearMemoryBytes(0, UNIT_RECORD_SIZE);
                UnitMemoryScenario.of(unit).writeMemoryWord(UnitMemoryScenario.UNIT_TYPE_OFFSET, unitType);
                UnitMemoryScenario.of(unit).writeMemoryWord(UnitMemoryScenario.BUILD_QUEUE_OFFSET, queuedType);
                UnitMemoryScenario.of(unit).writeMemoryWord(REMAINING_BUILD_TIME_OFFSET, remaining);
                UnitMemoryScenario.of(unit).writeMemoryByte(UnitMemoryScenario.OWNER_OFFSET, owner);
                UnitPoolScenario.setFreeGeneration(unitPool, poolIndex, generation);
                UnitMemoryScenario.of(unit).writeMemoryInteger(UnitMemoryScenario.STATUS_OFFSET, completed);

                byte[] before = UnitMemoryScenario.of(unit).exportMemoryBytes();

                registry.registerStoredUnit(unit, includeCompleted);
                registry.registerStoredUnit(unit, includeCompleted);

                int pendingBefore = registry.getPendingProductionCount(FIRST_OWNER) + registry.getPendingProductionCount(SECOND_OWNER);

                Assertions.assertEquals(expectedBefore, pendingBefore, "case=" + caseIndex);

                registry.completeUnit(unit);

                int pendingAfter = registry.getPendingProductionCount(FIRST_OWNER) + registry.getPendingProductionCount(SECOND_OWNER);

                Assertions.assertEquals(expectedAfter, pendingAfter, "case=" + caseIndex);
                Assertions.assertEquals(expectedEvents, registry.getProductionEventCount(), "case=" + caseIndex);

                if (expectedEvents != 0) {
                    UnitRegistry.ProductionEvent event = registry.getProductionEvent(0);

                    Assertions.assertEquals(expectedOwner, event.getOwner(), "case=" + caseIndex);
                    Assertions.assertEquals(expectedHandle, event.getHandle(), "case=" + caseIndex);
                    Assertions.assertEquals(expectedType, event.getUnitType(), "case=" + caseIndex);
                    Assertions.assertEquals(expectedTotal, event.getTotalMilliseconds(), "case=" + caseIndex);
                    Assertions.assertEquals(expectedRemaining, event.getRemainingMilliseconds(), "case=" + caseIndex);
                    Assertions.assertTrue(event.isDone());
                }

                Assertions.assertArrayEquals(before, UnitMemoryScenario.of(unit).exportMemoryBytes());
                Assertions.assertTrue(registry.hasPendingSubscriberEffects());

                registry.bindReplaySubscriber(RECORD_HIGH_WORD_BIT | REPLAY_BUS_NODE, RECORD_HIGH_WORD_BIT | RECORD_TYPE, RECORD_FLAGS);

                Assertions.assertTrue(registry.isReplaySubscriberBound());
                Assertions.assertEquals(RECORD_FLAGS | SUBSCRIBER_ENABLED_FLAG, registry.getSubscriberRecordFlags());
                Assertions.assertEquals(RECORD_TYPE, registry.getSubscriberRecordType());
                Assertions.assertEquals(expectedEvents != 0, registry.hasPendingSubscriberEffects());
                Assertions.assertEquals(expectedEvents, registry.getProductionEventCount());
                Assertions.assertThrows(IllegalStateException.class, () -> registry.bindReplaySubscriber(REPLAY_BUS_NODE, RECORD_TYPE, 0));
                Assertions.assertFalse(registry.isInitializationActive());
            }

            Assertions.assertFalse(input.hasRemaining());
        }
    }

    @RequiresInstallation
    @Test
    void egg_with_an_empty_queued_type_reads_the_word_after_the_build_time_column() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]);
            Game game = Game.create(gameData, map, new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(GAME_UNIT_CAPACITY);
            game.getPlayerTable().setPlayer(FIRST_OWNER, new Player(FIRST_OWNER, FIRST_OWNER, PlayerTypes.HUMAN, ScrRaceIds.ZERG, 0));

            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            UnitRegistry registry = new UnitRegistry(unitPool);

            registry.populateReplayOwners(game, FRAME_DURATION);

            Unit unit = unitPool.getUnit(EGG_POOL_INDEX);

            UnitMemoryScenario.of(unit).clearMemoryBytes(0, UNIT_RECORD_SIZE);
            UnitMemoryScenario.of(unit).writeMemoryWord(UnitMemoryScenario.UNIT_TYPE_OFFSET, ScrUnitIds.ZERG_EGG);
            UnitMemoryScenario.of(unit).writeMemoryWord(UnitMemoryScenario.BUILD_QUEUE_OFFSET, ScrUnitIds.NONE);
            UnitMemoryScenario.of(unit).writeMemoryWord(REMAINING_BUILD_TIME_OFFSET, EGG_REMAINING_BUILD_TIME);
            UnitMemoryScenario.of(unit).writeMemoryByte(UnitMemoryScenario.OWNER_OFFSET, FIRST_OWNER);
            UnitPoolScenario.setFreeGeneration(unitPool, EGG_POOL_INDEX, 1);

            registry.registerStoredUnit(unit, false);

            Assertions.assertEquals(1, registry.getPendingProductionCount(FIRST_OWNER));

            registry.completeUnit(unit);

            Assertions.assertEquals(gameData.getUnits().get("Requirement Index", ScrUnitIds.TERRAN_MARINE) * FRAME_DURATION,
                                    registry.getProductionEvent(0).getTotalMilliseconds());
        }
    }
}
