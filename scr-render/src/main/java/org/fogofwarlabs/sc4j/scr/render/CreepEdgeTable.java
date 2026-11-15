package org.fogofwarlabs.sc4j.scr.render;

import org.jetbrains.annotations.NotNull;

/**
 * Selects a tileset creep-edge frame for a tile without creep from its eight neighboring creep tiles.
 * <p>
 * Bit {@code n} of a neighbor mask represents the neighbor in direction {@code n}: {@code (1,1)}, {@code (0,1)}, {@code (-1,1)}, {@code (1,0)},
 * {@code (-1,0)}, {@code (1,-1)}, {@code (0,-1)} and {@code (-1,-1)}. The table maps these masks to one-based edge frames.
 */
final class CreepEdgeTable {
    /**
     * The horizontal tile delta of each neighbor direction.
     */
    private static final int @NotNull [] NEIGHBOR_HORIZONTAL = {1, 0, -1, 1, -1, 1, 0, -1};
    /**
     * The vertical tile delta of each neighbor direction.
     */
    private static final int @NotNull [] NEIGHBOR_VERTICAL = {1, 1, 1, 0, 0, -1, -1, -1};

    /**
     * The neighbor mask bits of the eight directions, named by the neighbor's side of the tile (a larger vertical delta is lower).
     */
    private static final int NEIGHBOR_BOTTOM_RIGHT = 0x01;
    private static final int NEIGHBOR_BOTTOM = 0x02;
    private static final int NEIGHBOR_BOTTOM_LEFT = 0x04;
    private static final int NEIGHBOR_RIGHT = 0x08;
    private static final int NEIGHBOR_LEFT = 0x10;
    private static final int NEIGHBOR_TOP_RIGHT = 0x20;
    private static final int NEIGHBOR_TOP = 0x40;
    private static final int NEIGHBOR_TOP_LEFT = 0x80;
    /**
     * The neighbor pairs that both have to hold creep to mark a corner edge.
     */
    private static final int TOP_LEFT_NEIGHBORS = NEIGHBOR_TOP | NEIGHBOR_TOP_LEFT;
    private static final int TOP_RIGHT_NEIGHBORS = NEIGHBOR_TOP_RIGHT | NEIGHBOR_TOP;
    private static final int BOTTOM_RIGHT_NEIGHBORS = NEIGHBOR_BOTTOM_RIGHT | NEIGHBOR_BOTTOM;
    private static final int BOTTOM_LEFT_NEIGHBORS = NEIGHBOR_BOTTOM | NEIGHBOR_BOTTOM_LEFT;
    /**
     * The edge mask bits: the four corners, the top and bottom sides, and both corners of a diagonal.
     */
    private static final int EDGE_TOP_LEFT = 0x01;
    private static final int EDGE_TOP = 0x02;
    private static final int EDGE_TOP_RIGHT = 0x04;
    private static final int EDGE_BOTTOM_LEFT = 0x08;
    private static final int EDGE_BOTTOM = 0x10;
    private static final int EDGE_BOTTOM_RIGHT = 0x20;
    private static final int EDGE_DIAGONAL = 0x40;
    /**
     * The edge pairs of the two diagonals, either of which adds {@link #EDGE_DIAGONAL}.
     */
    private static final int FALLING_DIAGONAL_EDGES = EDGE_TOP_LEFT | EDGE_BOTTOM_RIGHT;
    private static final int RISING_DIAGONAL_EDGES = EDGE_TOP_RIGHT | EDGE_BOTTOM_LEFT;

