package org.fogofwarlabs.sc4j.scr.render;

import org.jetbrains.annotations.NotNull;

/**
 * Computes the paused game's palette: each of the {@code 256} entries of the screen palette becomes the gray of its luminance,
 * {@code (R * 0x4D >> 8) + (G * 0x97 >> 8) + (B * 0x1C >> 8)} in a byte, in all three channels; the fourth byte is kept.
 */
public final class PaletteGrayscaleUtils {
    /**
     * The luminance weights of red, green and blue, in units of {@code 1/256}.
     */
    private static final int RED_WEIGHT = 0x4D;
    private static final int GREEN_WEIGHT = 0x97;
    private static final int BLUE_WEIGHT = 0x1C;
    /**
     * The shift that divides a weighted channel by {@code 256}.
     */
    private static final int WEIGHT_SHIFT = 8;
    /**
     * The mask of an unsigned palette byte.
     */
    private static final int BYTE_MASK = 0xFF;

    /**
     * Converts palette entries to grayscale while retaining each fourth byte.
     *
     * @param palette the entries, four bytes each (red, green, blue, fourth byte)
     * @return a new array of the gray entries with the fourth bytes kept
     */
    public static byte @NotNull [] apply(byte @NotNull [] palette) {
        byte[] gray = new byte[palette.length];

        for (int offset = 0; offset + RenderAssets.PALETTE_FOURTH < palette.length; offset += RenderAssets.PALETTE_ENTRY_SIZE) {
            int red = palette[offset] & BYTE_MASK;
            int green = palette[offset + RenderAssets.PALETTE_GREEN] & BYTE_MASK;
            int blue = palette[offset + RenderAssets.PALETTE_BLUE] & BYTE_MASK;

            byte value = (byte) ((red * RED_WEIGHT >> WEIGHT_SHIFT) + (green * GREEN_WEIGHT >> WEIGHT_SHIFT) + (blue * BLUE_WEIGHT >> WEIGHT_SHIFT));

            gray[offset]                               = value;
            gray[offset + RenderAssets.PALETTE_GREEN]  = value;
            gray[offset + RenderAssets.PALETTE_BLUE]   = value;
            gray[offset + RenderAssets.PALETTE_FOURTH] = palette[offset + RenderAssets.PALETTE_FOURTH];
        }

        return gray;
    }

    private PaletteGrayscaleUtils() {
    }
}
