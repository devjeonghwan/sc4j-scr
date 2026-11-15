package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.scr.client.debug.CameraCommands;
import org.fogofwarlabs.sc4j.scr.client.debug.ConsoleCommands;
import org.fogofwarlabs.sc4j.scr.client.debug.PreviewCommands;
import org.fogofwarlabs.sc4j.scr.client.debug.ReplayCommands;
import org.fogofwarlabs.sc4j.scr.client.world.ClientCamera;
import org.fogofwarlabs.sc4j.scr.client.world.GameViewport;
import org.fogofwarlabs.sc4j.scr.game.presentation.ImageScriptPreview;
import org.jetbrains.annotations.NotNull;

/**
 * Connects the client's camera, replay, console and preview commands ({@code /zoom}, {@code /pan}, {@code /replay}, {@code /console},
 * {@code /preview}) to the running client.
 */
final class ClientCommandTarget implements CameraCommands.Target, ReplayCommands.Target, ConsoleCommands.Target, PreviewCommands.Target {
    /**
     * The percent of a zoom factor.
     */
    private static final float PERCENT = 100f;
    /**
     * The factor that takes the middle of a length.
     */
    private static final float HALF = 0.5f;

    private final @NotNull Main main;

    /**
     * Creates a {@link ClientCommandTarget} instance.
     *
     * @param main the client
     */
    ClientCommandTarget(@NotNull Main main) {
        this.main = main;
    }

    /**
     * @return {@code true} if a live game runs, where the viewer commands are refused, {@code false} otherwise
     */
    @Override
    public boolean isLiveGame() {
        return this.main.session != null && this.main.requireSession().isLive();
    }

    /**
     * @return {@code true} if the mouse wheel zooms the game view, {@code false} otherwise
     */
    @Override
    public boolean isWheelZoom() {
        return this.main.gameClientState.isWheelZoom();
    }

    /**
     * Sets whether mouse wheel input zooms the game view.
     *
     * @param enabled whether the mouse wheel zooms the game view
     */
    @Override
    public void setWheelZoom(boolean enabled) {
        this.main.gameClientState.setWheelZoom(enabled);
    }

    /**
     * @return {@code true} if a middle or right drag pans the camera, {@code false} otherwise
     */
    @Override
    public boolean isDragPan() {
        return this.main.gameClientState.isDragPan();
    }

    /**
     * Sets whether middle or right dragging pans the camera.
     *
     * @param enabled whether a middle or right drag pans the camera
     */
    @Override
    public void setDragPan(boolean enabled) {
        this.main.gameClientState.setDragPan(enabled);
    }

    /**
     * @return {@code true} if a game view is shown for zooming, {@code false} otherwise
     */
    @Override
    public boolean hasGameView() {
        return this.main.session != null;
    }

    /**
     * @return the game view's zoom in percent of the native scale; during an animated zoom, the zoom it approaches
     */
    @Override
    public int getZoomPercent() {
        ClientCamera camera = this.main.requireSession().getCamera();

        return Math.round(camera.getTargetZoom() / camera.getNativeZoom() * PERCENT);
    }

    /**
     * Sets the game view's zoom about its center; the camera keeps the zoom within its limits.
     * <p>
     * The zoom animates like the wheel zoom ({@link ClientCamera#zoomToward}).
     *
     * @param percent the zoom in percent of the native scale
     */
    @Override
    public void setZoomPercent(int percent) {
        ClientCamera camera = this.main.requireSession().getCamera();
        GameViewport viewport = this.main.gameViewport;
        float zoom = camera.getNativeZoom() * percent / PERCENT;

        camera.zoomToward(zoom / camera.getTargetZoom(), (viewport.getGameLeft() + viewport.getGameRight()) * HALF, this.main.viewHeight * HALF);
    }

    /**
     * @return the player whose recorded selection the console shows
     */
    @Override
    public int getConsolePlayer() {
        return this.main.consolePlayer;
    }

    /**
     * Sets the player whose recorded selection the console shows.
     *
     * @param player the player whose recorded selection the console shows, {@code 0..7}
     */
    @Override
    public void setConsolePlayer(int player) {
        this.main.consolePlayer = player;
    }

    /**
     * @return {@code true} if the command card shows replay controls, {@code false} otherwise
     */
    @Override
    public boolean isReplayControls() {
        return this.main.consoleReplayControls;
    }

