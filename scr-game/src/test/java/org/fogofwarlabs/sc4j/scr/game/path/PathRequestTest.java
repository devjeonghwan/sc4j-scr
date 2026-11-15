package org.fogofwarlabs.sc4j.scr.game.path;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainRegionLookupUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the request changes a failed local path search retains and the bounds of the route slots of a {@link PathRequest}.
 */
final class PathRequestTest {
    /**
     * The width and height of the test map in tiles.
     */
    private static final int MAP_SIDE_TILES = 8;
    private static final int TILE_COUNT = MAP_SIDE_TILES * MAP_SIDE_TILES;
    /**
     * The number of terrain tile groups and miniature flag bytes of the parsed tileset.
     */
    private static final int TILE_GROUP_COUNT = 3;
    private static final int MINIATURE_FLAG_BYTES = 32;
    /**
     * The destination position of the failed search.
     */
    private static final int DESTINATION_POSITION = 0x10001;

    /**
     * The last route slot of a request and the route slot count, the first index past the slots.
     */
    private static final int LAST_ROUTE_SLOT = 49;
    private static final int ROUTE_SLOT_COUNT = 50;
    /**
     * The region WORD and the waypoint written into the last route slot.
     */
    private static final int REGION_WORD = 0xFFFF;
    private static final int WAYPOINT = 0x87654321;

    /**
     * The search writes the looked-up regions and resets the request flags before it reads the unit dimensions, which fail here because the game has
     * no data.
     */
    @Test
    void search_should_retain_partial_request_changes_when_it_fails() throws IOException {
        GameMap map = new GameMap(MAP_SIDE_TILES, MAP_SIDE_TILES, 0, new int[TILE_COUNT], new int[TILE_COUNT]);
        TerrainTileset tileset = TerrainTileset.parse(new byte[TILE_GROUP_COUNT * TerrainTileset.CV5_RECORD_SIZE], new byte[MINIATURE_FLAG_BYTES]);
        TerrainContours contours = TerrainContours.createWithoutBoundaries(map, tileset);
        int region = TerrainRegionLookupUtils.lookup(contours.getRegions(), 0, 0);
        Game game = Game.create(null, map, new LcgRandom(0));

        game.getPathSystem().setTerrainContours(contours);

        PathRequest request = new PathRequest();

        request.sourcePosition      = 0;
        request.destinationPosition = DESTINATION_POSITION;
        request.adjustedDestination = -1;
        request.destinationAdjusted = 1;
        request.sourceRegion        = -1;
        request.destinationRegion   = -1;

        Assertions.assertThrows(NullPointerException.class, () -> new LocalPathSearch().search(game, UnitTestUtils.createUnit(1), request, false));

        Assertions.assertEquals(region, request.sourceRegion);
        Assertions.assertEquals(region, request.destinationRegion);
        Assertions.assertEquals(DESTINATION_POSITION, request.adjustedDestination);
        Assertions.assertEquals(0, request.destinationAdjusted);

        Game absentTerrain = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> new LocalPathSearch().search(absentTerrain, UnitTestUtils.createUnit(1), (PathRequest) null, false));
    }

    @Test
    void route_slots_store_signed_regions_and_reject_out_of_bounds_indices() {
        PathRequest request = new PathRequest();

        request.setRegion(LAST_ROUTE_SLOT, REGION_WORD);
        request.setWaypoint(LAST_ROUTE_SLOT, WAYPOINT);

        Assertions.assertEquals(-1, request.getRegion(LAST_ROUTE_SLOT));
        Assertions.assertEquals(WAYPOINT, request.getWaypoint(LAST_ROUTE_SLOT));

        for (int index : new int[]{-1, ROUTE_SLOT_COUNT, Integer.MIN_VALUE, Integer.MAX_VALUE, 1 << 30}) {
            Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> request.getRegion(index));
            Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> request.setRegion(index, 0));
            Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> request.getWaypoint(index));
            Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> request.setWaypoint(index, 0));
        }

        Assertions.assertEquals(-1, request.getRegion(LAST_ROUTE_SLOT));
        Assertions.assertEquals(WAYPOINT, request.getWaypoint(LAST_ROUTE_SLOT));
    }
}
