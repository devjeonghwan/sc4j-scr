package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.engine.client.world.ViewJumps;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalView;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the alert position of the view jumps in the game's local view, where EUD writes may change it.
 */
public final class LocalViewAlertPosition implements ViewJumps.AlertPosition {
    private final @NotNull LocalView view;

    /**
     * Creates a {@link LocalViewAlertPosition} instance.
     *
     * @param view the game's local view, which holds the alert screen position
     */
    public LocalViewAlertPosition(@NotNull LocalView view) {
        this.view = view;
    }

    /**
     * @return the alert camera x in pixels
     */
    @Override
    public int getAlertX() {
        return this.view.getAlertScreenX();
    }

    /**
     * @return the alert camera y in pixels
     */
    @Override
    public int getAlertY() {
        return this.view.getAlertScreenY();
    }

    /**
     * Sets the alert position.
     *
     * @param x the alert camera x in pixels
     * @param y the alert camera y in pixels
     */
    @Override
    public void setAlert(int x, int y) {
        this.view.setAlertScreen(x, y);
    }
}
