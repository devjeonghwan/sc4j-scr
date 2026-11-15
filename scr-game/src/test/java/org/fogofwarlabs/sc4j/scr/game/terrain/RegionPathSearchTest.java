package org.fogofwarlabs.sc4j.scr.game.terrain;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks region routes, disconnected-component fallback and search limits.
 */
final class RegionPathSearchTest {
    /**
     * The saved pathing data: the tile-region WORDs and the 64-byte region records with their fields.
     */
    private static final int SAVED_TILE_REGIONS = 0x0C;
    private static final int SAVED_REGIONS = 0x449FC;
    private static final int SAVED_REGION_SIZE = 0x40;
    private static final int SAVED_COMPONENT = 0x02;
    private static final int SAVED_WALKABLE_NEIGHBORS = 0x06;
    private static final int SAVED_NEIGHBORS = 0x07;
    private static final int SAVED_NEIGHBOR_LIST = 0x0C;
    private static final int SAVED_CENTER_X = 0x10;
    private static final int SAVED_INLINE_NEIGHBORS = 0x2C;

    /**
     * The fixed-point shift of a region center and the tile size in fixed point.
     */
    private static final int FIXED_POSITION_SHIFT = 8;
    private static final int FIXED_TILE_SIZE = TerrainTileset.TILE_SIZE << FIXED_POSITION_SHIFT;

    /**
     * The tile of region {@code 4} of the four-region graphs and its left pixel, the destination of their searches.
     */
    private static final int LAST_REGION_TILE = 3;
    private static final int DESTINATION_POSITION_X = LAST_REGION_TILE * TerrainTileset.TILE_SIZE;

    /**
     * The replay maps of the captured cases, the cases of each map and the bytes of one case record.
     */
    private static final int CASE_MAP_COUNT = 2;
    private static final int CASES_PER_MAP = 2048;
    private static final int CASE_RECORD_SIZE = 144;

    /**
     * The number of route region WORDs of one case record and the mask of the saved full route count.
     */
    private static final int ROUTE_CAPACITY = 50;
    private static final int BYTE_MASK = 0xFF;

    private static @NotNull TerrainRegions createRegionGraph(int @NotNull [] components,
                                                             int @NotNull [] @NotNull [] neighbors,
                                                             int @NotNull [] walkableCounts) throws IOException {
        int[] centers = new int[components.length];

        for (int region = 1; region < components.length; region++) {
            centers[region] = (region - 1) * TerrainTileset.TILE_SIZE << FIXED_POSITION_SHIFT;
        }

        return createRegionGraph(components, neighbors, walkableCounts, centers);
    }

    /**
     * Creates a {@link TerrainRegions} instance from saved pathing data: region {@code r} covers tile {@code r - 1} of the first row, lists its
     * neighbors inline and has the given component, walkable neighbor count and fixed-point horizontal center.
     *
     * @param components             the component of each region
     * @param neighbors              the inline neighbors of each region
     * @param walkableCounts         the walkable neighbor count of each region
     * @param centersHorizontalFixed the fixed-point horizontal center of each region
     * @return the created {@link TerrainRegions} instance
     * @throws IOException if the empty tileset input cannot be parsed
     */
    private static @NotNull TerrainRegions createRegionGraph(int @NotNull [] components,
                                                             int @NotNull [] @NotNull [] neighbors,
                                                             int @NotNull [] walkableCounts,
                                                             int @NotNull [] centersHorizontalFixed) throws IOException {
        ByteBuffer saved = ByteBuffer.allocate(TerrainRegions.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int region = 0; region < TerrainRegions.REGION_COUNT; region++) {
            saved.putInt(SAVED_REGIONS + region * SAVED_REGION_SIZE + SAVED_NEIGHBOR_LIST, TerrainRegionCheck.INLINE_NEIGHBOR_LIST);
        }

        for (int region = 1; region < components.length; region++) {
            int record = SAVED_REGIONS + region * SAVED_REGION_SIZE;

            saved.putShort(SAVED_TILE_REGIONS + (region - 1) * Short.BYTES, (short) region);
            saved.putShort(record + SAVED_COMPONENT, (short) components[region]);
            saved.putInt(record + SAVED_CENTER_X, centersHorizontalFixed[region]);
            saved.put(record + SAVED_WALKABLE_NEIGHBORS, (byte) walkableCounts[region]);
            saved.put(record + SAVED_NEIGHBORS, (byte) neighbors[region].length);

            for (int neighbor = 0; neighbor < neighbors[region].length; neighbor++) {
                saved.putShort(record + SAVED_INLINE_NEIGHBORS + neighbor * Short.BYTES, (short) neighbors[region][neighbor]);
            }
        }

        TerrainRegions regions = TerrainRegionCheck.createEmptyRegions();

        regions.readSaveData(saved);

        return regions;
    }


