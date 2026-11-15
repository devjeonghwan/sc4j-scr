package org.fogofwarlabs.sc4j.scr.game.unit.finder;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.spatial.SearchCounter;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitTraits;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.ArrayDatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitMovement;
import org.fogofwarlabs.sc4j.scr.game.movement.WalkableTerrainScenario;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Checks insertion ordering, reciprocal finder indices, flying unit cells, nearby moving unit queries, the stamps of unexpanded queries, the
 * flying separation and the unstacking candidate query.
 */
final class UnitSpatialIndexTest {
    private static final int UNIT_TYPE_COLUMN_BYTES = 12;
    private static final int UNIT_DIMENSION_ENTRY_SIZE = 8;
    private static final int UP_DIMENSION_OFFSET = 2;
    private static final int RIGHT_DIMENSION_OFFSET = 4;
    private static final int DOWN_DIMENSION_OFFSET = 6;
    private static final int SMALL_POOL_CAPACITY = 8;
    private static final int SMALL_TYPE_COUNT = 8;
    private static final int SINGLE_POOL_CAPACITY = 1;
    private static final int FIRST_UNIT_INDEX = 1;
    private static final int FIRST_POPULATED_TYPE_IDENTIFIER = 2;
    private static final int AXIS_COUNT = 2;
    private static final int EDGES_PER_UNIT = 2;
    private static final int SMALL_POOL_EDGE_CAPACITY = 16;
    private static final int BASE_SPRITE_X = 20;
    private static final int BASE_SPRITE_Y = 40;
    private static final int EDGE_TIE_PERIOD = 3;
    private static final int EXTENT_SIGN_PERIOD = 2;
    /**
     * The pool indices in the order the units leave the index, mixing the ends and the middle of both axes.
     */
    private static final int @NotNull [] REMOVAL_ORDER = {4, 1, 8, 3, 2, 7, 6, 5};
    private static final int REUSED_UNIT_INDEX = 4;
    /**
     * A vertical maximum edge index that does not belong to the unit.
     */
    private static final int BROKEN_EDGE_INDEX = 2;

    private static final int REGISTRATION_RESULT_SIZE = 16;
    private static final int FLYING_CASE_COUNT = 4096;
    private static final int TYPE_FLAG_PERIOD = 7;
    private static final int STATUS_CLEAR_PERIOD = 11;
    private static final int MOVING_STATUS_SAMPLE = 0x20004;
    private static final int REGISTERED_CASE_PERIOD = 3;
    private static final int REGISTRATION_VARIANT_COUNT = 32;
    private static final int UNREGISTERED_CASE_MASK = 15;
    private static final int REGISTRATION_FIELD_SAMPLE = 0xC3B2A100;
    private static final int SPRITE_COORDINATE_CASE_INTERVAL = 8;
    private static final int RANDOM_SEED = 0x12345678;
    private static final int RANDOM_SEED_STEP = 0x9E3779B9;
    private static final int FLYING_RANDOM_SOURCE = 37;
    private static final int LAST_UNIT_TYPE_WORD = 0xFFFF;
    /**
     * Ground, flying, building, resource and powerup types, the first unit type past units.dat and the largest unit type WORD.
     */
    private static final int @NotNull [] FLYING_CASE_UNIT_TYPES = {
            ScrUnitIds.TERRAN_MARINE,
            ScrUnitIds.TERRAN_GHOST,
            ScrUnitIds.TERRAN_SCV,
            ScrUnitIds.ZERG_OVERLORD,
            ScrUnitIds.PROTOSS_INTERCEPTOR,
            ScrUnitIds.TERRAN_COMMAND_CENTER,
            ScrUnitIds.RESOURCE_MINERAL_FIELD,
            ScrUnitIds.POWERUP_TERRAN_GAS_TANK_TYPE_2,
            ScrUnitIds.NONE,
            LAST_UNIT_TYPE_WORD
    };
    /**
     * Sprite coordinates around the first flying cells and the far end of the largest map.
     */
    private static final int @NotNull [] FLYING_CASE_COORDINATES = {-47, 0, 47, 48, 49, 4095, 8191, 8192};
    /**
     * Destination distances around the cell change threshold and the largest signed coordinate.
     */
    private static final int @NotNull [] FLYING_CASE_DISTANCES = {-25, -24, -23, 0, 23, 24, 25, Short.MAX_VALUE};
    private static final int FLYING_RANDOM_DRAWS = 1487;
    private static final @NotNull String REGISTER_FLYING_UNIT_DIGEST = "472b79aefa13db7eec6240ace53417a3d13d1eeb3db174a09bacc26cd9a908b4";
    private static final @NotNull String UPDATE_FLYING_UNIT_CELL_DIGEST = "7b3a4519076a7754d4e9014fde5821bf3fe8139bca06adb96d568f74abbde1f7";
    private static final int CELL_FAILURE_SPRITE_X = 97;
    private static final int CELL_FAILURE_SPRITE_Y = 49;
    private static final int CELL_FAILURE_RANDOM_DRAWS = 1;
    private static final int CELL_FAILURE_CELL_X = 2;
    private static final int CELL_FAILURE_CELL_Y = 1;

    private static final int CAPTURED_UNIT_COUNT = 64;
    private static final int LEFT_UP_EXTENT = 2;
    private static final int RIGHT_DOWN_EXTENT = 3;
    private static final int SPRITE_GRID_COLUMNS = 8;
    private static final int SPRITE_GRID_SPACING = 2;
    private static final int RELOCATED_UNIT_RESULT_SIZE = 48;
    private static final int CAPTURED_EDGE_COUNT = 128;
    /**
     * The captured relocations: the pool index, then the horizontal and vertical movement.
     */
    private static final int @NotNull [] @NotNull [] RELOCATIONS = {
            {12, -10, -10}, {4, -1, 0}, {29, 100000, 10}, {4, -10, -10}, {20, 0, 100000}, {37, -100000, 0}, {40, -1, 100000},
            {48, -100000, 0}, {18, 1, 10}, {61, 100000, -10}, {3, -100000, 10}, {62, 10, 1}, {61, 100000, -10},
            {57, -100000, -100000}, {47, -100000, 10}, {56, 1, 0}, {17, 1, -1}, {51, -1, -1}, {33, 0, 1}, {28, -1, 100000},
            {58, 0, -1}, {19, 10, -100000}, {33, -10, -100000}, {31, 1, -1}, {64, -100000, 0}, {62, 0, 10}, {23, -100000, -1},
            {59, 0, -10}, {51, 0, -10}, {36, 10, -100000}, {19, 1, -100000}, {4, -100000, 1}
    };
    private static final @NotNull String RELOCATE_DIGEST = "b8efb6d6a9f05197035b3980d29491b0f258d6db03a13e9c5464c33315d5c12e";

