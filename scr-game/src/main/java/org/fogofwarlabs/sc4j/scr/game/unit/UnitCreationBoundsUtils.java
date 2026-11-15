package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;

/**
 * Tests unit extents against map pixel bounds before unit storage initialization.
 */
public final class UnitCreationBoundsUtils {
    /**
     * The mask of the WORD map extent in pixels.
     */
    private static final int WORD_MASK = 0xFFFF;

    /**
     * Tests the four coordinate bounds with signed unit extents.
     * <p>
     * Passing this check does not establish allocation, collision resolution or completion success.
     * The lower checks preserve the sign flag of subtraction, including integer overflow.
     *
     * @param unitTypes the unit type table
     * @param map       the simulation map
     * @param unitType  the unit type index
     * @param positionX the horizontal creation coordinate in pixels
     * @param positionY the vertical creation coordinate in pixels
     * @return {@code true} if all four coordinate checks pass, {@code false} otherwise
     */
    public static boolean isWithinCreationBounds(@NotNull ScrUnitTypeView unitTypes, @NotNull GameMap map, int unitType, int positionX, int positionY) {
        int leftExtent = (short) unitTypes.getDimensionLeft(unitType);
        int upperExtent = (short) unitTypes.getDimensionUp(unitType);
        int rightExtent = (short) unitTypes.getDimensionRight(unitType);
        int lowerExtent = (short) unitTypes.getDimensionDown(unitType);
        int mapWidthPixels = (map.getWidth() << TerrainTileset.TILE_SHIFT) & WORD_MASK;
        int mapHeightPixels = (map.getHeight() << TerrainTileset.TILE_SHIFT) & WORD_MASK;

        return positionX - leftExtent >= 0 && positionX + rightExtent < mapWidthPixels &&
               positionY - upperExtent >= 0 && positionY + lowerExtent < mapHeightPixels;
    }


    private UnitCreationBoundsUtils() {
    }
}
