package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Reads DXT1, DXT3, DXT5 and uncompressed RGB texture levels while borrowing the source array.
 * DX10 extension headers are rejected.
 */
public final class DdsFile {
    private static final int BYTE_MASK = 0xFF;
    private static final int MAGIC = 0x20534444;
    private static final int HEADER_SIZE = 128;
    private static final int DECLARED_HEADER_SIZE = 124;
    private static final int DECLARED_HEADER_SIZE_OFFSET = 4;
    private static final int HEIGHT_OFFSET = 12;
    private static final int WIDTH_OFFSET = 16;
    private static final int MIPMAP_COUNT_OFFSET = 28;
    private static final int PIXEL_FLAGS_OFFSET = 80;
    private static final int FOUR_CHARACTER_CODE_OFFSET = 84;
    private static final int BIT_COUNT_OFFSET = 88;
    private static final int RED_MASK_OFFSET = 92;
    private static final int GREEN_MASK_OFFSET = 96;
    private static final int BLUE_MASK_OFFSET = 100;
    private static final int ALPHA_MASK_OFFSET = 104;
    private static final int MAXIMUM_DIMENSION = 16384;
    private static final int MAXIMUM_MIPMAP_COUNT = 15;

    private static final int PIXEL_FORMAT_FOUR_CHARACTER_CODE = 0x4;
    private static final int PIXEL_FORMAT_RGB = 0x40;
    private static final int PIXEL_FORMAT_ALPHA_PIXELS = 0x1;
    private static final int FOUR_CHARACTER_DXT1 = 0x31545844;
    private static final int FOUR_CHARACTER_DXT3 = 0x33545844;
    private static final int FOUR_CHARACTER_DXT5 = 0x35545844;
    private static final int FOUR_CHARACTER_DX10 = 0x30315844;
    private static final int RGB32_BIT_COUNT = 32;
    private static final int RGB24_BIT_COUNT = 24;
    private static final int RGB32_BYTES_PER_PIXEL = 4;
    private static final int RGB24_BYTES_PER_PIXEL = 3;

    /**
     * The RGBA output: four bytes per pixel in red, green, blue, alpha order.
     */
    private static final int CHANNEL_COUNT = 4;
    private static final int CHANNEL_RED = 0;
    private static final int CHANNEL_GREEN = 1;
    private static final int CHANNEL_BLUE = 2;
    private static final int CHANNEL_ALPHA = 3;
    private static final int MAXIMUM_COMPONENT = 255;

    /**
     * The texels per side of a compressed block.
     */
    private static final int BLOCK_DIMENSION = 4;
    /**
     * The explicit or interpolated alpha block that precedes the color block of DXT3 and DXT5.
     */
    private static final int ALPHA_BLOCK_SIZE = 8;
    private static final int COLOR_SELECTORS_OFFSET = 4;
    private static final int COLOR_PALETTE_SIZE = 4;
    private static final int THIRD_COLOR = 2;
    private static final int FOURTH_COLOR = 3;
    private static final int COLOR_SELECTOR_BITS = 2;
    private static final int COLOR_SELECTOR_MASK = 0x3;
    private static final int THIRDS = 3;
    private static final int TWO_THIRDS = 2;
    private static final int HALVES = 2;
    /**
     * The shift of the red component of a packed {@code 0xRRGGBB} color, the highest of the three.
     */
    private static final int PACKED_RED_SHIFT = 16;
    private static final int PACKED_GREEN_SHIFT = 8;

    private static final int ALPHA_PALETTE_SIZE = 8;
    /**
     * The interpolation divisor of the eight-alpha mode (first endpoint greater than the second).
     */
    private static final int EIGHT_ALPHA_STEPS = 7;
    /**
     * The interpolation divisor of the six-alpha mode, which adds transparent and opaque entries.
     */
    private static final int SIX_ALPHA_STEPS = 5;
    private static final int TRANSPARENT_ALPHA_INDEX = 6;
    private static final int OPAQUE_ALPHA_INDEX = 7;
    private static final int ALPHA_SELECTORS_OFFSET = 2;
    private static final int ALPHA_SELECTOR_BYTES = 6;
    private static final int ALPHA_SELECTOR_BITS = 3;
    private static final int ALPHA_SELECTOR_MASK = 0x7;