    private static final int MINIMUM_QUERY_DIMENSION = 6;
    /**
     * The statuses the captured units cycle through before the collision notices.
     */
    private static final int @NotNull [] COLLISION_STATUS_SAMPLES = {2, 4, 0x800000, 0};
    private static final int COLLISION_STATUS_SAMPLE = 0x40000;
    private static final int COLLISION_SECOND_INDEX = 2;
    private static final int COLLISION_THIRD_INDEX = 10;
    private static final int COLLISION_FOURTH_INDEX = 18;

    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int STATUS_OFFSET = 0x140;
    private static final int FIRST_EDGE_OFFSET = 0x1D4;
    private static final int LAST_EDGE_OFFSET = 0x1E0;
    private static final int REPULSION_FIELD_OFFSET = 0x1E4;
    private static final int REPULSION_HORIZONTAL_CAPTURE_OFFSET = 0x1E6;
    private static final int REPULSION_VERTICAL_CAPTURE_OFFSET = 0x1E7;
    private static final int REPULSION_HIGH_NIBBLE_MASK = 0xF0;
    private static final int DESTINATION_HORIZONTAL_OFFSET = 0x20;
    private static final int DESTINATION_VERTICAL_OFFSET = 0x22;
    private static final int WAYPOINT_HORIZONTAL_OFFSET = 0x30;
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int NEARBY_GROUP_OFFSET = 0x191;

    private static final int SIGNED_COORDINATE_CASE_SPLIT = 4;
    private static final int WIDE_COORDINATE_SAMPLE = 65546;
    private static final int MAXIMUM_SIGNED_COORDINATE_SAMPLE = 32767;
    private static final int MINIMUM_SIGNED_COORDINATE_WORD = 32768;
    private static final int SUBUNIT_TYPE_INDEX = 7;
    private static final int SUBUNIT_FLAG_SAMPLE = 0x10;
    private static final int FOUR_EDGE_FIELD_COUNT = 4;
    /**
     * The seven registered units of the eight, the subunit type excluded.
     */
    private static final int SIGNED_EDGE_COUNT = (SMALL_POOL_CAPACITY - 1) * EDGES_PER_UNIT;
    /**
     * The expected horizontal and vertical edges, each a pool index and a coordinate.
     */
    private static final int @NotNull [] @NotNull [] SIGNED_HORIZONTAL_EDGES = {
            {4, 9}, {4, 9}, {3, 9}, {3, 9}, {2, 10}, {2, 10}, {1, 10}, {1, 10},
            {7, 32766}, {7, 32766}, {6, 32766}, {6, 32766}, {5, 32766}, {5, 32766}
    };
    private static final int @NotNull [] @NotNull [] SIGNED_VERTICAL_EDGES = {
            {7, -32767}, {7, -32767}, {6, -32767}, {6, -32767}, {5, -32767}, {5, -32767},
            {2, 20}, {2, 20}, {1, 20}, {1, 20}, {4, 21}, {4, 21}, {3, 21}, {3, 21}
    };
    /**
     * The expected four edge indices of each unit; the subunit keeps {@code -1}.
     */
    private static final int @NotNull [] @NotNull [] SIGNED_EDGE_INDICES = {
            {7, 6, 9, 8}, {5, 4, 7, 6}, {3, 2, 13, 12}, {1, 0, 11, 10},
            {13, 12, 5, 4}, {11, 10, 3, 2}, {9, 8, 1, 0}, {-1, -1, -1, -1}
    };

    private static final int TRANSFER_RESULT_SIZE = 12;
    private static final int REPULSION_VERTICAL_CELL_SHIFT = 24;
    private static final int REPULSION_HORIZONTAL_CELL_SHIFT = 16;
    private static final int REPULSION_FLAGS_SAMPLE = 0xA100;
    private static final int HORIZONTAL_CASE_INTERVAL = 64;
    private static final int VERTICAL_CASE_INTERVAL = 512;
    private static final int DISTANCE_CASE_INTERVAL = 4;
    private static final int VERTICAL_DISTANCE_DIVISOR = 2;
    private static final int WAYPOINT_SAMPLE = 0xA5C3B2D1;
    private static final int DISPLACED_CASE_PERIOD = 2;

    private static final int NEARBY_MAP_WIDTH = 8;
    private static final int NEARBY_MAP_HEIGHT = 8;
    private static final int NEARBY_MAP_TILE_COUNT = NEARBY_MAP_WIDTH * NEARBY_MAP_HEIGHT;
    private static final int NEARBY_UNIT_COUNT = 16;
    private static final int NEARBY_GROUP = 1;
    private static final int NEARBY_RESULT_CAPACITY = 32;
    private static final int NEARBY_CASE_COUNT = 1024;
    private static final @NotNull String NEARBY_MOTION_RESOURCE_DIGEST = "c4b1bd1db695c86cb8a61d4bf54290ef2393a5307f5cd7149035b8b3fe148a87";

    private static final int REPULSION_CELL_HORIZONTAL = 5;
    private static final int REPULSION_CELL_VERTICAL = 7;

    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";

    private static final int STAMPS = 0x6BD3D0;
    private static final int IGNORED_WRITE_SAMPLE = 7;
    private static final int TILE_SIZE = 32;

    /**
     * The width and height in tiles of the map of the flying separation test, its unit capacity, and the horizontal and vertical position of the
     * separated Overlord.
     */
    private static final int SEPARATION_MAP_DIMENSION = 32;
    private static final int SEPARATION_MAP_TILE_COUNT = SEPARATION_MAP_DIMENSION * SEPARATION_MAP_DIMENSION;
    private static final int SEPARATION_UNIT_CAPACITY = 8;
    private static final int SEPARATION_POSITION = 512;
    /**
     * The shift of the fixed-point movement position, the repulsion direction of the separated Overlord and the units of its crowded cell.
     */
    private static final int FIXED_POSITION_SHIFT = 8;
    private static final int SEPARATION_REPULSION_DIRECTION = 255;
    private static final int CROWDED_CELL_UNIT_COUNT = 2;
    /**
     * The repulsion countdown that the separation counts down to zero.
     */
    private static final int LAST_REPULSION_COUNTDOWN = 1;
    /**
     * The fixed-point push of the separated Overlord to the right and upwards.
     */
    private static final int HORIZONTAL_SEPARATION_PUSH = 4;
    private static final int VERTICAL_SEPARATION_PUSH = 69;
    /**
     * The capacity of the unstacking candidate results.
     */
    private static final int CANDIDATE_CAPACITY = 3;

