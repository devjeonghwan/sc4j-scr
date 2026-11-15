package org.fogofwarlabs.sc4j.scr.game.terrain;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.jetbrains.annotations.NotNull;

/**
 * Provides terrain-array inputs through saved-game restore methods and finds represented installed height patterns.
 */
public final class GameMapScenario {
    private static final int WORD_MASK = 0xFFFF;
    private static final int TILE_VARIANTS_PER_GROUP = 16;
    private static final int MAXIMUM_TILE_GROUPS = 2048;
    private static final int FIRST_TERRAIN_TILE_IDENTIFIER = 32;

    /**
     * Finds an installed tile with the requested height at the given miniature position using the existing raw height API. Raw height megatiles
     * outside VF4 are omitted; no match returns {@code -1}, which the behavior test must reject.
     *
     * @param terrain   the borrowed terrain resources
     * @param positionX the horizontal pixel position
     * @param positionY the vertical pixel position
     * @param height    the required miniature height
     * @return the first represented tile identifier, or {@code -1} if none exists
     */
    public static int findTileAtHeight(@NotNull TerrainTileset terrain, int positionX, int positionY, int height) {
        int limit = Math.min(terrain.getGroupCount(), MAXIMUM_TILE_GROUPS) * TILE_VARIANTS_PER_GROUP;

        for (int tileIdentifier = FIRST_TERRAIN_TILE_IDENTIFIER; tileIdentifier < limit; tileIdentifier++) {
            try {
                if (terrain.getTerrainHeight(tileIdentifier, positionX, positionY) == height) {
                    return tileIdentifier;
                }
            } catch (IllegalArgumentException exception) {
                continue;
            }
        }

        return -1;
    }

    /**
     * Sets the flags of one terrain tile.
     *
     * @param map       the map receiving the flags
     * @param tileIndex the tile index
     * @param flags     the tile flags
     */
    public static void setFlags(@NotNull GameMap map, int tileIndex, int flags) {
        map.restoreTileFlags(tileIndex, flags);
    }

    /**
     * Sets every overlay identifier after validating their extent and range; the overlays are initialized first when the map has none.
     *
     * @param map             the map receiving the identifiers
     * @param tileIdentifiers the overlay identifiers
     * @throws IllegalArgumentException if the requested overlay extent differs from the map or an identifier does not fit a WORD
     */
    public static void setOverlays(@NotNull GameMap map, int @NotNull [] tileIdentifiers) {
        if (tileIdentifiers.length != (long) map.getWidth() * map.getHeight()) {
            throw Exceptions.newIllegalArgumentException("Fixture overlays do not match the map extent.");
        }

        for (int identifier : tileIdentifiers) {
            if (identifier < 0 || identifier > WORD_MASK) {
                throw Exceptions.newIllegalArgumentException("Fixture overlay identifier is outside the WORD range.");
            }
        }

        if (!map.hasOverlayTiles()) {
            map.initializeOverlayTiles();
        }

        for (int tileIndex = 0; tileIndex < tileIdentifiers.length; tileIndex++) {
            map.restoreOverlayTileIdentifier(tileIndex, tileIdentifiers[tileIndex]);
        }
    }

    /**
     * Creates a {@link GameMapScenario} instance.
     */
    private GameMapScenario() {
    }
}
