package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.ability.UnitPsi;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.terrain.RegionPathSearch;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainRegions;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Chooses computer building positions on a candidate grid around an origin.
 * <p>
 * The grid holds {@code 64 x 64} tile cells; cell {@code (row, column)} is the tile
 * {@code (originX / 32 - 32 + column, originY / 32 - 32 + row)} and its sample pixel is
 * {@code (originX - 0x400 + 32 * column, originY - 0x400 + 32 * row)}. Spacing, height, obstacle, resource and add-on
 * filters restore their previous grid when they remove every candidate; initial placement, component and route filters
 * may leave it empty.
 */
public final class ComputerPlacement {
    /**
     * The side of the candidate grid in tiles.
     */
    private static final int SIZE = 64;
    /**
     * The last row or column of the grid.
     */
    private static final int LAST_CELL = SIZE - 1;
    /**
     * The grid cells from its edge to the origin tile.
     */
    private static final int GRID_HALF = SIZE / 2;
    /**
     * The pixel offset from the grid origin to the sample pixel of cell {@code (0, 0)}: half the grid.
     */
    private static final int ORIGIN_OFFSET = GRID_HALF * TerrainTileset.TILE_SIZE;
    /**
     * The path tile distance of an unreachable position.
     */
    private static final int UNREACHABLE_TILES = 9999;
    /**
     * The first intermediate region of a route.
     */
    private static final int FIRST_ROUTE_REGION = 1;

    /**
     * The search radius in tiles a Missile Turret always uses.
     */
    private static final int TURRET_SEARCH_RADIUS = 0x28;
    /**
     * The search radius in tiles around a unit's own position.
     */
    private static final int UNIT_SEARCH_RADIUS = 3;
    /**
     * The search passes: around the defense choke, then around the origin.
     */
    private static final int SEARCH_PASSES = 2;
    /**
     * The column, right of the candidate tile, where an add-on host's grid probes for its add-on.
     */
    private static final int ADDON_PROBE_COLUMN = 4;
    /**
     * The add-on columns kept free right of a host: the first is two tiles right of the host's center tile.
     */
    private static final int ADDON_FIRST_COLUMN = 2;
    private static final int ADDON_COLUMNS = 2;
    /**
     * The tile margin kept around resource depots and resources, and around the larger production buildings.
     */
    private static final int WIDE_MARGIN = 2;

    /**
     * The positions of the values of a cleared line in {@link #CROSS_LINES}.
     */
    private static final int LINE_COLUMN = 0;
    private static final int LINE_ROW = 1;
    private static final int LINE_COLUMN_STEP = 2;
    private static final int LINE_ROW_STEP = 3;
    /**
     * The lines cleared through the origin: start column, start row, column and row step.
     */
    private static final int @NotNull [] @NotNull [] CROSS_LINES = {{32, 0, 0, 1}, {0, 32, 1, 0}, {31, 0, 0, 1}, {0, 31, 1, 0},
                                                                    {33, 0, 0, 1}, {0, 33, 1, 0}, {30, 0, 0, 1}, {29, 0, 0, 1},
                                                                    {0, 30, 1, 0}, {28, 0, 0, 1}, {0, 29, 1, 0}, {27, 0, 0, 1},
                                                                    {0, 28, 1, 0}};
    /**
     * The defense spacing patterns from the widest to the narrowest; {@code x} marks a cleared cell.
     */
    private static final @NotNull String @NotNull [] SPACING_PATTERNS = {
            "......xxxxx......" +
            "....xxxxxxxxx...." +
            "...xxxxxxxxxxx..." +
            "..xxxxxxxxxxxxx.." +
            ".xxxxxxxxxxxxxxx." +
            ".xxxxxxxxxxxxxxx." +
            "xxxxxxxxxxxxxxxxx" +
            "xxxxxxxxxxxxxxxxx" +
            "xxxxxxxxxxxxxxxxx" +
            "xxxxxxxxxxxxxxxxx" +
            "xxxxxxxxxxxxxxxxx" +
            ".xxxxxxxxxxxxxxx." +
            ".xxxxxxxxxxxxxxx." +
            "..xxxxxxxxxxxxx.." +
            "...xxxxxxxxxxx..." +
            "....xxxxxxxxx...." +
            "......xxxxx......",
            "....xxxxx...." +
            "...xxxxxxx..." +
            "..xxxxxxxxx.." +
            ".xxxxxxxxxxx." +
            "xxxxxxxxxxxxx" +
            "xxxxxxxxxxxxx" +
            "xxxxxxxxxxxxx" +
            "xxxxxxxxxxxxx" +
            "xxxxxxxxxxxxx" +
            ".xxxxxxxxxxx." +
            "..xxxxxxxxx.." +
            "...xxxxxxx..." +
            "....xxxxx....",
            "...xxx..." +
            ".xxxxxxx." +
            ".xxxxxxx." +
            "xxxxxxxxx" +
            "xxxxxxxxx" +
            "xxxxxxxxx" +
            ".xxxxxxx." +
            ".xxxxxxx." +
            "...xxx...",
            ".xxx." +
            "xxxxx" +
            "xxxxx" +
            "xxxxx" +
            ".xxx."
    };
    /**
     * The side of each spacing pattern.
     */
    private static final int @NotNull [] SPACING_PATTERN_SIZES = {17, 13, 9, 5};
    /**
     * The narrowest spacing pattern.
     */
    private static final int LAST_SPACING_LEVEL = SPACING_PATTERNS.length - 1;
    /**
     * The pattern character of a cleared cell.
     */
    private static final char SPACING_CLEARED = 'x';
    /**
     * Halves a placement width or height to the center of the footprint.
     */
    private static final int FOOTPRINT_CENTER_DIVISOR = 2;
    /**
     * The low WORD of a packed position: its horizontal coordinate.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The shift of the vertical coordinate in the high WORD of a packed position.
     */
    private static final int HIGH_WORD_SHIFT = Short.SIZE;
    /**
     * A margin is kept on both sides of a rectangle.
     */
    private static final int MARGIN_SIDE_COUNT = 2;

