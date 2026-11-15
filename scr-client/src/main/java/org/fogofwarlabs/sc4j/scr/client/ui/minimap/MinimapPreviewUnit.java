package org.fogofwarlabs.sc4j.scr.client.ui.minimap;

/**
 * Retains one map-preview unit position, type and owning player slot.
 */
public final class MinimapPreviewUnit {
    private final int x;
    private final int y;

    private final int type;

    private final int owner;

    /**
     * Creates a {@link MinimapPreviewUnit} instance.
     *
     * @param x     the map x in pixels
     * @param y     the map y in pixels
     * @param type  the unit type
     * @param owner the owning player slot
     */
    public MinimapPreviewUnit(int x, int y, int type, int owner) {
        this.x = x;
        this.y = y;

        this.type = type;

        this.owner = owner;
    }

    /**
     * @return the map x in pixels
     */
    public int getX() {
        return this.x;
    }

    /**
     * @return the map y in pixels
     */
    public int getY() {
        return this.y;
    }

    /**
     * @return the unit type
     */
    public int getType() {
        return this.type;
    }

    /**
     * @return the owning player slot
     */
    public int getOwner() {
        return this.owner;
    }
}
