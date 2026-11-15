package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the camera, replay, console and preview commands against a client with a game view, a selected placement and a console that records
 * what the commands do.
 */
final class ViewerCommandScenario implements CameraCommands.Target, ReplayCommands.Target, ConsoleCommands.Target, PreviewCommands.Target {
    private static final int FRAME_COUNT = 17;
    /**
     * The camera's own lowest zoom.
     */
    private static final int MINIMUM_ZOOM_PERCENT = 50;
    /**
     * The camera's own highest zoom.
     */
    private static final int MAXIMUM_ZOOM_PERCENT = 400;

    final @NotNull List<String> actions;
    boolean live;
    boolean hidden;
    boolean session;

    boolean wheelZoom;
    boolean dragPan;
    int zoomPercent;

    int consolePlayer;
    private boolean replayControls;

    boolean placement;
    private boolean preview;
    private int frame;
    private boolean mirrored;
    private int animation;

    /**
     * Creates a {@link ViewerCommandScenario} instance at the native zoom with a session, a selected placement and the replay controls shown.
     */
    ViewerCommandScenario() {
        this.actions = new ArrayList<>();
        this.live    = false;
        this.hidden  = false;
        this.session = true;

        this.wheelZoom   = false;
        this.dragPan     = false;
        this.zoomPercent = CameraCommands.NATIVE_ZOOM_PERCENT;

        this.consolePlayer  = 0;
        this.replayControls = true;

        this.placement = true;
        this.preview   = false;
        this.frame     = 0;
        this.mirrored  = false;
        this.animation = 0;
    }

    /**
     * @return {@code true} if a live game runs and refuses viewer commands, {@code false} otherwise
     */
    @Override
    public boolean isLiveGame() {
        return this.live;
    }

    /**
     * @return {@code true} if the mouse wheel zooms the game view, {@code false} otherwise
     */
    @Override
    public boolean isWheelZoom() {
        return this.wheelZoom;
    }

    /**
     * Sets whether mouse wheel input zooms the game view.
     *
     * @param enabled whether the mouse wheel zooms the game view
     */
    @Override
    public void setWheelZoom(boolean enabled) {
        this.wheelZoom = enabled;
    }

    /**
     * @return {@code true} if a middle or right drag pans the camera, {@code false} otherwise
     */
    @Override
    public boolean isDragPan() {
        return this.dragPan;
    }

    /**
     * Sets whether middle or right dragging pans the camera.
     *
     * @param enabled whether a middle or right drag pans the camera
     */
    @Override
    public void setDragPan(boolean enabled) {
        this.dragPan = enabled;
    }

    /**
     * @return {@code true} if a game view is shown for zooming, {@code false} otherwise
     */
    @Override
    public boolean hasGameView() {
        return this.session;
    }

    /**
     * @return the game view's zoom in percent of the native scale
     */
    @Override
    public int getZoomPercent() {
        return this.zoomPercent;
    }

    /**
     * Sets the game view's zoom about its center; the camera keeps the zoom within its limits.
     *
     * @param percent the zoom in percent of the native scale
     */
    @Override
    public void setZoomPercent(int percent) {
        this.zoomPercent = Math.clamp(percent, MINIMUM_ZOOM_PERCENT, MAXIMUM_ZOOM_PERCENT);
    }

    /**
     * @return the player whose recorded selection the console shows
     */
    @Override
    public int getConsolePlayer() {
        return this.consolePlayer;
    }

    /**
     * Sets the player whose recorded selection the console shows.
     *
     * @param player the player whose recorded selection the console shows, {@code 0..7}
     */
    @Override
    public void setConsolePlayer(int player) {
        this.consolePlayer = player;
    }

    /**
     * @return {@code true} if the command card shows replay controls, {@code false} otherwise
     */
    @Override
    public boolean isReplayControls() {
        return this.replayControls;
    }

    /**
     * Sets whether the command card shows replay controls.
     *
     * @param shown whether the command card shows the native replay controls
     */
    @Override
    public void setReplayControls(boolean shown) {
        this.replayControls = shown;
    }

