package org.fogofwarlabs.sc4j.scr.game.map;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.map.grid.GridTopology;
import org.fogofwarlabs.sc4j.engine.map.grid.SquareGridTopology;
import org.fogofwarlabs.sc4j.engine.terrain.TerrainGrid;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.creep.CreepField;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Reads the terrain of an SC:R game as the engine {@link TerrainGrid}: {@code 32}-pixel tiles of {@code 8}-pixel walk cells (the VF4 miniatures).
 * The storage is the game's {@link GameMap} tile flags ({@link MapTileFlags}), the creep field's graphics cache and the tileset's miniature flags,
 * read at every query, so creep and EUD changes of the tile flags show at once.
 * <p>
 * A walk cell is walkable on a creep tile, by the walkable flag of a tile that is not partially walkable, and otherwise by the walkable bit of its
 * miniature in the tile's current megatile. A tile is buildable without {@link MapTileFlags#UNBUILDABLE}. The elevation is the miniature height of
 * the tile under an overlay first. Walk cells reach their tile through the row-major index {@code walkY / 4 * width + walkX / 4}, which bounds
 * neither axis. The tile and walk grids are square grids of the current map size, made again when the map size changes.
 */
public final class ScrTerrainGrid implements TerrainGrid {
    private final @NotNull Game game;

    private @Nullable SquareGridTopology tileGrid;
    private @Nullable SquareGridTopology walkGrid;

    /**
     * Creates a {@link ScrTerrainGrid} instance over the map of a game.
     *
     * @param game the game
     */
    public ScrTerrainGrid(@NotNull Game game) {
        this.game = game;

        this.tileGrid = null;
        this.walkGrid = null;
    }

    /**
     * @return the size of a tile in pixels
     */
    @Override
    public int getTileSize() {
        return TerrainTileset.TILE_SIZE;
    }

    /**
     * @return the size of a walk cell in pixels, which divides the tile size
     */
    @Override
    public int getWalkCellSize() {
        return TerrainTileset.MINI_TILE_SIZE;
    }

    /**
     * @return the map width in tiles
     */
    @Override
    public int getWidth() {
        return this.game.getMap().getWidth();
    }

    /**
     * @return the map height in tiles
     */
    @Override
    public int getHeight() {
        return this.game.getMap().getHeight();
    }

    /**
     * @return the grid of the tiles over the world plane, the same instance on every call while the map size stays
     */
    @Override
    public @NotNull GridTopology getTileGrid() {
        SquareGridTopology grid = this.tileGrid;
        int width = this.getWidth();
        int height = this.getHeight();

        if (grid == null || grid.getColumnCount() != width || grid.getRowCount() != height) {
            grid          = new SquareGridTopology(TerrainTileset.TILE_SIZE, width, height);
            this.tileGrid = grid;
        }

        return grid;
    }

    /**
     * @return the grid of the walk cells over the world plane, the same instance on every call while the map size stays
     */
    @Override
    public @NotNull GridTopology getWalkGrid() {
        SquareGridTopology grid = this.walkGrid;
        int width = this.getWalkWidth();
        int height = this.getWalkHeight();

        if (grid == null || grid.getColumnCount() != width || grid.getRowCount() != height) {
            grid          = new SquareGridTopology(TerrainTileset.MINI_TILE_SIZE, width, height);
            this.walkGrid = grid;
        }

        return grid;
    }


    /**
     * Checks whether ground units can stand on a walk cell.
     * <p>
     * Stored flags decide creep and fully walkable tiles before any cache or resource read; a partially walkable tile reads the retained graphics
     * WORD before acquiring the tileset.
     *
     * @param walkX the horizontal walk cell
     * @param walkY the vertical walk cell
     * @return {@code true} if the walk cell is walkable, {@code false} otherwise
     * @throws UnsupportedOperationException if tile flags are absent, the tile index is outside storage or the creep field is missing
     * @throws IllegalStateException         if the tileset's miniature flags cannot be loaded
     * @throws NullPointerException          if the world was built without game data
     */
    @Override
    public boolean isWalkable(int walkX, int walkY) {
        GameMap map = this.game.getMap();
        int tileIndex = walkY / TerrainTileset.MINI_TILES_PER_ROW * map.getWidth() + walkX / TerrainTileset.MINI_TILES_PER_ROW;

        if (!map.hasTileFlags() || tileIndex < 0 || tileIndex >= GameMap.MAXIMUM_TILE_COUNT) {
            throw Exceptions.newUnsupportedOperationException("Native terrain-point predicate references unrepresented tile memory.");
        }

        int flags = map.getStoredTileFlags(tileIndex);

        if ((flags & MapTileFlags.HAS_CREEP) != 0) {
            return true;
        }

        if ((flags & MapTileFlags.PARTIALLY_WALKABLE) == 0) {
            return (flags & MapTileFlags.WALKABLE) != 0;
        }

        int miniatureIndex = (walkY & TerrainTileset.MINI_TILE_COLUMN_MASK) * TerrainTileset.MINI_TILES_PER_ROW +
                             (walkX & TerrainTileset.MINI_TILE_COLUMN_MASK);
        CreepField creep = this.game.getCreepField();

        if (creep == null) {
            throw Exceptions.newUnsupportedOperationException("Native terrain-point predicate requires initialized graphics-cache storage.");
        }

        int graphicsCache = creep.getGraphicsCache(tileIndex);

        return (this.requireTileset().getMegatileMiniatureFlags(graphicsCache & CreepField.GRAPHICS_MEGATILE_MASK, miniatureIndex) &
                TerrainTileset.MINI_TILE_WALKABLE) != 0;
    }

    /**
     * Checks whether a tile takes buildings.
     *
     * @param tileX the horizontal tile
     * @param tileY the vertical tile
     * @return {@code true} if the tile is buildable, {@code false} otherwise
     * @throws IllegalStateException     if expanded terrain flags are unavailable
     * @throws IndexOutOfBoundsException if the tile is outside the map
     */
    @Override
    public boolean isBuildable(int tileX, int tileY) {
        return (this.game.getMap().getTileFlags(tileX, tileY) & MapTileFlags.UNBUILDABLE) == 0;
    }

    /**
     * Reads the elevation of a walk cell.
     * <p>
     * The SC:R elevation is the terrain height of the walk cell's top left pixel: {@code 0} low, {@code 1} middle and {@code 2} high ground.
     *
     * @param walkX the horizontal walk cell
     * @param walkY the vertical walk cell
     * @return the elevation level, {@code 0} for the lowest ground
     * @throws IllegalStateException    if overlays have not been initialized or the tileset cannot be loaded
     * @throws IllegalArgumentException if the selected tile or its megatile is outside the terrain resources
     * @throws NullPointerException     if the world was built without game data
     */
    @Override
    public int getElevation(int walkX, int walkY) {
        return this.game.getMap().getTerrainHeight(this.requireTileset(),
                                                   walkX * TerrainTileset.MINI_TILE_SIZE, walkY * TerrainTileset.MINI_TILE_SIZE);
    }

    private @NotNull TerrainTileset requireTileset() {
        try {
            return this.game.requireGameData().getTerrainTileset(this.game.getMap().getTileset());
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "Native terrain-point predicate could not load original miniature flags.");
        }
    }
}
