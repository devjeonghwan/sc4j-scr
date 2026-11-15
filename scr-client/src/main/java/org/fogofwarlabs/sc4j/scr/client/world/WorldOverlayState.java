package org.fogofwarlabs.sc4j.scr.client.world;

import org.fogofwarlabs.sc4j.scr.client.ui.input.PlacementPreview;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the read-only interface-model state that the world renderers draw: viewing player, selection, hover, drag box and click markers.
 * <p>
 * The in-game interface model owns this state; the scene extraction only reads it. Implementations must not mutate game state. The selection circles
 * and health bars are images of the game's sprites, and the vision the view shows is the game's {@link LocalVision}, not overlay state.
 */
public interface WorldOverlayState {
    /**
     * @return the viewing player, whose color draws the ground click markers and the placement preview
     */
    int getViewingPlayer();

    /**
     * @return the number of selected units, at most {@code 12}
     */
    int getSelectionCount();

    /**
     * @param slot the selection slot below {@link #getSelectionCount()}
     * @return the selected unit
     */
    @NotNull Unit getSelectedUnit(int slot);

    /**
     * @return the unit under the cursor, or {@code null}
     */
    @Nullable Unit getHoveredUnit();

    /**
     * @return the target unit of the newest unit-targeting click, or {@code null}
     */
    @Nullable Unit getFlashedUnit();

    /**
     * @return {@code true} if a drag selection box is open, {@code false} otherwise
     */
    boolean isDragSelecting();

    /**
     * @return the drag box left edge in map pixels
     */
    int getDragLeft();

    /**
     * @return the drag box top edge in map pixels
     */
    int getDragTop();

    /**
     * @return the drag box right edge in map pixels
     */
    int getDragRight();

    /**
     * @return the drag box bottom edge in map pixels
     */
    int getDragBottom();

    /**
     * @return the number of live click markers, oldest first
     */
    int getMarkerCount();

    /**
     * @param index the marker index
     * @return the marker map x coordinate
     */
    int getMarkerX(int index);

    /**
     * @param index the marker index
     * @return the marker map y coordinate
     */
    int getMarkerY(int index);

    /**
     * @param index the marker index
     * @return the flashed target unit, or {@code null} for a ground click or a flashed remembered building
     */
    @Nullable Unit getMarkerTarget(int index);

    /**
     * @param index the marker index
     * @return the fog sprite of a flashed remembered building, or {@code null}
     */
    @Nullable Sprite getMarkerRememberedSprite(int index);

    /**
     * @param index the marker index
     * @return {@code 0} ground, {@code 1} own, {@code 2} allied or neutral, {@code 3} enemy
     */
    int getMarkerRelation(int index);

    /**
     * @param index the marker index
     * @return the {@code orders.dat} identifier of a targeted order, or {@code -1} for a right click
     */
    int getMarkerOrder(int index);

    /**
     * @param index the marker index
     * @return the game frame of the click
     */
    int getMarkerFrame(int index);

    /**
     * @return the building placement preview, or {@code null} when the overlay has none
     */
    default @Nullable PlacementPreview getPlacementPreview() {
        return null;
    }
}