    /**
     * Sets whether the command card shows replay controls.
     *
     * @param shown whether the command card shows the native replay controls
     */
    @Override
    public void setReplayControls(boolean shown) {
        this.main.consoleReplayControls = shown;
    }

    /**
     * @return {@code true} if a map placement is selected, {@code false} otherwise
     */
    @Override
    public boolean hasPlacement() {
        return this.main.session != null && this.main.requireSession().getSelectedPlacement() != null;
    }

    /**
     * @return {@code true} if a preview is open, {@code false} otherwise
     */
    @Override
    public boolean hasPreview() {
        return this.main.gameClientState.getInspection().getPreview() != null;
    }

    /**
     * @return the GRP frame count, or {@code 0} while unknown
     */
    @Override
    public int getFrameCount() {
        return this.main.gameClientState.getInspection().getFrameCount();
    }

    /**
     * @return the animation the preview requests
     */
    @Override
    public int getRequestedAnimation() {
        return this.main.gameClientState.getInspection().getRequestedAnimation();
    }


    /**
     * Opens the file dialog that chooses a replay to load; a hidden run shows no dialog.
     *
     * @return {@code true} if the dialog was shown, {@code false} otherwise
     */
    @Override
    public boolean openReplay() {
        return this.main.chooseReplay();
    }

    /**
     * Restarts the session from its first frame; nothing restarts without a session or when the restart fails.
     *
     * @return {@code true} if the session restarted, {@code false} otherwise
     */
    @Override
    public boolean restart() {
        return this.main.resetSession();
    }

    /**
     * Zooms and centers the camera so that the whole map shows; the camera stays without a session.
     *
     * @return {@code true} if the camera moved, {@code false} otherwise
     */
    @Override
    public boolean fitMap() {
        if (this.main.session == null) {
            return false;
        }

        this.main.fitMap();

        return true;
    }

    /**
     * Returns the console to the console player's recorded selection, dropping the viewer's own local selection.
     *
     * @return {@code true} if a console is shown, {@code false} otherwise
     */
    @Override
    public boolean followRecordedSelection() {
        if (this.main.consoleModel == null) {
            return false;
        }

        this.main.consoleModel.followSynchronizedSelection();

        return true;
    }

    /**
     * Starts the image script preview of the selected placement and pauses the session; nothing starts without a selected placement.
     *
     * @return {@code true} if a preview started, {@code false} otherwise
     */
    @Override
    public boolean startPreview() {
        if (!this.main.gameClientState.getInspection().startPreview(this.main.session)) {
            return false;
        }

        this.main.playing = false;

        return true;
    }

    /**
     * Closes the preview; the GRP frame shows again.
     */
    @Override
    public void closePreview() {
        this.main.gameClientState.getInspection().closePreview();
    }

    /**
     * Steps the shown GRP frame of the selected placement and closes the preview.
     *
     * @param delta the frames to step, negative toward the first
     * @return the shown frame
     */
    @Override
    public int changeFrame(int delta) {
        UnitInspection inspection = this.main.gameClientState.getInspection();

        inspection.changeFrame(this.main.session, delta);

        return inspection.getFrame();
    }

    /**
     * Mirrors the shown GRP frame and closes the preview.
     *
     * @return {@code true} if the frame is now mirrored, {@code false} otherwise
     */
    @Override
    public boolean toggleMirror() {
        UnitInspection inspection = this.main.gameClientState.getInspection();

        inspection.toggleMirror();

        return inspection.isFlipped();
    }

    /**
     * Changes the requested animation within {@code 0..255}.
     *
     * @param delta the change
     */
    @Override
    public void chooseAnimation(int delta) {
        this.main.gameClientState.getInspection().chooseAnimation(delta);
    }

    /**
     * Requests the chosen animation in the open preview.
     */
    @Override
    public void playAnimation() {
        UnitInspection inspection = this.main.gameClientState.getInspection();
        ImageScriptPreview preview = inspection.getPreview();

        if (preview != null) {
            preview.requestAnimation(inspection.getRequestedAnimation());
        }
    }

    /**
     * Runs the open preview's image script once.
     */
    @Override
    public void stepPreview() {
        ImageScriptPreview preview = this.main.gameClientState.getInspection().getPreview();

        if (preview != null) {
            preview.step();
        }
    }
}