    private static boolean hasFullNeighborhood(byte @NotNull [] cells, int row, int column) {
        if (column < 1 || column >= LAST_CELL || row < 1 || row >= LAST_CELL) {
            return false;
        }

        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                if ((rowOffset != 0 || columnOffset != 0) && cells[(row + rowOffset) * SIZE + column + columnOffset] == 0) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isEmpty(byte @NotNull [] cells) {
        for (byte cell : cells) {
            if (cell != 0) {
                return false;
            }
        }

        return true;
    }


    private final @NotNull Game game;

    private final byte @NotNull [] grid;
    private final byte @NotNull [] saved;
    private final byte @NotNull [] inner;

    /**
     * Creates a {@link ComputerPlacement} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public ComputerPlacement(@NotNull Game game) {
        this.game = game;

        this.grid  = new byte[SIZE * SIZE];
        this.saved = new byte[SIZE * SIZE];
        this.inner = new byte[SIZE * SIZE];
    }

    /**
     * Returns the path distance in tiles: the straight tile distance within one region, the tile length of the
     * region-center route inside one component, or {@value #UNREACHABLE_TILES}.
     *
     * @param sourceX the source horizontal pixel
     * @param sourceY the source vertical pixel
     * @param targetX the target horizontal pixel
     * @param targetY the target vertical pixel
     * @return the distance in tiles
     */
    int getPathTiles(int sourceX, int sourceY, int targetX, int targetY) {
        TerrainRegions regions = this.game.getComputerTerrain().getRegions();
        int source = this.game.getComputerTerrain().getRegion(sourceX, sourceY);
        int target = this.game.getComputerTerrain().getRegion(targetX, targetY);

        if (source == target) {
            return RegionPathSearch.calculateDistance(sourceX, targetX, sourceY, targetY) / TerrainTileset.TILE_SIZE;
        }

        if (regions.getComponent(source) != regions.getComponent(target)) {
            return UNREACHABLE_TILES;
        }

        RegionPathSearch search = this.game.getComputerTerrain().getPathSearch();

        if (!search.search(source, sourceX, sourceY, target, targetX, targetY)) {
            return UNREACHABLE_TILES;
        }

        int routeCount = (short) search.getRouteCount();

        if (routeCount <= RegionPathSearch.DIRECT_ROUTE_COUNT) {
            return RegionPathSearch.calculateDistance(sourceX, targetX, sourceY, targetY) / TerrainTileset.TILE_SIZE;
        }

        int previousX = (short) regions.getCenterX(search.getRouteRegion(FIRST_ROUTE_REGION));
        int previousY = (short) regions.getCenterY(search.getRouteRegion(FIRST_ROUTE_REGION));
        int total = RegionPathSearch.calculateDistance(sourceX, previousX, sourceY, previousY);

        for (int routeIndex = FIRST_ROUTE_REGION + 1; routeIndex < routeCount - 1; routeIndex++) {
            int region = search.getRouteRegion(routeIndex);
            int centerX = (short) regions.getCenterX(region);
            int centerY = (short) regions.getCenterY(region);

            total += RegionPathSearch.calculateDistance(previousX, centerX, previousY, centerY);
            previousX = centerX;
            previousY = centerY;
        }

        return (total + RegionPathSearch.calculateDistance(previousX, targetX, previousY, targetY)) >>> TerrainTileset.TILE_SHIFT;
    }

    /**
     * Tests the building placement at a tile with no builder, the collision override set to the worker, undetected
     * units obstructing and invisible tiles ignored.
     */
    private boolean canPlace(@NotNull Unit worker, int owner, int type, int tileX, int tileY) {
        return this.canPlace(null, worker, owner, type, tileX, tileY);
    }

    /**
     * Tests the building placement at a tile with an explicit builder (the add-on host) and collision unit.
     *
     * @throws NullPointerException if the world was built without game data
     */
    private boolean canPlace(@Nullable Unit builder, @NotNull Unit collision, int owner, int type, int tileX, int tileY) {
        if (tileX < 0 || tileY < 0) {
            return false;
        }

        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        int centerX = tileX * TerrainTileset.TILE_SIZE + (short) units.getPlacementWidth(type) / FOOTPRINT_CENTER_DIVISOR;
        int centerY = tileY * TerrainTileset.TILE_SIZE + (short) units.getPlacementHeight(type) / FOOTPRINT_CENTER_DIVISOR;

        return this.game.getUnitPlacement().canPlaceBuilding(builder, collision, owner, type, centerX, centerY, true, false);
    }

    /**
     * Tests whether every path neighbor of the region is quiet ({@link ComputerRegions#isQuiet}).
     *
     * @throws UnsupportedOperationException if the neighbor count underflows
     */
    private boolean hasQuietNeighbors(@NotNull ComputerRegions records, int owner, int region) {
        TerrainRegions regions = this.game.getComputerTerrain().getRegions();
        int count = ComputerTerrain.getPathNeighborCount(regions, region);

        if (count < 0) {
            throw Exceptions.newUnsupportedOperationException("Region {0} has a negative path-neighbor count.", region);
        }

        for (int neighborIndex = 0; neighborIndex < count; neighborIndex++) {
            if (!records.isQuiet(owner, regions.getNeighborRegion(region, neighborIndex))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Searches a building position that honors and sets the owner's failed-placement mark: a blocked owner gets no
     * position, and a failed search blocks the owner.
     *
     * @param computer the owner's computer record
     * @param worker   the constructing worker
     * @param type     the building type
     * @param originX  the horizontal origin pixel
     * @param originY  the vertical origin pixel
     * @param radius   the search radius in tiles; a Missile Turret always uses {@value #TURRET_SEARCH_RADIUS}
     * @return the packed center {@code x | y << 16}, or {@code -1}
     */
    int find(@NotNull ComputerPlayer computer, @NotNull Unit worker, int type, int originX, int originY, int radius) {
        if (computer.isPlacementBlocked()) {
            return -1;
        }

        int position = this.findUnblocked(worker, type, originX, originY, radius);

        if (position == -1) {
            computer.blockPlacement();
        }

        return position;
    }

    /**
     * Searches a building position without the owner's failed-placement mark, as the beacon placement of flag games calls
     * it.
     *
     * @param unit    the unit the search places for (its owner and add-on host)
     * @param type    the building type
     * @param originX the horizontal origin pixel
     * @param originY the vertical origin pixel
     * @param radius  the search radius in tiles; a Missile Turret always uses {@code 0x28}
     * @return the packed center {@code x | y << 16}, or {@code -1}
     */
    public int findUnblocked(@NotNull Unit unit, int type, int originX, int originY, int radius) {
        return this.search(unit, type, (short) originX, (short) originY, type == ScrUnitIds.TERRAN_MISSILE_TURRET ? TURRET_SEARCH_RADIUS : radius);
    }

    /**
     * Searches a building position around the unit's own position (radius {@value #UNIT_SEARCH_RADIUS}, or
     * {@value #TURRET_SEARCH_RADIUS} for a Missile Turret) without the failed-placement mark.
     *
     * @param unit the unit the search places for
     * @param type the building type
     * @return the packed center {@code x | y << 16}, or {@code -1}
     * @throws IllegalStateException if the unit has no sprite
     */
    int findAtUnit(@NotNull Unit unit, int type) {
        Sprite sprite = unit.requireSprite();

        return this.search(unit,
                           type,
                           (short) sprite.getX(), (short) sprite.getY(),
                           type == ScrUnitIds.TERRAN_MISSILE_TURRET ? TURRET_SEARCH_RADIUS : UNIT_SEARCH_RADIUS);
    }

    /**
     * Searches the nearest candidate cell to the center tile: an add-on first tries its own slot beside the host;
     * depots are not spread, Comsat Stations, Nuclear Silos and the other add-ons are placed as their hosts, a Bunker or
     * Creep Colony centers on the defense choke and a Pylon on the owner's nearest unpowered unit.
     *
     * @throws IllegalStateException         if the anchor has no sprite
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the computer-player phase is not initialized
     */
    private int search(@NotNull Unit worker, int type, int originX, int originY, int radius) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        int owner = worker.getOwnerIndex();
        int originTileX = originX / TerrainTileset.TILE_SIZE;
        int originTileY = originY / TerrainTileset.TILE_SIZE;

        if ((units.getFlags(type) & UnitTypeFlags.ADDON) != 0) {
            // The add-on first tries its own slot beside the host; on success the host keeps its center, otherwise the
            // host site is searched as its type.
            int hostType = worker.getUnitId();
            int hostWidth = (short) units.getPlacementWidth(hostType);
            int hostHeight = (short) units.getPlacementHeight(hostType);
            int addonX = (short) units.getAddonHorizontalOffset(type);
            int addonY = (short) units.getAddonVerticalOffset(type);
            int addonTileX = (short) ((short) originTileX - hostWidth / (FOOTPRINT_CENTER_DIVISOR * TerrainTileset.TILE_SIZE) +
                                      addonX / TerrainTileset.TILE_SIZE);
            int addonTileY = (short) ((short) originTileY - hostHeight / (FOOTPRINT_CENTER_DIVISOR * TerrainTileset.TILE_SIZE) +
                                      addonY / TerrainTileset.TILE_SIZE);

            if (this.canPlace(worker, worker, owner, type, addonTileX, addonTileY)) {
                int hostX = (short) (((short) (addonTileX - addonX / TerrainTileset.TILE_SIZE) << TerrainTileset.TILE_SHIFT) +
                                     hostWidth / FOOTPRINT_CENTER_DIVISOR);
                int hostY = (short) (((short) (addonTileY - addonY / TerrainTileset.TILE_SIZE) << TerrainTileset.TILE_SHIFT) +
                                     hostHeight / FOOTPRINT_CENTER_DIVISOR);

                return hostX & WORD_MASK | hostY << HIGH_WORD_SHIFT;
            }
        }

        boolean spread = true;
        int region = ComputerPlayerPhase.NO_REGION;
        int placedType = type;

        switch (ScrUnitIds.requireValid(this.game.requireGameData().getBaseType(type))) {
            case ScrUnitIds.TERRAN_COMMAND_CENTER, ScrUnitIds.ZERG_HATCHERY, ScrUnitIds.PROTOSS_NEXUS -> spread = false;
            case ScrUnitIds.TERRAN_COMSAT_STATION, ScrUnitIds.TERRAN_NUCLEAR_SILO -> placedType = ScrUnitIds.TERRAN_COMMAND_CENTER;
            case ScrUnitIds.TERRAN_CONTROL_TOWER -> placedType = ScrUnitIds.TERRAN_STARPORT;
            case ScrUnitIds.TERRAN_COVERT_OPS, ScrUnitIds.TERRAN_PHYSICS_LAB -> placedType = ScrUnitIds.TERRAN_SCIENCE_FACILITY;
            case ScrUnitIds.TERRAN_MACHINE_SHOP -> placedType = ScrUnitIds.TERRAN_FACTORY;
            case ScrUnitIds.TERRAN_BUNKER, ScrUnitIds.ZERG_CREEP_COLONY -> {
                if (type == ScrUnitIds.TERRAN_BUNKER ||
                    (this.game.requireComputerPlayerPhase().getPlayer(owner).getFlags() & ComputerPlayer.FLAG_CREEP_SPACING) == 0) {
                    int choke = this.game.getComputerTerrain().selectDefenseChoke(owner);

                    if (choke != -1) {
                        region = choke;
                        spread = false;
                    }
                }
            }
            case ScrUnitIds.PROTOSS_PYLON -> {
                // The owner's nearest unpowered unit anchors the Pylon without spreading.
                Unit anchor = this.game.getComputerResources().findUnpowered(owner, originX, originY);

                if (anchor != null) {
                    originX     = (short) anchor.requireSprite().getX();
                    originY     = (short) anchor.requireSprite().getY();
                    originTileX = originX / TerrainTileset.TILE_SIZE;
                    originTileY = originY / TerrainTileset.TILE_SIZE;
                    spread      = false;
                }
            }
            default -> {
            }
        }

        this.buildGrid(worker, placedType, owner, originX, originY, radius);

        if (spread) {
            this.clearCrossLines();
        }

        int centerTileX = originTileX;
        int centerTileY = originTileY;

        if (region != ComputerPlayerPhase.NO_REGION) {
            TerrainRegions regions = this.game.getComputerTerrain().getRegions();

            centerTileX = (short) regions.getCenterX(region) / TerrainTileset.TILE_SIZE;
            centerTileY = (short) regions.getCenterY(region) / TerrainTileset.TILE_SIZE;
        }

        int bestFar = ComputerPlayerPhase.NO_DISTANCE;
        int farColumn = 0;
        int farRow = 0;

        for (int pass = 0; pass < SEARCH_PASSES; pass++) {
            int bestNear = ComputerPlayerPhase.NO_DISTANCE;
            int nearColumn = 0;
            int nearRow = 0;

            for (int row = 0; row < SIZE; row++) {
                int tileY = originTileY - GRID_HALF + row;

                for (int column = 0; column < SIZE; column++) {
                    if (this.grid[row * SIZE + column] == 0) {
                        continue;
                    }

                    int tileX = originTileX - GRID_HALF + column;
                    int distance = this.getPathTiles(centerTileX << TerrainTileset.TILE_SHIFT, centerTileY << TerrainTileset.TILE_SHIFT,
                                                     tileX << TerrainTileset.TILE_SHIFT, tileY << TerrainTileset.TILE_SHIFT);

                    if (Integer.compareUnsigned(distance, bestNear) >= 0) {
                        continue;
                    }

                    if (Integer.compareUnsigned(distance, radius) <= 0) {
                        bestNear   = distance;
                        nearColumn = tileX;
                        nearRow    = tileY;
                    } else if (Integer.compareUnsigned(distance, bestFar) < 0) {
                        bestFar   = distance;
                        farColumn = tileX;
                        farRow    = tileY;
                    }
                }
            }

            int chosenColumn = bestNear != ComputerPlayerPhase.NO_DISTANCE ? nearColumn : farColumn;
            int chosenRow = bestNear != ComputerPlayerPhase.NO_DISTANCE ? nearRow : farRow;

            if ((bestNear != ComputerPlayerPhase.NO_DISTANCE ? bestNear : bestFar) != ComputerPlayerPhase.NO_DISTANCE) {
                int left = (short) ((short) (chosenColumn << TerrainTileset.TILE_SHIFT) +
                                    (short) units.getPlacementWidth(placedType) / FOOTPRINT_CENTER_DIVISOR);
                int top = (short) ((short) (chosenRow << TerrainTileset.TILE_SHIFT) +
                                   (short) units.getPlacementHeight(placedType) / FOOTPRINT_CENTER_DIVISOR);

                return left & WORD_MASK | top << HIGH_WORD_SHIFT;
            }

            if (region == ComputerPlayerPhase.NO_REGION) {
                break;
            }

            centerTileX = originX / TerrainTileset.TILE_SIZE;
            centerTileY = originY / TerrainTileset.TILE_SIZE;
        }

        return -1;
    }

    /**
     * Builds the candidate grid: placeable cells within the radius in the origin's component, quiet regions (except
     * for depots and Pylons), within the route distance and on the origin's height, away from unwalkable tiles,
     * resources and add-on columns, then the type's spacing filter.
     *
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the computer-player phase or the unit pool is not initialized
     */
    private void buildGrid(@NotNull Unit worker, int type, int owner, int originX, int originY, int radius) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        GameMap map = this.game.getMap();
        TerrainRegions regions = this.game.getComputerTerrain().getRegions();
        byte[] cells = this.grid;
        boolean addonHost = type == ScrUnitIds.TERRAN_COMMAND_CENTER || type == ScrUnitIds.TERRAN_FACTORY ||
                            type == ScrUnitIds.TERRAN_STARPORT || type == ScrUnitIds.TERRAN_SCIENCE_FACILITY;
        int low = GRID_HALF - (radius >>> 1);
        int high = radius + low;
        int originTileX = originX / TerrainTileset.TILE_SIZE;
        int originTileY = originY / TerrainTileset.TILE_SIZE;

        Arrays.fill(cells, (byte) 0);

        for (int row = 0; row < SIZE && row < high; row++) {
            if (row < low) {
                continue;
            }

            int tileY = (short) (row - GRID_HALF + originTileY);

            for (int column = 0; column < SIZE && column < high; column++) {
                if (column < low) {
                    continue;
                }

                int tileX = originTileX - GRID_HALF + column;

                if (addonHost &&
                    !this.canPlace(worker, owner, ScrUnitIds.TERRAN_MISSILE_TURRET,
                                   (short) (tileX + ADDON_PROBE_COLUMN), (short) (tileY + 1))) {
                    continue;
                }

                if (this.canPlace(worker, owner, type, (short) tileX, tileY)) {
                    cells[row * SIZE + column] = 1;
                }
            }
        }

        int component = regions.getComponent(this.game.getComputerTerrain().getRegion(originX, originY));

        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int index = row * SIZE + column;

                if (cells[index] != 0 &&
                    regions.getComponent(
                            this.game.getComputerTerrain().getRegion(originX - ORIGIN_OFFSET + column * TerrainTileset.TILE_SIZE,
                                                                     originY - ORIGIN_OFFSET + row * TerrainTileset.TILE_SIZE)
                    ) != component) {
                    cells[index] = 0;
                }
            }
        }

        if (type != ScrUnitIds.TERRAN_COMMAND_CENTER && type != ScrUnitIds.ZERG_HATCHERY && type != ScrUnitIds.PROTOSS_NEXUS &&
            type != ScrUnitIds.PROTOSS_PYLON) {
            ComputerRegions records = this.game.requireComputerPlayerPhase().getRegions();

            for (int row = 0; row < SIZE; row++) {
                for (int column = 0; column < SIZE; column++) {
                    int index = row * SIZE + column;

                    if (cells[index] != 0 &&
                        !this.hasQuietNeighbors(records,
                                                owner,
                                                this.game.getComputerTerrain().getRegion(originX - ORIGIN_OFFSET + column * TerrainTileset.TILE_SIZE,
                                                                                         originY - ORIGIN_OFFSET + row * TerrainTileset.TILE_SIZE))) {
                        cells[index] = 0;
                    }
                }
            }
        }

        this.clearDistant(originX, originY, radius);

        if (isEmpty(cells)) {
            return;
        }

        System.arraycopy(cells, 0, this.saved, 0, cells.length);

        int height = this.game.getComputerResources().getTerrainHeight(originX, originY);

        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int index = row * SIZE + column;

                if (cells[index] != 0 &&
                    this.game.getComputerResources().getTerrainHeight(originX - ORIGIN_OFFSET + column * TerrainTileset.TILE_SIZE,
                                                                      originY - ORIGIN_OFFSET + row * TerrainTileset.TILE_SIZE) != height) {
                    cells[index] = 0;
                }
            }
        }

