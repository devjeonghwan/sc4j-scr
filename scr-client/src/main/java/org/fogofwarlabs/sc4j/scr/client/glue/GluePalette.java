package org.fogofwarlabs.sc4j.scr.client.glue;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceText;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

/**
 * Loads the main glue palette and its text color table from {@code tfont.pcx}.
 * Codes {@code 2} through {@code 7} use the first six entries; the remaining codes use palette index {@code 0}.
 */
public final class GluePalette {
    /**
     * The number of codes ({@code 0..31}) of the text color table.
     */
    public static final int CODE_COUNT = 32;

    private static final int FIRST_CODE = 2;
    private static final int ENTRY_SIZE = 8;
    private static final int ENTRY_COUNT = 6;
    private static final int ENTRY_SAMPLE_OFFSET = 1;
    private static final @NotNull String TEXT_FONT_FILE = "/tfont.pcx";

    /**
     * An 8-bit PCX image with its {@code 256}-color palette.
     */
    public static final class IndexedImage {
        private static final int HEADER_SIZE = 128;
        private static final int PALETTE_SIZE = 768;
        private static final int PALETTE_MARKER = 0x0C;
        private static final int RUN_FLAG = 0xC0;
        private static final int BITS_PER_PIXEL_OFFSET = 3;
        private static final int MINIMUM_X_OFFSET = 4;
        private static final int MINIMUM_Y_OFFSET = 6;
        private static final int MAXIMUM_X_OFFSET = 8;
        private static final int MAXIMUM_Y_OFFSET = 10;
        private static final int LINE_BYTES_OFFSET = 66;
        private static final int RGB_SIZE = 3;
        private static final int GREEN_OFFSET = 1;
        private static final int BLUE_OFFSET = 2;
        private static final int RED_SHIFT = 3 * Byte.SIZE;
        private static final int GREEN_SHIFT = 2 * Byte.SIZE;
        private static final int BLUE_SHIFT = Byte.SIZE;
        private static final int MAXIMUM_INDEX = 255;
        private static final int OPAQUE = 0xFF;

        /**
         * Creates a {@link IndexedImage} instance by decoding an 8-bit PCX image and its palette.
         *
         * @param data the image bytes
         * @return the created {@link IndexedImage} instance
         * @throws IOException if the header or palette marker does not describe an 8-bit image
         */
        public static @NotNull IndexedImage parse(byte @NotNull [] data) throws IOException {
            if (data.length < HEADER_SIZE + PALETTE_SIZE + 1 || data[BITS_PER_PIXEL_OFFSET] != Byte.SIZE ||
                Byte.toUnsignedInt(data[data.length - PALETTE_SIZE - 1]) != PALETTE_MARKER) {
                throw Exceptions.newIOException("Not an 8-bit PCX with a palette.");
            }

            int width = LittleEndianUtils.readUnsigned16(data, MAXIMUM_X_OFFSET) - LittleEndianUtils.readUnsigned16(data, MINIMUM_X_OFFSET) + 1;
            int height = LittleEndianUtils.readUnsigned16(data, MAXIMUM_Y_OFFSET) - LittleEndianUtils.readUnsigned16(data, MINIMUM_Y_OFFSET) + 1;
            int lineBytes = LittleEndianUtils.readUnsigned16(data, LINE_BYTES_OFFSET);
            byte[] lines = new byte[lineBytes * height];
            int input = HEADER_SIZE;
            int end = data.length - PALETTE_SIZE - 1;

            for (int output = 0; output < lines.length && input < end; ) {
                int value = Byte.toUnsignedInt(data[input++]);
                int run = 1;

                if ((value & RUN_FLAG) == RUN_FLAG && input < end) {
                    run   = value & ~RUN_FLAG;
                    value = Byte.toUnsignedInt(data[input++]);
                }

                for (int repeat = 0; repeat < run && output < lines.length; repeat++) {
                    lines[output++] = (byte) value;
                }
            }

            byte[] pixels = new byte[width * height];

            for (int row = 0; row < height; row++) {
                System.arraycopy(lines, row * lineBytes, pixels, row * width, width);
            }

            return new IndexedImage(pixels, Arrays.copyOfRange(data, data.length - PALETTE_SIZE, data.length));
        }

        private final byte @NotNull [] pixels;
        private final byte @NotNull [] palette;

        private IndexedImage(byte @NotNull [] pixels, byte @NotNull [] palette) {
            this.pixels  = pixels;
            this.palette = palette;
        }

        /**
         * @param index the nonnegative pixel index
         * @return the unsigned palette index, or {@code 0} beyond the pixel array
         */
        public int getPixel(int index) {
            return index < this.pixels.length ? Byte.toUnsignedInt(this.pixels[index]) : 0;
        }

        /**
         * @param index the palette index, clamped to {@code 0..255}
         * @return the packed {@code 0xRRGGBBAA} palette color
         */
        public int getColor(int index) {
            int offset = Math.clamp(index, 0, MAXIMUM_INDEX) * RGB_SIZE;

            return Byte.toUnsignedInt(this.palette[offset]) << RED_SHIFT |
                   Byte.toUnsignedInt(this.palette[offset + GREEN_OFFSET]) << GREEN_SHIFT |
                   Byte.toUnsignedInt(this.palette[offset + BLUE_OFFSET]) << BLUE_SHIFT |
                   OPAQUE;
        }
    }

    /**
     * Creates a {@link GluePalette} instance from the {@code tfont.pcx} of a palette folder.
     *
     * @param storage the installed game storage
     * @param folder  the palette folder, for example {@code glue/palmm} or {@code glue/title}
     * @return the created {@link GluePalette} instance
     * @throws IOException if {@code tfont.pcx} is missing or malformed
     */
    public static @NotNull GluePalette load(@NotNull CascStorage storage, @NotNull String folder) throws IOException {
        IndexedImage font = IndexedImage.parse(storage.readFile(folder + TEXT_FONT_FILE));
        int[] codeColors = new int[CODE_COUNT];

        for (int code = 0; code < CODE_COUNT; code++) {
            int entry = code - FIRST_CODE;
            int index = entry >= 0 && entry < ENTRY_COUNT ? font.getPixel(entry * ENTRY_SIZE + ENTRY_SAMPLE_OFFSET) : 0;

            codeColors[code] = font.getColor(index);
        }

        return new GluePalette(codeColors);
    }

    private final int @NotNull [] codeColors;

    private GluePalette(int @NotNull [] codeColors) {
        this.codeColors = codeColors;
    }

    /**
     * @param code a color code {@code 0..31}
     * @return the packed {@code 0xRRGGBBAA} color
     */
    public int getCodeColor(int code) {
        return this.codeColors[Math.floorMod(code, CODE_COUNT)];
    }

    /**
     * @return the packed colors of codes {@code 0..31}
     */
    public int @NotNull [] getCodeColors() {
        return this.codeColors.clone();
    }


    /**
     * Creates a {@link GluePalette} instance with other text colors: the game's dialogs draw their text in the color
     * codes of {@code tfontgam} while the glue palette stays loaded.
     *
     * @param codeColors the packed colors of codes {@code 0..31}, for example {@link InterfaceText#GAME_CODE_COLORS}
     * @return the created {@link GluePalette} instance
     */
    public @NotNull GluePalette createWithCodeColors(int @NotNull [] codeColors) {
        return new GluePalette(codeColors.clone());
    }
}
