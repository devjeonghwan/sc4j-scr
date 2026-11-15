package org.fogofwarlabs.sc4j.scr.client.cursor;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.resource.graphics.Picture;
import org.fogofwarlabs.sc4j.engine.resource.graphics.PixelLayer;
import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.jetbrains.annotations.NotNull;

/**
 * One cursor picture in screen pixels for the platform pointer ({@link org.fogofwarlabs.sc4j.engine.client.cursor.CursorSurface}): straight-alpha
 * RGBA rows and the hot spot, the pixel that sits at the pointer.
 */
public final class CursorImage {
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int BYTE_MASK = 0xFF;

    private final int width;
    private final int height;

    private final int hotSpotX;
    private final int hotSpotY;

    private final byte @NotNull [] rgba;

    /**
     * Creates a {@link CursorImage} instance.
     *
     * @param width    the width in screen pixels
     * @param height   the height in screen pixels
     * @param hotSpotX the hot spot column
     * @param hotSpotY the hot spot row
     * @param rgba     the row-major RGBA bytes, {@code width * height * OverlayPass.RGBA_BYTES}
     * @throws IllegalArgumentException if the dimensions are nonpositive or the pixel array does not match them
     */
    public CursorImage(int width, int height, int hotSpotX, int hotSpotY, byte @NotNull [] rgba) {
        if (width <= 0 || height <= 0 || rgba.length != width * height * OverlayPass.RGBA_BYTES) {
            throw Exceptions.newIllegalArgumentException("Cursor image size {0}x{1} does not match {2} bytes.", width, height, rgba.length);
        }

        this.width  = width;
        this.height = height;

        this.hotSpotX = Math.clamp(hotSpotX, 0, width - 1);
        this.hotSpotY = Math.clamp(hotSpotY, 0, height - 1);

        this.rgba = rgba;
    }

    /**
     * @return the width in screen pixels
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * @return the height in screen pixels
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * @return the hot spot column
     */
    public int getHotSpotX() {
        return this.hotSpotX;
    }

    /**
     * @return the hot spot row
     */
    public int getHotSpotY() {
        return this.hotSpotY;
    }

    /**
     * @return the row-major RGBA bytes
     */
    public byte @NotNull [] getRgba() {
        return this.rgba;
    }


    /**
     * Creates a {@link Picture} instance of the image for the platform pointer: the same pixels as packed colors, the hot spot as the anchor.
     *
     * @return the created {@link Picture} instance
     */
    public @NotNull Picture createPicture() {
        int[] colors = new int[this.width * this.height];

        for (int index = 0; index < colors.length; index++) {
            int offset = index * OverlayPass.RGBA_BYTES;

            colors[index] = (this.rgba[offset + OverlayPass.RGBA_ALPHA] & BYTE_MASK) << ALPHA_SHIFT |
                            (this.rgba[offset] & BYTE_MASK) << RED_SHIFT |
                            (this.rgba[offset + OverlayPass.RGBA_GREEN] & BYTE_MASK) << GREEN_SHIFT |
                            this.rgba[offset + OverlayPass.RGBA_BLUE] & BYTE_MASK;
        }

        return new Picture(this.width, this.height, PixelLayer.ofRgba(colors), null, this.hotSpotX, this.hotSpotY, 0, 0, 0, 0);
    }
}