    private static final int EXPLICIT_ALPHA_BITS = 4;
    private static final int EXPLICIT_ALPHA_MASK = 0xF;
    private static final int EXPLICIT_ALPHAS_PER_BYTE = 2;
    /**
     * Scales a four-bit alpha to eight bits: {@code 15 * 17 = 255}.
     */
    private static final int EXPLICIT_ALPHA_SCALE = 17;

    private static final int RGB565_RED_SHIFT = 11;
    private static final int RGB565_GREEN_SHIFT = 5;
    private static final int FIVE_BIT_MASK = 0x1F;
    private static final int SIX_BIT_MASK = 0x3F;
    private static final int FIVE_BIT_WIDEN_SHIFT = 3;
    private static final int FIVE_BIT_REPLICATE_SHIFT = 2;
    private static final int SIX_BIT_WIDEN_SHIFT = 2;
    private static final int SIX_BIT_REPLICATE_SHIFT = 4;

    /**
     * Identifies the stored pixel layout.
     */
    public enum Format {
        /**
         * BC1 blocks of eight bytes with optional one-bit alpha.
         */
        DXT1(8),
        /**
         * BC2 blocks of sixteen bytes with explicit four-bit alpha.
         */
        DXT3(16),
        /**
         * BC3 blocks of sixteen bytes with interpolated alpha.
         */
        DXT5(16),
        /**
         * Uncompressed 32-bit pixels described by the header masks.
         */
        RGB32(0),
        /**
         * Uncompressed 24-bit pixels described by the header masks.
         */
        RGB24(0);

        private final int blockSize;

        Format(int blockSize) {
            this.blockSize = blockSize;
        }

        /**
         * @return the compressed block size in bytes, or {@code 0} for uncompressed formats
         */
        public int getBlockSize() {
            return this.blockSize;
        }

        /**
         * @return {@code true} if the format stores 4 by 4 compressed blocks, {@code false} otherwise
         */
        public boolean isCompressed() {
            return this.blockSize != 0;
        }
    }

    /**
     * Creates a {@link DdsFile} instance from a whole DDS file array.
     *
     * @param data the DDS file content
     * @return the created {@link DdsFile} instance
     * @throws IOException if the header or data size is invalid
     */
    public static @NotNull DdsFile parse(byte @NotNull [] data) throws IOException {
        return parse(data, 0, data.length);
    }

