package org.fogofwarlabs.sc4j.scr.render.hd;

import org.fogofwarlabs.sc4j.engine.resource.graphics.TextureFrame;
import org.fogofwarlabs.sc4j.engine.resource.graphics.TextureSheet;
import org.jetbrains.annotations.NotNull;

/**
 * The frame placement of an HD anim without its textures: canvas size, each frame's rectangle in the canvas (scale-{@code 4} pixels), its position in
 * the texture and its flags. It is what a drawn image needs of its {@link TextureSheet}, whose file bytes are not kept once the textures are
 * uploaded (all {@code 999} HD anims hold about {@code 2} GB).
 */
final class HdFrameGeometry {
    /**
     * The frame units per game pixel: anim frame rectangles are in scale-{@code 4} pixels whatever the texture scale.
     */
    static final int UNIT_SCALE = 4;

    /**
     * The positions of the seven values of a frame: x offset, y offset, width, height, texture x, texture y, flags.
     */
    private static final int X_OFFSET = 0;
    private static final int Y_OFFSET = 1;
    private static final int WIDTH = 2;
    private static final int HEIGHT = 3;
    private static final int TEXTURE_X = 4;
    private static final int TEXTURE_Y = 5;
    private static final int FLAGS = 6;
    private static final int VALUES = FLAGS + 1;

    /**
     * Creates an {@link HdFrameGeometry} instance by copying the placement of a texture sheet.
     *
     * @param sheet the texture sheet
     * @return the created {@link HdFrameGeometry} instance
     */
    static @NotNull HdFrameGeometry of(@NotNull TextureSheet sheet) {
        int frameCount = sheet.getFrameCount();
        int[] rectangles = new int[frameCount * VALUES];

        for (int frame = 0; frame < frameCount; frame++) {
            TextureFrame stored = sheet.getFrame(frame);

            rectangles[frame * VALUES + X_OFFSET]  = stored.getX();
            rectangles[frame * VALUES + Y_OFFSET]  = stored.getY();
            rectangles[frame * VALUES + WIDTH]     = stored.getWidth();
            rectangles[frame * VALUES + HEIGHT]    = stored.getHeight();
            rectangles[frame * VALUES + TEXTURE_X] = stored.getTextureX();
            rectangles[frame * VALUES + TEXTURE_Y] = stored.getTextureY();
            rectangles[frame * VALUES + FLAGS]     = stored.getFlags();
        }

        return new HdFrameGeometry(sheet.getWidth(), sheet.getHeight(), rectangles);
    }

    private final int canvasWidth;
    private final int canvasHeight;

    /**
     * Seven values per frame: x offset, y offset, width, height, texture x, texture y, flags.
     */
    private final int @NotNull [] rectangles;

    private HdFrameGeometry(int canvasWidth, int canvasHeight, int @NotNull [] rectangles) {
        this.canvasWidth  = canvasWidth;
        this.canvasHeight = canvasHeight;

        this.rectangles = rectangles;
    }

    /**
     * @return the number of frames
     */
    int getFrameCount() {
        return this.rectangles.length / VALUES;
    }

    /**
     * @return the canvas width in scale-{@code 4} pixels
     */
    int getCanvasWidth() {
        return this.canvasWidth;
    }

    /**
     * @return the canvas height in scale-{@code 4} pixels
     */
    int getCanvasHeight() {
        return this.canvasHeight;
    }

    /**
     * @param frame the frame index
     * @return the frame rectangle's horizontal offset in the canvas
     */
    int getFrameXOffset(int frame) {
        return this.rectangles[frame * VALUES + X_OFFSET];
    }

    /**
     * @param frame the frame index
     * @return the frame rectangle's vertical offset in the canvas
     */
    int getFrameYOffset(int frame) {
        return this.rectangles[frame * VALUES + Y_OFFSET];
    }

    /**
     * @param frame the frame index
     * @return the frame rectangle's width
     */
    int getFrameWidth(int frame) {
        return this.rectangles[frame * VALUES + WIDTH];
    }

    /**
     * @param frame the frame index
     * @return the frame rectangle's height
     */
    int getFrameHeight(int frame) {
        return this.rectangles[frame * VALUES + HEIGHT];
    }

    /**
     * @param frame the frame index
     * @return the frame's horizontal position in the texture
     */
    int getFrameX(int frame) {
        return this.rectangles[frame * VALUES + TEXTURE_X];
    }

    /**
     * @param frame the frame index
     * @return the frame's vertical position in the texture
     */
    int getFrameY(int frame) {
        return this.rectangles[frame * VALUES + TEXTURE_Y];
    }

    /**
     * @param frame the frame index
     * @return the frame's flags ({@link TextureFrame#getFlags()})
     */
    int getFrameFlags(int frame) {
        return this.rectangles[frame * VALUES + FLAGS];
    }
}
