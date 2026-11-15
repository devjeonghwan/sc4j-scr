package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.map.StartingUnitScenario;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMapScenario;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Checks the Larva position search, the Larva primary order, also against captured native cases, and the Larvae that a Hatchery spawns.
 */
@RequiresInstallation
final class LarvaSpawnerTest {
    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;

    private static final int MAP_DIMENSION = 16;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int INSTRUCTION_LIMIT = 10000;

    /**
     * The unit pool indexes of a Larva and its parent, and of the parent of a position search case, which has no Larva.
     */
    private static final int LARVA_POOL_INDEX = 1;
    private static final int PARENT_POOL_INDEX = 2;
    private static final int SEARCH_PARENT_POOL_INDEX = 1;

    private static final int HATCHERY_POSITION = 256;
    private static final int LARVA_POSITION = 64;
    private static final int PATH_INDEX = 1;

    /**
     * The flags of a retained path, and the flags after the returning Larva invalidated it.
     */
    private static final int RETAINED_PATH_FLAGS = 0xA4;
    private static final int INVALIDATED_PATH_FLAGS = 0xA5;

    private static final int PATH_DESTINATION = 0x12345678;
    private static final int MOVEMENT_DESTINATION_COUNTDOWN = 15;

    private static final int UNIT_RECORD_SIZE = 488;
    private static final int MOVEMENT_DESTINATION_X_OFFSET = 0x20;
    private static final int MOVEMENT_DESTINATION_Y_OFFSET = 0x22;
    private static final int DIRECTION_TARGET_X_OFFSET = 0x34;
    private static final int DIRECTION_TARGET_Y_OFFSET = 0x36;
    private static final int MOVEMENT_FLAGS_OFFSET = 0x38;
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int PRIMARY_ORDER_STATE_OFFSET = 0x6A;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int LARVA_SIDE_COUNT_OFFSET = 0x128;
    private static final int STATUS_OFFSET = 0x140;

    private static final int LARVA_SIDES = 4;
    private static final int LARVA_TYPE = 35;
    private static final int LARVA_ORDER = 77;
    private static final int WANDER_RANDOM_SOURCE = 20;

    /**
     * The generator of the tile flags of a captured case: multiplier, increment and the shift to a palette index.
     */
    private static final int TILE_RANDOM_MULTIPLIER = 1664525;
    private static final int TILE_RANDOM_INCREMENT = 1013904223;
    private static final int TILE_PALETTE_SHIFT = 29;
    /**
     * The interval of the captured cases that have walkable creep on every tile.
     */
    private static final int CREEP_CASE_INTERVAL = 8;
    /**
     * The tile flags of a walkable tile with creep.
     */
    private static final int WALKABLE_CREEP_TILE = 0x410000;
    /**
     * The tile flags that the palette index of a captured case selects.
     */
    private static final int @NotNull [] TILE_PALETTE = {0, 0x10000, 0x400000, WALKABLE_CREEP_TILE,
                                                         0x8400000, 0x8410000, 0x400000, WALKABLE_CREEP_TILE};
    /**
     * The result coordinates that a position search without a found position leaves unchanged.
     */
    private static final int UNCHANGED_RESULT_X = 0x12345678;
    private static final int UNCHANGED_RESULT_Y = -0x12345678;

    /**
     * The script instruction budget of the Larva spawn cases.
     */
    private static final int SPAWN_INSTRUCTION_LIMIT = 2048;
    /**
     * The unit pool capacities of the spawn case and of the idle-hatchery case.
     */
    private static final int LARVA_CASE_CAPACITY = 3;
    private static final int IDLE_HATCHERY_CASE_CAPACITY = 4;
    /**
     * The position and creation tag of the hatchery of the spawn cases, and the larvae the cases spawn from it before their counts are checked.
     */
    private static final int HATCHERY_X = 320;
    private static final int HATCHERY_Y = 624;
    private static final int HATCHERY_CREATION_TAG = 0x1234;
    private static final int INITIAL_LARVA_COUNT = 2;
    /**
     * The completed status flag of a spawned Larva, and the primary order state that a spawned Larva starts in.
     */
    private static final int COMPLETED_FLAG = 0x1;
    private static final int SPAWNED_LARVA_ORDER_STATE = 3;
    /**
     * The allocated units of the spawn case, the Hatchery and its initial Larvae, and the creation random calls that created them.
     */
    private static final int SPAWN_CASE_UNIT_COUNT = 3;
    private static final int SPAWN_CASE_CREATION_RANDOM_CALLS = 3;
    /**
     * The allocated units of the idle-hatchery case after the spawn, the Larvae a Hatchery spawns at most, and the Larva spawn countdown after a
     * spawn.
     */
    private static final int IDLE_HATCHERY_CASE_UNIT_COUNT = 4;
    private static final int MAXIMUM_LARVA_COUNT = 3;
    private static final int LARVA_SPAWN_COUNTDOWN = 37;
    /**
     * The pool index of the Larva that the idle-hatchery case checks.
     */
    private static final int CHECKED_LARVA_POOL_INDEX = 2;
    /**
     * The unit memory offsets of the primary order countdown and of the Larva spawn countdown.
     */
    private static final int PRIMARY_ORDER_COUNTDOWN_OFFSET = 0xC9;
    private static final int LARVA_SPAWN_COUNTDOWN_OFFSET = 0x116;
    /**
     * The random source of unit creation.
     */
    private static final int CREATION_RANDOM_SOURCE = 14;


