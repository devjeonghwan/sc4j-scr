package org.fogofwarlabs.sc4j.scr.client.ui.portrait;

import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Fills the portrait canvas with procedural static from the shared portrait generator.
 * A restart keeps full noise until a clip becomes available. Showing the first clip frame clears the pending state.
 * Ticks raise the transparent-pixel threshold; redraws consume generator values only when the canvas is dirty.
 */
public final class PortraitStatic {
    /**
     * The canvas width: the portrait control's width in interface pixels.
     */
    public static final int WIDTH = 60;
    /**
     * The canvas height: the portrait control's height in interface pixels.
     */
    public static final int HEIGHT = 56;
    /**
     * The level from which no noise is drawn.
     */
    public static final int CLEAR_LEVEL = 80;
    /**
     * The grays of the color table, white to black.
     */
    public static final int @NotNull [] GRAYS = {
            0xFFFFFF, 0xE0E0E0, 0xD0D0D0, 0xC0C0C0, 0xB0B0B0, 0xA0A0A0, 0x909090, 0x808080,
            0x707070, 0x606060, 0x505050, 0x404040, 0x303030, 0x202020, 0x101010, 0x000000
    };

    /**
     * The id of the static timer of the portrait control, armed with the period {@link #TICK_MILLISECONDS}.
     */
    public static final int TIMER = 2;
    /**
     * The period of {@link #TIMER} in milliseconds.
     */
    public static final int TICK_MILLISECONDS = 30;
    /**
     * The level step per static tick.
     */
    private static final int LEVEL_STEP = 10;
    /**
     * The level set when the new clip shows its first frame.
     */
    private static final int SHOWN_LEVEL = 50;
    private static final int RGB_MASK = 0xFFFFFF00;
    private static final int RED_SHIFT = 24;
    private static final int GREEN_SHIFT = 16;
    private static final int BLUE_SHIFT = 8;
    private static final int BYTE_MASK = 0xFF;
    private static final int OPAQUE = OverlayPass.OPAQUE_ALPHA & BYTE_MASK;

    private final @NotNull PortraitGenerator generator;
    private final byte @NotNull [] canvas;
    private final int @NotNull [] colors;

    private int level;
    private boolean pending;
    private boolean dirty;

    /**
     * Creates a {@link PortraitStatic} instance without noise.
     *
     * @param generator the portrait generator shared with the clip choice
     */
    public PortraitStatic(@NotNull PortraitGenerator generator) {
        this.generator = generator;
        this.canvas    = new byte[WIDTH * HEIGHT * OverlayPass.RGBA_BYTES];
        this.colors    = new int[GRAYS.length];

        this.level   = CLEAR_LEVEL;
        this.pending = false;
        this.dirty   = false;
    }


    /**
     * @return {@code true} if noise is drawn, {@code false} otherwise
     */
    public boolean isVisible() {
        return this.level < CLEAR_LEVEL;
    }

    /**
     * @return the static level: the percent of transparent pixels, {@code 0..80}
     */
    public int getLevel() {
        return this.level;
    }

    /**
     * @return {@code true} if a redraw is due and the next {@link #takeCanvas} fills the canvas again, {@code false} otherwise
     */
    public boolean isDirty() {
        return this.dirty;
    }

    /**
     * Sets the color table: the palette entries nearest to {@link #GRAYS}.
     *
     * @param colors the packed {@code 0xRRGGBBAA} colors in the order of {@link #GRAYS}
     */
    public void setColors(int @NotNull [] colors) {
        System.arraycopy(colors, 0, this.colors, 0, this.colors.length);
    }


    /**
     * Restarts the static for another unit or portrait.
     */
    public void restart() {
        this.level   = 0;
        this.pending = true;
        this.dirty   = true;
    }

    /**
     * Called when a video frame is shown (timer {@code 1}): the first frame of the new clip ends the full static.
     */
    public void onFrameShown() {
        if (this.pending) {
            this.pending = false;
            this.level   = SHOWN_LEVEL;
        }

        if (this.level < CLEAR_LEVEL) {
            this.dirty = true;
        }
    }

    /**
     * Advances the static by one firing of timer {@code 2}, which runs while the portrait control is visible and shows a portrait.
     * A pending static with the clip not ready is redrawn unchanged; otherwise the level rises by {@code 10} until {@code 80}.
     *
     * @param clipLoaded whether the clip is ready
     */
    public void tick(boolean clipLoaded) {
        if (this.pending && !clipLoaded) {
            this.dirty = true;
        } else if (this.level < CLEAR_LEVEL) {
            this.level += LEVEL_STEP;
            this.dirty = true;
        }
    }

    /**
     * Clears and fills the canvas when a redraw is due.
     * Noise draws consume the shared generator after the restart chooses its clip.
     *
     * @return the RGBA canvas, {@link #WIDTH} x {@link #HEIGHT}
     */
    public byte @NotNull [] takeCanvas() {
        if (this.dirty) {
            this.dirty = false;

            Arrays.fill(this.canvas, (byte) 0);

            if (this.level < CLEAR_LEVEL) {
                for (int pixel = 0; pixel < WIDTH * HEIGHT; pixel++) {
                    if (this.generator.nextPercent() < this.level) {
                        continue;
                    }

                    int color = this.colors[this.generator.nextNibble()];
                    int offset = pixel * OverlayPass.RGBA_BYTES;

                    this.canvas[offset]                          = (byte) (color >>> RED_SHIFT);
                    this.canvas[offset + OverlayPass.RGBA_GREEN] = (byte) (color >>> GREEN_SHIFT);
                    this.canvas[offset + OverlayPass.RGBA_BLUE]  = (byte) (color >>> BLUE_SHIFT);
                    this.canvas[offset + OverlayPass.RGBA_ALPHA] = (byte) ((color & RGB_MASK) == 0 ? 0 : OPAQUE);
                }
            }
        }

        return this.canvas;
    }
}