    /**
     * The number of neighbor directions, one mask bit each.
     */
    private static final int DIRECTION_COUNT = 8;
    /**
     * The number of edge bits, {@link #EDGE_DIAGONAL} included.
     */
    private static final int EDGE_BIT_COUNT = 7;
    /**
     * The number of neighbor masks: one bit per direction.
     */
    private static final int NEIGHBOR_MASKS = 1 << DIRECTION_COUNT;
    /**
     * The neighbor mask with all eight direction bits.
     */
    private static final int ALL_NEIGHBORS = NEIGHBOR_MASKS - 1;
    /**
     * The number of edge masks: one per combination of the edge bits.
     */
    private static final int EDGE_MASKS = 1 << EDGE_BIT_COUNT;
    /**
     * The one-based creep-edge frame of each neighbor mask, {@code 0} without an edge.
     */
    private static final int @NotNull [] FRAMES = new int[NEIGHBOR_MASKS];

    static {
        int[] edgeMasks = new int[NEIGHBOR_MASKS];

        for (int neighbors = 0; neighbors < NEIGHBOR_MASKS; neighbors++) {
            int mask = 0;

            if ((neighbors & NEIGHBOR_BOTTOM) != 0) {
                mask |= EDGE_BOTTOM;
            }

            if ((neighbors & NEIGHBOR_RIGHT) != 0) {
                mask |= EDGE_TOP_RIGHT | EDGE_BOTTOM_RIGHT;
            }

            if ((neighbors & NEIGHBOR_LEFT) != 0) {
                mask |= EDGE_TOP_LEFT | EDGE_BOTTOM_LEFT;
            }

            if ((neighbors & NEIGHBOR_TOP) != 0) {
                mask |= EDGE_TOP;
            }

            if ((neighbors & TOP_LEFT_NEIGHBORS) == TOP_LEFT_NEIGHBORS) {
                mask |= EDGE_TOP_LEFT;
            }

            if ((neighbors & TOP_RIGHT_NEIGHBORS) == TOP_RIGHT_NEIGHBORS) {
                mask |= EDGE_TOP_RIGHT;
            }

            if ((neighbors & BOTTOM_RIGHT_NEIGHBORS) == BOTTOM_RIGHT_NEIGHBORS) {
                mask |= EDGE_BOTTOM_RIGHT;
            }

            if ((neighbors & BOTTOM_LEFT_NEIGHBORS) == BOTTOM_LEFT_NEIGHBORS) {
                mask |= EDGE_BOTTOM_LEFT;
            }

            if ((mask & FALLING_DIAGONAL_EDGES) == FALLING_DIAGONAL_EDGES) {
                mask |= EDGE_DIAGONAL;
            }

            if ((mask & RISING_DIAGONAL_EDGES) == RISING_DIAGONAL_EDGES) {
                mask |= EDGE_DIAGONAL;
            }

            edgeMasks[neighbors] = mask;
        }

        int[] identifiers = new int[EDGE_MASKS];
        int nextIdentifier = 0;

        for (int mask = 0; mask < identifiers.length; mask++) {
            for (int edgeMask : edgeMasks) {
                if (edgeMask == mask) {
                    identifiers[mask] = nextIdentifier++;

                    break;
                }
            }
        }

        for (int neighbors = 0; neighbors < NEIGHBOR_MASKS; neighbors++) {
            FRAMES[neighbors] = identifiers[edgeMasks[neighbors]];
        }
    }

    /**
     * @return the number of neighbor directions
     */
    static int getDirectionCount() {
        return NEIGHBOR_HORIZONTAL.length;
    }

    /**
     * @param direction the neighbor direction from {@code 0} through {@code 7}
     * @return the neighbor's horizontal tile delta
     */
    static int getNeighborHorizontal(int direction) {
        return NEIGHBOR_HORIZONTAL[direction];
    }

    /**
     * @param direction the neighbor direction from {@code 0} through {@code 7}
     * @return the neighbor's vertical tile delta
     */
    static int getNeighborVertical(int direction) {
        return NEIGHBOR_VERTICAL[direction];
    }

    /**
     * @param neighborMask the eight-bit mask of neighboring creep tiles
     * @return the one-based creep GRP frame, or {@code 0} when no edge is drawn
     */
    static int getFrame(int neighborMask) {
        return FRAMES[neighborMask & ALL_NEIGHBORS];
    }

    private CreepEdgeTable() {
    }
}
