package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.creep.CreepField;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameCheats;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Applies map graphics-cache, visibility reset and reveal-cheat passes. Control fields retain their encoded widths. Input controls and diagnostic
 * snapshots do not share mutable storage.
 */
public final class MapVisibility {
    /**
     * The index of the shared mode byte in the control array ({@link #MapVisibility}).
     */
    private static final int CONTROL_VISIBILITY_OVERRIDE = 0;
    /**
     * The index of the cheat bit field in the control array.
     */
    private static final int CONTROL_CHEAT_FLAGS = 1;
    /**
     * The index of the display byte in the control array.
     */
    private static final int CONTROL_VISIBILITY_FLAGS = 2;
    /**
     * The index of the graphics refresh byte in the control array.
     */
    private static final int CONTROL_GRAPHICS_DIRTY = 3;
    /**
     * The index of the display refresh DWORD in the control array.
     */
    private static final int CONTROL_DISPLAY_DIRTY = 4;
    /**
     * The number of control fields.
     */
    private static final int CONTROL_COUNT = 5;
    /**
     * The cheats that reveal the map, which the shared mode byte suppresses.
     */
    private static final int REVEAL_CHEATS = GameCheats.BLACK_SHEEP_WALL | GameCheats.WAR_AINT_WHAT_IT_USED_TO_BE;
    /**
     * The bit of the display byte that the display refresh sets.
     */
    private static final int DISPLAY_REFRESH_FLAG = 0x1;
    /**
     * The bit of the display byte that the visibility reset sets while the refresh bit is clear.
     */
    private static final int DISPLAY_RESET_FLAG = 0x2;
    /**
     * The megatile identifier bits of a graphics-cache WORD.
     */
    private static final int GRAPHICS_CACHE_MEGATILE_MASK = 0x7FFF;
    /**
     * The value of the graphics and display refresh controls that requests a refresh.
     */
    private static final int REFRESH_PENDING = 1;
    /**
     * The tiles the viewport covers in each direction beyond its pixel size rounded up to whole tiles.
     */
    private static final int VIEWPORT_EDGE_TILES = 1;
    /**
     * The mask of a control BYTE.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The mask of a viewport tile WORD.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The multiplier of the diagnostic hash.
     */
    private static final int HASH_MULTIPLIER = 31;

    /**
     * Creates the graphics-cache WORDs: the megatile identifier of every tile with the tile identifier's high bit.
     *
     * @param map     the map with the already normalized MTXM tile identifiers
     * @param terrain the original installed CV5 records
     * @return the initialized graphics-cache WORDs
     */
    public static int @NotNull [] createGraphicsCache(@NotNull GameMap map, @NotNull TerrainTileset terrain) {
        int[] cache = new int[map.getTileCount()];

        for (int tileIndex = 0; tileIndex < map.getTileCount(); tileIndex++) {
            cache[tileIndex] = terrain.getMegatileIdentifier(map.getTileIdentifier(tileIndex)) |
                               (map.getTileIdentifier(tileIndex) & CreepField.GRAPHICS_REFRESH_FLAG);
        }

        return cache;
    }

    private final @NotNull TerrainTileset terrain;

    private final int visibilityOverride;
    private int cheatFlags;
    private int visibilityFlags;
    private int graphicsDirty;
    private int displayDirty;

    /**
     * Creates a {@link MapVisibility} instance with the retained rendering and visibility controls.
     *
     * @param terrain  the original installed CV5 records
     * @param controls the shared mode BYTE, the cheat DWORD, the display BYTE, the graphics refresh BYTE and the display refresh DWORD, in that order
     * @throws IllegalArgumentException if the control array does not hold the five fields
     */
    public MapVisibility(@NotNull TerrainTileset terrain, int @NotNull [] controls) {
        if (controls.length != CONTROL_COUNT) {
            throw Exceptions.newIllegalArgumentException("Native map visibility requires five established control fields.");
        }

        this.terrain = terrain;

        this.visibilityOverride = controls[CONTROL_VISIBILITY_OVERRIDE] & BYTE_MASK;
        this.cheatFlags         = controls[CONTROL_CHEAT_FLAGS];
        this.visibilityFlags    = controls[CONTROL_VISIBILITY_FLAGS] & BYTE_MASK;
        this.graphicsDirty      = controls[CONTROL_GRAPHICS_DIRTY] & BYTE_MASK;
        this.displayDirty       = controls[CONTROL_DISPLAY_DIRTY];
    }

    /**
     * @return the shared mode byte that suppresses cheat application when nonzero
     */
    public int getVisibilityOverride() {
        return this.visibilityOverride;
    }

    /**
     * @return the shared original cheat bit field
     */
    public int getCheatFlags() {
        return this.cheatFlags;
    }

    /**
     * Sets the shared cheat flags consumed by gameplay and map visibility.
     *
     * @param cheatFlags the retained cheat bit field
     */
    public void setCheatFlags(int cheatFlags) {
        this.cheatFlags = cheatFlags;
    }

    /**
     * @return an independent snapshot of the controls in the constructor's order
     */
    public int @NotNull [] getControlSnapshot() {
        return new int[]{this.visibilityOverride, this.cheatFlags, this.visibilityFlags, this.graphicsDirty, this.displayDirty};
    }


