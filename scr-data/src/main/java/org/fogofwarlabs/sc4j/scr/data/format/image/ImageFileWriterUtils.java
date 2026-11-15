package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Writes the image files a mod ships for its added images ({@link org.fogofwarlabs.sc4j.scr.data.resource.ScrModGraphics}) in the formats the
 * readers of this package read: SD frames as a GRP file ({@link GrpFile}), block-compressed DXT5 textures ({@link DdsFile}) and single-entry
 * HD frame files ({@link AnimFile}), and interface sheets of DDS frames ({@link DdsGrpFile}) for its command icons and wireframes. A mod's
 * build-time art converter writes its frame files with them, a mod may encode SD frames that it
 * quantizes when it loads, and the tests that check added images generate their frames with them; nothing here reads or copies an installed file.
 */
public final class ImageFileWriterUtils {
    /**
     * The largest canvas side of a GRP file: the frame rectangles hold byte coordinates.
     */
    public static final int MAXIMUM_GRP_SIZE = 255;
    /**
     * The four bytes per pixel of the RGBA input of {@link #encodeDxt5}, in red, green, blue, alpha order.
     */
    public static final int RGBA_BYTES = 4;
    /**
     * The values of one frame of {@link #encodeAnim}: atlas x, atlas y, canvas x, canvas y, width, height, flags.
     */
    public static final int ANIM_FRAME_VALUES = 7;
    /**
     * The units of the canvas and of the frame values of an ANIM file per SD pixel, whatever the texture scale.
     */
    public static final int ANIM_UNIT_SCALE = 4;
    /**
     * The ANIM frame flag of a frame placed by its canvas offset in the canvas centered on the image position; a frame without it is placed at the
     * SD frame's GRP rectangle.
     */
    public static final int ANIM_FRAME_CANVAS_PLACED = 1;
    /**
     * The version word of a {@code .dds.grp} sheet whose texture scale is in its low four bits, as the installed interface sheets have it
     * ({@code 0x1001} SD, {@code 0x1002} HD2, {@code 0x1004} HD).
     */
    public static final int DDS_GRP_SCALED_VERSION = 0x1000;

    private static final int BYTE_MASK = 0xFF;
    private static final int GRP_HEADER_SIZE = 6;
    private static final int GRP_FRAME_HEADER_SIZE = 8;
    private static final int GRP_TRANSPARENT_PACKET = 0x80;
    private static final int GRP_MAXIMUM_TRANSPARENT_RUN = 0x7F;
    private static final int GRP_MAXIMUM_LITERAL_RUN = 0x3F;

    private static final int DDS_MAGIC = 0x20534444;
    private static final int DDS_HEADER_SIZE = 124;
    private static final int DDS_FLAGS = 0x1 | 0x2 | 0x4 | 0x1000 | 0x80000;
    private static final int DDS_PIXEL_FORMAT_SIZE = 32;
    private static final int DDS_FOUR_CHARACTER_CODE = 0x4;
    private static final int DDS_DXT5 = 0x35545844;
    private static final int DDS_TEXTURE_CAPS = 0x1000;
    private static final int DDS_RESERVED_WORDS = 11;
    private static final int DDS_PIXEL_FORMAT_TAIL_WORDS = 5;
    private static final int DDS_TAIL_WORDS = 4;
    private static final int BLOCK_SIZE = 4;
    private static final int BLOCK_PIXELS = BLOCK_SIZE * BLOCK_SIZE;
    private static final int DXT5_BLOCK_BYTES = 16;
    private static final int ALPHA_INDEX_BITS = 3;
    private static final int ALPHA_STEPS = 7;
    private static final int COLOR_INDEX_BITS = 2;
    private static final int COLOR_STEPS = 3;
    private static final int RED_BITS = 5;
    private static final int GREEN_BITS = 6;
    private static final int GREEN_SHIFT = 5;
    private static final int RED_SHIFT = 11;
    private static final int FIVE_BIT_MASK = 0x1F;
    private static final int SIX_BIT_MASK = 0x3F;
    private static final int FOUR_BIT_MASK = 0xF;
    private static final int CHANNEL_MAXIMUM = 255;

    private static final int ANIM_MAGIC = 0x4D494E41;
    private static final int ANIM_SINGLE_ENTRY_TYPE = 2;
    private static final int ANIM_VERSION = 0x0202;
    private static final int ANIM_LAYER_NAME_COUNT = 10;
    private static final int ANIM_LAYER_NAME_SIZE = 32;
    private static final int ANIM_HEADER_SIZE = 12 + ANIM_LAYER_NAME_COUNT * ANIM_LAYER_NAME_SIZE;
    private static final int ANIM_ENTRY_HEADER_SIZE = 12;
    private static final int ANIM_LAYER_RECORD_SIZE = 12;
    private static final int ANIM_FRAME_SIZE = 16;

