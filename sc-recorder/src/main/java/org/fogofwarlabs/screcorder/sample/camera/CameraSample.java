package org.fogofwarlabs.screcorder.sample.camera;

import org.fogofwarlabs.screcorder.sample.Sample;

/**
 * Stores immutable camera, cursor and input state with explicit EUD mouse availability.
 */
public final class CameraSample implements Sample {
    private final int screenX;
    private final int screenY;
    private final int maximumX;
    private final int maximumY;
    private final int previousX;
    private final int previousY;
    private final int centerViewX;
    private final int centerViewY;
    private final int centerViewActive;
    private final boolean eudMouseAvailable;
    private final int eudMouseX;
    private final int eudMouseY;
    private final int cursorX;
    private final int cursorY;
    private final int buttons;
    private final int tileX;
    private final int tileY;

    /**
     * Creates a {@link CameraSample} instance.
     *
     * @param screenX           the screen horizontal origin
     * @param screenY           the screen vertical origin
     * @param maximumX          the maximum horizontal origin
     * @param maximumY          the maximum vertical origin
     * @param previousX         the previous horizontal origin
     * @param previousY         the previous vertical origin
     * @param centerViewX       the Center View horizontal target
     * @param centerViewY       the Center View vertical target
     * @param centerViewActive  the Center View activity byte
     * @param eudMouseAvailable whether EUD mouse coordinates were available
     * @param eudMouseX         the EUD mouse horizontal coordinate
     * @param eudMouseY         the EUD mouse vertical coordinate
     * @param cursorX           the client cursor horizontal coordinate
     * @param cursorY           the client cursor vertical coordinate
     * @param buttons           the unsigned mouse button mask
     * @param tileX             the unsigned horizontal tile position
     * @param tileY             the unsigned vertical tile position
     */
    public CameraSample(int screenX, int screenY, int maximumX, int maximumY, int previousX, int previousY,
                        int centerViewX, int centerViewY, int centerViewActive,
                        boolean eudMouseAvailable, int eudMouseX, int eudMouseY,
                        int cursorX, int cursorY, int buttons, int tileX, int tileY) {
        this.screenX           = screenX;
        this.screenY           = screenY;
        this.maximumX          = maximumX;
        this.maximumY          = maximumY;
        this.previousX         = previousX;
        this.previousY         = previousY;
        this.centerViewX       = centerViewX;
        this.centerViewY       = centerViewY;
        this.centerViewActive  = centerViewActive;
        this.eudMouseAvailable = eudMouseAvailable;
        this.eudMouseX         = eudMouseX;
        this.eudMouseY         = eudMouseY;
        this.cursorX           = cursorX;
        this.cursorY           = cursorY;
        this.buttons           = buttons;
        this.tileX             = tileX;
        this.tileY             = tileY;
    }


    /**
     * @return the screen horizontal origin
     */
    public int getScreenX() {
        return this.screenX;
    }

    /**
     * @return the screen vertical origin
     */
    public int getScreenY() {
        return this.screenY;
    }

    /**
     * @return the maximum horizontal origin
     */
    public int getMaximumX() {
        return this.maximumX;
    }

    /**
     * @return the maximum vertical origin
     */
    public int getMaximumY() {
        return this.maximumY;
    }

    /**
     * @return the previous horizontal origin
     */
    public int getPreviousX() {
        return this.previousX;
    }

    /**
     * @return the previous vertical origin
     */
    public int getPreviousY() {
        return this.previousY;
    }

    /**
     * @return the Center View horizontal target
     */
    public int getCenterViewX() {
        return this.centerViewX;
    }

    /**
     * @return the Center View vertical target
     */
    public int getCenterViewY() {
        return this.centerViewY;
    }

    /**
     * @return the Center View activity byte
     */
    public int getCenterViewActive() {
        return this.centerViewActive;
    }

    /**
     * @return {@code true} if EUD mouse coordinates were available, {@code false} otherwise
     */
    public boolean isEudMouseAvailable() {
        return this.eudMouseAvailable;
    }

    /**
     * @return the EUD mouse horizontal coordinate
     */
    public int getEudMouseX() {
        return this.eudMouseX;
    }

    /**
     * @return the EUD mouse vertical coordinate
     */
    public int getEudMouseY() {
        return this.eudMouseY;
    }

    /**
     * @return the client cursor horizontal coordinate
     */
    public int getCursorX() {
        return this.cursorX;
    }

    /**
     * @return the client cursor vertical coordinate
     */
    public int getCursorY() {
        return this.cursorY;
    }

    /**
     * @return the unsigned mouse button mask
     */
    public int getButtons() {
        return this.buttons;
    }

    /**
     * @return the unsigned horizontal tile position
     */
    public int getTileX() {
        return this.tileX;
    }

    /**
     * @return the unsigned vertical tile position
     */
    public int getTileY() {
        return this.tileY;
    }
}
