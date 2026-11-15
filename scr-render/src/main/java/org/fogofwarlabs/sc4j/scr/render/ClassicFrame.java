package org.fogofwarlabs.sc4j.scr.render;

import org.fogofwarlabs.sc4j.engine.resource.graphics.SpriteFrame;
import org.fogofwarlabs.sc4j.engine.resource.graphics.SpriteSheet;
import org.jetbrains.annotations.NotNull;

/**
 * Holds one classic (SD) frame of an engine {@link SpriteSheet} on the sheet's full frame box: the palette indices and the
 * coverage of the frame rectangle at its place, zero elsewhere. The coverage marks the nonzero pixels: in a sheet imported
 * from a GRP, palette index {@code 0} is transparent
 * ({@link org.fogofwarlabs.sc4j.scr.data.resource.importer.GrpSheetUtils}).
 */
public final class ClassicFrame {
    /**
     * Creates a {@link ClassicFrame} instance of one frame of a sheet.
     *
     * @param sheet      the sprite sheet, an indexed one
     * @param frameIndex the frame index
     * @return the created {@link ClassicFrame} instance
     * @throws IndexOutOfBoundsException if the sheet has no such frame
     * @throws IllegalStateException     if the sheet stores packed colours
     */
    public static @NotNull ClassicFrame of(@NotNull SpriteSheet sheet, int frameIndex) {
        SpriteFrame frame = sheet.getFrame(frameIndex);
        int width = sheet.getWidth();
        byte[] pixels = new byte[width * sheet.getHeight()];
        byte[] coverage = new byte[pixels.length];

        for (int row = 0; row < frame.getHeight(); row++) {
            int destination = (frame.getY() + row) * width + frame.getX();

            sheet.getColor().copyBytes(frame.getPixelOffset() + row * frame.getWidth(), pixels, destination, frame.getWidth());

            for (int column = 0; column < frame.getWidth(); column++) {
                coverage[destination + column] = pixels[destination + column] == 0 ? 0 : (byte) 1;
            }
        }

        return new ClassicFrame(frame.getX(), frame.getY(), width, sheet.getHeight(), frame.getWidth(), frame.getHeight(), pixels, coverage);
    }


    private final int xOffset;
    private final int yOffset;

    private final int width;
    private final int height;

    private final int frameWidth;
    private final int frameHeight;

    private final byte @NotNull [] pixels;
    private final byte @NotNull [] coverage;

    private ClassicFrame(int xOffset, int yOffset,
                         int width, int height,
                         int frameWidth, int frameHeight,
                         byte @NotNull [] pixels, byte @NotNull [] coverage) {
        this.xOffset = xOffset;
        this.yOffset = yOffset;

        this.width  = width;
        this.height = height;

        this.frameWidth  = frameWidth;
        this.frameHeight = frameHeight;

        this.pixels   = pixels;
        this.coverage = coverage;
    }

    /**
     * @return the left of the frame rectangle in the frame box
     */
    public int getXOffset() {
        return this.xOffset;
    }

    /**
     * @return the top of the frame rectangle in the frame box
     */
    public int getYOffset() {
        return this.yOffset;
    }

    /**
     * @return the frame box width
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * @return the frame box height
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * @return the frame rectangle width
     */
    public int getFrameWidth() {
        return this.frameWidth;
    }

    /**
     * @return the frame rectangle height
     */
    public int getFrameHeight() {
        return this.frameHeight;
    }

    /**
     * @return the palette indices of the frame box in row-major order
     */
    public byte @NotNull [] getPixels() {
        return this.pixels;
    }

    /**
     * @return the coverage of the frame box, {@code 1} for a drawn pixel and {@code 0} for a transparent one
     */
    public byte @NotNull [] getCoverage() {
        return this.coverage;
    }
}
