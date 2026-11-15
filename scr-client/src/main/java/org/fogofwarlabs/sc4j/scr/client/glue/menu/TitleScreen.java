package org.fogofwarlabs.sc4j.scr.client.glue.menu;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueCanvas;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueContext;
import org.fogofwarlabs.sc4j.scr.client.glue.GluePalette;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueScreen;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.DialogPainter;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Shows the title background and music while loading, with a blinking loading string.
 * Completion requires the minimum display interval and finished loading.
 */
public final class TitleScreen extends GlueScreen {
    private static final @NotNull String DIALOG = "titledlg";
    private static final @NotNull String PALETTE_FOLDER = "glue/title";
    private static final @NotNull String BACKGROUND = "glue/title/title.DDS";
    private static final int LOADING_TEXT = 1;
    private static final int COPYRIGHT_TEXT = 12;
    private static final long BLINK_MILLISECONDS = 500;
    private static final long MINIMUM_MILLISECONDS = 2000;
    private static final @NotNull String CURRENT_YEAR_KEY = "{CurrentYear}";
    private static final @NotNull String CURRENT_YEAR = "2020";
    private static final @NotNull String TITLE_MUSIC = "title";

    private boolean loadingTextShown;
    private boolean loadingFinished;

    private boolean loadingVisible;
    private long blinkTicks;
    private boolean finished;

    /**
     * Creates a {@link TitleScreen} instance.
     */
    public TitleScreen() {
        super(DIALOG, PALETTE_FOLDER, BACKGROUND);

        this.loadingTextShown = true;
        this.loadingFinished  = false;

        this.loadingVisible = false;
        this.blinkTicks     = 0;
        this.finished       = false;
    }

    /**
     * @return {@code true} if the minimum display interval has passed and loading has finished, {@code false} otherwise
     */
    public boolean isFinished() {
        return this.finished;
    }

    /**
     * Sets whether the blinking loading string is shown (hidden while the client's loading panel shows the loading instead).
     *
     * @param shown whether the string blinks
     */
    public void setLoadingTextShown(boolean shown) {
        this.loadingTextShown = shown;
    }

    /**
     * Sets whether the loading behind the screen has finished.
     *
     * @param loadingFinished whether loading has finished
     */
    public void setLoadingFinished(boolean loadingFinished) {
        this.loadingFinished = loadingFinished;
    }


    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     *
     * @param glueContext the shared services
     * @throws IOException if an asset cannot be read
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) throws IOException {
        DialogControl copyright = ScrDialogControlUtils.findControl(this.getDialog(), COPYRIGHT_TEXT);

        if (copyright != null) {
            this.bindControl(COPYRIGHT_TEXT).setReplacedText(this.getControlString(copyright).replace(CURRENT_YEAR_KEY, CURRENT_YEAR));
        }

        glueContext.getAudio().playMusic(TITLE_MUSIC);
    }

    /**
     * Called by {@link #update} for screen-specific timers.
     *
     * @param time the time in milliseconds
     */
    @Override
    protected void onUpdate(long time) {
        while ((this.blinkTicks + 1) * BLINK_MILLISECONDS <= this.getElapsed()) {
            this.blinkTicks++;
            this.loadingVisible = !this.loadingVisible;

            if (this.blinkTicks * BLINK_MILLISECONDS >= MINIMUM_MILLISECONDS && this.loadingFinished) {
                this.finished = true;
            }
        }
    }

    /**
     * Draws the controls in child order.
     * <p>
     * The blinking loading string is drawn after the controls while it is visible and shown.
     *
     * @param canvas the glue canvas
     * @throws IOException if an image cannot be read
     */
    @Override
    protected void drawControls(@NotNull GlueCanvas canvas) throws IOException {
        super.drawControls(canvas);

        DialogControl loading = ScrDialogControlUtils.findControl(this.getDialog(), LOADING_TEXT);

        if (loading == null || !this.loadingVisible || !this.loadingTextShown) {
            return;
        }

        GluePalette palette = this.getPalette();

        canvas.drawText(GlueCanvas.BIG_FONT,
                        this.getControlString(loading),
                        this.getDialogLeft() + loading.getX(), this.getDialogTop() + loading.getY(),
                        loading.getWidth(), loading.getHeight(),
                        GlueCanvas.FORMAT_CENTER | GlueCanvas.FORMAT_MIDDLE,
                        palette.getCodeColor(DialogPainter.CODE_TEXT),
                        palette);
    }

    /**
     * Called when a control is activated by a click, its hotkey, Esc or Enter.
     *
     * @param control the control id
     */
    @Override
    protected void onActivate(int control) {
    }
}
