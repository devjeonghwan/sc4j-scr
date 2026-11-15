package org.fogofwarlabs.sc4j.scr.client.world;

import org.fogofwarlabs.sc4j.engine.client.world.CameraControl;
import org.jetbrains.annotations.NotNull;

/**
 * Moves a {@link ClientCamera} for the engine camera scrolls and jumps: the game screen's camera as a {@link CameraControl}. The position is
 * the map position of the game screen's top-left corner in whole map pixels (the camera rounded), between {@code 0} and the map size less the
 * game screen columns and the view rows in whole map pixels. The view rows are the play view rows for the edge and key scroll and the jumps,
 * the whole window's rows for a center view scroll. A move pans the camera so that the game screen's top-left corner shows the position; the
 * camera then keeps itself inside its own limits.
 */
public final class GameScreenCamera implements CameraControl {
    /**
     * Creates a {@link GameScreenCamera} instance whose range ends at the play view rows: the camera of the edge and key scroll and the jumps.
     *
     * @param camera    the camera
     * @param viewport  the client's screen geometry
     * @param mapWidth  the map width in pixels
     * @param mapHeight the map height in pixels
     * @return the created {@link GameScreenCamera} instance
     */
    public static @NotNull GameScreenCamera ofPlayView(@NotNull ClientCamera camera, @NotNull GameViewport viewport, int mapWidth, int mapHeight) {
        return new GameScreenCamera(camera, viewport, mapWidth, mapHeight, false);
    }

    /**
     * Creates a {@link GameScreenCamera} instance whose range ends at the whole window's rows: the camera of a center view scroll.
     *
     * @param camera    the camera
     * @param viewport  the client's screen geometry
     * @param mapWidth  the map width in pixels
     * @param mapHeight the map height in pixels
     * @return the created {@link GameScreenCamera} instance
     */
    public static @NotNull GameScreenCamera ofWholeView(@NotNull ClientCamera camera, @NotNull GameViewport viewport, int mapWidth, int mapHeight) {
        return new GameScreenCamera(camera, viewport, mapWidth, mapHeight, true);
    }

    private final @NotNull ClientCamera camera;
    private final @NotNull GameViewport viewport;
    private final int mapWidth;
    private final int mapHeight;
    private final boolean wholeWindowRows;

    private GameScreenCamera(@NotNull ClientCamera camera, @NotNull GameViewport viewport, int mapWidth, int mapHeight, boolean wholeWindowRows) {
        this.camera          = camera;
        this.viewport        = viewport;
        this.mapWidth        = mapWidth;
        this.mapHeight       = mapHeight;
        this.wholeWindowRows = wholeWindowRows;
    }

    /**
     * @return the projected x shown at the left edge
     */
    @Override
    public float getX() {
        return Math.round(this.getLeft());
    }

    /**
     * @return the projected y shown at the top edge
     */
    @Override
    public float getY() {
        return Math.round(this.camera.getY());
    }

    /**
     * @return the smallest x of the position
     */
    @Override
    public float getMinimumX() {
        return 0;
    }

    /**
     * @return the largest x of the position; below {@link #getMinimumX} the camera has no room to move on the axis
     */
    @Override
    public float getMaximumX() {
        return this.mapWidth - this.viewport.getViewTileWidth(this.camera.getZoom());
    }

    /**
     * @return the smallest y of the position
     */
    @Override
    public float getMinimumY() {
        return 0;
    }

    /**
     * @return the largest y of the position; below {@link #getMinimumY} the camera has no room to move on the axis
     */
    @Override
    public float getMaximumY() {
        float zoom = this.camera.getZoom();

        return this.mapHeight - (this.wholeWindowRows ? this.viewport.getViewTileHeight(zoom) : this.viewport.getPlayMapRows(zoom));
    }

    /**
     * @return the width of the shown area in projected pixels
     */
    @Override
    public float getViewWidth() {
        return this.viewport.getViewTileWidth(this.camera.getZoom());
    }

    /**
     * @return the height of the shown area in projected pixels
     */
    @Override
    public float getViewHeight() {
        return this.viewport.getPlayMapRows(this.camera.getZoom());
    }

    /**
     * @return the screen pixels per projected pixel
     */
    @Override
    public float getZoom() {
        return this.camera.getZoom();
    }

    /**
     * @return the map x at the game screen's left edge
     */
    private float getLeft() {
        return this.camera.screenToWorldX(this.viewport.getGameLeft());
    }


    /**
     * Moves the camera to a position of the top-left corner; the camera keeps itself inside the map as it does for every move.
     *
     * @param x the projected x shown at the left edge
     * @param y the projected y shown at the top edge
     */
    @Override
    public void moveTo(float x, float y) {
        float zoom = this.camera.getZoom();

        this.camera.pan((x - this.getLeft()) * zoom, (y - this.camera.getY()) * zoom);
    }
}
