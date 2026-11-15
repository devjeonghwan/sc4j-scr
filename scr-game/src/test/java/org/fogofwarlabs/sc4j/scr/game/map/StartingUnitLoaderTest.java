package org.fogofwarlabs.sc4j.scr.game.map;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.GameSettings;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.creep.CreepField;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Checks starting-unit dispatch with explicit terrain and random-state fixtures.
 */
@RequiresInstallation
final class StartingUnitLoaderTest {
    /**
     * The script instruction budget passed to every loader and unit call.
     */
    private static final int INSTRUCTION_LIMIT = 2048;
    /**
     * The runtime random state the creep field starts with.
     */
    private static final int CREEP_SEED = 0x5A123456;
    /**
     * The number of start position WORDs: a horizontal and a vertical WORD per playable slot.
     */
    private static final int START_POSITION_WORDS = 16;
    /**
     * The WORDs of one start position: the horizontal WORD and the vertical WORD.
     */
    private static final int POSITION_COMPONENT_COUNT = 2;
    /**
     * The starting minerals of the starting-unit settings.
     */
    private static final int STARTING_MINERALS = 50;
    /**
     * The initial unit modes that create the start workers, and the start depot and the start workers.
     */
    private static final int WORKERS_MODE = 1;
    private static final int DEPOT_AND_WORKERS_MODE = 2;
    /**
     * The random sources of unit creation and of the creep spread.
     */
    private static final int CREATION_RANDOM_SOURCE = 14;
    private static final int CREEP_SPREAD_RANDOM_SOURCE = 26;
    /**
     * The unit memory offsets of the position WORDs and of the fixed-point position DWORDs.
     */
    private static final int POSITION_X_OFFSET = 0x40;
    private static final int POSITION_Y_OFFSET = 0x42;
    private static final int FIXED_POSITION_X_OFFSET = 0x44;
    private static final int FIXED_POSITION_Y_OFFSET = 0x48;
    /**
     * The fraction bits of a fixed-point position DWORD.
     */
    private static final int FIXED_POSITION_FRACTION_BITS = 8;
    /**
     * The starting units of a Protoss or Terran player in the depot mode: the depot and four workers.
     */
    private static final int DEPOT_AND_WORKERS_UNIT_COUNT = 5;
    /**
     * The starting units of one player in the worker mode.
     */
    private static final int WORKERS_PER_PLAYER = 4;
    /**
     * The unit pool capacities of the depot, worker, creep and failed-coordinate cases.
     */
    private static final int DEPOT_CASE_CAPACITY = 10;
    private static final int WORKER_CASE_CAPACITY = 12;
    private static final int CREEP_CASE_CAPACITY = 13;
    private static final int FAILURE_CASE_CAPACITY = 16;
    /**
     * The Protoss and Terran player slots of the depot case and their start positions.
     */
    private static final int DEPOT_PROTOSS_SLOT = 7;
    private static final int DEPOT_TERRAN_SLOT = 3;
    private static final int DEPOT_PROTOSS_START_X = 1025;
    private static final int DEPOT_PROTOSS_START_Y = 1025;
    private static final int DEPOT_TERRAN_START_X = 321;
    private static final int DEPOT_TERRAN_START_Y = 641;
    /**
     * The start position of player slot {@code 0} in the worker case, the horizontal spacing of the next slots, and the highest slot of the case.
     */
    private static final int WORKER_START_X = 320;
    private static final int WORKER_START_SPACING = 384;
    private static final int WORKER_START_Y = 640;
    private static final int WORKER_LAST_SLOT = 2;
    /**
     * The Protoss player slot of the creep case and the start positions of its Protoss player and of its Zerg player in slot {@code 0}.
     */
    private static final int CREEP_PROTOSS_SLOT = 3;
    private static final int CREEP_PROTOSS_START_X = 1280;
    private static final int CREEP_PROTOSS_START_Y = 1280;
    private static final int CREEP_ZERG_START_X = 320;
    private static final int CREEP_ZERG_START_Y = 640;
    /**
     * The start position WORD of every slot in the failed-coordinate case, outside the map.
     */
    private static final int INVALID_START_POSITION = 0x8001;

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

    private static @NotNull Game createState(int capacity) throws IOException {
        Assertions.assertNotNull(gameData);

        return StartingUnitScenario.createGame(gameData, capacity);
    }