    private static @NotNull DatSchema createDimensionSchema(int typeCount) {
        return new DatSchema().fieldU32(UnitsDat.FLAGS, typeCount)
                              .fieldU16x4(UnitsDat.DIMENSION_LEFT, UnitsDat.DIMENSION_UP, UnitsDat.DIMENSION_RIGHT, UnitsDat.DIMENSION_DOWN,
                                          typeCount);
    }

    /**
     * Writes the four signed dimensions of a unit type into a flags and dimensions table of {@code typeCount} types.
     *
     * @param contents  the little-endian table contents
     * @param typeCount the number of unit types of the table
     * @param unitType  the unit type
     * @param left      the left dimension
     * @param up        the upper dimension
     * @param right     the right dimension
     * @param down      the lower dimension
     */
    private static void putDimensions(@NotNull ByteBuffer contents, int typeCount, int unitType, int left, int up, int right, int down) {
        int entryOffset = typeCount * Integer.BYTES + unitType * UNIT_DIMENSION_ENTRY_SIZE;

        contents.putShort(entryOffset, (short) left);
        contents.putShort(entryOffset + UP_DIMENSION_OFFSET, (short) up);
        contents.putShort(entryOffset + RIGHT_DIMENSION_OFFSET, (short) right);
        contents.putShort(entryOffset + DOWN_DIMENSION_OFFSET, (short) down);
    }

    /**
     * Creates a {@link DatTable} instance of a units.dat flags and dimensions table whose first {@link #CAPTURED_UNIT_COUNT} types extend two
     * pixels left and up and three pixels right and down.
     *
     * @return the created {@link DatTable} instance
     * @throws IOException if the table cannot be parsed
     */
    private static @NotNull DatTable createCapturedDimensions() throws IOException {
        ByteBuffer contents = ByteBuffer.allocate(UnitsDat.UNIT_COUNT * UNIT_TYPE_COLUMN_BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int unitType = 0; unitType < CAPTURED_UNIT_COUNT; unitType++) {
            putDimensions(contents, UnitsDat.UNIT_COUNT, unitType, LEFT_UP_EXTENT, LEFT_UP_EXTENT, RIGHT_DOWN_EXTENT, RIGHT_DOWN_EXTENT);
        }

        return createDimensionSchema(UnitsDat.UNIT_COUNT).parse(contents.array());
    }

    /**
     * Creates a {@link DatTable} instance of a units.dat flags table in which every seventh type is a building.
     *
     * @return the created {@link DatTable} instance
     * @throws IOException if the table cannot be parsed
     */
    private static @NotNull DatTable createFlyingCaseFlags() throws IOException {
        ByteBuffer contents = ByteBuffer.allocate(UnitsDat.UNIT_COUNT * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
            contents.putInt(unitType % TYPE_FLAG_PERIOD == 0 ? UnitTypeFlags.BUILDING : 0);
        }

        return new DatSchema().fieldU32(UnitsDat.FLAGS, UnitsDat.UNIT_COUNT).parse(contents.array());
    }

    private static @NotNull Sprite createGridSprite(int unitIndex) {
        return SpriteScenario.createSprite(0,
                                           BASE_SPRITE_X + unitIndex % SPRITE_GRID_COLUMNS * SPRITE_GRID_SPACING,
                                           BASE_SPRITE_Y + unitIndex / SPRITE_GRID_COLUMNS * SPRITE_GRID_SPACING,
                                           0);
    }

    private static @NotNull String formatDigest(@NotNull MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }


