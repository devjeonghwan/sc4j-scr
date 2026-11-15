package org.fogofwarlabs.sc4j.scr.game.terrain;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Prepares terrain inputs for tests in other packages.
 */
public final class TerrainRegionScenario {
    /**
     * The saved pathing data: the tile-region WORDs, the 64-byte region records and the component WORD of the region records.
     */
    private static final int SAVED_TILE_REGIONS = 0x0C;
    private static final int SAVED_REGIONS = 0x449FC;
    private static final int SAVED_REGION_SIZE = 0x40;
    private static final int SAVED_REGION_COMPONENTS = SAVED_REGIONS + 0x02;
    /**
     * The saved neighbor fields of the 64-byte region records: the walkable and total neighbor count BYTEs, the neighbor list DWORD
     * ({@link TerrainRegionCheck#INLINE_NEIGHBOR_LIST} for the inline list), the region-zero neighbor count BYTE and the first inline neighbor WORD.
     */
    private static final int SAVED_REGION_WALKABLE_NEIGHBORS = SAVED_REGIONS + 0x06;
    private static final int SAVED_REGION_NEIGHBORS = SAVED_REGIONS + 0x07;
    private static final int SAVED_REGION_NEIGHBOR_LIST = SAVED_REGIONS + 0x0C;
    private static final int SAVED_REGION_ZERO_NEIGHBORS = SAVED_REGIONS + 0x21;
    private static final int SAVED_REGION_INLINE_NEIGHBORS = SAVED_REGIONS + 0x2C;
    /**
     * The tile-region WORDs of one tile row.
     */
    private static final int TILE_REGION_ROW = 256;

    /**
     * The rows and columns of the placement input and the columns of its left region.
     */
    private static final int PLACEMENT_INPUT_SIZE = 8;
    private static final int PLACEMENT_LEFT_COLUMNS = 4;
    /**
     * The rows and columns of the neighbor-region and region-row inputs and the columns of their left region.
     */
    private static final int NEIGHBOR_INPUT_ROWS = 8;
    private static final int NEIGHBOR_INPUT_COLUMNS = 16;
    private static final int NEIGHBOR_LEFT_COLUMNS = 7;
    /**
     * The regions of the placement and neighbor-region inputs, the component of their left region (also the component of the region-row input) and
     * the other component their right region has or may have.
     */
    private static final int LEFT_REGION = 1;
    private static final int RIGHT_REGION = 2;
    private static final int LEFT_COMPONENT = 17;
    private static final int OTHER_COMPONENT = 29;
    /**
     * The first column of the far region of the region-row input and its regions after the left region.
     */
    private static final int ROW_FAR_COLUMN = 12;
    private static final int MIDDLE_REGION = 2;
    private static final int FAR_REGION = 3;