    /**
     * Creates a {@link DdsFile} instance from a DDS file embedded in a larger container.
     *
     * @param data   the container content
     * @param offset the first byte of the DDS magic
     * @param length the DDS file length in bytes
     * @return the created {@link DdsFile} instance
     * @throws IOException if the range, header or data size is invalid
     */
    public static @NotNull DdsFile parse(byte @NotNull [] data, int offset, int length) throws IOException {
        if (offset < 0 || length < HEADER_SIZE || (long) offset + length > data.length) {
            throw Exceptions.newIOException("DDS range is outside the container: {0}+{1}", offset, length);
        }

        if (LittleEndianUtils.readInt32(data, offset) != MAGIC ||
            LittleEndianUtils.readInt32(data, offset + DECLARED_HEADER_SIZE_OFFSET) != DECLARED_HEADER_SIZE) {
            throw Exceptions.newIOException("Missing DDS magic or header size at offset {0}", offset);
        }

        int height = LittleEndianUtils.readInt32(data, offset + HEIGHT_OFFSET);
        int width = LittleEndianUtils.readInt32(data, offset + WIDTH_OFFSET);
        int mipmapCount = Math.max(1, LittleEndianUtils.readInt32(data, offset + MIPMAP_COUNT_OFFSET));
        int pixelFlags = LittleEndianUtils.readInt32(data, offset + PIXEL_FLAGS_OFFSET);
        int fourCharacterCode = LittleEndianUtils.readInt32(data, offset + FOUR_CHARACTER_CODE_OFFSET);
        int bitCount = LittleEndianUtils.readInt32(data, offset + BIT_COUNT_OFFSET);

        if (width <= 0 || height <= 0 || width > MAXIMUM_DIMENSION || height > MAXIMUM_DIMENSION || mipmapCount > MAXIMUM_MIPMAP_COUNT) {
            throw Exceptions.newIOException("Invalid DDS dimensions: {0} by {1}, mipmaps {2}", width, height, mipmapCount);
        }

        Format format;
        int[] masks = new int[CHANNEL_COUNT];

        if ((pixelFlags & PIXEL_FORMAT_FOUR_CHARACTER_CODE) != 0) {
            format = switch (fourCharacterCode) {
                case FOUR_CHARACTER_DXT1 -> Format.DXT1;
                case FOUR_CHARACTER_DXT3 -> Format.DXT3;
                case FOUR_CHARACTER_DXT5 -> Format.DXT5;
                case FOUR_CHARACTER_DX10 -> throw Exceptions.newIOException("DDS DX10 extension headers are not supported.");
                default -> throw Exceptions.newIOException("Unsupported DDS four-character code 0x{0}", Integer.toHexString(fourCharacterCode));
            };
        } else if ((pixelFlags & PIXEL_FORMAT_RGB) != 0 && (bitCount == RGB32_BIT_COUNT || bitCount == RGB24_BIT_COUNT)) {
            format               = bitCount == RGB32_BIT_COUNT ? Format.RGB32 : Format.RGB24;
            masks[CHANNEL_RED]   = LittleEndianUtils.readInt32(data, offset + RED_MASK_OFFSET);
            masks[CHANNEL_GREEN] = LittleEndianUtils.readInt32(data, offset + GREEN_MASK_OFFSET);
            masks[CHANNEL_BLUE]  = LittleEndianUtils.readInt32(data, offset + BLUE_MASK_OFFSET);
            masks[CHANNEL_ALPHA] = (pixelFlags & PIXEL_FORMAT_ALPHA_PIXELS) != 0 ? LittleEndianUtils.readInt32(data, offset + ALPHA_MASK_OFFSET) : 0;
        } else {
            throw Exceptions.newIOException("Unsupported DDS pixel format flags 0x{0}, bits {1}", Integer.toHexString(pixelFlags), bitCount);
        }

        int[] levelOffsets = new int[mipmapCount];
        int[] levelSizes = new int[mipmapCount];
        long position = offset + HEADER_SIZE;

        for (int level = 0; level < mipmapCount; level++) {
            int levelWidth = Math.max(1, width >> level);
            int levelHeight = Math.max(1, height >> level);
            long size = format.isCompressed()
                        ? (long) ((levelWidth + BLOCK_DIMENSION - 1) / BLOCK_DIMENSION) * ((levelHeight + BLOCK_DIMENSION - 1) / BLOCK_DIMENSION) *
                          format.getBlockSize()
                        : (long) levelWidth * levelHeight * (bitCount / Byte.SIZE);

            if (position + size > (long) offset + length) {
                throw Exceptions.newIOException("DDS level {0} exceeds the file length.", level);
            }

            levelOffsets[level] = (int) position;
            levelSizes[level]   = (int) size;

            position += size;
        }

        return new DdsFile(data, width, height, format, masks, levelOffsets, levelSizes);
    }

    private static int expandRgb565(int value) {
        int red = value >>> RGB565_RED_SHIFT & FIVE_BIT_MASK;
        int green = value >>> RGB565_GREEN_SHIFT & SIX_BIT_MASK;
        int blue = value & FIVE_BIT_MASK;

        return (red << FIVE_BIT_WIDEN_SHIFT | red >>> FIVE_BIT_REPLICATE_SHIFT) << PACKED_RED_SHIFT |
               (green << SIX_BIT_WIDEN_SHIFT | green >>> SIX_BIT_REPLICATE_SHIFT) << PACKED_GREEN_SHIFT |
               (blue << FIVE_BIT_WIDEN_SHIFT | blue >>> FIVE_BIT_REPLICATE_SHIFT);
    }

    private static int mixRgbComponents(int first, int second, int firstWeight, int secondWeight, int divisor) {
        int result = 0;

        for (int shift = 0; shift <= PACKED_RED_SHIFT; shift += Byte.SIZE) {
            result |= (((first >>> shift & BYTE_MASK) * firstWeight + (second >>> shift & BYTE_MASK) * secondWeight) / divisor) << shift;
        }

        return result;
    }

    private static int normalizeMaskedComponent(int value, int mask) {
        int shift = Integer.numberOfTrailingZeros(mask);
        int bits = Integer.bitCount(mask);
        int component = (value & mask) >>> shift;

        return bits >= Byte.SIZE ? component >>> (bits - Byte.SIZE) : component * MAXIMUM_COMPONENT / ((1 << bits) - 1);
    }