    @Test
    void remove_should_keep_edge_order_across_equal_coordinates_and_reinserted_units() throws IOException {
        ByteBuffer contents = ByteBuffer.allocate(UnitsDat.UNIT_COUNT * UNIT_TYPE_COLUMN_BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int unitType = 0; unitType < SMALL_TYPE_COUNT; unitType++) {
            putDimensions(contents, UnitsDat.UNIT_COUNT, unitType,
                          unitType % EDGE_TIE_PERIOD, -(unitType % EXTENT_SIGN_PERIOD), unitType % EXTENT_SIGN_PERIOD, -(unitType % EDGE_TIE_PERIOD));
        }

        DatTable units = createDimensionSchema(UnitsDat.UNIT_COUNT).parse(contents.array());
        UnitPool pool = new UnitPool(SMALL_POOL_CAPACITY);
        UnitSpatialIndex spatialIndex = new UnitSpatialIndex(pool);

        for (int unitIndex = 1; unitIndex <= SMALL_POOL_CAPACITY; unitIndex++) {
            Unit unit = pool.getUnit(unitIndex);

            unit.setUnitId(unitIndex - 1);
            unit.setSprite(SpriteScenario.createSprite(0,
                                                       BASE_SPRITE_X + unitIndex % EDGE_TIE_PERIOD,
                                                       BASE_SPRITE_Y - unitIndex % EDGE_TIE_PERIOD,
                                                       0));
            spatialIndex.insert(unit, UnitsDat.createTypeTable(units));
        }

        int[][] precedingUnitIndices = new int[AXIS_COUNT][SMALL_POOL_EDGE_CAPACITY];
        int[][] precedingCoordinates = new int[AXIS_COUNT][SMALL_POOL_EDGE_CAPACITY];

        for (int removedUnitIndex : REMOVAL_ORDER) {
            int precedingCount = spatialIndex.getCount();

            for (int axis = 0; axis < AXIS_COUNT; axis++) {
                for (int edgeIndex = 0; edgeIndex < precedingCount; edgeIndex++) {
                    precedingUnitIndices[axis][edgeIndex] = spatialIndex.getUnitIndex(axis == 0, edgeIndex);
                    precedingCoordinates[axis][edgeIndex] = spatialIndex.getCoordinate(axis == 0, edgeIndex);
                }
            }

            Unit removedUnit = pool.getUnit(removedUnitIndex);

            spatialIndex.remove(removedUnit);

            Assertions.assertEquals(precedingCount - EDGES_PER_UNIT, spatialIndex.getCount());
            Assertions.assertEquals(-1, removedUnit.getMinimumHorizontalEdgeIndex());
            Assertions.assertEquals(-1, removedUnit.getMaximumHorizontalEdgeIndex());
            Assertions.assertEquals(-1, removedUnit.getMinimumVerticalEdgeIndex());
            Assertions.assertEquals(-1, removedUnit.getMaximumVerticalEdgeIndex());

            for (int axis = 0; axis < AXIS_COUNT; axis++) {
                int resultIndex = 0;

                for (int sourceIndex = 0; sourceIndex < precedingCount; sourceIndex++) {
                    if (precedingUnitIndices[axis][sourceIndex] == removedUnitIndex) {
                        continue;
                    }

                    Assertions.assertEquals(precedingUnitIndices[axis][sourceIndex], spatialIndex.getUnitIndex(axis == 0, resultIndex));
                    Assertions.assertEquals(precedingCoordinates[axis][sourceIndex], spatialIndex.getCoordinate(axis == 0, resultIndex));

                    Unit remainingUnit = pool.getUnit(spatialIndex.getUnitIndex(axis == 0, resultIndex));
                    int minimumIndex = axis == 0 ? remainingUnit.getMinimumHorizontalEdgeIndex() : remainingUnit.getMinimumVerticalEdgeIndex();
                    int maximumIndex = axis == 0 ? remainingUnit.getMaximumHorizontalEdgeIndex() : remainingUnit.getMaximumVerticalEdgeIndex();

                    Assertions.assertTrue(minimumIndex == resultIndex || maximumIndex == resultIndex);
                    Assertions.assertEquals(remainingUnit.getIndex(), spatialIndex.getUnitIndex(axis == 0, minimumIndex));
                    Assertions.assertEquals(remainingUnit.getIndex(), spatialIndex.getUnitIndex(axis == 0, maximumIndex));
                    Assertions.assertNotEquals(minimumIndex, maximumIndex);

                    resultIndex++;
                }
            }

            spatialIndex.remove(removedUnit);

            Assertions.assertEquals(precedingCount - EDGES_PER_UNIT, spatialIndex.getCount());
        }

        Unit reusedUnit = pool.getUnit(REUSED_UNIT_INDEX);

        spatialIndex.insert(reusedUnit, UnitsDat.createTypeTable(units));

        Assertions.assertEquals(EDGES_PER_UNIT, spatialIndex.getCount());

        spatialIndex.remove(reusedUnit);

        Assertions.assertEquals(0, spatialIndex.getCount());
        Assertions.assertEquals(0, pool.getAllocatedCount());
    }

    @Test
    void remove_should_reject_foreign_or_broken_units_before_changing_either_axis() throws IOException {
        DatTable units = createDimensionSchema(UnitsDat.UNIT_COUNT).parse(new byte[UnitsDat.UNIT_COUNT * UNIT_TYPE_COLUMN_BYTES]);
        UnitPool pool = new UnitPool(SINGLE_POOL_CAPACITY);
        Unit unit = pool.getUnit(FIRST_UNIT_INDEX);

        unit.setSprite(SpriteScenario.createSprite(0, BASE_SPRITE_X, BASE_SPRITE_Y, 0));

        UnitSpatialIndex spatialIndex = new UnitSpatialIndex(pool);

        spatialIndex.insert(unit, UnitsDat.createTypeTable(units));

        int horizontalMinimum = unit.getMinimumHorizontalEdgeIndex();
        int horizontalMaximum = unit.getMaximumHorizontalEdgeIndex();

        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> spatialIndex.remove(new UnitPool(SINGLE_POOL_CAPACITY).getUnit(FIRST_UNIT_INDEX)));

        unit.setMaximumVerticalEdgeIndex(BROKEN_EDGE_INDEX);

