package org.fogofwarlabs.sc4j.scr.game.terrain;

import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;

/**
 * Names the bits of a map tile's flag DWORD ({@link GameMap#getTileFlags(int)}).
 * <p>
 * The low WORD holds per-player visibility: bit {@code n} of the low byte is set while player {@code n} does not see the tile, and bit {@code n} of
 * the second byte while player {@code n} has not explored it. The high WORD holds the terrain and occupancy bits shifted by {@code 16}.
 * <p>
 * {@link TerrainTileset} writes the same terrain bits when it expands the CV5/VF4 flags of a map (walkable, unwalkable, creep, blocks view as
 * {@link #VERY_HIGH}, middle and high ground, partially walkable) under its own private names, because the data module cannot depend on this package;
 * this class owns the bits for the game.
 */
public final class MapTileFlags {
    /**
     * The per-player "not visible" bits (low byte).
     */
    public static final int NOT_VISIBLE = 0xFF;
    /**
     * The per-player "not visible" and "not explored" bits (low WORD).
     */
    public static final int NOT_VISIBLE_OR_EXPLORED = 0xFFFF;
    /**
     * The shift of the per-player "not explored" bits: bit {@code n + 8} is set while player {@code n} has not explored the tile.
     */
    public static final int NOT_EXPLORED_SHIFT = 8;

    /**
     * The tile is walkable.
     */
    public static final int WALKABLE = 0x10000;
    /**
     * Terrain bit {@code 0x20000}.
     */
    public static final int FLAG_20000 = 0x20000;
    /**
     * The tile is unwalkable.
     */
    public static final int UNWALKABLE = 0x40000;
    /**
     * Terrain bit {@code 0x80000}.
     */
    public static final int FLAG_80000 = 0x80000;
    /**
     * The tile provides cover.
     */
    public static final int PROVIDES_COVER = 0x100000;
    /**
     * Terrain bit {@code 0x200000}.
     */
    public static final int FLAG_200000 = 0x200000;
    /**
     * The tile has creep.
     */
    public static final int HAS_CREEP = 0x400000;
    /**
     * The tile is unbuildable.
     */
    public static final int UNBUILDABLE = 0x800000;
    /**
     * The tile is very high ground.
     */
    public static final int VERY_HIGH = 0x1000000;
    /**
     * The tile is middle ground.
     */
    public static final int MIDDLE = 0x2000000;
    /**
     * The tile is high ground.
     */
    public static final int HIGH = 0x4000000;
    /**
     * A building occupies the tile.
     */
    public static final int OCCUPIED = 0x8000000;
    /**
     * The tile's creep is receding.
     */
    public static final int CREEP_RECEDING = 0x10000000;
    /**
     * The tile is partially walkable.
     */
    public static final int PARTIALLY_WALKABLE = 0x20000000;
    /**
     * The tile has temporary creep.
     */
    public static final int TEMPORARY_CREEP = 0x40000000;
    /**
     * Terrain bit {@code 0x80000000}.
     */
    public static final int FLAG_80000000 = 0x80000000;

    private MapTileFlags() {
    }
}