    @Test
    void search_should_reverse_obstructed_destination_route_and_stop_at_source_component() throws IOException {
        TerrainRegions regions = createRegionGraph(new int[]{0, 1, 1, 2, 3}, new int[][]{{}, {2}, {1, 3}, {2, 4}, {3}}, new int[]{0, 1, 1, 2, 0});
        RegionPathSearch search = new RegionPathSearch(regions);

        Assertions.assertTrue(search.search(0, 0, DESTINATION_POSITION_X, 0));
        Assertions.assertFalse(search.isIncomplete());
        Assertions.assertEquals(2, search.getRouteCount());
        Assertions.assertEquals(1, search.getRouteRegion(0));
        Assertions.assertEquals(2, search.getRouteRegion(1));
    }

    @Test
    void search_should_retry_forward_toward_closest_reverse_node_when_disconnected() throws IOException {
        TerrainRegions regions = createRegionGraph(new int[]{0, 1, 1, 2, 3}, new int[][]{{}, {2}, {1}, {4}, {3}}, new int[]{0, 1, 1, 1, 0});
        RegionPathSearch search = new RegionPathSearch(regions);

        Assertions.assertTrue(search.search(0, 0, DESTINATION_POSITION_X, 0));
        Assertions.assertTrue(search.isIncomplete());
        Assertions.assertEquals(2, search.getRouteCount());
        Assertions.assertEquals(1, search.getRouteRegion(0));
        Assertions.assertEquals(2, search.getRouteRegion(1));
        Assertions.assertEquals(2, search.getNodeCount());
    }

    @Test
    void search_should_preserve_native_heap_tie_promotion_in_reverse_search() throws IOException {
        TerrainRegions regions = createRegionGraph(new int[]{0, 1, 1, 1, 2},
                                                   new int[][]{{}, {2, 3}, {1, 4}, {1, 4}, {2, 3}},
                                                   new int[]{0, 2, 1, 1, 2},
                                                   new int[]{0, 0, FIXED_TILE_SIZE, FIXED_TILE_SIZE, LAST_REGION_TILE * FIXED_TILE_SIZE});
        RegionPathSearch search = new RegionPathSearch(regions);

        Assertions.assertTrue(search.search(0, 0, DESTINATION_POSITION_X, 0));
        Assertions.assertEquals(2, search.getRouteCount());
        Assertions.assertEquals(1, search.getRouteRegion(0));
        Assertions.assertEquals(3, search.getRouteRegion(1));
    }

    @RequiresInstallation
    @Test
    void matches_native_region_path_search_cases() throws Exception {
        byte[] expected = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_REGION_ROUTES);

        Assertions.assertEquals(CASE_MAP_COUNT * CASES_PER_MAP * CASE_RECORD_SIZE, expected.length);

        ByteBuffer fixture = ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN);
        String[] replayFiles = {GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT, GameTestResources.REPLAY_PVP_FORTRESS};

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            for (int mapIndex = 0; mapIndex < replayFiles.length; mapIndex++) {
                ReplayMapScenario replayMap = ReplayMapScenario.load(storage, replayFiles[mapIndex]);
                RegionPathSearch search = new RegionPathSearch(TerrainContours.create(replayMap.getMap(), replayMap.getTerrain()).getRegions());

                for (int caseIndex = 0; caseIndex < CASES_PER_MAP; caseIndex++) {
                    Assertions.assertEquals(mapIndex, fixture.getInt());

                    int sourcePositionX = fixture.getInt();
                    int sourcePositionY = fixture.getInt();
                    int destinationPositionX = fixture.getInt();
                    int destinationPositionY = fixture.getInt();
                    String label = "map=" + mapIndex + ",case=" + caseIndex;

                    Assertions.assertEquals(fixture.getInt() != 0,
                                            search.search(sourcePositionX, sourcePositionY, destinationPositionX, destinationPositionY),
                                            label);
                    Assertions.assertEquals(fixture.getInt() != 0, search.isIncomplete(), label);
                    Assertions.assertEquals(fixture.getInt(), search.getNodeCount(), label);
                    Assertions.assertEquals(fixture.getInt(), search.getMaximumHeapCount(), label);
                    Assertions.assertEquals(fixture.getInt(), search.getFullRouteCount() & BYTE_MASK, label);
                    Assertions.assertEquals(fixture.getInt(), search.getRouteCount(), label);

                    for (int routeIndex = 0; routeIndex < ROUTE_CAPACITY; routeIndex++) {
                        int region = Short.toUnsignedInt(fixture.getShort());

                        if (routeIndex < search.getRouteCount()) {
                            Assertions.assertEquals(region, search.getRouteRegion(routeIndex), label + ",route=" + routeIndex);
                        }
                    }
                }
            }
        }
    }
}