    /**
     * Decodes the blocks of one compressed level to row-major RGBA bytes.
     *
     * @param format the block format, {@link Format#DXT1}, {@link Format#DXT3} or {@link Format#DXT5}
     * @param width  the level width in pixels
     * @param height the level height in pixels
     * @param data   holds the blocks
     * @param source the offset of the first block in {@code data}
     * @return {@code width * height * 4} bytes in red, green, blue, alpha order
     * @throws IllegalArgumentException if the format is not compressed
     */
    public static byte @NotNull [] decodeBlocks(@NotNull Format format, int width, int height, byte @NotNull [] data, int source) {
        if (!format.isCompressed()) {
            throw Exceptions.newIllegalArgumentException("Not a block format: {0}", format);
        }

        byte[] result = new byte[width * height * CHANNEL_COUNT];
        int blocksWide = (width + BLOCK_DIMENSION - 1) / BLOCK_DIMENSION;
        int blocksHigh = (height + BLOCK_DIMENSION - 1) / BLOCK_DIMENSION;
        int[] colors = new int[COLOR_PALETTE_SIZE];
        int[] alphas = new int[ALPHA_PALETTE_SIZE];

        for (int blockRow = 0; blockRow < blocksHigh; blockRow++) {
            for (int blockColumn = 0; blockColumn < blocksWide; blockColumn++) {
                int block = source + (blockRow * blocksWide + blockColumn) * format.getBlockSize();
                int colorBlock = format == Format.DXT1 ? block : block + ALPHA_BLOCK_SIZE;
                int color0 = LittleEndianUtils.readUnsigned16(data, colorBlock);
                int color1 = LittleEndianUtils.readUnsigned16(data, colorBlock + Short.BYTES);

                colors[0] = expandRgb565(color0);
                colors[1] = expandRgb565(color1);

                boolean fourColors = format != Format.DXT1 || color0 > color1;

                if (fourColors) {
                    colors[THIRD_COLOR]  = mixRgbComponents(colors[0], colors[1], TWO_THIRDS, 1, THIRDS);
                    colors[FOURTH_COLOR] = mixRgbComponents(colors[0], colors[1], 1, TWO_THIRDS, THIRDS);
                } else {
                    colors[THIRD_COLOR]  = mixRgbComponents(colors[0], colors[1], 1, 1, HALVES);
                    colors[FOURTH_COLOR] = 0;
                }

                if (format == Format.DXT5) {
                    alphas[0] = data[block] & BYTE_MASK;
                    alphas[1] = data[block + 1] & BYTE_MASK;

                    if (alphas[0] > alphas[1]) {
                        for (int step = 1; step < EIGHT_ALPHA_STEPS; step++) {
                            alphas[step + 1] = ((EIGHT_ALPHA_STEPS - step) * alphas[0] + step * alphas[1]) / EIGHT_ALPHA_STEPS;
                        }
                    } else {
                        for (int step = 1; step < SIX_ALPHA_STEPS; step++) {
                            alphas[step + 1] = ((SIX_ALPHA_STEPS - step) * alphas[0] + step * alphas[1]) / SIX_ALPHA_STEPS;
                        }

                        alphas[TRANSPARENT_ALPHA_INDEX] = 0;
                        alphas[OPAQUE_ALPHA_INDEX]      = MAXIMUM_COMPONENT;
                    }
                }

                long alphaBits = 0;

                if (format == Format.DXT5) {
                    for (int byteIndex = 0; byteIndex < ALPHA_SELECTOR_BYTES; byteIndex++) {
                        alphaBits |= (long) (data[block + ALPHA_SELECTORS_OFFSET + byteIndex] & BYTE_MASK) << (byteIndex * Byte.SIZE);
                    }
                }

                int colorBits = LittleEndianUtils.readInt32(data, colorBlock + COLOR_SELECTORS_OFFSET);

                for (int pixelY = 0; pixelY < BLOCK_DIMENSION; pixelY++) {
                    int y = blockRow * BLOCK_DIMENSION + pixelY;

                    if (y >= height) {
                        break;
                    }

                    for (int pixelX = 0; pixelX < BLOCK_DIMENSION; pixelX++) {
                        int x = blockColumn * BLOCK_DIMENSION + pixelX;

                        if (x >= width) {
                            continue;
                        }

                        int texel = pixelY * BLOCK_DIMENSION + pixelX;
                        int selector = colorBits >>> (texel * COLOR_SELECTOR_BITS) & COLOR_SELECTOR_MASK;
                        int color = colors[selector];
                        int alpha;

                        if (format == Format.DXT1) {
                            alpha = !fourColors && selector == FOURTH_COLOR ? 0 : MAXIMUM_COMPONENT;
                        } else if (format == Format.DXT3) {
                            alpha = (data[block + texel / EXPLICIT_ALPHAS_PER_BYTE] >>> ((texel & 1) * EXPLICIT_ALPHA_BITS) &
                                     EXPLICIT_ALPHA_MASK) * EXPLICIT_ALPHA_SCALE;
                        } else {
                            alpha = alphas[(int) (alphaBits >>> (texel * ALPHA_SELECTOR_BITS) & ALPHA_SELECTOR_MASK)];
                        }

                        int destination = (y * width + x) * CHANNEL_COUNT;

                        result[destination + CHANNEL_RED]   = (byte) (color >>> PACKED_RED_SHIFT);
                        result[destination + CHANNEL_GREEN] = (byte) (color >>> PACKED_GREEN_SHIFT);
                        result[destination + CHANNEL_BLUE]  = (byte) color;
                        result[destination + CHANNEL_ALPHA] = (byte) alpha;
                    }
                }
            }
        }

        return result;
    }

