package org.fogofwarlabs.sc4j.scr.render.ui;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.jetbrains.annotations.NotNull;

/**
 * Holds a decoded interface image as row-major RGBA bytes.
 */
public final class InterfaceImage {
    /**
     * The byte of a color channel at full intensity.
     */
    static final byte FULL_CHANNEL = (byte) 0xFF;
    /**
     * The alpha of an opaque packed {@code 0xRRGGBBAA} color.
     */
    static final int OPAQUE = 0xFF;
    /**
     * The channel shifts of a packed {@code 0xRRGGBBAA} color.
     */
    static final int RGBA_RED_SHIFT = 24;
    static final int RGBA_GREEN_SHIFT = 16;
    static final int RGBA_BLUE_SHIFT = 8;

    private final int width;
    private final int height;

    private final byte @NotNull [] rgba;

    /**
     * Creates an {@link InterfaceImage} instance.
     *
     * @param width  the width
     * @param height the height
     * @param rgba   the RGBA bytes, top row first; the array is retained
     * @throws IllegalArgumentException if a dimension is not positive or the pixel array does not match the dimensions
     */
    public InterfaceImage(int width, int height, byte @NotNull [] rgba) {
        if (width <= 0 || height <= 0 || rgba.length != width * height * OverlayPass.RGBA_BYTES) {
            throw Exceptions.newIllegalArgumentException("RGBA pixels do not match {0} by {1}.", width, height);
        }

        this.width  = width;
        this.height = height;

        this.rgba = rgba;
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * @return the height
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * @return the retained RGBA bytes
     */
    public byte @NotNull [] getRgba() {
        return this.rgba;
    }
}