        Assertions.assertThrows(IllegalStateException.class, () -> spatialIndex.remove(unit));
        Assertions.assertEquals(EDGES_PER_UNIT, spatialIndex.getCount());
        Assertions.assertEquals(horizontalMinimum, unit.getMinimumHorizontalEdgeIndex());
        Assertions.assertEquals(horizontalMaximum, unit.getMaximumHorizontalEdgeIndex());
        Assertions.assertEquals(FIRST_UNIT_INDEX, spatialIndex.getUnitIndex(true, horizontalMinimum));
        Assertions.assertEquals(FIRST_UNIT_INDEX, spatialIndex.getUnitIndex(true, horizontalMaximum));
    }

    @Test
    void matches_native_register_flying_unit_cases() throws Exception {
        DatTable units = createFlyingCaseFlags();
        UnitPool pool = new UnitPool(SINGLE_POOL_CAPACITY);
        UnitSpatialIndex spatialIndex = new UnitSpatialIndex(pool);

        spatialIndex.initializeFlyingUnits();

        Unit unit = pool.getUnit(FIRST_UNIT_INDEX);
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer result = ByteBuffer.allocate(REGISTRATION_RESULT_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int randomDraws = 0;

        for (int caseIndex = 0; caseIndex < FLYING_CASE_COUNT; caseIndex++) {
            byte[] flyingUnitCounts = spatialIndex.getFlyingUnitCounts();

            Assertions.assertNotNull(flyingUnitCounts);

            Arrays.fill(flyingUnitCounts, (byte) caseIndex);
            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, FLYING_CASE_UNIT_TYPES[caseIndex % FLYING_CASE_UNIT_TYPES.length]);
            UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, caseIndex % STATUS_CLEAR_PERIOD == 0 ? 0 : MOVING_STATUS_SAMPLE);

            int registration = caseIndex % REGISTERED_CASE_PERIOD != 0 ? caseIndex % REGISTRATION_VARIANT_COUNT : caseIndex & UNREGISTERED_CASE_MASK;

            UnitMemoryScenario.of(unit).writeMemoryInteger(REPULSION_FIELD_OFFSET, REGISTRATION_FIELD_SAMPLE | registration);
            unit.setSprite(SpriteScenario.createSprite(0,
                                                       FLYING_CASE_COORDINATES[(caseIndex / SPRITE_COORDINATE_CASE_INTERVAL) %
                                                                               FLYING_CASE_COORDINATES.length],
                                                       FLYING_CASE_COORDINATES[caseIndex % FLYING_CASE_COORDINATES.length],
                                                       0));

            LcgRandom random = new LcgRandom(RANDOM_SEED + caseIndex * RANDOM_SEED_STEP);

            spatialIndex.registerFlyingUnit(unit, UnitsDat.createTypeTable(units), random);

            result.clear();
            result.putInt(UnitMemoryScenario.of(unit).readMemoryInteger(REPULSION_FIELD_OFFSET));
            result.putInt(random.getState());
            result.putInt(random.getTotalRandomCounts());
            result.putInt(random.getCallCount(FLYING_RANDOM_SOURCE));

            randomDraws += random.getTotalRandomCounts();

            digest.update(result.array());
            digest.update(spatialIndex.getFlyingUnitCounts());
        }

        Assertions.assertEquals(FLYING_RANDOM_DRAWS, randomDraws);
        Assertions.assertEquals(REGISTER_FLYING_UNIT_DIGEST, formatDigest(digest));
    }

    @Test
    void register_flying_unit_should_keep_its_writes_when_count_storage_is_unavailable() throws IOException {
        DatTable units = new DatSchema().fieldU32(UnitsDat.FLAGS, UnitsDat.UNIT_COUNT).parse(new byte[UnitsDat.UNIT_COUNT * Integer.BYTES]);
        UnitPool pool = new UnitPool(SINGLE_POOL_CAPACITY);
        UnitSpatialIndex spatialIndex = new UnitSpatialIndex(pool);
        Unit unit = pool.getUnit(FIRST_UNIT_INDEX);

        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.ZERG_OVERLORD);
        UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, MOVING_STATUS_SAMPLE);
        unit.setSprite(SpriteScenario.createSprite(0, CELL_FAILURE_SPRITE_X, CELL_FAILURE_SPRITE_Y, 0));

        LcgRandom random = new LcgRandom(RANDOM_SEED);

        Assertions.assertThrows(IllegalStateException.class, () -> spatialIndex.registerFlyingUnit(unit, UnitsDat.createTypeTable(units), random));

        Assertions.assertEquals(CELL_FAILURE_RANDOM_DRAWS, random.getCallCount(FLYING_RANDOM_SOURCE));
        Assertions.assertEquals(CELL_FAILURE_CELL_X, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(REPULSION_HORIZONTAL_CAPTURE_OFFSET));
        Assertions.assertEquals(CELL_FAILURE_CELL_Y, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(REPULSION_VERTICAL_CAPTURE_OFFSET));
        Assertions.assertTrue((UnitMemoryScenario.of(unit).readMemoryUnsignedByte(REPULSION_FIELD_OFFSET) & REPULSION_HIGH_NIBBLE_MASK) != 0);
    }

    @Test
    void matches_native_relocate_cases() throws Exception {
        DatTable units = createCapturedDimensions();
        UnitPool pool = new UnitPool(CAPTURED_UNIT_COUNT);
        UnitSpatialIndex spatialIndex = new UnitSpatialIndex(pool);

        for (int unitIndex = 0; unitIndex < CAPTURED_UNIT_COUNT; unitIndex++) {
            Unit unit = pool.getUnit(unitIndex + 1);

            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitIndex);
            unit.setSprite(createGridSprite(unitIndex));
            spatialIndex.insert(unit, UnitsDat.createTypeTable(units));
        }

        for (int[] relocation : RELOCATIONS) {
            spatialIndex.relocate(pool.getUnit(relocation[0]), relocation[1], relocation[2]);
        }

        ByteBuffer result = ByteBuffer.allocate(CAPTURED_UNIT_COUNT * RELOCATED_UNIT_RESULT_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            for (int edgeIndex = 0; edgeIndex < CAPTURED_EDGE_COUNT; edgeIndex++) {
                result.putInt(spatialIndex.getUnitIndex(axis == 0, edgeIndex));
                result.putInt(spatialIndex.getCoordinate(axis == 0, edgeIndex));
            }
        }

        for (int unitIndex = 1; unitIndex <= CAPTURED_UNIT_COUNT; unitIndex++) {
            for (int fieldOffset = FIRST_EDGE_OFFSET; fieldOffset <= LAST_EDGE_OFFSET; fieldOffset += Integer.BYTES) {
                result.putInt(UnitMemoryScenario.of(pool.getUnit(unitIndex)).readMemoryInteger(fieldOffset));
            }
        }

        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);

        digest.update(result.array());

        Assertions.assertEquals(RELOCATE_DIGEST, formatDigest(digest));
    }

    @Test
    void notify_collision_should_set_the_collision_status_on_overlapping_units() throws IOException {
        DatTable units = createCapturedDimensions();
        UnitPool pool = new UnitPool(CAPTURED_UNIT_COUNT);
        UnitSpatialIndex spatialIndex = new UnitSpatialIndex(pool);

        spatialIndex.initializeQueryBounds(UnitsDat.createTypeTable(units));

        Assertions.assertTrue(spatialIndex.hasQueryBounds());
        Assertions.assertEquals(MINIMUM_QUERY_DIMENSION, spatialIndex.getMinimumQueryDimension(true));
        Assertions.assertEquals(MINIMUM_QUERY_DIMENSION, spatialIndex.getMinimumQueryDimension(false));

        for (int unitIndex = 0; unitIndex < CAPTURED_UNIT_COUNT; unitIndex++) {
            Unit unit = pool.getUnit(unitIndex + 1);

            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitIndex);
            UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, COLLISION_STATUS_SAMPLES[unitIndex % COLLISION_STATUS_SAMPLES.length]);
            unit.setSprite(createGridSprite(unitIndex));

            for (int offset = FIRST_EDGE_OFFSET; offset <= LAST_EDGE_OFFSET; offset += Integer.BYTES) {
                UnitMemoryScenario.of(unit).writeMemoryInteger(offset, -1);
            }

            spatialIndex.insert(unit, UnitsDat.createTypeTable(units));
        }

        spatialIndex.notifyCollision(pool.getUnit(FIRST_UNIT_INDEX), UnitsDat.createTypeTable(units));

        for (int unitIndex = 0; unitIndex < CAPTURED_UNIT_COUNT; unitIndex++) {
            int expected = COLLISION_STATUS_SAMPLES[unitIndex % COLLISION_STATUS_SAMPLES.length];

            if (unitIndex == 0 || unitIndex == COLLISION_SECOND_INDEX || unitIndex == COLLISION_THIRD_INDEX || unitIndex == COLLISION_FOURTH_INDEX) {
                expected |= COLLISION_STATUS_SAMPLE;
            }

            Assertions.assertEquals(expected, UnitMemoryScenario.of(pool.getUnit(unitIndex + 1)).readMemoryInteger(STATUS_OFFSET));
        }

        for (int unitIndex = 1; unitIndex < CAPTURED_UNIT_COUNT; unitIndex++) {
            spatialIndex.notifyCollision(pool.getUnit(unitIndex + 1), UnitsDat.createTypeTable(units));
        }

        for (int unitIndex = 0; unitIndex < CAPTURED_UNIT_COUNT; unitIndex++) {
            Assertions.assertEquals(COLLISION_STATUS_SAMPLES[unitIndex % COLLISION_STATUS_SAMPLES.length] | COLLISION_STATUS_SAMPLE,
                                    UnitMemoryScenario.of(pool.getUnit(unitIndex + 1)).readMemoryInteger(STATUS_OFFSET));
        }
    }

    @Test
    void insert_should_order_equal_edges_with_signed_extents_and_skip_subunits() throws IOException {
        ByteBuffer contents = ByteBuffer.allocate(SMALL_TYPE_COUNT * UNIT_TYPE_COLUMN_BYTES).order(ByteOrder.LITTLE_ENDIAN);

        contents.putInt(SUBUNIT_TYPE_INDEX * Integer.BYTES, SUBUNIT_FLAG_SAMPLE);

        for (int unitIndex = FIRST_POPULATED_TYPE_IDENTIFIER; unitIndex < SMALL_POOL_CAPACITY; unitIndex++) {
            putDimensions(contents, SMALL_TYPE_COUNT, unitIndex, 1, -1, -1, 1);
        }

        DatTable units = createDimensionSchema(SMALL_TYPE_COUNT).parse(contents.array());
        UnitPool pool = new UnitPool(SMALL_POOL_CAPACITY);
        UnitSpatialIndex spatialIndex = new UnitSpatialIndex(pool);

        for (int unitIndex = 0; unitIndex < SMALL_POOL_CAPACITY; unitIndex++) {
            Unit unit = pool.getUnit(unitIndex + 1);
            boolean wideCase = unitIndex < SIGNED_COORDINATE_CASE_SPLIT;

            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitIndex);
            unit.setSprite(SpriteScenario.createSprite(0,
                                                       wideCase ? WIDE_COORDINATE_SAMPLE : MAXIMUM_SIGNED_COORDINATE_SAMPLE,
                                                       wideCase ? BASE_SPRITE_X : MINIMUM_SIGNED_COORDINATE_WORD,
                                                       0));

            for (int offset = FIRST_EDGE_OFFSET; offset <= LAST_EDGE_OFFSET; offset += Integer.BYTES) {
                UnitMemoryScenario.of(unit).writeMemoryInteger(offset, -1);
            }

            spatialIndex.insert(unit, UnitsDat.createTypeTable(units));
        }

        Assertions.assertEquals(SIGNED_EDGE_COUNT, spatialIndex.getCount());
        Assertions.assertEquals(0, pool.getAllocatedCount());

        for (int edgeIndex = 0; edgeIndex < SIGNED_HORIZONTAL_EDGES.length; edgeIndex++) {
            Assertions.assertEquals(SIGNED_HORIZONTAL_EDGES[edgeIndex][0], spatialIndex.getUnitIndex(true, edgeIndex));
            Assertions.assertEquals(SIGNED_HORIZONTAL_EDGES[edgeIndex][1], spatialIndex.getCoordinate(true, edgeIndex));
            Assertions.assertEquals(SIGNED_VERTICAL_EDGES[edgeIndex][0], spatialIndex.getUnitIndex(false, edgeIndex));
            Assertions.assertEquals(SIGNED_VERTICAL_EDGES[edgeIndex][1], spatialIndex.getCoordinate(false, edgeIndex));
        }

        for (int unitIndex = 0; unitIndex < SIGNED_EDGE_INDICES.length; unitIndex++) {
            UnitMemoryScenario capture = UnitMemoryScenario.of(pool.getUnit(unitIndex + 1));

            for (int fieldIndex = 0; fieldIndex < FOUR_EDGE_FIELD_COUNT; fieldIndex++) {
                Assertions.assertEquals(SIGNED_EDGE_INDICES[unitIndex][fieldIndex],
                                        capture.readMemoryInteger(FIRST_EDGE_OFFSET + fieldIndex * Integer.BYTES));
            }
        }
    }

    @Test
    void matches_native_update_flying_unit_cell_cases() throws Exception {
        DatTable units = createFlyingCaseFlags();
        UnitPool pool = new UnitPool(SINGLE_POOL_CAPACITY);
        UnitSpatialIndex spatialIndex = new UnitSpatialIndex(pool);

        spatialIndex.initializeFlyingUnits();

        Unit unit = pool.getUnit(FIRST_UNIT_INDEX);
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer result = ByteBuffer.allocate(TRANSFER_RESULT_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int randomDraws = 0;

        for (int caseIndex = 0; caseIndex < FLYING_CASE_COUNT; caseIndex++) {
            byte[] flyingUnitCounts = spatialIndex.getFlyingUnitCounts();

            Assertions.assertNotNull(flyingUnitCounts);

            Arrays.fill(flyingUnitCounts, (byte) caseIndex);
            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, FLYING_CASE_UNIT_TYPES[caseIndex % FLYING_CASE_UNIT_TYPES.length]);
            UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, caseIndex % STATUS_CLEAR_PERIOD == 0 ? 0 : MOVING_STATUS_SAMPLE);

            int registration = caseIndex % REGISTERED_CASE_PERIOD != 0 ? caseIndex % REGISTRATION_VARIANT_COUNT : caseIndex & UNREGISTERED_CASE_MASK;
            int cellVertical = caseIndex % UnitSpatialIndex.FLYING_GRID_DIMENSION;
            int cellHorizontal = caseIndex / UnitSpatialIndex.FLYING_GRID_DIMENSION % UnitSpatialIndex.FLYING_GRID_DIMENSION;
            int repulsion = cellVertical << REPULSION_VERTICAL_CELL_SHIFT | cellHorizontal << REPULSION_HORIZONTAL_CELL_SHIFT |
                            REPULSION_FLAGS_SAMPLE | registration;

            UnitMemoryScenario.of(unit).writeMemoryInteger(REPULSION_FIELD_OFFSET, repulsion);
            unit.setSprite(SpriteScenario.createSprite(0,
                                                       FLYING_CASE_COORDINATES[(caseIndex / SPRITE_COORDINATE_CASE_INTERVAL) %
                                                                               FLYING_CASE_COORDINATES.length],
                                                       FLYING_CASE_COORDINATES[caseIndex % FLYING_CASE_COORDINATES.length],
                                                       0));

            int nextHorizontal = FLYING_CASE_COORDINATES[(caseIndex / HORIZONTAL_CASE_INTERVAL) % FLYING_CASE_COORDINATES.length];
            int nextVertical = FLYING_CASE_COORDINATES[(caseIndex / VERTICAL_CASE_INTERVAL) % FLYING_CASE_COORDINATES.length];
            int distance = FLYING_CASE_DISTANCES[(caseIndex / DISTANCE_CASE_INTERVAL) % FLYING_CASE_DISTANCES.length];

            UnitMemoryScenario.of(unit).writeMemoryWord(DESTINATION_HORIZONTAL_OFFSET, nextHorizontal + distance);
            UnitMemoryScenario.of(unit).writeMemoryWord(DESTINATION_VERTICAL_OFFSET,
                                                        nextVertical + Math.floorDiv(distance, VERTICAL_DISTANCE_DIVISOR));
            UnitMemoryScenario.of(unit).writeMemoryInteger(WAYPOINT_HORIZONTAL_OFFSET, WAYPOINT_SAMPLE);

            LcgRandom random = new LcgRandom(RANDOM_SEED + caseIndex * RANDOM_SEED_STEP);

            spatialIndex.updateFlyingUnitCell(unit,
                                              UnitsDat.createTypeTable(units),
                                              random,
                                              caseIndex % DISPLACED_CASE_PERIOD != 0,
                                              nextHorizontal,
                                              nextVertical);

            digest.update(UnitMemoryScenario.of(unit).exportMemoryBytes());
            result.clear();
            result.putInt(random.getState());
            result.putInt(random.getTotalRandomCounts());
            result.putInt(random.getCallCount(FLYING_RANDOM_SOURCE));
            digest.update(result.array());
            digest.update(spatialIndex.getFlyingUnitCounts());

            randomDraws += random.getTotalRandomCounts();
        }

        Assertions.assertEquals(FLYING_RANDOM_DRAWS, randomDraws);
        Assertions.assertEquals(UPDATE_FLYING_UNIT_CELL_DIGEST, formatDigest(digest));
    }

    @RequiresInstallation
    @Test
    void matches_native_collect_nearby_moving_units_cases() throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_NEARBY_MOTION);
        MessageDigest resourceDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);

        resourceDigest.update(bytes);

        Assertions.assertEquals(NEARBY_MOTION_RESOURCE_DIGEST, formatDigest(resourceDigest));

        Path installation = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installation);

        try (GameData data = GameData.loadFrom(installation)) {
            Game game = Game.create(data, new GameMap(NEARBY_MAP_WIDTH, NEARBY_MAP_HEIGHT, 0, new int[NEARBY_MAP_TILE_COUNT]), new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(NEARBY_UNIT_COUNT);
            game.initializeUnitSpatialIndex();

            UnitPool pool = game.getUnitSystem().getUnitPool();

            if (pool == null) {
                throw Exceptions.newNullPointerException();
            }

            for (int index = 0; index < NEARBY_UNIT_COUNT; index++) {
                int allocated = UnitPoolScenario.allocate(pool);
                Unit unit = pool.getUnit(allocated);

                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, UnitStatusFlags.GROUND_UNIT);
                UnitMemoryScenario.of(unit).writeMemoryByte(NEARBY_GROUP_OFFSET, NEARBY_GROUP);
            }

            UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

            if (spatialIndex == null) {
                throw Exceptions.newNullPointerException();
            }

            ByteBuffer records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

            UnitSpatialIndexScenario.seed(spatialIndex, pool, data.getUnitTypes(), records, NEARBY_UNIT_COUNT);

            for (int unitIndex = 1; unitIndex <= NEARBY_UNIT_COUNT; unitIndex++) {
                Unit unit = pool.getUnit(unitIndex);

                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, records.getInt());
                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, records.getInt());
                UnitMemoryScenario.of(unit).writeMemoryInteger(DESTINATION_HORIZONTAL_OFFSET, records.getInt());

                int current = records.getInt();

                unit.setSprite(SpriteScenario.createSprite(0, (short) current, (short) (current >>> Short.SIZE), 0));
                UnitMemoryScenario.of(unit).writeMemoryByte(NEARBY_GROUP_OFFSET, records.getInt());
                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, records.getInt());
            }

            Unit incoming = pool.getUnit(FIRST_UNIT_INDEX);
            int[] results = new int[NEARBY_RESULT_CAPACITY];
            UnitCollisionRules rules = UnitTestUtils.createCollisionRules();

            for (int index = 0; index < NEARBY_CASE_COUNT; index++) {
                int range = records.getInt();
                int limit = records.getInt();
                Sprite sprite = incoming.getSpriteOrNull();

                if (sprite == null) {
                    throw Exceptions.newNullPointerException();
                }

                sprite.setFlags(records.getInt());

                int expectedCount = records.getInt();
                int count = spatialIndex.collectNearbyMovingUnits(rules, incoming, range, limit, results);

                Assertions.assertEquals(expectedCount, count, "Count case " + index);

                for (int resultIndex = 0; resultIndex < count; resultIndex++) {
                    Assertions.assertEquals(records.getInt(), results[resultIndex], "Ordered entry case " + index + ":" + resultIndex);
                }
            }

            Assertions.assertFalse(records.hasRemaining());
        }
    }

    @Test
    void unregister_flying_unit_should_count_down_a_flyer_whose_type_is_past_units_dat() {
        int[] flags = new int[UnitsDat.UNIT_COUNT];
        ScrUnitTypeView units = new ScrUnitTypeView(column -> new ArrayDatColumn(column == ScrUnitTypeView.Column.FLAGS ? flags : new int[UnitsDat.UNIT_COUNT]),
                                                    ScrUnitTraits.createColumns(),
                                                    UnitsDat.REQUIREMENT_INDEX,
                                                    (column, index) -> UnitTypeFlags.BUILDING);
        UnitPool pool = new UnitPool(SINGLE_POOL_CAPACITY);
        UnitSpatialIndex spatialIndex = new UnitSpatialIndex(pool);
        Unit unit = pool.getUnit(FIRST_UNIT_INDEX);

        spatialIndex.initializeFlyingUnits();
        unit.setUnitId(ScrUnitIds.NONE);
        unit.setStatus(UnitStatusFlags.CAN_MOVE);
        unit.setRepulsionCellX(REPULSION_CELL_HORIZONTAL);
        unit.setRepulsionCellY(REPULSION_CELL_VERTICAL);

        byte[] flyingUnitCounts = spatialIndex.getFlyingUnitCounts();
        int cellIndex = REPULSION_CELL_VERTICAL * UnitSpatialIndex.FLYING_GRID_DIMENSION + REPULSION_CELL_HORIZONTAL;

        Assertions.assertNotNull(flyingUnitCounts);

        flyingUnitCounts[cellIndex] = 1;
        spatialIndex.unregisterFlyingUnit(unit, units);

        Assertions.assertEquals(0, flyingUnitCounts[cellIndex]);
    }

    @RequiresInstallation
    @Test
    void unexpanded_query_stamps_visited_units_with_the_counter() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = UnitFinderScenario.start(gameData);
            UnitSpatialIndex finder = game.getUnitSystem().getUnitSpatialIndex();
            SearchCounter counter = game.getUnitSystem().getUnitFinderSearchCounter();
            int before = counter.getValue();

            Assertions.assertTrue(before > 0, "The first frames already ran unexpanded queries.");

            int width = game.getMap().getWidth() * TILE_SIZE;
            int height = game.getMap().getHeight() * TILE_SIZE;

            Assertions.assertNotNull(finder);

            int[] found = finder.findAllInRectangleUnexpanded(0, 0, width, height);

            Assertions.assertEquals(before + 1, counter.getValue());
            Assertions.assertTrue(found.length > 0);

            ClassicAddressSpace space = ClassicAddressSpace.create(game);

            for (int poolIndex : found) {
                Assertions.assertEquals(before + 1, space.readDword(STAMPS + Integer.BYTES * poolIndex));
            }

            space.writeDword(STAMPS + Integer.BYTES * found[0], IGNORED_WRITE_SAMPLE);

            Assertions.assertEquals(before + 1, space.readDword(STAMPS + Integer.BYTES * found[0]), "The region type 0x2D is write-ignored.");

            // A second query visits every unit again: the stamps of the first one differ from the new counter.
            Assertions.assertEquals(found.length, finder.findAllInRectangleUnexpanded(0, 0, width, height).length);
        }
    }

    @RequiresInstallation
    @Test
    void apply_flying_separation_should_push_crowded_flyer_using_truncating_division() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(gameData,
                                    new GameMap(SEPARATION_MAP_DIMENSION, SEPARATION_MAP_DIMENSION, 0, new int[SEPARATION_MAP_TILE_COUNT]),
                                    new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(SEPARATION_UNIT_CAPACITY);
            game.initializeUnitSpatialIndex();

            UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

            Assertions.assertNotNull(spatialIndex);

            spatialIndex.initializeFlyingUnits();

            Unit unit = UnitTestUtils.createUnit(1);

            unit.setUnitId(ScrUnitIds.ZERG_OVERLORD);
            unit.setStatus(UnitStatusFlags.CAN_MOVE);
            unit.setSprite(SpriteScenario.createSprite(0, SEPARATION_POSITION, SEPARATION_POSITION, 0));
            unit.setPositionX(SEPARATION_POSITION);
            unit.setPositionY(SEPARATION_POSITION);
            unit.setMovementDestinationX(SEPARATION_POSITION);
            unit.setMovementDestinationY(SEPARATION_POSITION);
            unit.setRepulsionDirection(SEPARATION_REPULSION_DIRECTION);
            unit.setRepulsionFlags(LAST_REPULSION_COUNTDOWN);

            byte[] flyingUnitCounts = spatialIndex.getFlyingUnitCounts();

            Assertions.assertNotNull(flyingUnitCounts);

            flyingUnitCounts[0] = CROWDED_CELL_UNIT_COUNT;

            UnitMovement.Context context = new UnitMovement.Context(0, 0);

            context.setFixedPositionX(SEPARATION_POSITION << FIXED_POSITION_SHIFT);
            context.setFixedPositionY(SEPARATION_POSITION << FIXED_POSITION_SHIFT);

            Assertions.assertTrue(spatialIndex.applyFlyingSeparation(context, game, unit));

            Assertions.assertEquals((SEPARATION_POSITION << FIXED_POSITION_SHIFT) + HORIZONTAL_SEPARATION_PUSH, context.getFixedPositionX());
            Assertions.assertEquals((SEPARATION_POSITION << FIXED_POSITION_SHIFT) - VERTICAL_SEPARATION_PUSH, context.getFixedPositionY());
            Assertions.assertEquals(0, unit.getRepulsionFlags());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());

            unit.setStatus(unit.getStatus() | UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE);

            Assertions.assertFalse(spatialIndex.applyFlyingSeparation(context, game, unit));
        }
    }

    @RequiresInstallation
    @Test
    void collect_unstacking_candidates_should_use_half_open_bounds_without_duplicates() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = WalkableTerrainScenario.createStackedUnitGame(gameData);
            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit unit = unitPool.getUnit(WalkableTerrainScenario.UNIT_POOL_INDEX);
            UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

            Assertions.assertNotNull(spatialIndex);

            spatialIndex.remove(unitPool.getUnit(WalkableTerrainScenario.BLOCKER_POOL_INDEX));

            int left = spatialIndex.getCoordinate(true, unit.getMinimumHorizontalEdgeIndex());
            int top = spatialIndex.getCoordinate(false, unit.getMinimumVerticalEdgeIndex());
            int right = spatialIndex.getCoordinate(true, unit.getMaximumHorizontalEdgeIndex());
            int bottom = spatialIndex.getCoordinate(false, unit.getMaximumVerticalEdgeIndex());
            int[] results = new int[CANDIDATE_CAPACITY];

            Assertions.assertEquals(0, spatialIndex.collectUnstackingCandidates(left - 1, top - 1, left, bottom + 1, results));
            Assertions.assertEquals(0, spatialIndex.collectUnstackingCandidates(left - 1, top - 1, right + 1, top, results));
            Assertions.assertEquals(1, spatialIndex.collectUnstackingCandidates(left - 1, top - 1, left + 1, bottom + 1, results));
            Assertions.assertEquals(1, results[0]);
            Assertions.assertEquals(1, spatialIndex.collectUnstackingCandidates(left - 1, top - 1, right + 1, bottom + 1, results));
            Assertions.assertEquals(1, results[0]);
        }
    }
}
