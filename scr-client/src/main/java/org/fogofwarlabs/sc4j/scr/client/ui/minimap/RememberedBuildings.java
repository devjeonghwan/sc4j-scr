package org.fogofwarlabs.sc4j.scr.client.ui.minimap;

import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retains the last seen type, owner and position of grounded buildings for minimap display.
 * An unseen record persists until a placement-box tile becomes visible; a changed type or position creates a separate record.
 * Changing games, seeking backward or revealing the map clears the remembered entries.
 */
public final class RememberedBuildings {
    private static final int LEFT_INDEX = 0;
    private static final int TOP_INDEX = 1;
    private static final int RIGHT_INDEX = 2;
    private static final int BOTTOM_INDEX = 3;
    private static final int PLACEMENT_CENTER_DIVISOR = 2;

    /**
     * A remembered building.
     */
    public static final class Ghost {
        private final @NotNull Unit unit;
        private final int type;
        private final int owner;
        private final int positionX;
        private final int positionY;

        private boolean seen;

        /**
         * Creates a {@link Ghost} instance with the remembered identity, owner and position.
         *
         * @param unit      the displayed unit
         * @param type      the unit type identifier
         * @param owner     the owning player
         * @param positionX the horizontal position
         * @param positionY the vertical position
         */
        Ghost(@NotNull Unit unit, int type, int owner, int positionX, int positionY) {
            this.unit      = unit;
            this.type      = type;
            this.owner     = owner;
            this.positionX = positionX;
            this.positionY = positionY;

            this.seen = false;
        }

        /**
         * @return the unit type
         */
        public int getType() {
            return this.type;
        }

        /**
         * @return the owner
         */
        public int getOwner() {
            return this.owner;
        }

        /**
         * @return the last seen horizontal coordinate
         */
        public int getX() {
            return this.positionX;
        }

        /**
         * @return the last seen vertical coordinate
         */
        public int getY() {
            return this.positionY;
        }
    }

