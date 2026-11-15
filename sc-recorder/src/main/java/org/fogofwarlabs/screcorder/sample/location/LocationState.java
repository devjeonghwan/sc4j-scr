package org.fogofwarlabs.screcorder.sample.location;

/**
 * Contains one location rectangle, string identifier and elevation flags.
 */
public final class LocationState {
    private final int identifier;

    private final long left;
    private final long top;
    private final long right;
    private final long bottom;

    private final int stringIdentifier;
    private final int flags;

    /**
     * Creates a {@link LocationState} instance.
     *
     * @param identifier       the one-based location identifier
     * @param left             the unsigned left edge
     * @param top              the unsigned top edge
     * @param right            the unsigned right edge
     * @param bottom           the unsigned bottom edge
     * @param stringIdentifier the unsigned string identifier
     * @param flags            the unsigned location flags
     */
    public LocationState(int identifier, long left, long top, long right, long bottom, int stringIdentifier, int flags) {
        this.identifier = identifier;

        this.left   = left;
        this.top    = top;
        this.right  = right;
        this.bottom = bottom;

        this.stringIdentifier = stringIdentifier;
        this.flags            = flags;
    }


    /**
     * @return the one-based location identifier
     */
    public int getIdentifier() {
        return this.identifier;
    }

    /**
     * @return the unsigned left edge
     */
    public long getLeft() {
        return this.left;
    }

    /**
     * @return the unsigned top edge
     */
    public long getTop() {
        return this.top;
    }

    /**
     * @return the unsigned right edge
     */
    public long getRight() {
        return this.right;
    }

    /**
     * @return the unsigned bottom edge
     */
    public long getBottom() {
        return this.bottom;
    }

    /**
     * @return the unsigned string identifier
     */
    public int getStringIdentifier() {
        return this.stringIdentifier;
    }

    /**
     * @return the unsigned location flags
     */
    public int getFlags() {
        return this.flags;
    }
}
