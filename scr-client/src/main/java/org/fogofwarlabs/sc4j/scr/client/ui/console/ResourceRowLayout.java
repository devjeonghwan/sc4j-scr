package org.fogofwarlabs.sc4j.scr.client.ui.console;

/**
 * Lays out a row of resource items, each an icon and a text, against the fixed right edge of the row.
 * <p>
 * Items are placed from the right edge leftwards into their slots. An item stays at its slot's left while its content
 * fits before its right limit, and otherwise moves left until it ends at that limit. The right limit is the slot's right
 * edge moved left by as much as the item on its right moved, so the slot spacing between the two items stays, and an
 * item moves only when its own free room cannot take the shift.
 * <p>
 * The text keeps its font size: no item overlaps another, no text is clipped, and the rightmost item keeps its slot's
 * right edge as the right edge of the row.
 */
final class ResourceRowLayout {
    private float shift;

    private float itemLeft;
    private float itemRight;

    /**
     * Creates a {@link ResourceRowLayout} instance for an empty row.
     */
    ResourceRowLayout() {
        this.shift = 0;

        this.itemLeft  = 0;
        this.itemRight = 0;
    }

    /**
     * @return the left of the last placed item
     */
    float getItemLeft() {
        return this.itemLeft;
    }

    /**
     * @return the right limit of the last placed item, at least its left plus its content width
     */
    float getItemRight() {
        return this.itemRight;
    }


    /**
     * Starts a new row whose first item is the rightmost one.
     */
    void reset() {
        this.shift = 0;

        this.itemLeft  = 0;
        this.itemRight = 0;
    }

    /**
     * Places the next item to the left of the previously placed one.
     *
     * @param slotLeft     the left of the item's slot
     * @param slotRight    the right of the item's slot
     * @param contentWidth the width of the item's icon, gap and text at the font size of the text
     * @return the left of the item
     */
    float placeItem(float slotLeft, float slotRight, float contentWidth) {
        float right = slotRight - this.shift;
        float left = Math.min(slotLeft, right - contentWidth);

        this.shift = slotLeft - left;

        this.itemLeft  = left;
        this.itemRight = right;

        return left;
    }
}