        this.restoreIfEmpty();

        System.arraycopy(cells, 0, this.saved, 0, cells.length);

        int tileRow = originY / TerrainTileset.TILE_SIZE - GRID_HALF;

        for (int row = 0; row < SIZE; row++, tileRow++) {
            if (tileRow >= map.getHeight()) {
                break;
            }

            if (tileRow < 0) {
                continue;
            }

            int tileColumn = originX / TerrainTileset.TILE_SIZE - GRID_HALF;

            for (int column = 0; column < SIZE; column++, tileColumn++) {
                if (tileColumn >= map.getWidth()) {
                    break;
                }

                if (tileColumn < 0) {
                    continue;
                }

                if ((map.getTileFlags(tileRow * map.getWidth() + tileColumn) & MapTileFlags.WALKABLE) == 0) {
                    this.clearAround(units, 1, 1, tileColumn, tileRow, type, originX, originY, 1);
                }
            }
        }

        this.restoreIfEmpty();

        boolean depot = type == ScrUnitIds.TERRAN_COMMAND_CENTER || type == ScrUnitIds.ZERG_HATCHERY || type == ScrUnitIds.PROTOSS_NEXUS;

        if (!depot && (units.getFlags(type) & UnitTypeFlags.ADDON) == 0) {
            System.arraycopy(cells, 0, this.saved, 0, cells.length);

            UnitPool pool = this.game.getUnitSystem().requireUnitPool();

            for (int poolIndex = pool.getFirstOwnerIndex(owner); poolIndex != 0; poolIndex = pool.getNextOwnerIndex(poolIndex)) {
                Unit unit = pool.getUnit(poolIndex);

                if (!ComputerUnits.isDeadOrHidden(unit) &&
                    (this.game.getUnitResourceSearch().isActiveDepot(unit) ||
                     (units.getFlags(unit.getUnitId()) & UnitTypeFlags.RESOURCE_CONTAINER) != 0)) {
                    this.clearAroundUnit(units, unit, type, originX, originY, WIDE_MARGIN);
                }
            }

            if (type != ScrUnitIds.TERRAN_BUNKER) {
                for (int poolIndex = pool.getFirstOwnerIndex(PlayerTable.NEUTRAL_PLAYER_ID); poolIndex != 0;
                     poolIndex = pool.getNextOwnerIndex(poolIndex)) {
                    Unit unit = pool.getUnit(poolIndex);

                    if (!ComputerUnits.isDeadOrHidden(unit) && (units.getFlags(unit.getUnitId()) & UnitTypeFlags.RESOURCE_CONTAINER) != 0) {
                        this.clearAroundUnit(units, unit, type, originX, originY, WIDE_MARGIN);
                    }
                }
            }

            this.restoreIfEmpty();
        }

