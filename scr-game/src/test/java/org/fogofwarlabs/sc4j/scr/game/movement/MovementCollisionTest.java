package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndexScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks the unit and terrain collision queries of {@link MovementCollision} against native captures.
 */
final class MovementCollisionTest {
    /**
     * The SHA-256 digest of the native unit motion collision capture.
     */
    private static final @NotNull String UNIT_MOTION_CAPTURE_HASH = "a70a6a5daf33022686fc337fcb61bfbb38318cc92350dd3bf34533f600ff7d6c";
    /**
     * The SHA-256 digest of the native terrain motion collision capture.
     */
    private static final @NotNull String TERRAIN_MOTION_CAPTURE_HASH = "63e82d39a8608b8453f2863a48fca179b8750884074d6753b6153560eac8de60";

    private static final int MAP_DIMENSION = 8;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;

    private static final int CAPTURED_UNIT_COUNT = 16;
    private static final int QUERY_ORIGIN = 100;
    private static final int QUERY_CASE_COUNT = 1024;
    private static final int GROUND_PATH_FLAG = 1;

    private static final int BOUNDARIES_PER_DIRECTION = 32;
    private static final int SAVED_SIZE_FIELDS_PER_DIRECTION = 2;
    private static final int SEGMENT_CASE_COUNT = 4096;
    private static final int RECTANGLE_HALF_EXTENT = 128;
    private static final long WORD_MASK = 0xFFFFL;
    private static final int RECTANGLE_TOP_SHIFT = Short.SIZE;
    private static final int RECTANGLE_RIGHT_SHIFT = Integer.SIZE;
    private static final int RECTANGLE_BOTTOM_SHIFT = 3 * Short.SIZE;

    @RequiresInstallation
    @Test
    void matches_native_swept_unit_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_UNIT_MOTION_COLLISION);

        Assertions.assertEquals(UNIT_MOTION_CAPTURE_HASH, MovementCheck.formatDigest(capture));

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(data, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(CAPTURED_UNIT_COUNT);
            game.initializeUnitSpatialIndex();

            UnitPool pool = game.getUnitSystem().getUnitPool();

            if (pool == null) {
                throw Exceptions.newNullPointerException();
            }

            for (int index = 0; index < CAPTURED_UNIT_COUNT; index++) {
                int allocated = UnitPoolScenario.allocate(pool);
                Unit unit = pool.getUnit(allocated);

                UnitMemoryScenario.of(unit).writeMemoryInteger(MovementCheck.UNIT_STATUS_OFFSET, UnitStatusFlags.GROUND_UNIT);
                UnitMemoryScenario.of(unit).writeMemoryByte(MovementCheck.UNIT_PATH_COLLISION_FLAGS_OFFSET, GROUND_PATH_FLAG);
            }

            UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

            if (spatialIndex == null) {
                throw Exceptions.newNullPointerException();
            }

            ByteBuffer records = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);

            UnitSpatialIndexScenario.seed(spatialIndex, pool, data.getUnitTypes(), records, CAPTURED_UNIT_COUNT);

            Unit incoming = pool.getUnit(1);
            Sprite sprite = SpriteScenario.createSprite(0, QUERY_ORIGIN, QUERY_ORIGIN, 0);

            incoming.setSprite(sprite);

            for (int index = 0; index < QUERY_CASE_COUNT; index++) {
                int horizontal = QUERY_ORIGIN + records.getInt();
                int vertical = QUERY_ORIGIN + records.getInt();

                sprite.setFlags(records.getInt());

                int expected = records.getInt();
                Unit result = game.getMovementCollision().findUnitCollision(incoming, horizontal, vertical);

                Assertions.assertEquals(expected, result == null ? 0 : result.getIndex(), "Query case " + index);
            }

            Assertions.assertFalse(records.hasRemaining());
        }
    }

    @Test
    void matches_native_terrain_collision_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_TERRAIN_MOTION);

        Assertions.assertEquals(TERRAIN_MOTION_CAPTURE_HASH, MovementCheck.formatDigest(capture));

        ByteBuffer records = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        long[] boundaries = new long[TerrainContours.DIRECTION_COUNT * BOUNDARIES_PER_DIRECTION];
        int savedSize = TerrainContours.SAVED_HEADER_SIZE + boundaries.length * TerrainContours.SAVED_BOUNDARY_SIZE;
        ByteBuffer saved = ByteBuffer.allocate(savedSize).order(ByteOrder.LITTLE_ENDIAN);

        // The saved contours: four pointer marks, four counts, four capacities, twelve zero control WORDs, then the records.
        saved.position(TerrainContours.DIRECTION_COUNT * Integer.BYTES);

        for (int field = 0; field < SAVED_SIZE_FIELDS_PER_DIRECTION * TerrainContours.DIRECTION_COUNT; field++) {
            saved.putShort((short) BOUNDARIES_PER_DIRECTION);
        }

        saved.position(TerrainContours.SAVED_HEADER_SIZE);

        for (int index = 0; index < boundaries.length; index++) {
            boundaries[index] = records.getLong();
            saved.putLong(boundaries[index]);
        }

        GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]);
        TerrainTileset tileset = TerrainTileset.parse(new byte[WalkableTerrainScenario.TILESET_GROUP_COUNT * TerrainTileset.CV5_RECORD_SIZE],
                                                      new byte[WalkableTerrainScenario.MEGATILE_FLAGS_SIZE]);
        TerrainContours terrain = TerrainContours.createWithoutBoundaries(map, tileset);

        terrain.readSaveData(saved.flip());

        for (int direction = 0; direction < TerrainContours.DIRECTION_COUNT; direction++) {
            Assertions.assertEquals(BOUNDARIES_PER_DIRECTION, terrain.getBoundaryCount(direction));

            for (int index = 0; index < BOUNDARIES_PER_DIRECTION; index++) {
                Assertions.assertEquals(boundaries[direction * BOUNDARIES_PER_DIRECTION + index], terrain.getBoundary(direction, index));
            }
        }

        long dimensions = records.getLong();

        for (int index = 0; index < SEGMENT_CASE_COUNT; index++) {
            int horizontal = records.getInt();
            int vertical = records.getInt();
            int destinationHorizontal = records.getInt();
            int destinationVertical = records.getInt();
            long expectedRectangle = records.getLong();
            boolean expectedClear = records.getInt() != 0;
            long rectangle = (horizontal - RECTANGLE_HALF_EXTENT & WORD_MASK) |
                             (vertical - RECTANGLE_HALF_EXTENT & WORD_MASK) << RECTANGLE_TOP_SHIFT |
                             (horizontal + RECTANGLE_HALF_EXTENT & WORD_MASK) << RECTANGLE_RIGHT_SHIFT |
                             (vertical + RECTANGLE_HALF_EXTENT & WORD_MASK) << RECTANGLE_BOTTOM_SHIFT;

            Assertions.assertEquals(expectedRectangle,
                                    MovementCollision.refreshTerrainRectangle(terrain, dimensions, horizontal, vertical, rectangle),
                                    "Rectangle case " + index);
            Assertions.assertEquals(expectedClear,
                                    MovementCollision.isTerrainSegmentClear(terrain, dimensions,
                                                                            horizontal, vertical,
                                                                            destinationHorizontal, destinationVertical),
                                    "Segment case " + index);
        }

        Assertions.assertFalse(records.hasRemaining());
    }
}