    /**
     * Refreshes the graphics cache of every tile the local view sees outside the viewport whose megatile changed, with its creep flag and creep
     * edges, and marks the graphics and the display for a refresh when a tile changed. The local view sees a tile that is not unseen
     * ({@link LocalVision#isTileUnseen(int)}); "Reveal Entire Map" does not count here.
     *
     * @param game               the game whose map, creep field with the graphics cache and local vision the update uses
     * @param viewportHorizontal the main viewport's horizontal tile WORD
     * @param viewportVertical   the main viewport's vertical tile WORD
     * @param viewportWidth      the viewport width in pixels
     * @param viewportHeight     the viewport height in pixels
     * @throws UnsupportedOperationException if the creep and graphics-cache context is missing
     */
    public void updateGraphicsCache(@NotNull Game game, int viewportHorizontal, int viewportVertical, int viewportWidth, int viewportHeight) {
        CreepField creep = game.getCreepField();

        if (creep == null) {
            throw Exceptions.newUnsupportedOperationException("Native map graphics updates require the shared creep and graphics-cache context.");
        }

        GameMap map = game.getMap();
        LocalVision vision = game.getLocalVision();

        // Requires the expanded terrain flags.
        map.getTileFlagCount();

        int horizontalExtent = (viewportWidth + TerrainTileset.TILE_SIZE - 1) / TerrainTileset.TILE_SIZE + VIEWPORT_EDGE_TILES;
        int verticalExtent = (viewportHeight + TerrainTileset.TILE_SIZE - 1) / TerrainTileset.TILE_SIZE + VIEWPORT_EDGE_TILES;
        boolean changed = false;

        for (int tileIndex = 0; tileIndex < map.getTileCount(); tileIndex++) {
            if (vision.isTileUnseen(map.getTileFlags(tileIndex))) {
                continue;
            }

            int horizontal = tileIndex % map.getWidth();
            int vertical = tileIndex / map.getWidth();

            if (Integer.compareUnsigned(horizontal - (viewportHorizontal & WORD_MASK), horizontalExtent) < 0 &&
                Integer.compareUnsigned(vertical - (viewportVertical & WORD_MASK), verticalExtent) < 0) {
                continue;
            }

            int megatile = this.terrain.getMegatileIdentifier(map.getTileIdentifier(tileIndex));

            if (megatile == (creep.getGraphicsCache(tileIndex) & GRAPHICS_CACHE_MEGATILE_MASK)) {
                continue;
            }

            creep.updateGraphicsCache(tileIndex, megatile);
            map.refreshCreepGraphicsFlag(tileIndex);
            creep.refreshGraphicsEdges(horizontal, vertical);
            changed = true;
        }

        if (changed) {
            this.graphicsDirty = REFRESH_PENDING;
            this.displayDirty  = REFRESH_PENDING;
        }
    }

    /**
     * Applies the complete visibility reset: with the shared mode byte set the reveal cheats are cleared; the display byte records the reset while
     * its refresh bit is clear; unless a reveal cheat is active the display is refreshed and every tile is marked unseen
     * ({@link GameMap#resetVisibility()}).
     *
     * @param map the map with the per-tile visibility DWORDs
     */
    public void resetVisibility(@NotNull GameMap map) {
        if (this.visibilityOverride != 0) {
            this.cheatFlags &= ~REVEAL_CHEATS;
        }

        if ((this.visibilityFlags & DISPLAY_REFRESH_FLAG) == 0) {
            this.visibilityFlags |= DISPLAY_RESET_FLAG;
        }

        if ((this.cheatFlags & REVEAL_CHEATS) != 0) {
            return;
        }

        this.visibilityFlags |= DISPLAY_REFRESH_FLAG;
        map.resetVisibility();
    }

    /**
     * Applies the reveal-cheat update: with the shared mode byte set the reveal cheats are cleared; {@link GameCheats#BLACK_SHEEP_WALL} clears every
     * tile's not-visible and not-explored bits, otherwise {@link GameCheats#WAR_AINT_WHAT_IT_USED_TO_BE} clears the not-visible bits; either then
     * refreshes the display ({@link #refreshDisplay()}).
     *
     * @param map the map with the per-tile visibility DWORDs
     */
    public void applyRevealCheat(@NotNull GameMap map) {
        if (this.visibilityOverride != 0) {
            this.cheatFlags &= ~REVEAL_CHEATS;
        }

        int clearedBits;

        if ((this.cheatFlags & GameCheats.BLACK_SHEEP_WALL) != 0) {
            clearedBits = MapTileFlags.NOT_VISIBLE_OR_EXPLORED;
        } else if ((this.cheatFlags & GameCheats.WAR_AINT_WHAT_IT_USED_TO_BE) != 0) {
            clearedBits = MapTileFlags.NOT_VISIBLE;
        } else {
            return;
        }

        map.clearVisibilityBits(clearedBits);
        this.refreshDisplay();
    }

    /**
     * Refreshes the display: sets the refresh bit of the display byte when it is clear.
     */
    public void refreshDisplay() {
        if ((this.visibilityFlags & DISPLAY_REFRESH_FLAG) == 0) {
            this.visibilityFlags |= DISPLAY_REFRESH_FLAG;
        }
    }

    /**
     * Mixes every retained control into a diagnostic hash.
     *
     * @param hash the preceding diagnostic hash
     * @return the hash including every retained control
     */
    public int computeStateHash(int hash) {
        hash = HASH_MULTIPLIER * hash + this.visibilityOverride;
        hash = HASH_MULTIPLIER * hash + this.cheatFlags;
        hash = HASH_MULTIPLIER * hash + this.visibilityFlags;
        hash = HASH_MULTIPLIER * hash + this.graphicsDirty;
        hash = HASH_MULTIPLIER * hash + this.displayDirty;

        return hash;
    }
}