    /**
     * Tests whether some tile of a ghost's placement box is unexplored by the mask; such ghosts are not drawn on the replay minimap.
     *
     * @param map   the map with tile flags
     * @param units the unit type table
     * @param ghost the ghost
     * @param mask  the vision mask
     * @return {@code true} if a placement-box tile is unexplored by the mask, {@code false} otherwise
     */
    public static boolean hasUnexploredTile(@NotNull GameMap map, @NotNull ScrUnitTypeView units, @NotNull Ghost ghost, int mask) {
        int[] box = createPlacementBox(map, units, ghost);

        for (int tileY = box[TOP_INDEX]; tileY < box[BOTTOM_INDEX]; tileY++) {
            for (int tileX = box[LEFT_INDEX]; tileX < box[RIGHT_INDEX]; tileX++) {
                if ((~(map.getTileFlags(tileY * map.getWidth() + tileX) >>> MapTileFlags.NOT_EXPLORED_SHIFT) & mask) == 0) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks whether any placement-box tile is currently visible.
     *
     * @param map   the map with tile flags
     * @param units the unit type definitions
     * @param ghost the remembered building
     * @param mask  the current vision mask
     * @return {@code true} if a placement-box tile is currently visible, {@code false} otherwise
     */
    private static boolean hasVisibleTile(@NotNull GameMap map, @NotNull ScrUnitTypeView units, @NotNull Ghost ghost, int mask) {
        if (!map.hasTileFlags()) {
            return false;
        }

        int[] box = createPlacementBox(map, units, ghost);

        for (int tileY = box[TOP_INDEX]; tileY < box[BOTTOM_INDEX]; tileY++) {
            for (int tileX = box[LEFT_INDEX]; tileX < box[RIGHT_INDEX]; tileX++) {
                if ((~map.getTileFlags(tileY * map.getWidth() + tileX) & mask) != 0) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Creates the clipped tile rectangle centered on the remembered placement box.
     *
     * @param map   the map bounds
     * @param units the unit type definitions
     * @param ghost the remembered building
     * @return the left, top, right and bottom tile coordinates
     */
    private static int @NotNull [] createPlacementBox(@NotNull GameMap map, @NotNull ScrUnitTypeView units, @NotNull Ghost ghost) {
        int width = units.getPlacementWidth(ghost.type);
        int height = units.getPlacementHeight(ghost.type);
        int left = Math.max(0, (ghost.positionX - width / PLACEMENT_CENTER_DIVISOR) / TerrainTileset.TILE_SIZE);
        int top = Math.max(0, (ghost.positionY - height / PLACEMENT_CENTER_DIVISOR) / TerrainTileset.TILE_SIZE);

        return new int[]{left, top,
                         Math.min(map.getWidth(), left + (width + TerrainTileset.TILE_SIZE - 1) / TerrainTileset.TILE_SIZE),
                         Math.min(map.getHeight(), top + (height + TerrainTileset.TILE_SIZE - 1) / TerrainTileset.TILE_SIZE)};
    }

    private final @NotNull List<Ghost> entries;
    private final @NotNull Map<Unit, Ghost> byUnit;

    private @Nullable ScrGameView view;
    private long frame;

    /**
     * Creates a {@link RememberedBuildings} instance.
     */
    public RememberedBuildings() {
        this.entries = new ArrayList<>();
        this.byUnit  = new IdentityHashMap<>();

        this.view  = null;
        this.frame = -1;
    }

    /**
     * @return the buildings that are remembered but not currently seen
     */
    public @NotNull List<Ghost> getGhosts() {
        List<Ghost> ghosts = new ArrayList<>();

        for (Ghost ghost : this.entries) {
            if (!ghost.seen) {
                ghosts.add(ghost);
            }
        }

        return ghosts;
    }


    /**
     * Updates the list for the current game frame. The sight of the players the view shows counts
     * ({@link LocalVision#getViewMask}), and a replay that reveals the entire map removes every ghost.
     *
     * @param view  the game view
     * @param frame the last completed frame; an earlier frame than before (a seek restart) clears the list
     * @throws NullPointerException if the world was built without game data
     */
    public void update(@NotNull ScrGameView view, long frame) {
        LocalVision vision = view.getLocalVision();
        int mask = vision.getViewMask();
        boolean reveal = vision.isReplayOrObserver() && vision.isRevealed();

        if (view != this.view || frame < this.frame) {
            this.entries.clear();
            this.byUnit.clear();

            this.view = view;
        }

        this.frame = frame;

        if (reveal) {
            this.entries.clear();
            this.byUnit.clear();

            return;
        }

        UnitPool pool = view.getUnitSystem().getUnitPool();

        if (pool == null) {
            return;
        }

        for (Ghost ghost : this.entries) {
            ghost.seen = false;
        }

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index)) {
                continue;
            }

            Unit unit = pool.getUnit(index);
            Sprite sprite = unit.getSpriteOrNull();

            if (sprite == null || !UnitQueries.isGroundedBuilding(unit) || (sprite.getVisibilityFlags() & mask) == 0) {
                continue;
            }

            Ghost current = this.byUnit.get(unit);

            if (current != null && current.type == unit.getUnitId() && current.positionX == sprite.getX() && current.positionY == sprite.getY()) {
                current.seen = true;

                continue;
            }

            // A changed type or position (a morph, a lift-off and landing, or a reused unit slot) starts a new record; the
            // previous one stays as an independent ghost until its tiles are seen.
            Ghost created = new Ghost(unit, unit.getUnitId(), unit.getOwnerIndex(), sprite.getX(), sprite.getY());

            created.seen = true;

            this.entries.add(created);
            this.byUnit.put(unit, created);
        }

        ScrUnitTypeView units = view.requireGameData().getUnitTypes();

        this.entries.removeIf(ghost -> {
            boolean remove = !ghost.seen && hasVisibleTile(view.getMap(), units, ghost, mask);

            if (remove && this.byUnit.get(ghost.unit) == ghost) {
                this.byUnit.remove(ghost.unit);
            }

            return remove;
        });
    }
}