    /**
     * Sets an eight-by-eight input with two components: region {@code 1} (component {@code 17}) covers the four left columns and region {@code 2}
     * (component {@code 29}) the four right columns. The regions are rewritten through their saved pathing data, so every other region field stays as
     * it was.
     *
     * @param regions the retained regions receiving the input
     */
    public static void configurePlacementComponents(@NotNull TerrainRegions regions) {
        ByteBuffer saved = ByteBuffer.allocate(TerrainRegions.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        regions.writeSaveData(saved);

        for (int vertical = 0; vertical < PLACEMENT_INPUT_SIZE; vertical++) {
            for (int horizontal = 0; horizontal < PLACEMENT_INPUT_SIZE; horizontal++) {
                saved.putShort(SAVED_TILE_REGIONS + (vertical * TILE_REGION_ROW + horizontal) * Short.BYTES,
                               (short) (horizontal < PLACEMENT_LEFT_COLUMNS ? LEFT_REGION : RIGHT_REGION));
            }
        }

        saved.putShort(SAVED_REGION_COMPONENTS + LEFT_REGION * SAVED_REGION_SIZE, (short) LEFT_COMPONENT);
        saved.putShort(SAVED_REGION_COMPONENTS + RIGHT_REGION * SAVED_REGION_SIZE, (short) OTHER_COMPONENT);

        regions.readSaveData(saved);
    }

    /**
     * Sets a sixteen-by-eight input with two regions that list each other as their only neighbor, an inaccessible one: region {@code 1} (component
     * {@code 17}) covers the seven left columns and region {@code 2} (component {@code 17}, or {@code 29} when the components differ) the nine right
     * columns. Neither region has a walkable neighbor. The regions are rewritten through their saved pathing data, so every other region field stays
     * as it was.
     *
     * @param regions         the retained regions receiving the input
     * @param sharedComponent whether region {@code 2} is in the component of region {@code 1}
     */
    public static void configureNeighborRegions(@NotNull TerrainRegions regions, boolean sharedComponent) {
        ByteBuffer saved = ByteBuffer.allocate(TerrainRegions.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        regions.writeSaveData(saved);

        for (int vertical = 0; vertical < NEIGHBOR_INPUT_ROWS; vertical++) {
            for (int horizontal = 0; horizontal < NEIGHBOR_INPUT_COLUMNS; horizontal++) {
                saved.putShort(SAVED_TILE_REGIONS + (vertical * TILE_REGION_ROW + horizontal) * Short.BYTES,
                               (short) (horizontal < NEIGHBOR_LEFT_COLUMNS ? LEFT_REGION : RIGHT_REGION));
            }
        }

        writeNeighbors(saved, LEFT_REGION, LEFT_COMPONENT, false, RIGHT_REGION);
        writeNeighbors(saved, RIGHT_REGION, sharedComponent ? LEFT_COMPONENT : OTHER_COMPONENT, false, LEFT_REGION);

        regions.readSaveData(saved);
    }

    /**
     * Sets a sixteen-by-eight input with three regions of one component (component {@code 17}) in a row: region {@code 1} covers the seven left
     * columns, region {@code 2} the five middle columns and region {@code 3} the four right columns. Each region lists its row neighbors as walkable
     * neighbors, so region {@code 3} is reachable from region {@code 1} only through region {@code 2}. The regions are rewritten through their saved
     * pathing data, so every other region field stays as it was.
     *
     * @param regions the retained regions receiving the input
     */
    public static void configureRegionRow(@NotNull TerrainRegions regions) {
        ByteBuffer saved = ByteBuffer.allocate(TerrainRegions.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        regions.writeSaveData(saved);

        for (int vertical = 0; vertical < NEIGHBOR_INPUT_ROWS; vertical++) {
            for (int horizontal = 0; horizontal < NEIGHBOR_INPUT_COLUMNS; horizontal++) {
                int region = horizontal < NEIGHBOR_LEFT_COLUMNS ? LEFT_REGION : horizontal < ROW_FAR_COLUMN ? MIDDLE_REGION : FAR_REGION;

                saved.putShort(SAVED_TILE_REGIONS + (vertical * TILE_REGION_ROW + horizontal) * Short.BYTES, (short) region);
            }
        }

        writeNeighbors(saved, LEFT_REGION, LEFT_COMPONENT, true, MIDDLE_REGION);
        writeNeighbors(saved, MIDDLE_REGION, LEFT_COMPONENT, true, LEFT_REGION, FAR_REGION);
        writeNeighbors(saved, FAR_REGION, LEFT_COMPONENT, true, MIDDLE_REGION);

        regions.readSaveData(saved);
    }

    /**
     * Writes the component of a saved region record and an inline neighbor list whose neighbors are all walkable or all inaccessible.
     *
     * @param saved     the saved pathing data
     * @param region    the region whose record is written
     * @param component the component of the region
     * @param walkable  whether every neighbor is walkable
     * @param neighbors the neighbor regions
     */
    private static void writeNeighbors(@NotNull ByteBuffer saved, int region, int component, boolean walkable, int @NotNull ... neighbors) {
        int offset = region * SAVED_REGION_SIZE;

        saved.putShort(SAVED_REGION_COMPONENTS + offset, (short) component);
        saved.put(SAVED_REGION_WALKABLE_NEIGHBORS + offset, (byte) (walkable ? neighbors.length : 0));
        saved.put(SAVED_REGION_NEIGHBORS + offset, (byte) neighbors.length);
        saved.putInt(SAVED_REGION_NEIGHBOR_LIST + offset, TerrainRegionCheck.INLINE_NEIGHBOR_LIST);
        saved.put(SAVED_REGION_ZERO_NEIGHBORS + offset, (byte) 0);

        for (int neighborIndex = 0; neighborIndex < neighbors.length; neighborIndex++) {
            saved.putShort(SAVED_REGION_INLINE_NEIGHBORS + offset + neighborIndex * Short.BYTES, (short) neighbors[neighborIndex]);
        }
    }


    /**
     * Creates a {@link TerrainRegionScenario} instance.
     */
    private TerrainRegionScenario() {
    }
}
