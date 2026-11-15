package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.scr.game.presentation.ImageScriptPreview;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the inspected map placement frame, mirroring and manual image-script preview state.
 * The preview uses {@link ImageScriptPreview} with the requested animation.
 */
final class UnitInspection {
    /**
     * The highest animation identifier the preview can request.
     */
    private static final int MAXIMUM_ANIMATION = 255;

    private int frame;
    private int frameCount;
    private boolean flipped;

    private @Nullable ImageScriptPreview preview;
    private int requestedAnimation;

    /**
     * Creates a {@link UnitInspection} instance with no shown frame, no preview and no requested animation.
     */
    UnitInspection() {
        this.frame      = 0;
        this.frameCount = 0;
        this.flipped    = false;

        this.preview            = null;
        this.requestedAnimation = 0;
    }

    /**
     * @return the shown GRP frame
     */
    int getFrame() {
        return this.frame;
    }

    /**
     * @return the GRP frame count, or {@code 0} while unknown
     */
    int getFrameCount() {
        return this.frameCount;
    }

    /**
     * @return {@code true} if the shown frame is mirrored, {@code false} otherwise
     */
    boolean isFlipped() {
        return this.flipped;
    }

    /**
     * @return the image script preview, or {@code null} when no preview is open
     */
    @Nullable ImageScriptPreview getPreview() {
        return this.preview;
    }

    /**
     * @return the animation the preview requests
     */
    int getRequestedAnimation() {
        return this.requestedAnimation;
    }

    /**
     * Sets the shown GRP frame count.
     *
     * @param frameCount the GRP frame count the renderer reports
     */
    void setFrameCount(int frameCount) {
        this.frameCount = frameCount;
    }


    /**
     * Forgets the shown frame, the mirroring and the preview; the requested animation is kept.
     */
    void clear() {
        this.frame      = 0;
        this.frameCount = 0;
        this.flipped    = false;

        this.preview = null;
    }

    /**
     * Starts the inspection of a loaded session from the launch options.
     *
     * @param frame              the first GRP frame
     * @param frameCount         the GRP frame count, or {@code 0} until the renderer knows it
     * @param flipped            whether the frame is mirrored
     * @param requestedAnimation the animation the preview requests, {@code 0} for none
     */
    void load(int frame, int frameCount, boolean flipped, int requestedAnimation) {
        this.frame      = frame;
        this.frameCount = frameCount;
        this.flipped    = flipped;

        this.preview            = null;
        this.requestedAnimation = Math.max(0, requestedAnimation);
    }

    /**
     * Steps the shown GRP frame and closes the preview.
     *
     * @param session   the session, or {@code null}
     * @param direction {@code -1} or {@code 1}
     */
    void changeFrame(@Nullable ClientSession session, int direction) {
        if (session == null || session.getSelectedPlacement() == null || this.frameCount == 0) {
            return;
        }

        this.preview = null;
        this.frame   = Math.clamp(this.frame + direction, 0, this.frameCount - 1);
    }

    /**
     * Starts the image script preview of the selected placement.
     *
     * @param session the session, or {@code null}
     * @return {@code true} if a placement was selected and its preview started, {@code false} otherwise
     */
    boolean startPreview(@Nullable ClientSession session) {
        if (session == null || session.getSelectedPlacement() == null) {
            return false;
        }

        ImageScriptPreview started = session.getView().createImageScriptPreview(session.getSelectedPlacement());

        if (this.requestedAnimation != 0) {
            started.requestAnimation(this.requestedAnimation);
        }

        this.preview = started;

        return true;
    }

    /**
     * Mirrors the shown GRP frame and closes the preview.
     */
    void toggleMirror() {
        this.preview = null;
        this.flipped = !this.flipped;
    }

    /**
     * Changes the requested animation within {@code 0..255}.
     *
     * @param delta the change
     */
    void chooseAnimation(int delta) {
        this.requestedAnimation = Math.clamp(this.requestedAnimation + delta, 0, MAXIMUM_ANIMATION);
    }

    /**
     * Closes the preview; the GRP frame shows again.
     */
    void closePreview() {
        this.preview = null;
    }
}