    private final byte @NotNull [] data;

    private final int width;
    private final int height;

    private final @NotNull Format format;
    private final int @NotNull [] masks;

    private final int @NotNull [] levelOffsets;
    private final int @NotNull [] levelSizes;

    private DdsFile(byte @NotNull [] data,
                    int width, int height,
                    @NotNull Format format, int @NotNull [] masks,
                    int @NotNull [] levelOffsets, int @NotNull [] levelSizes) {
        this.data = data;

        this.width  = width;
        this.height = height;

        this.format = format;
        this.masks  = masks;

        this.levelOffsets = levelOffsets;
        this.levelSizes   = levelSizes;
    }

    /**
     * @return the level-0 width in pixels
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * @return the level-0 height in pixels
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * @return the stored pixel format
     */
    public @NotNull Format getFormat() {
        return this.format;
    }

    /**
     * @return the number of stored mipmap levels, at least {@code 1}
     */
    public int getMipmapCount() {
        return this.levelOffsets.length;
    }

    /**
     * @param level the mipmap level
     * @return the stored byte size of the level
     */
    public int getLevelSize(int level) {
        return this.levelSizes[level];
    }


    /**
     * Copies a stored level's raw bytes into a buffer, for example for a compressed GPU upload.
     *
     * @param level       the mipmap level
     * @param destination the buffer receiving {@link #getLevelSize(int)} bytes at its position
     */
    public void copyLevel(int level, @NotNull ByteBuffer destination) {
        destination.put(this.data, this.levelOffsets[level], this.levelSizes[level]);
    }

    /**
     * Decodes level {@code 0} to row-major RGBA bytes.
     *
     * @return {@code width * height * 4} bytes in red, green, blue, alpha order
     */
    public byte @NotNull [] decodeRgba() {
        int source = this.levelOffsets[0];

        if (!this.format.isCompressed()) {
            byte[] result = new byte[this.width * this.height * CHANNEL_COUNT];
            int bytesPerPixel = this.format == Format.RGB32 ? RGB32_BYTES_PER_PIXEL : RGB24_BYTES_PER_PIXEL;

            for (int pixel = 0; pixel < this.width * this.height; pixel++) {
                int value = 0;

                for (int byteIndex = 0; byteIndex < bytesPerPixel; byteIndex++) {
                    value |= (this.data[source + pixel * bytesPerPixel + byteIndex] & BYTE_MASK) << (byteIndex * Byte.SIZE);
                }

                for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
                    result[pixel * CHANNEL_COUNT + channel] = (byte) (this.masks[channel] == 0
                                                                      ? MAXIMUM_COMPONENT
                                                                      : normalizeMaskedComponent(value, this.masks[channel]));
                }
            }

            return result;
        }

        return decodeBlocks(this.format, this.width, this.height, this.data, source);
    }
}