    private static final int DDS_GRP_HEADER_SIZE = 8;
    private static final int DDS_GRP_FRAME_HEADER_SIZE = 12;
    private static final int DDS_HEIGHT_OFFSET = 12;
    private static final int DDS_WIDTH_OFFSET = 16;

    /**
     * Encodes SD frames as a GRP file: each frame's opaque rectangle (palette index {@code 0} is transparent) with transparent and literal runs.
     *
     * @param width    the canvas width, from {@code 1} to {@link #MAXIMUM_GRP_SIZE}
     * @param height   the canvas height, from {@code 1} to {@link #MAXIMUM_GRP_SIZE}
     * @param canvases the palette indices of each frame on the full canvas, row-major
     * @return the GRP file contents
     * @throws IllegalArgumentException if the canvas size is out of range or a frame has another size
     */
    public static byte @NotNull [] encodeGrp(int width, int height, @NotNull List<byte @NotNull []> canvases) {
        if (width < 1 || height < 1 || width > MAXIMUM_GRP_SIZE || height > MAXIMUM_GRP_SIZE) {
            throw Exceptions.newIllegalArgumentException("Invalid GRP canvas: {0} by {1}", width, height);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[][] frameData = new byte[canvases.size()][];
        int[][] rectangles = new int[canvases.size()][];

        for (int frame = 0; frame < canvases.size(); frame++) {
            byte[] canvas = canvases.get(frame);

            if (canvas.length != width * height) {
                throw Exceptions.newIllegalArgumentException("GRP frame {0} holds {1} pixels, not {2}.", frame, canvas.length, width * height);
            }

            rectangles[frame] = findOpaqueRectangle(canvas, width, height);
            frameData[frame]  = encodeGrpFrame(canvas, width, rectangles[frame]);
        }

        writeWord(output, canvases.size());
        writeWord(output, width);
        writeWord(output, height);

        int dataOffset = GRP_HEADER_SIZE + canvases.size() * GRP_FRAME_HEADER_SIZE;

        for (int frame = 0; frame < canvases.size(); frame++) {
            for (int value : rectangles[frame]) {
                output.write(value);
            }

            writeInt(output, dataOffset);
            dataOffset += frameData[frame].length;
        }

        for (byte[] data : frameData) {
            output.writeBytes(data);
        }

        return output.toByteArray();
    }

    /**
     * Encodes an RGBA image as a DDS file of one DXT5 level: per 4 by 4 block the alpha between the block's lowest and highest alpha in eight
     * steps and the color between the block's lowest and highest channels in four steps.
     *
     * @param width  the width, a multiple of {@code 4}
     * @param height the height, a multiple of {@code 4}
     * @param rgba   the pixels, {@link #RGBA_BYTES} per pixel, row-major
     * @return the DDS file contents
     * @throws IllegalArgumentException if a side is not a positive multiple of {@code 4} or the pixel array has another size
     */
    public static byte @NotNull [] encodeDxt5(int width, int height, byte @NotNull [] rgba) {
        return encodeDxt5(width, height, rgba, false);
    }

    /**
     * Encodes an RGBA image as a DDS file of one DXT5 level: per 4 by 4 block the alpha between the block's lowest and highest alpha in eight
     * steps and the color between the block's lowest and highest channels in four steps. When alpha is coverage, the color range of a block
     * covers its visible pixels only (all pixels if none is visible), so the transparent pixels around a sprite take no color steps from it; a
     * layer whose alpha holds other data (a normal or depth value) keeps the range of all pixels.
     *
     * @param width         the width, a multiple of {@code 4}
     * @param height        the height, a multiple of {@code 4}
     * @param rgba          the pixels, {@link #RGBA_BYTES} per pixel, row-major
     * @param coverageAlpha whether alpha {@code 0} marks a pixel whose color is not shown
     * @return the DDS file contents
     * @throws IllegalArgumentException if a side is not a positive multiple of {@code 4} or the pixel array has another size
     */
    public static byte @NotNull [] encodeDxt5(int width, int height, byte @NotNull [] rgba, boolean coverageAlpha) {
        if (width <= 0 || height <= 0 || width % BLOCK_SIZE != 0 || height % BLOCK_SIZE != 0 || rgba.length != width * height * RGBA_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid DXT5 image: {0} by {1}, {2} bytes", width, height, rgba.length);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeInt(output, DDS_MAGIC);
        writeInt(output, DDS_HEADER_SIZE);
        writeInt(output, DDS_FLAGS);
        writeInt(output, height);
        writeInt(output, width);
        writeInt(output, width / BLOCK_SIZE * (height / BLOCK_SIZE) * DXT5_BLOCK_BYTES);
        writeInt(output, 0);
        writeInt(output, 1);

        for (int word = 0; word < DDS_RESERVED_WORDS; word++) {
            writeInt(output, 0);
        }

        writeInt(output, DDS_PIXEL_FORMAT_SIZE);
        writeInt(output, DDS_FOUR_CHARACTER_CODE);
        writeInt(output, DDS_DXT5);

        for (int word = 0; word < DDS_PIXEL_FORMAT_TAIL_WORDS; word++) {
            writeInt(output, 0);
        }

        writeInt(output, DDS_TEXTURE_CAPS);

        for (int word = 0; word < DDS_TAIL_WORDS; word++) {
            writeInt(output, 0);
        }

        for (int blockY = 0; blockY < height; blockY += BLOCK_SIZE) {
            for (int blockX = 0; blockX < width; blockX += BLOCK_SIZE) {
                encodeDxt5Block(rgba, width, blockX, blockY, coverageAlpha, output);
            }
        }

        return output.toByteArray();
    }

    /**
     * Encodes a single-entry HD frame file: a canvas, one frame record per frame and one DDS texture per named layer. The canvas and every frame
     * value except the flags are in {@link #ANIM_UNIT_SCALE} units per SD pixel; the textures hold the atlas at {@code scale} texels per SD pixel.
     *
     * @param scale  the texels per SD pixel: {@code 4} for HD, {@code 2} for HD2
     * @param width  the canvas width in units
     * @param height the canvas height in units
     * @param frames the {@link #ANIM_FRAME_VALUES} values of each frame: atlas x, atlas y, canvas x, canvas y, width, height, flags
     * @param layers the DDS file of each layer by layer name ({@code diffuse}, {@code teamcolor}), at most ten, in order
     * @return the ANIM file contents
     * @throws IllegalArgumentException if there are more than ten layers, a frame has another value count or a layer is not a DDS file
     */
    public static byte @NotNull [] encodeAnim(int scale, int width, int height, @NotNull List<int @NotNull []> frames,
                                              @NotNull Map<String, byte @NotNull []> layers) {
        if (layers.size() > ANIM_LAYER_NAME_COUNT) {
            throw Exceptions.newIllegalArgumentException("An ANIM file holds at most {0} layers, not {1}.", ANIM_LAYER_NAME_COUNT, layers.size());
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int frameTable = ANIM_HEADER_SIZE + ANIM_ENTRY_HEADER_SIZE + layers.size() * ANIM_LAYER_RECORD_SIZE;
        int textureOffset = frameTable + frames.size() * ANIM_FRAME_SIZE;

        writeInt(output, ANIM_MAGIC);
        output.write(scale);
        output.write(ANIM_SINGLE_ENTRY_TYPE);
        writeWord(output, ANIM_VERSION);
        writeWord(output, layers.size());
        writeWord(output, 1);

        for (String name : layers.keySet()) {
            byte[] bytes = name.getBytes(StandardCharsets.US_ASCII);

            output.write(bytes, 0, Math.min(bytes.length, ANIM_LAYER_NAME_SIZE - 1));
            output.writeBytes(new byte[ANIM_LAYER_NAME_SIZE - Math.min(bytes.length, ANIM_LAYER_NAME_SIZE - 1)]);
        }

        output.writeBytes(new byte[(ANIM_LAYER_NAME_COUNT - layers.size()) * ANIM_LAYER_NAME_SIZE]);

        writeWord(output, frames.size());
        writeWord(output, 0);
        writeWord(output, width);
        writeWord(output, height);
        writeInt(output, frameTable);

        for (byte[] texture : layers.values()) {
            if (texture.length < DDS_WIDTH_OFFSET + Integer.BYTES || LittleEndianUtils.readInt32(texture, 0) != DDS_MAGIC) {
                throw Exceptions.newIllegalArgumentException("An ANIM layer must be a DDS file.");
            }

            writeInt(output, textureOffset);
            writeInt(output, texture.length);
            writeWord(output, LittleEndianUtils.readInt32(texture, DDS_WIDTH_OFFSET));
            writeWord(output, LittleEndianUtils.readInt32(texture, DDS_HEIGHT_OFFSET));
            textureOffset += texture.length;
        }

        for (int[] frame : frames) {
            if (frame.length != ANIM_FRAME_VALUES) {
                throw Exceptions.newIllegalArgumentException("An ANIM frame has {0} values, not {1}.", ANIM_FRAME_VALUES, frame.length);
            }

            for (int value = 0; value < ANIM_FRAME_VALUES - 1; value++) {
                writeWord(output, frame[value]);
            }

            writeInt(output, frame[ANIM_FRAME_VALUES - 1]);
        }

        for (byte[] texture : layers.values()) {
            output.writeBytes(texture);
        }

        return output.toByteArray();
    }

    /**
     * Encodes DDS textures as a {@code .dds.grp} sheet: the file size, the frame count and the version word {@link #DDS_GRP_SCALED_VERSION} with
     * the texture scale, then per frame a reserved DWORD, its width and height from the DDS header, its size and the DDS file.
     *
     * @param scale    the texels per interface pixel: {@code 1} for SD, {@code 2} for HD2, {@code 4} for HD
     * @param textures the DDS file of each frame
     * @return the sheet file contents
     * @throws IllegalArgumentException if the scale is outside {@code 1..15} or a texture is not a DDS file
     */
    public static byte @NotNull [] encodeDdsGrp(int scale, @NotNull List<byte @NotNull []> textures) {
        if (scale < 1 || scale > FOUR_BIT_MASK) {
            throw Exceptions.newIllegalArgumentException("Invalid texture scale of a DDS sheet: {0}", scale);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int size = DDS_GRP_HEADER_SIZE;

        for (byte[] texture : textures) {
            if (texture.length < DDS_WIDTH_OFFSET + Integer.BYTES || LittleEndianUtils.readInt32(texture, 0) != DDS_MAGIC) {
                throw Exceptions.newIllegalArgumentException("A DDS sheet frame must be a DDS file.");
            }

            size += DDS_GRP_FRAME_HEADER_SIZE + texture.length;
        }

        writeInt(output, size);
        writeWord(output, textures.size());
        writeWord(output, DDS_GRP_SCALED_VERSION | scale);

        for (byte[] texture : textures) {
            writeInt(output, 0);
            writeWord(output, LittleEndianUtils.readInt32(texture, DDS_WIDTH_OFFSET));
            writeWord(output, LittleEndianUtils.readInt32(texture, DDS_HEIGHT_OFFSET));
            writeInt(output, texture.length);
            output.writeBytes(texture);
        }

        return output.toByteArray();
    }

    private static int @NotNull [] findOpaqueRectangle(byte @NotNull [] canvas, int width, int height) {
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (canvas[y * width + x] != 0) {
                    left   = Math.min(left, x);
                    top    = Math.min(top, y);
                    right  = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }

        return right < 0 ? new int[]{0, 0, 0, 0} : new int[]{left, top, right - left + 1, bottom - top + 1};
    }

    private static byte @NotNull [] encodeGrpFrame(byte @NotNull [] canvas, int width, int @NotNull [] rectangle) {
        int left = rectangle[0];
        int top = rectangle[1];
        int frameWidth = rectangle[2];
        int frameHeight = rectangle[3];
        ByteArrayOutputStream lines = new ByteArrayOutputStream();
        int[] lineOffsets = new int[frameHeight];

        for (int row = 0; row < frameHeight; row++) {
            int rowStart = (top + row) * width + left;
            int column = 0;

            lineOffsets[row] = frameHeight * Short.BYTES + lines.size();

            while (column < frameWidth) {
                int run = 0;

                if (canvas[rowStart + column] == 0) {
                    while (column + run < frameWidth && run < GRP_MAXIMUM_TRANSPARENT_RUN && canvas[rowStart + column + run] == 0) {
                        run++;
                    }

                    lines.write(GRP_TRANSPARENT_PACKET | run);
                } else {
                    while (column + run < frameWidth && run < GRP_MAXIMUM_LITERAL_RUN && canvas[rowStart + column + run] != 0) {
                        run++;
                    }

                    lines.write(run);
                    lines.write(canvas, rowStart + column, run);
                }

                column += run;
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        for (int offset : lineOffsets) {
            writeWord(output, offset);
        }

        output.writeBytes(lines.toByteArray());

        return output.toByteArray();
    }

    private static void encodeDxt5Block(byte @NotNull [] rgba, int width, int blockX, int blockY, boolean coverageAlpha,
                                        @NotNull ByteArrayOutputStream output) {
        int[] colors = new int[BLOCK_PIXELS * RGBA_BYTES];
        int minimumAlpha = CHANNEL_MAXIMUM;
        int maximumAlpha = 0;
        int[] minimum = {CHANNEL_MAXIMUM, CHANNEL_MAXIMUM, CHANNEL_MAXIMUM};
        int[] maximum = {0, 0, 0};

        for (int pixel = 0; pixel < BLOCK_PIXELS; pixel++) {
            int source = ((blockY + pixel / BLOCK_SIZE) * width + blockX + pixel % BLOCK_SIZE) * RGBA_BYTES;

            for (int channel = 0; channel < RGBA_BYTES; channel++) {
                colors[pixel * RGBA_BYTES + channel] = rgba[source + channel] & BYTE_MASK;
            }

            minimumAlpha = Math.min(minimumAlpha, colors[pixel * RGBA_BYTES + COLOR_STEPS]);
            maximumAlpha = Math.max(maximumAlpha, colors[pixel * RGBA_BYTES + COLOR_STEPS]);
        }

        // With coverage alpha the color range covers the visible pixels only.
        for (int pixel = 0; pixel < BLOCK_PIXELS; pixel++) {
            if (coverageAlpha && maximumAlpha > 0 && colors[pixel * RGBA_BYTES + COLOR_STEPS] == 0) {
                continue;
            }

            for (int channel = 0; channel < COLOR_STEPS; channel++) {
                minimum[channel] = Math.min(minimum[channel], colors[pixel * RGBA_BYTES + channel]);
                maximum[channel] = Math.max(maximum[channel], colors[pixel * RGBA_BYTES + channel]);
            }
        }

        long alphaIndices = 0;

        for (int pixel = 0; pixel < BLOCK_PIXELS && maximumAlpha > minimumAlpha; pixel++) {
            int step = Math.round((maximumAlpha - colors[pixel * RGBA_BYTES + COLOR_STEPS]) * (float) ALPHA_STEPS / (maximumAlpha - minimumAlpha));
            // The eight-step palette orders maximum (0), minimum (1), then the six steps from maximum to minimum (2..7).
            int index = step == 0 ? 0 : step == ALPHA_STEPS ? 1 : step + 1;

            alphaIndices |= (long) index << (pixel * ALPHA_INDEX_BITS);
        }

        output.write(maximumAlpha);
        output.write(minimumAlpha);

        for (int shift = 0; shift < BLOCK_PIXELS * ALPHA_INDEX_BITS; shift += Byte.SIZE) {
            output.write((int) (alphaIndices >>> shift) & BYTE_MASK);
        }

        int high = packColor(maximum);
        int low = packColor(minimum);
        int indices = 0;

        for (int pixel = 0; pixel < BLOCK_PIXELS && high != low; pixel++) {
            int span = 0;
            int position = 0;

            for (int channel = 0; channel < COLOR_STEPS; channel++) {
                span += maximum[channel] - minimum[channel];
                position += maximum[channel] - colors[pixel * RGBA_BYTES + channel];
            }

            int step = span == 0 ? 0 : Math.clamp(Math.round(position * (float) COLOR_STEPS / span), 0, COLOR_STEPS);
            // The four-step palette orders maximum (0), minimum (1), two thirds (2), one third (3).
            int index = step == 0 ? 0 : step == COLOR_STEPS ? 1 : step + 1;

            indices |= index << (pixel * COLOR_INDEX_BITS);
        }

        writeWord(output, high);
        writeWord(output, low);
        writeInt(output, indices);
    }

    private static int packColor(int @NotNull [] color) {
        return (color[0] >> (Byte.SIZE - RED_BITS) & FIVE_BIT_MASK) << RED_SHIFT |
               (color[1] >> (Byte.SIZE - GREEN_BITS) & SIX_BIT_MASK) << GREEN_SHIFT |
               color[2] >> (Byte.SIZE - RED_BITS) & FIVE_BIT_MASK;
    }

    private static void writeWord(@NotNull ByteArrayOutputStream output, int value) {
        output.write(value & BYTE_MASK);
        output.write(value >>> Byte.SIZE & BYTE_MASK);
    }

    private static void writeInt(@NotNull ByteArrayOutputStream output, int value) {
        writeWord(output, value);
        writeWord(output, value >>> Short.SIZE);
    }

    private ImageFileWriterUtils() {
    }
}
