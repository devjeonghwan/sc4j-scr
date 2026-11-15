package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Checks sight shapes and ground and air reveal vectors.
 */
final class SightCoverageTest {
    /**
     * The record count of the largest sight descriptor.
     */
    private static final int MAXIMUM_RECORD_COUNT = 489;
    /**
     * The size in bytes of the seven fields of a sight record.
     */
    private static final int RECORD_SIZE = 28;
    /**
     * The map widths whose sight shapes are compared.
     */
    private static final int @NotNull [] SHAPE_MAP_DIMENSIONS = {8, 64, 128, 256};
    /**
     * The map widths and heights of the reveal cases.
     */
    private static final int @NotNull [] REVEAL_MAP_DIMENSIONS = {8, 64};
    /**
     * The divisor of a map dimension that gives the middle coordinate of the reveal cases.
     */
    private static final int MIDDLE_COORDINATE_DIVISOR = 2;
    /**
     * The tile count of the largest reveal map.
     */
    private static final int MAXIMUM_REVEAL_TILE_COUNT = 64 * 64;
    /**
     * The player masks of the reveal cases.
     */
    private static final int @NotNull [] VISIBILITY_MASKS = {1, 0x35, 255};
    /**
     * The multiplier of the tile flag generator.
     */
    private static final int FLAG_MULTIPLIER = 1103515245;
    /**
     * The increment of the tile flag generator.
     */
    private static final int FLAG_INCREMENT = 12345;
    /**
     * The ground height and occupied bits the tile flag generator keeps.
     */
    private static final int TERRAIN_BITS = 0x0F000000;
    /**
     * The not-visible and not-explored bits of every player, which every generated tile has.
     */
    private static final int UNSEEN_AND_UNEXPLORED = 0xFFFF;
    /**
     * The tileset index of a reveal map.
     */
    private static final int TILESET_INDEX = 0;
    /**
     * The terrain height of low ground.
     */
    private static final int LOW_GROUND_HEIGHT = 0;
    /**
     * The ground heights of the reveal cases: low, middle and high ground.
     */
    private static final int @NotNull [] GROUND_HEIGHTS = {LOW_GROUND_HEIGHT, TerrainTileset.MIDDLE_GROUND_HEIGHT, TerrainTileset.HIGH_GROUND_HEIGHT};
    /**
     * The number of reveal cases.
     */
    private static final int REVEAL_CASE_COUNT = 1296;

    @Test
    void matches_native_sight_shape_cases() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteBuffer values = ByteBuffer.allocate(Integer.BYTES + MAXIMUM_RECORD_COUNT * RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int mapDimension : SHAPE_MAP_DIMENSIONS) {
            SightCoverage coverage = new SightCoverage(mapDimension);

            for (int radiusIndex = 0; radiusIndex < SightCoverage.DESCRIPTOR_COUNT; radiusIndex++) {
                values.clear();

                int count = coverage.getRecordCount(radiusIndex);

                values.putInt(count);

                for (int recordIndex = 0; recordIndex < count; recordIndex++) {
                    for (int field = 0; field < SightCoverage.RECORD_FIELD_COUNT; field++) {
                        values.putInt(coverage.getRecordField(radiusIndex, recordIndex, field));
                    }
                }

                digest.update(values.array(), 0, values.position());
            }
        }

        Assertions.assertEquals("5d4d7e4df940c7925b473c80473f0b1889ed51dbd170d5b319afaf56be78cbdf", HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void matches_native_ground_and_air_reveal_cases() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteBuffer values = ByteBuffer.allocate((MAXIMUM_REVEAL_TILE_COUNT + MAXIMUM_RECORD_COUNT) * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        int caseCount = 0;

        for (int mapDimension : REVEAL_MAP_DIMENSIONS) {
            for (int radiusIndex = 0; radiusIndex < SightCoverage.DESCRIPTOR_COUNT; radiusIndex++) {
                for (boolean air : new boolean[]{false, true}) {
                    for (int coordinate : new int[]{0, mapDimension / MIDDLE_COORDINATE_DIVISOR, mapDimension - 1}) {
                        for (int visibilityMask : VISIBILITY_MASKS) {
                            for (int terrainHeight : GROUND_HEIGHTS) {
                                SightCoverage coverage = new SightCoverage(mapDimension);
                                int[] tileFlags = new int[mapDimension * mapDimension];

                                for (int tileIndex = 0; tileIndex < tileFlags.length; tileIndex++) {
                                    tileFlags[tileIndex] = (tileIndex * FLAG_MULTIPLIER + FLAG_INCREMENT & TERRAIN_BITS) | UNSEEN_AND_UNEXPLORED;
                                }

                                GameMap map = new GameMap(mapDimension, mapDimension, TILESET_INDEX, new int[tileFlags.length], tileFlags);

                                coverage.reveal(map,
                                                coordinate * TerrainTileset.TILE_SIZE, coordinate * TerrainTileset.TILE_SIZE,
                                                air,
                                                visibilityMask,
                                                radiusIndex,
                                                terrainHeight);

                                values.clear();

                                for (int tileIndex = 0; tileIndex < map.getTileFlagCount(); tileIndex++) {
                                    values.putInt(map.getTileFlags(tileIndex));
                                }

                                for (int recordIndex = 0; recordIndex < coverage.getRecordCount(radiusIndex); recordIndex++) {
                                    values.putInt(coverage.getRecordField(radiusIndex, recordIndex, SightCoverage.RECORD_FIELD_CACHED_FLAGS));
                                }

                                digest.update(values.array(), 0, values.position());

                                caseCount++;
                            }
                        }
                    }
                }
            }
        }

        Assertions.assertEquals(REVEAL_CASE_COUNT, caseCount);
        Assertions.assertEquals("87fd01d5cbeef456612e116508e7ef71bdc8162bc8554707254c1e5b7eb54f69", HexFormat.of().formatHex(digest.digest()));
    }
}
