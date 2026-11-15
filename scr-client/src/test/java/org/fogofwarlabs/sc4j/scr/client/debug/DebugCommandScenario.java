package org.fogofwarlabs.sc4j.scr.client.debug;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A single-player replay session at a fixed frame that the debug and frame commands run against; it records the frame tools the commands start.
 */
final class DebugCommandScenario implements DebugCommands.Target {
    /**
     * The frames of the replay and the completed frame the session starts at.
     */
    private static final long REPLAY_FRAMES = 20_000;
    private static final long START_FRAME = 3000;

    private final @NotNull DebugOverlay overlay;
    private final @NotNull DebugPanel panel;
    final @NotNull List<String> actions;

    @NotNull LightingView lightingView;
    private boolean debugView;
    boolean network;
    boolean replay;
    private long frame;

    /**
     * Creates a {@link DebugCommandScenario} instance of a replay with the debug view hidden and the lighting view off.
     */
    DebugCommandScenario() {
        this.overlay = new DebugOverlay();
        this.panel   = new DebugPanel();
        this.actions = new ArrayList<>();

        this.lightingView = LightingView.OFF;
        this.debugView    = false;
        this.network      = false;
        this.replay       = true;
        this.frame        = START_FRAME;
    }

    /**
     * @return {@code true} if the debug view is shown, {@code false} otherwise
     */
    @Override
    public boolean isDebugView() {
        return this.debugView;
    }

    /**
     * Sets whether the development panel and debug overlays are shown.
     *
     * @param shown whether the debug view (development panel and overlays) is shown
     */
    @Override
    public void setDebugView(boolean shown) {
        this.debugView = shown;
    }

    /**
     * @return the overlay whose layers the commands switch
     */
    @Override
    public @NotNull DebugOverlay getOverlay() {
        return this.overlay;
    }

    /**
     * Sets the player whose vision the vision layer shows.
     *
     * @param player the player whose vision the vision layer shows
     */
    @Override
    public void setVisionPlayer(int player) {
        this.actions.add("vision " + player);
    }

    /**
     * @return the selected HD lighting debug view
     */
    @Override
    public @NotNull LightingView getLightingView() {
        return this.lightingView;
    }

    /**
     * Sets the selected HD lighting debug view.
     *
     * @param view the view
     * @return the message describing the effect
     */
    @Override
    public @NotNull String setLightingView(@NotNull LightingView view) {
        this.lightingView = view;

        return "Lighting view: " + view.getName() + ".";
    }

    /**
     * @return {@code true} if the session is a network game, whose lockstep simulation the frame tools must not move, {@code false} otherwise
     */
    @Override
    public boolean isNetworkGame() {
        return this.network;
    }

    /**
     * @return {@code true} if a replay plays, the only session {@code /seek} can move, {@code false} otherwise
     */
    @Override
    public boolean isReplay() {
        return this.replay;
    }

    /**
     * @return the last completed game frame
     */
    @Override
    public long getFrame() {
        return this.frame;
    }

    /**
     * Shows the next page of the debug panel.
     *
     * @return the page now shown
     */
    @Override
    public @NotNull DebugPanel.Page showNextDebugPage() {
        this.panel.cyclePage();

        return this.panel.getPage();
    }

    /**
     * Pauses and runs one game frame.
     */
    @Override
    public void step() {
        this.actions.add("step");
        this.frame++;
    }

    /**
     * Requests a seek of the replay.
     *
     * @param frame the game frame to seek to
     * @return the requested frame after clamping it to the replay
     */
    @Override
    public long seek(long frame) {
        long target = Math.clamp(frame, 0, REPLAY_FRAMES);

        this.actions.add("seek " + target);

        return target;
    }
}
