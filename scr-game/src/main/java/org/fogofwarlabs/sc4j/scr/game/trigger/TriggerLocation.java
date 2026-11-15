package org.fogofwarlabs.sc4j.scr.game.trigger;

/**
 * One map location of the CHK {@code MRGN} section: a pixel rectangle and its elevation flags.
 * <p>
 * Bounds are the raw DWORDs of the record and may be inverted or out of the map; callers apply their masks.
 */
public final class TriggerLocation {
    /**
     * The mask of the low WORD.
     */
    private static final int WORD_MASK = 0xFFFF;

    private int left;
    private int top;
    private int right;
    private int bottom;

    private int stringIndex;
    private int elevationFlags;

    /**
     * Creates a {@link TriggerLocation} instance with empty bounds, string and flags.
     */
    TriggerLocation() {
        this.left   = 0;
        this.top    = 0;
        this.right  = 0;
        this.bottom = 0;

        this.stringIndex    = 0;
        this.elevationFlags = 0;
    }

    /**
     * @return the left bound DWORD
     */
    public int getLeft() {
        return this.left;
    }

    void setLeft(int left) {
        this.left = left;
    }

    /**
     * @return the top bound DWORD
     */
    public int getTop() {
        return this.top;
    }

    void setTop(int top) {
        this.top = top;
    }

    /**
     * @return the right bound DWORD
     */
    public int getRight() {
        return this.right;
    }

    void setRight(int right) {
        this.right = right;
    }

    /**
     * @return the bottom bound DWORD
     */
    public int getBottom() {
        return this.bottom;
    }

    void setBottom(int bottom) {
        this.bottom = bottom;
    }

    /**
     * @return the horizontal center {@code (left + right) / 2}
     */
    public int getCenterX() {
        return (this.left + this.right) / 2;
    }

    /**
     * @return the vertical center {@code (top + bottom) / 2}
     */
    public int getCenterY() {
        return (this.top + this.bottom) / 2;
    }

    /**
     * @return the location-name string WORD of the record
     */
    public int getStringIndex() {
        return this.stringIndex;
    }

    void setStringIndex(int stringIndex) {
        this.stringIndex = stringIndex & WORD_MASK;
    }

    /**
     * @return the elevation flag WORD of the record
     */
    public int getElevationFlags() {
        return this.elevationFlags;
    }

    void setElevationFlags(int elevationFlags) {
        this.elevationFlags = elevationFlags;
    }


    void setBounds(int left, int top, int right, int bottom) {
        this.left   = left;
        this.top    = top;
        this.right  = right;
        this.bottom = bottom;
    }
}
