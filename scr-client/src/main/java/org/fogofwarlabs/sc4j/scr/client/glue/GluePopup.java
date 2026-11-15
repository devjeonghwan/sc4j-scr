package org.fogofwarlabs.sc4j.scr.client.glue;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Runs a dialog modally over the current screen and draws its panel image beneath the controls.
 * A button ends the popup through {@link #finish}; the owner closes it after the input event.
 */
public abstract class GluePopup extends GlueScreen {
    /**
     * The console race of a save or load popup shown over a glue screen rather than over a game.
     */
    public static final int NO_CONSOLE_RACE = -1;

    private final @Nullable String panelPath;

    private boolean closed;

    /**
     * Creates a {@link GluePopup} instance.
     *
     * @param dialogName    the dialog file name, for example {@code gluPOk}
     * @param paletteFolder the palette folder of the popup's colors
     * @param panelPath     the panel image drawn under the controls, or {@code null} for none
     */
    protected GluePopup(@NotNull String dialogName, @NotNull String paletteFolder, @Nullable String panelPath) {
        super(dialogName, paletteFolder, null);

        this.panelPath = panelPath;

        this.closed = false;
    }

    /**
     * @return {@code true} if this popup asked to close, {@code false} otherwise
     */
    @Override
    protected final boolean isClosed() {
        return this.closed;
    }


    /**
     * Draws the panel image and then the controls in child order.
     *
     * @param canvas the glue canvas
     * @throws IOException if an image cannot be read
     */
    @Override
    protected void drawControls(@NotNull GlueCanvas canvas) throws IOException {
        String panel = this.panelPath;

        if (canvas.hasImage(panel)) {
            canvas.drawImage(canvas.getImage(panel), this.getDialogLeft(), this.getDialogTop());
        }

        super.drawControls(canvas);
    }

    /**
     * Ends the popup: its owner closes it after the input that ended it.
     */
    protected final void finish() {
        this.closed = true;
    }
}
