package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.production.PlacementResult;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Retains building placement validity and per-tile preview colors.
 * The hovered footprint starts at the view tile plus the camera tile, advanced when its leading tile lies before the camera.
 * Add-on previews check the parent footprint at the add-on offset and retain its map pixel position.
 * A pointer outside the game view hides the preview without changing the last validity code.
 * Fogged tiles are judged from remembered state with the remembered resources of the view ({@link RememberedResources}).
 */
public final class PlacementPreview {
    private @Nullable PlacementResult result;
    private int type;
    private int parentType;
    private boolean visible;
    private boolean psiFields;
    private int parentPixelX;
    private int parentPixelY;

    /**
     * Creates a {@link PlacementPreview} instance.
     */
    public PlacementPreview() {
        this.result       = null;
        this.type         = -1;
        this.parentType   = -1;
        this.visible      = false;
        this.psiFields    = false;
        this.parentPixelX = 0;
        this.parentPixelY = 0;
    }

    /**
     * @return {@code true} if the ghost and grid are drawn, {@code false} otherwise
     */
    public boolean isVisible() {
        return this.visible && this.type >= 0 && this.result != null;
    }

    /**
     * @return {@code true} if placement shows the local psi fields, {@code false} otherwise
     */
    public boolean isShowingPsiFields() {
        return this.psiFields && this.type >= 0;
    }

    /**
     * @return the building type being placed, or {@code -1}
     */
    public int getType() {
        return this.type;
    }

    /**
     * @return the left footprint tile
     */
    public int getTileX() {
        return this.result == null ? 0 : this.result.getTileX();
    }

    /**
     * @return the top footprint tile
     */
    public int getTileY() {
        return this.result == null ? 0 : this.result.getTileY();
    }

    /**
     * @return the last validity code; {@code 0} when placement is permitted
     */
    public int getCode() {
        return this.result == null ? 0 : this.result.getCode();
    }

    /**
     * @param row    the footprint row
     * @param column the footprint column
     * @return the tile's code for the placed building
     */
    public int getTileCode(int row, int column) {
        return this.result == null ? 0 : this.result.getTileCode(column, row);
    }

    /**
     * @return the add-on parent type, or {@code -1} outside add-on mode
     */
    public int getParentType() {
        return this.parentType;
    }

    /**
     * @return the parent footprint's left map pixel (the add-on position minus the units.dat add-on position)
     */
    public int getParentPixelX() {
        return this.parentPixelX;
    }

    /**
     * @return the parent footprint's top map pixel
     */
    public int getParentPixelY() {
        return this.parentPixelY;
    }

    /**
     * Returns a parent footprint tile's code. The parent is evaluated whenever an add-on is placed, also while the add-on footprint is invalid.
     *
     * @param row    the footprint row
     * @param column the footprint column
     * @return the tile's code for the add-on parent, or {@code 0} outside add-on mode
     */
    public int getParentTileCode(int row, int column) {
        PlacementResult parent = this.result == null ? null : this.result.getParent();

        return parent == null ? 0 : parent.getTileCode(column, row);
    }


    /**
     * Updates the preview for a pointer position.
     *
     * @param view       the game view
     * @param builder    the active unit, or {@code null}
     * @param owner      the owner used for the checks
     * @param type       the building type being placed
     * @param viewX      the pointer x in game view pixels
     * @param viewY      the pointer y in game view pixels
     * @param cameraX    the camera map x in pixels
     * @param cameraY    the camera map y in pixels
     * @param viewWidth  the game view width in map pixels
     * @param viewHeight the game view height in map pixels
     * @throws NullPointerException if the world was built without game data
     */
    public void update(@NotNull ScrGameView view,
                       @Nullable Unit builder,
                       int owner,
                       int type,
                       int viewX, int viewY,
                       int cameraX, int cameraY,
                       int viewWidth, int viewHeight) {
        this.type = type;

        ScrUnitTypeView units = view.requireGameData().getUnitTypes();

        // The local player's psi fields are shown whenever the active unit is Protoss (== 2).
        this.psiFields = builder != null && (units.getGroupFlags(builder.getUnitId()) & UnitGroupFlags.PROTOSS) != 0;

        if (viewX < 0 || viewY < 0 || viewX >= viewWidth || viewY >= viewHeight) {
            this.visible = false;

            return;
        }

        int tileX = (viewX >> TerrainTileset.TILE_SHIFT) + (cameraX >> TerrainTileset.TILE_SHIFT);
        int tileY = (viewY >> TerrainTileset.TILE_SHIFT) + (cameraY >> TerrainTileset.TILE_SHIFT);

        if (tileX * TerrainTileset.TILE_SIZE - cameraX < 0) {
            tileX++;
        }

        if (tileY * TerrainTileset.TILE_SIZE - cameraY < 0) {
            tileY++;
        }

        this.visible    = true;
        this.parentType = -1;

        RememberedResources memory = new RememberedResources(view);

        if ((units.getFlags(type) & UnitTypeFlags.ADDON) != 0 && builder != null) {
            this.result       = view.getUnitPlacement().evaluateAddon(builder, owner, type, tileX, tileY, true, false, false, memory);
            this.parentType   = builder.getUnitId();
            this.parentPixelX = tileX * TerrainTileset.TILE_SIZE - (short) units.getAddonHorizontalOffset(type);
            this.parentPixelY = tileY * TerrainTileset.TILE_SIZE - (short) units.getAddonVerticalOffset(type);
        } else {
            this.result = view.getUnitPlacement().evaluateBuilding(builder, owner, type, tileX, tileY, true, false, false, memory);
        }
    }


    /**
     * Ends the preview.
     */
    public void clear() {
        this.psiFields  = false;
        this.visible    = false;
        this.parentType = -1;
        this.type       = -1;
        this.result     = null;
    }
}