    /**
     * @return {@code true} if a map placement is selected, {@code false} otherwise
     */
    @Override
    public boolean hasPlacement() {
        return this.placement;
    }

    /**
     * @return {@code true} if a preview is open, {@code false} otherwise
     */
    @Override
    public boolean hasPreview() {
        return this.preview;
    }

    /**
     * @return the GRP frame count, or {@code 0} while unknown
     */
    @Override
    public int getFrameCount() {
        return FRAME_COUNT;
    }

    /**
     * @return the animation the preview requests
     */
    @Override
    public int getRequestedAnimation() {
        return this.animation;
    }

    /**
     * Opens the file dialog that chooses a replay to load; a hidden run shows no dialog.
     *
     * @return {@code true} if the dialog was shown, {@code false} otherwise
     */
    @Override
    public boolean openReplay() {
        this.actions.add("open");

        return !this.hidden;
    }

    /**
     * Restarts the session from its first frame; nothing restarts without a session or when the restart fails.
     *
     * @return {@code true} if the session restarted, {@code false} otherwise
     */
    @Override
    public boolean restart() {
        this.actions.add("restart");

        return this.session;
    }

    /**
     * Zooms and centers the camera so that the whole map shows; the camera stays without a session.
     *
     * @return {@code true} if the camera moved, {@code false} otherwise
     */
    @Override
    public boolean fitMap() {
        this.actions.add("fit");

        return this.session;
    }

    /**
     * Returns the console to the console player's recorded selection, dropping the viewer's own local selection.
     *
     * @return {@code true} if a console is shown, {@code false} otherwise
     */
    @Override
    public boolean followRecordedSelection() {
        this.actions.add("follow");

        return true;
    }

    /**
     * Starts the image script preview of the selected placement and pauses the session; nothing starts without a selected placement.
     *
     * @return {@code true} if a preview started, {@code false} otherwise
     */
    @Override
    public boolean startPreview() {
        this.preview = this.placement;

        return this.placement;
    }

    /**
     * Closes the preview; the GRP frame shows again.
     */
    @Override
    public void closePreview() {
        this.preview = false;
    }

    /**
     * Steps the shown GRP frame of the selected placement and closes the preview.
     *
     * @param delta the frames to step, negative toward the first
     * @return the shown frame
     */
    @Override
    public int changeFrame(int delta) {
        this.preview = false;
        this.frame   = Math.clamp(this.frame + delta, 0, FRAME_COUNT - 1);

        return this.frame;
    }

    /**
     * Mirrors the shown GRP frame and closes the preview.
     *
     * @return {@code true} if the frame is now mirrored, {@code false} otherwise
     */
    @Override
    public boolean toggleMirror() {
        this.preview  = false;
        this.mirrored = !this.mirrored;

        return this.mirrored;
    }

    /**
     * Changes the requested animation within {@code 0..255}.
     *
     * @param delta the change
     */
    @Override
    public void chooseAnimation(int delta) {
        this.animation = Math.clamp(this.animation + delta, 0, PreviewCommands.MAXIMUM_ANIMATION);
    }

    /**
     * Requests the chosen animation in the open preview.
     */
    @Override
    public void playAnimation() {
        this.actions.add("play " + this.animation);
    }

    /**
     * Runs the open preview's image script once.
     */
    @Override
    public void stepPreview() {
        this.actions.add("step");
    }


    /**
     * Creates a {@link ConsoleCommandRegistry} instance with the camera, replay, console and preview commands of this client.
     *
     * @return the created {@link ConsoleCommandRegistry} instance
     */
    @NotNull ConsoleCommandRegistry createRegistry() {
        ConsoleCommandRegistry registry = DebugCommands.createRegistry();

        CameraCommands.register(registry, this);
        ReplayCommands.register(registry, this);
        ConsoleCommands.register(registry, this);
        PreviewCommands.register(registry, this);

        return registry;
    }
}
