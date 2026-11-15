package org.fogofwarlabs.sc4j.scr.game.player;

/**
 * Represents a player's map start location.
 */
public final class StartLocation {
    private final int owner;

    private final int x;
    private final int y;

    /**
     * Creates a {@link StartLocation} instance.
     *
     * @param owner the player slot
     * @param x     the x coordinate in pixels
     * @param y     the y coordinate in pixels
     */
    public StartLocation(int owner, int x, int y) {
        this.owner = owner;

        this.x = x;
        this.y = y;
    }

    /**
     * @return the player slot
     */
    public int getOwner() {
        return this.owner;
    }

    /**
     * @return the x coordinate in pixels
     */
    public int getX() {
        return this.x;
    }

    /**
     * @return the y coordinate in pixels
     */
    public int getY() {
        return this.y;
    }
}