        this.clearAddonSlots(units, type, owner, originX, originY);

        switch (ScrUnitIds.requireValid(this.game.requireGameData().getBaseType(type))) {
            case ScrUnitIds.TERRAN_MISSILE_TURRET, ScrUnitIds.PROTOSS_PHOTON_CANNON -> this.spaceDefenses(owner, originX, originY, type);
            case ScrUnitIds.TERRAN_BUNKER -> {
                if (this.game.getComputerTerrain().selectDefenseChoke(owner) == -1) {
                    this.spaceDefenses(owner, originX, originY, ScrUnitIds.TERRAN_BUNKER);
                }
            }
            case ScrUnitIds.ZERG_CREEP_COLONY -> {
                if ((this.game.requireComputerPlayerPhase().getPlayer(owner).getFlags() & ComputerPlayer.FLAG_CREEP_SPACING) != 0 ||
                    this.game.getComputerTerrain().selectDefenseChoke(owner) == -1) {
                    this.spaceDefenses(owner, originX, originY, ScrUnitIds.ZERG_CREEP_COLONY);
                }
            }
            case ScrUnitIds.PROTOSS_PYLON -> {
                // A Pylon avoids points the owner already powers.
                System.arraycopy(cells, 0, this.saved, 0, cells.length);

                UnitPsi psi = this.game.getPsi();

                for (int row = 0; row < SIZE; row++) {
                    for (int column = 0; column < SIZE; column++) {
                        int index = row * SIZE + column;

                        if (cells[index] != 0 &&
                            psi.isPowered(owner,
                                          originX - ORIGIN_OFFSET + column * TerrainTileset.TILE_SIZE,
                                          originY - ORIGIN_OFFSET + row * TerrainTileset.TILE_SIZE)) {
                            cells[index] = 0;
                        }
                    }
                }

                this.restoreIfEmpty();
            }
            default -> {
                if (!depot) {
                    this.spaceProduction(units, type, owner, originX, originY);
                }
            }
        }
    }

    /**
     * Clears the cells whose route distance from the origin exceeds the radius.
     */
    private void clearDistant(int originX, int originY, int radius) {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int index = row * SIZE + column;

                if (this.grid[index] != 0 &&
                    Integer.compareUnsigned(radius,
                                            this.getPathTiles(originX, originY,
                                                              originX - ORIGIN_OFFSET + column * TerrainTileset.TILE_SIZE,
                                                              originY - ORIGIN_OFFSET + row * TerrainTileset.TILE_SIZE)) < 0) {
                    this.grid[index] = 0;
                }
            }
        }
    }

    /**
     * Clears the fixed lines through the origin while the grid keeps a candidate.
     */
    private void clearCrossLines() {
        System.arraycopy(this.grid, 0, this.saved, 0, this.grid.length);

        for (int[] line : CROSS_LINES) {
            int column = line[LINE_COLUMN];
            int row = line[LINE_ROW];

            for (int step = 0; step < SIZE; step++) {
                this.grid[row * SIZE + column] = 0;
                column += line[LINE_COLUMN_STEP];
                row += line[LINE_ROW_STEP];
            }

            if (isEmpty(this.grid)) {
                System.arraycopy(this.saved, 0, this.grid, 0, this.grid.length);

                return;
            }
        }
    }

    /**
     * Clears the cells where the placed type would come within the margin of a unit.
     *
     * @throws UnsupportedOperationException if the unit has no sprite
     */
    private void clearAroundUnit(@NotNull ScrUnitTypeView units, @NotNull Unit unit, int type, int originX, int originY, int margin) {
        int width = (short) units.getPlacementWidth(unit.getUnitId()) / TerrainTileset.TILE_SIZE;
        int height = (short) units.getPlacementHeight(unit.getUnitId()) / TerrainTileset.TILE_SIZE;
        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            throw Exceptions.newUnsupportedOperationException("Computer placement spacing requires a sprite.");
        }

        this.clearAround(units,
                         width, height,
                         sprite.getX() / TerrainTileset.TILE_SIZE, sprite.getY() / TerrainTileset.TILE_SIZE,
                         type,
                         originX, originY,
                         margin);
    }

    /**
     * Clears the cells where the placed type would come within the margin of a tile rectangle.
     */
    private void clearAround(@NotNull ScrUnitTypeView units,
                             int width, int height,
                             int tileX, int tileY,
                             int type,
                             int originX, int originY,
                             int margin) {
        int typeWidth = (short) units.getPlacementWidth(type) / TerrainTileset.TILE_SIZE;
        int typeHeight = (short) units.getPlacementHeight(type) / TerrainTileset.TILE_SIZE;
        int columns = width - 1 + margin * MARGIN_SIDE_COUNT + typeWidth;
        int rows = height - 1 + margin * MARGIN_SIDE_COUNT + typeHeight;
        int firstRow = tileY - originY / TerrainTileset.TILE_SIZE - (height >>> 1) - typeHeight - margin + (GRID_HALF + 1);
        int firstColumn = tileX - (width >>> 1) - typeWidth - margin - originX / TerrainTileset.TILE_SIZE + (GRID_HALF + 1);

        for (int row = firstRow; rows != 0; row++, rows--) {
            for (int column = firstColumn, count = columns; count != 0; column++, count--) {
                if (Integer.compareUnsigned(column, SIZE) < 0 && Integer.compareUnsigned(row, SIZE) < 0) {
                    this.grid[row * SIZE + column] = 0;
                }
            }
        }
    }

    /**
     * Keeps the add-on columns of the owner's Command Centers, Factories, Starports and Science Facilities free.
     *
     * @throws UnsupportedOperationException if the unit pool is not initialized
     * @throws IllegalStateException         if the unit has no sprite
     */
    private void clearAddonSlots(@NotNull ScrUnitTypeView units, int type, int owner, int originX, int originY) {
        System.arraycopy(this.grid, 0, this.saved, 0, this.grid.length);

        int typeHeight = (short) units.getPlacementHeight(type) / TerrainTileset.TILE_SIZE;
        UnitPool pool = this.game.getUnitSystem().requireUnitPool();

        for (int poolIndex = pool.getFirstOwnerIndex(owner); poolIndex != 0; poolIndex = pool.getNextOwnerIndex(poolIndex)) {
            Unit unit = pool.getUnit(poolIndex);
            int unitType = this.game.requireGameData().getBaseType(unit);

            if (ComputerUnits.isDeadOrHidden(unit) ||
                unitType != ScrUnitIds.TERRAN_COMMAND_CENTER && unitType != ScrUnitIds.TERRAN_FACTORY &&
                unitType != ScrUnitIds.TERRAN_STARPORT && unitType != ScrUnitIds.TERRAN_SCIENCE_FACILITY) {
                continue;
            }

            Sprite sprite = unit.requireSprite();
            int columnOffset = sprite.getX() / TerrainTileset.TILE_SIZE - originX / TerrainTileset.TILE_SIZE;
            int firstRow = sprite.getY() / TerrainTileset.TILE_SIZE - originY / TerrainTileset.TILE_SIZE + GRID_HALF + 1;

            for (int columnShift = 0; columnShift < ADDON_COLUMNS; columnShift++) {
                int column = columnOffset + (GRID_HALF + ADDON_FIRST_COLUMN) + columnShift;
                int row = firstRow;

                for (int count = 0; count < typeHeight + 1; count++, row--) {
                    if (Integer.compareUnsigned(column, LAST_CELL) > 0) {
                        break;
                    }

                    if (Integer.compareUnsigned(row, SIZE) < 0) {
                        this.grid[row * SIZE + column] = 0;
                    }
                }
            }
        }

        this.restoreIfEmpty();
    }

    /**
     * Clears the spacing pattern around each existing defense, relaxing the pattern while the grid would become empty.
     *
     * @throws UnsupportedOperationException if the unit pool is not initialized
     * @throws IllegalStateException         if the unit has no sprite
     */
    private void spaceDefenses(int owner, int originX, int originY, int type) {
        UnitPool pool = this.game.getUnitSystem().requireUnitPool();

        for (int level = 0; ; level++) {
            System.arraycopy(this.grid, 0, this.saved, 0, this.grid.length);

            for (int poolIndex = pool.getFirstOwnerIndex(owner); poolIndex != 0; poolIndex = pool.getNextOwnerIndex(poolIndex)) {
                Unit unit = pool.getUnit(poolIndex);

                if (ComputerUnits.isDeadOrHidden(unit)) {
                    continue;
                }

                int unitType = this.game.requireGameData().getBaseType(unit);
                boolean spaced = unitType == ScrUnitIds.ZERG_CREEP_COLONY || unitType == ScrUnitIds.ZERG_SPORE_COLONY ||
                                 unitType == ScrUnitIds.ZERG_SUNKEN_COLONY ||
                                 (unitType == ScrUnitIds.TERRAN_MISSILE_TURRET || unitType == ScrUnitIds.TERRAN_BUNKER ||
                                  unitType == ScrUnitIds.PROTOSS_PHOTON_CANNON) && unitType == type;

                if (!spaced) {
                    continue;
                }

                String pattern = SPACING_PATTERNS[level];
                int size = SPACING_PATTERN_SIZES[level];
                int half = size >>> 1;
                Sprite sprite = unit.requireSprite();
                int patternIndex = 0;
                int row = (sprite.getY() - (short) originY) / TerrainTileset.TILE_SIZE - half + (GRID_HALF - 1);

                for (int count = 0; count < size; count++, row++) {
                    if (row >= SIZE) {
                        break;
                    }

                    if (row < 0) {
                        patternIndex += size;

                        continue;
                    }

                    int column = (sprite.getX() - (short) originX) / TerrainTileset.TILE_SIZE - half + (GRID_HALF - 1);

                    for (int step = 0; step < size; step++, column++, patternIndex++) {
                        if (Integer.compareUnsigned(column, LAST_CELL) <= 0 && pattern.charAt(patternIndex) == SPACING_CLEARED) {
                            this.grid[row * SIZE + column] = 0;
                        }
                    }
                }
            }

            if (!isEmpty(this.grid)) {
                return;
            }

            System.arraycopy(this.saved, 0, this.grid, 0, this.grid.length);

            if (level >= LAST_SPACING_LEVEL) {
                return;
            }
        }
    }

    /**
     * Keeps a one-tile gap around existing production and requires the eight neighbors of each candidate for spaced
     * building types.
     *
     * @throws UnsupportedOperationException if the unit pool is not initialized
     */
    private void spaceProduction(@NotNull ScrUnitTypeView units, int type, int owner, int originX, int originY) {
        byte[] before = this.inner;
        UnitPool pool = this.game.getUnitSystem().requireUnitPool();

        System.arraycopy(this.grid, 0, before, 0, this.grid.length);

        for (int poolIndex = pool.getFirstOwnerIndex(owner); poolIndex != 0; poolIndex = pool.getNextOwnerIndex(poolIndex)) {
            Unit unit = pool.getUnit(poolIndex);

            if (ComputerUnits.isDeadOrHidden(unit)) {
                continue;
            }

            switch (ScrUnitIds.requireValid(this.game.requireGameData().getBaseType(unit.getUnitId()))) {
                case ScrUnitIds.TERRAN_BARRACKS, ScrUnitIds.TERRAN_BUNKER,
                     ScrUnitIds.ZERG_CREEP_COLONY -> this.clearAroundUnit(units, unit, type, originX, originY, 1);
                case ScrUnitIds.TERRAN_FACTORY, ScrUnitIds.TERRAN_MISSILE_TURRET, ScrUnitIds.PROTOSS_ROBOTICS_FACILITY, ScrUnitIds.PROTOSS_GATEWAY,
                     ScrUnitIds.PROTOSS_PHOTON_CANNON -> this.clearAroundUnit(units, unit, type, originX, originY, WIDE_MARGIN);
                default -> {
                }
            }
        }

        switch (ScrUnitIds.requireValid(this.game.requireGameData().getBaseType(type))) {
            case ScrUnitIds.TERRAN_BARRACKS, ScrUnitIds.TERRAN_FACTORY, ScrUnitIds.TERRAN_MISSILE_TURRET, ScrUnitIds.TERRAN_BUNKER,
                 ScrUnitIds.PROTOSS_ROBOTICS_FACILITY, ScrUnitIds.PROTOSS_GATEWAY, ScrUnitIds.PROTOSS_PHOTON_CANNON -> {
                for (int row = 0; row < SIZE; row++) {
                    for (int column = 0; column < SIZE; column++) {
                        int index = row * SIZE + column;

                        if (this.grid[index] != 0 && !hasFullNeighborhood(before, row, column)) {
                            this.grid[index] = 0;
                        }
                    }
                }
            }
            default -> {
            }
        }

        if (isEmpty(this.grid)) {
            System.arraycopy(before, 0, this.grid, 0, this.grid.length);
        }
    }

    private void restoreIfEmpty() {
        if (isEmpty(this.grid)) {
            System.arraycopy(this.saved, 0, this.grid, 0, this.grid.length);
        }
    }
}