    @Test
    void create_should_make_completed_depots_and_four_workers_in_descending_player_order() throws IOException {
        Game game = createState(DEPOT_CASE_CAPACITY);

        game.getPlayerTable().setPlayer(DEPOT_PROTOSS_SLOT,
                                        new Player(DEPOT_PROTOSS_SLOT, DEPOT_PROTOSS_SLOT, PlayerTypes.HUMAN, ScrRaceIds.PROTOSS, 0));
        game.getPlayerTable().setPlayer(DEPOT_TERRAN_SLOT,
                                        new Player(DEPOT_TERRAN_SLOT, DEPOT_TERRAN_SLOT, PlayerTypes.HUMAN, ScrRaceIds.TERRAN, 0));

        int[] positions = new int[START_POSITION_WORDS];

        positions[DEPOT_PROTOSS_SLOT * POSITION_COMPONENT_COUNT]     = DEPOT_PROTOSS_START_X;
        positions[DEPOT_PROTOSS_SLOT * POSITION_COMPONENT_COUNT + 1] = DEPOT_PROTOSS_START_Y;
        positions[DEPOT_TERRAN_SLOT * POSITION_COMPONENT_COUNT]      = DEPOT_TERRAN_START_X;
        positions[DEPOT_TERRAN_SLOT * POSITION_COMPONENT_COUNT + 1]  = DEPOT_TERRAN_START_Y;

        GameSettings startingSettings = new GameSettings(0, 0, STARTING_MINERALS, DEPOT_AND_WORKERS_MODE,
                                                         new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS], 0);

        List<StartingUnitLoader.Creation> creations = StartingUnitLoader.create(new UnitCreationContext(false, true, true, false, true, 0), game,
                                                                                startingSettings,
                                                                                positions,
                                                                                null,
                                                                                INSTRUCTION_LIMIT);

        int[] expectedTypes = {154, 64, 64, 64, 64, 106, 7, 7, 7, 7};

        UnitPool units = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(units);

        Assertions.assertEquals(10, units.getAllocatedCount());
        Assertions.assertEquals(10, creations.size());

        for (int creationIndex = 0; creationIndex < expectedTypes.length; creationIndex++) {
            int poolIndex = creationIndex == 0 ? 1 : DEPOT_CASE_CAPACITY + 1 - creationIndex;
            Unit unit = units.getUnit(poolIndex);
            int playerSlot = creationIndex < DEPOT_AND_WORKERS_UNIT_COUNT ? DEPOT_PROTOSS_SLOT : DEPOT_TERRAN_SLOT;
            StartingUnitLoader.Creation creation = creations.get(creationIndex);

            Assertions.assertEquals(expectedTypes[creationIndex], creation.getUnitType());
            Assertions.assertEquals(playerSlot, creation.getOwner());
            Assertions.assertEquals(poolIndex, creation.getPoolIndex());
            Assertions.assertEquals(units.getGeneration(poolIndex), creation.getGeneration());
            Assertions.assertEquals(unit.getPositionX(), creation.getFinalX());
            Assertions.assertEquals(unit.getPositionY(), creation.getFinalY());

            Sprite sprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(sprite);

            Assertions.assertEquals(sprite.getX(), creation.getSpriteX());
            Assertions.assertEquals(sprite.getY(), creation.getSpriteY());
            Assertions.assertTrue(creation.getRandomCallsAfter() >= creation.getRandomCallsBefore());

            if (creationIndex != 0) {
                Assertions.assertEquals(creations.get(creationIndex - 1).getRandomStateAfter(), creation.getRandomStateBefore());
                Assertions.assertEquals(creations.get(creationIndex - 1).getRandomCallsAfter(), creation.getRandomCallsBefore());
            }

            if (creationIndex % DEPOT_AND_WORKERS_UNIT_COUNT != 0) {
                Assertions.assertEquals(positions[playerSlot * POSITION_COMPONENT_COUNT], creation.getRequestedX());
                Assertions.assertEquals(positions[playerSlot * POSITION_COMPONENT_COUNT + 1], creation.getRequestedY());
                Assertions.assertTrue(creation.getRequestedX() != creation.getFinalX() || creation.getRequestedY() != creation.getFinalY());
            }

            Assertions.assertEquals(expectedTypes[creationIndex], unit.getUnitId());
            Assertions.assertEquals(playerSlot, unit.getOwnerIndex());
            Assertions.assertEquals(playerSlot | playerSlot << Byte.SIZE, units.getCreationTag(poolIndex));
            Assertions.assertEquals(1, unit.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertEquals(0, sprite.getFlags() & Sprite.FLAG_HIDDEN);
            Assertions.assertNotNull(gameData);

            Assertions.assertEquals(gameData.getUnits().get("Hit Points", unit.getUnitId()), unit.getHitPoints());
        }

        Assertions.assertEquals(1024, units.getUnit(1).getPositionX());
        Assertions.assertEquals(1008, units.getUnit(1).getPositionY());
        Assertions.assertEquals(320, units.getUnit(6).getPositionX());
        Assertions.assertEquals(624, units.getUnit(6).getPositionY());
        Assertions.assertEquals(10, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE));