    @Test
    void update_primary_should_invalidate_retained_path_without_recycling_when_returning() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(0));
            PathPool paths = new PathPool();

            game.getPathSystem().setPathPool(paths);

            int pathIndex = paths.allocate();
            int followingFreeIndex = paths.getFirstFreeIndex();

            paths.setFlags(pathIndex, RETAINED_PATH_FLAGS);
            paths.setDestination(pathIndex, PATH_DESTINATION);

            Unit parent = UnitTestUtils.createUnit(PARENT_POOL_INDEX);

            parent.setUnitId(ScrUnitIds.ZERG_HATCHERY);
            parent.setSprite(SpriteScenario.createSprite(0, HATCHERY_POSITION, HATCHERY_POSITION, 0));

            Unit unit = UnitTestUtils.createUnit(LARVA_POOL_INDEX);

            unit.setUnitId(ScrUnitIds.ZERG_LARVA);
            unit.setSprite(SpriteScenario.createSprite(1, LARVA_POSITION, LARVA_POSITION, 0));
            unit.setParentUnit(parent);
            unit.setPathIndex(pathIndex);
            unit.setMovementDestinationX(LARVA_POSITION);
            unit.setMovementDestinationY(LARVA_POSITION);

            game.getLarvaSpawner().updatePrimary(unit, INSTRUCTION_LIMIT);

            Assertions.assertEquals(INVALIDATED_PATH_FLAGS, paths.getFlags(pathIndex));
            Assertions.assertEquals(PATH_DESTINATION, paths.getDestination(pathIndex));
            Assertions.assertEquals(pathIndex, unit.getPathIndex());
            Assertions.assertEquals(followingFreeIndex, paths.getFirstFreeIndex());
            Assertions.assertSame(parent, unit.getMovementTargetUnit());
            Assertions.assertEquals(HATCHERY_POSITION, unit.getMovementDestinationX());
            Assertions.assertEquals(HATCHERY_POSITION, unit.getMovementDestinationY());
            Assertions.assertEquals(MOVEMENT_DESTINATION_COUNTDOWN, unit.getMovementDestinationCountdown());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());

            paths.setFlags(pathIndex, RETAINED_PATH_FLAGS);

            game.getLarvaSpawner().updatePrimary(unit, INSTRUCTION_LIMIT);

            Assertions.assertEquals(RETAINED_PATH_FLAGS, paths.getFlags(pathIndex));
            Assertions.assertEquals(followingFreeIndex, paths.allocate());
        }
    }

    @Test
    void update_primary_should_reject_missing_path_storage_before_changing_larva_target() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(0));
            Unit parent = UnitTestUtils.createUnit(PARENT_POOL_INDEX);

            parent.setUnitId(ScrUnitIds.ZERG_HATCHERY);
            parent.setSprite(SpriteScenario.createSprite(0, HATCHERY_POSITION, HATCHERY_POSITION, 0));

            Unit unit = UnitTestUtils.createUnit(LARVA_POOL_INDEX);

            unit.setUnitId(ScrUnitIds.ZERG_LARVA);
            unit.setSprite(SpriteScenario.createSprite(1, LARVA_POSITION, LARVA_POSITION, 0));
            unit.setParentUnit(parent);
            unit.setPathIndex(PATH_INDEX);
            unit.setMovementDestinationX(LARVA_POSITION);
            unit.setMovementDestinationY(LARVA_POSITION);

            Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getLarvaSpawner().updatePrimary(unit, INSTRUCTION_LIMIT));

            Assertions.assertNull(unit.getMovementTargetUnit());
            Assertions.assertEquals(LARVA_POSITION, unit.getMovementDestinationX());
            Assertions.assertEquals(LARVA_POSITION, unit.getMovementDestinationY());
            Assertions.assertEquals(PATH_INDEX, unit.getPathIndex());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void matches_native_larva_position_search_cases() throws IOException {
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_LARVA_POSITION);
        ByteBuffer input = ByteBuffer.wrap(fixture).order(ByteOrder.LITTLE_ENDIAN);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            int caseCount = input.getInt();

            for (int caseIndex = 0; caseIndex < caseCount; caseIndex++) {
                int unitType = input.getInt();
                int horizontal = input.getInt();
                int vertical = input.getInt();
                Unit parent = UnitTestUtils.createUnit(SEARCH_PARENT_POOL_INDEX);

                UnitMemoryScenario.of(parent).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                parent.setSprite(SpriteScenario.createSprite(0, horizontal, vertical, 0));

                for (int side = 0; side < LARVA_SIDES; side++) {
                    UnitMemoryScenario.of(parent).writeMemoryWord(LARVA_SIDE_COUNT_OFFSET + side * Short.BYTES, input.getShort());
                }

                int random = input.getInt();
                boolean expected = input.getInt() != 0;
                int expectedX = input.getInt();
                int expectedY = input.getInt();
                int[] flags = new int[MAP_TILE_COUNT];

                for (int tileIndex = 0; tileIndex < flags.length; tileIndex++) {
                    random           = random * TILE_RANDOM_MULTIPLIER + TILE_RANDOM_INCREMENT;
                    flags[tileIndex] = caseIndex % CREEP_CASE_INTERVAL == 0 ? WALKABLE_CREEP_TILE : TILE_PALETTE[random >>> TILE_PALETTE_SHIFT];
                }

                Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT], flags), new LcgRandom(0));
                int[] result = {UNCHANGED_RESULT_X, UNCHANGED_RESULT_Y};

                Assertions.assertEquals(expected, game.getLarvaSpawner().findPosition(parent, result), "case=" + caseIndex);
                Assertions.assertArrayEquals(new int[]{expectedX, expectedY}, result, "case=" + caseIndex);
            }

            Assertions.assertFalse(input.hasRemaining());
        }
    }

    @Test
    void matches_native_larva_primary_order_cases() throws IOException {
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_LARVA_PRIMARY_ORDER);
        ByteBuffer input = ByteBuffer.wrap(fixture).order(ByteOrder.LITTLE_ENDIAN);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            int caseCount = input.getInt();
            int[] flags = new int[MAP_TILE_COUNT];

            Arrays.fill(flags, WALKABLE_CREEP_TILE);

            LcgRandom random = new LcgRandom(0);
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT], flags), random);

            for (int caseIndex = 0; caseIndex < caseCount; caseIndex++) {
                int parentType = input.getInt();
                int parentX = input.getInt();
                int parentY = input.getInt();
                int horizontal = input.getInt();
                int vertical = input.getInt();
                boolean parentPresent = input.getInt() != 0;
                boolean targetPresent = input.getInt() != 0;
                int randomSeed = input.getInt();
                int destinationX = input.getInt();
                int destinationY = input.getInt();
                int side = input.getInt();
                int status = input.getInt();
                int movementFlags = input.getInt();
                int desiredX = input.getInt();
                int desiredY = input.getInt();
                boolean expectedTarget = input.getInt() != 0;
                int expectedRandom = input.getInt();
                int expectedCount = input.getInt();

                Assertions.assertEquals(caseIndex, input.getInt());

                byte[] expected = new byte[UNIT_RECORD_SIZE];

                input.get(expected);

                Unit parent = UnitTestUtils.createUnit(PARENT_POOL_INDEX);

                UnitMemoryScenario.of(parent).writeMemoryWord(UNIT_TYPE_OFFSET, parentType);
                parent.setSprite(SpriteScenario.createSprite(0, parentX, parentY, 0));

                Unit unit = UnitTestUtils.createUnit(LARVA_POOL_INDEX);

                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, LARVA_TYPE);
                UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_X_OFFSET, destinationX);
                UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_Y_OFFSET, destinationY);
                UnitMemoryScenario.of(unit).writeMemoryWord(DIRECTION_TARGET_X_OFFSET, desiredX);
                UnitMemoryScenario.of(unit).writeMemoryWord(DIRECTION_TARGET_Y_OFFSET, desiredY);
                UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_FLAGS_OFFSET, movementFlags);
                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, LARVA_ORDER);
                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_STATE_OFFSET, side);
                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);
                unit.setSprite(SpriteScenario.createSprite(0, horizontal, vertical, 0));
                unit.setParentUnit(parentPresent ? parent : null);
                unit.setMovementTargetUnit(targetPresent ? parent : null);

                random.setState(randomSeed);

                game.getLarvaSpawner().updatePrimary(unit, INSTRUCTION_LIMIT);

                byte[] actual = new byte[UNIT_RECORD_SIZE];

                for (int offset = 0; offset < actual.length; offset++) {
                    actual[offset] = (byte) UnitMemoryScenario.of(unit).readMemoryUnsignedByte(offset);
                }

                Assertions.assertArrayEquals(expected, actual, "case=" + caseIndex);
                Assertions.assertSame(expectedTarget ? parent : null, unit.getMovementTargetUnit(), "case=" + caseIndex);
                Assertions.assertEquals(expectedRandom, random.getState(), "case=" + caseIndex);
                Assertions.assertEquals(expectedCount, random.getCallCount(WANDER_RANDOM_SOURCE), "case=" + caseIndex);
                Assertions.assertEquals(expectedCount, random.getTotalRandomCounts(), "case=" + caseIndex);
            }

            Assertions.assertFalse(input.hasRemaining());
        }
    }

    @Test
    void larva_spawner_creates_completed_larvae_with_hatchery_parent_and_creation_tag() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = StartingUnitScenario.createGame(gameData, LARVA_CASE_CAPACITY);

            game.getPlayerTable().setPlayer(0, new Player(0, 0, PlayerTypes.HUMAN, ScrRaceIds.ZERG, 0));

            for (int tileIndex = 0; tileIndex < game.getMap().getTileFlagCount(); tileIndex++) {
                GameMapScenario.setFlags(game.getMap(),
                                         tileIndex,
                                         game.getMap().getTileFlags(tileIndex) & ~MapTileFlags.OCCUPIED |
                                         MapTileFlags.WALKABLE | MapTileFlags.HAS_CREEP);
            }

            UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
            UnitAccounting creationAccounting = game.getPlayerTable().getUnitAccounting();

            Assertions.assertNotNull(creationAccounting);

            int creationUnitType = ScrUnitIds.ZERG_HATCHERY;
            UnitCreationContext creationInputs = new UnitCreationContext(false, true, true, false, false, 0);

            Unit parent = storageConstructor.create(creationInputs,
                                                    creationAccounting,
                                                    creationUnitType,
                                                    HATCHERY_X, HATCHERY_Y,
                                                    0,
                                                    HATCHERY_CREATION_TAG,
                                                    SPAWN_INSTRUCTION_LIMIT);

            Assertions.assertNotNull(parent);

            game.getUnitStorageConstructor().tryComplete(new UnitCreationContext(false, true, true, false, true, 0), parent,
                                                         PlayerTypes.HUMAN, SPAWN_INSTRUCTION_LIMIT);

            for (int larvaIndex = 0; larvaIndex < INITIAL_LARVA_COUNT; larvaIndex++) {
                Unit larva = game.getLarvaSpawner().create(new UnitCreationContext(false, true, true, false, true, 0), parent,
                                                           PlayerTypes.HUMAN, SPAWN_INSTRUCTION_LIMIT);

                Assertions.assertNotNull(larva);
                Assertions.assertEquals(LARVA_TYPE, larva.getUnitId());
                Assertions.assertEquals(COMPLETED_FLAG, larva.getStatus() & UnitStatusFlags.COMPLETED);
                Assertions.assertSame(parent, larva.getParentUnit());

                UnitPool creationTags = game.getUnitSystem().getUnitPool();

                Assertions.assertNotNull(creationTags);

                Assertions.assertEquals(HATCHERY_CREATION_TAG, creationTags.getCreationTag(larva.getIndex()));
                Assertions.assertEquals(SPAWNED_LARVA_ORDER_STATE, UnitMemoryScenario.of(larva).readMemoryUnsignedByte(PRIMARY_ORDER_STATE_OFFSET));
            }

            UnitPool units = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(units);

            Assertions.assertEquals(SPAWN_CASE_UNIT_COUNT, units.getAllocatedCount());
            Assertions.assertEquals(SPAWN_CASE_CREATION_RANDOM_CALLS, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE));
            Assertions.assertFalse(game.hasInitializedWorldState());
        }
    }

    @Test
    void update_idle_hatchery_should_spawn_third_larva_and_stop_at_three() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = StartingUnitScenario.createGame(gameData, IDLE_HATCHERY_CASE_CAPACITY);

            game.getPlayerTable().setPlayer(0, new Player(0, 0, PlayerTypes.HUMAN, ScrRaceIds.ZERG, 0));

            for (int tileIndex = 0; tileIndex < game.getMap().getTileFlagCount(); tileIndex++) {
                GameMapScenario.setFlags(game.getMap(),
                                         tileIndex,
                                         game.getMap().getTileFlags(tileIndex) & ~MapTileFlags.OCCUPIED |
                                         MapTileFlags.WALKABLE | MapTileFlags.HAS_CREEP);
            }

            UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
            UnitAccounting creationAccounting = game.getPlayerTable().getUnitAccounting();

            Assertions.assertNotNull(creationAccounting);

            int creationUnitType = ScrUnitIds.ZERG_HATCHERY;
            UnitCreationContext creationInputs = new UnitCreationContext(false, true, true, false, false, 0);

            Unit parent = storageConstructor.create(creationInputs,
                                                    creationAccounting,
                                                    creationUnitType,
                                                    HATCHERY_X, HATCHERY_Y,
                                                    0,
                                                    HATCHERY_CREATION_TAG,
                                                    SPAWN_INSTRUCTION_LIMIT);

            Assertions.assertNotNull(parent);

            game.getUnitStorageConstructor().tryComplete(new UnitCreationContext(false, true, true, false, true, 0), parent,
                                                         PlayerTypes.HUMAN, SPAWN_INSTRUCTION_LIMIT);

            for (int larvaIndex = 0; larvaIndex < INITIAL_LARVA_COUNT; larvaIndex++) {
                Assertions.assertNotNull(game.getLarvaSpawner().create(new UnitCreationContext(false, true, true, false, true, 0), parent,
                                                                       PlayerTypes.HUMAN, SPAWN_INSTRUCTION_LIMIT));
            }

            UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

            Assertions.assertNotNull(spatialIndex);

            Assertions.assertEquals(INITIAL_LARVA_COUNT, spatialIndex.countLarvaeNearParent(parent));

            game.getUnitSystem().setUnitCreationContext(new UnitCreationContext(false, true, true, false, true, 0));
            UnitMemoryScenario.of(parent).writeMemoryByte(PRIMARY_ORDER_COUNTDOWN_OFFSET, 0);
            game.getLarvaSpawner().updateIdleHatchery(parent, PlayerTypes.HUMAN, SPAWN_INSTRUCTION_LIMIT);

            UnitPool units = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(units);

            Assertions.assertEquals(IDLE_HATCHERY_CASE_UNIT_COUNT, units.getAllocatedCount());

            spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

            Assertions.assertEquals(MAXIMUM_LARVA_COUNT, spatialIndex.countLarvaeNearParent(parent));
            Assertions.assertEquals(LARVA_SPAWN_COUNTDOWN, UnitMemoryScenario.of(parent).readMemoryUnsignedByte(LARVA_SPAWN_COUNTDOWN_OFFSET));

            UnitPool larvaPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(larvaPool);

            Unit larva = larvaPool.getUnit(CHECKED_LARVA_POOL_INDEX);

            Assertions.assertEquals(LARVA_TYPE, larva.getUnitId());
            Assertions.assertEquals(COMPLETED_FLAG, larva.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertSame(parent, larva.getParentUnit());

            UnitPool creationTags = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(creationTags);

            Assertions.assertEquals(HATCHERY_CREATION_TAG, creationTags.getCreationTag(CHECKED_LARVA_POOL_INDEX));

            int randomCount = game.getRandom().getTotalRandomCounts();

            game.getLarvaSpawner().updateIdleHatchery(parent, PlayerTypes.HUMAN, SPAWN_INSTRUCTION_LIMIT);

            Assertions.assertEquals(LARVA_SPAWN_COUNTDOWN, UnitMemoryScenario.of(parent).readMemoryUnsignedByte(LARVA_SPAWN_COUNTDOWN_OFFSET));
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
            Assertions.assertFalse(game.hasInitializedWorldState());
        }
    }
}