        UnitSpatialIndex countedIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(countedIndex);

        Assertions.assertEquals(20, countedIndex.getCount());

        int activeIndex = units.getFirstCreatedIndex(true);

        for (int poolIndex = 1; poolIndex <= DEPOT_CASE_CAPACITY; poolIndex++) {
            Assertions.assertEquals(poolIndex, activeIndex);

            activeIndex = units.getNextCreatedIndex(activeIndex);
        }

        Assertions.assertEquals(0, activeIndex);
        Assertions.assertFalse(game.hasInitializedWorldState());
    }

    @Test
    void create_should_make_only_four_workers_per_race_in_mode_one() throws IOException {
        Game game = createState(WORKER_CASE_CAPACITY);
        int[] positions = new int[START_POSITION_WORDS];

        for (int playerSlot = 0; playerSlot < ScrRaceIds.PLAYABLE_COUNT; playerSlot++) {
            game.getPlayerTable().setPlayer(playerSlot, new Player(playerSlot, playerSlot, PlayerTypes.HUMAN, playerSlot, 0));

            positions[playerSlot * POSITION_COMPONENT_COUNT]     = WORKER_START_X + playerSlot * WORKER_START_SPACING;
            positions[playerSlot * POSITION_COMPONENT_COUNT + 1] = WORKER_START_Y;
        }

        GameSettings startingSettings = new GameSettings(0, 0, STARTING_MINERALS, WORKERS_MODE, new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS], 0);

        StartingUnitLoader.create(new UnitCreationContext(false, true, true, false, true, 0), game,
                                  startingSettings,
                                  positions,
                                  null,
                                  INSTRUCTION_LIMIT);

        UnitPool units = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(units);

        Assertions.assertEquals(12, units.getAllocatedCount());

        for (int creationIndex = 0; creationIndex < WORKER_CASE_CAPACITY; creationIndex++) {
            int poolIndex = creationIndex == 0 ? 1 : WORKER_CASE_CAPACITY + 1 - creationIndex;
            Unit unit = units.getUnit(poolIndex);
            int playerSlot = WORKER_LAST_SLOT - creationIndex / WORKERS_PER_PLAYER;

            Assertions.assertEquals(playerSlot == 0 ? 41 : playerSlot == 1 ? 7 : 64, unit.getUnitId());
            Assertions.assertEquals(playerSlot, unit.getOwnerIndex());
            Assertions.assertEquals(1, unit.getStatus() & UnitStatusFlags.COMPLETED);

            int expectedHorizontal = UnitMemoryScenario.of(unit).readMemoryUnsignedWord(POSITION_X_OFFSET);
            int expectedVertical = UnitMemoryScenario.of(unit).readMemoryUnsignedWord(POSITION_Y_OFFSET);
            Sprite sprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(sprite);

            Assertions.assertEquals(expectedHorizontal, sprite.getX());
            Assertions.assertEquals(expectedVertical, sprite.getY());
            Assertions.assertEquals((short) unit.getPositionX() << FIXED_POSITION_FRACTION_BITS,
                                    UnitMemoryScenario.of(unit).readMemoryInteger(FIXED_POSITION_X_OFFSET));
            Assertions.assertEquals((short) unit.getPositionY() << FIXED_POSITION_FRACTION_BITS,
                                    UnitMemoryScenario.of(unit).readMemoryInteger(FIXED_POSITION_Y_OFFSET));

            Assertions.assertNotNull(gameData);

            UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

            Assertions.assertNotNull(spatialIndex);

            int horizontalEdges = 0;
            int verticalEdges = 0;

            for (int edgeIndex = 0; edgeIndex < spatialIndex.getCount(); edgeIndex++) {
                if (spatialIndex.getUnitIndex(true, edgeIndex) == poolIndex) {
                    int expected = horizontalEdges++ == 0
                                   ? unit.getPositionX() - gameData.getUnits().get("Unit Size Left", unit.getUnitId())
                                   : unit.getPositionX() + gameData.getUnits().get("Unit Size Right", unit.getUnitId());

                    Assertions.assertEquals(expected, spatialIndex.getCoordinate(true, edgeIndex));
                }

                if (spatialIndex.getUnitIndex(false, edgeIndex) == poolIndex) {
                    int expected = verticalEdges++ == 0
                                   ? unit.getPositionY() - gameData.getUnits().get("Unit Size Up", unit.getUnitId())
                                   : unit.getPositionY() + gameData.getUnits().get("Unit Size Down", unit.getUnitId());

                    Assertions.assertEquals(expected, spatialIndex.getCoordinate(false, edgeIndex));
                }
            }

            Assertions.assertEquals(2, horizontalEdges);
            Assertions.assertEquals(2, verticalEdges);
        }

        Assertions.assertEquals(12, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE));
        Assertions.assertFalse(game.hasInitializedWorldState());
    }

    @Test
    void create_should_make_protoss_then_zerg_starting_units_with_creep_and_larvae() throws IOException {
        Game game = createState(CREEP_CASE_CAPACITY);

        game.getPlayerTable().setPlayer(CREEP_PROTOSS_SLOT,
                                        new Player(CREEP_PROTOSS_SLOT, CREEP_PROTOSS_SLOT, PlayerTypes.HUMAN, ScrRaceIds.PROTOSS, 0));
        game.getPlayerTable().setPlayer(0, new Player(0, 0, PlayerTypes.HUMAN, ScrRaceIds.ZERG, 0));

        Assertions.assertNotNull(gameData);

        game.setReplayPlayback(true);
        game.getLocalVision().acceptDialog(StartingUnitScenario.LOCAL_PLAYER_MASK, false);
        game.getRuntimeRandom().seed(CREEP_SEED);
        game.setCreepField(new CreepField(game.getMap(), gameData.getTerrainTileset(0),
                                          new int[StartingUnitScenario.MAP_SIZE * StartingUnitScenario.MAP_SIZE]));

        int[] positions = new int[START_POSITION_WORDS];

        positions[CREEP_PROTOSS_SLOT * POSITION_COMPONENT_COUNT]     = CREEP_PROTOSS_START_X;
        positions[CREEP_PROTOSS_SLOT * POSITION_COMPONENT_COUNT + 1] = CREEP_PROTOSS_START_Y;
        positions[0]                                                 = CREEP_ZERG_START_X;
        positions[1]                                                 = CREEP_ZERG_START_Y;

        GameSettings startingSettings = new GameSettings(0, 0, STARTING_MINERALS, DEPOT_AND_WORKERS_MODE,
                                                         new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS], 0);

        StartingUnitLoader.create(new UnitCreationContext(false, true, true, false, true, 0), game,
                                  startingSettings,
                                  positions,
                                  null,
                                  INSTRUCTION_LIMIT);

        int[] expectedTypes = {154, 64, 64, 64, 64, 131, 35, 35, 42, 41, 41, 41, 41};

        UnitPool units = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(units);

        Assertions.assertEquals(13, units.getAllocatedCount());

        for (int creationIndex = 0; creationIndex < expectedTypes.length; creationIndex++) {
            int poolIndex = creationIndex == 0 ? 1 : CREEP_CASE_CAPACITY + 1 - creationIndex;
            Unit unit = units.getUnit(poolIndex);

            Assertions.assertEquals(expectedTypes[creationIndex], unit.getUnitId());
            Assertions.assertEquals(creationIndex < DEPOT_AND_WORKERS_UNIT_COUNT ? CREEP_PROTOSS_SLOT : 0, unit.getOwnerIndex());
            Assertions.assertEquals(1, unit.getStatus() & UnitStatusFlags.COMPLETED);
        }

        Unit hatchery = units.getUnit(9);

        Assertions.assertSame(hatchery, units.getUnit(8).getParentUnit());
        Assertions.assertSame(hatchery, units.getUnit(7).getParentUnit());
        Assertions.assertEquals(13, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE));
        Assertions.assertTrue(game.getRandom().getCallCount(CREEP_SPREAD_RANDOM_SOURCE) > 0);

        Assertions.assertTrue(game.getRuntimeRandom().getDrawCount() > 0);
        Assertions.assertFalse(game.hasInitializedWorldState());
    }

    @Test
    void create_should_keep_every_random_draw_when_coordinates_fail() throws IOException {
        Game game = createState(FAILURE_CASE_CAPACITY);
        int[] positions = new int[START_POSITION_WORDS];

        Arrays.fill(positions, INVALID_START_POSITION);

        for (int playerSlot = 0; playerSlot < ScrRaceIds.PLAYABLE_COUNT; playerSlot++) {
            game.getPlayerTable().setPlayer(playerSlot, new Player(playerSlot, playerSlot, PlayerTypes.HUMAN, playerSlot, 0));
        }

        GameSettings startingSettings = new GameSettings(0, 0, STARTING_MINERALS, DEPOT_AND_WORKERS_MODE,
                                                         new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS], 0);

        StartingUnitLoader.create(new UnitCreationContext(false, true, true, false, true, 0), game,
                                  startingSettings,
                                  positions,
                                  null,
                                  INSTRUCTION_LIMIT);

        UnitPool units = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(units);

        Assertions.assertEquals(0, units.getAllocatedCount());
        Assertions.assertEquals(16, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE));
        Assertions.assertEquals(16, game.getRandom().getTotalRandomCounts());
    }
}
