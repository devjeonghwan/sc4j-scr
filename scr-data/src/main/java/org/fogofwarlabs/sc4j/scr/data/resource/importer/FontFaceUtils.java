package org.fogofwarlabs.sc4j.scr.data.resource.importer;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.resource.graphics.PixelLayer;
import org.fogofwarlabs.sc4j.engine.resource.text.FontFace;
import org.fogofwarlabs.sc4j.engine.resource.text.FontGlyph;
import org.fogofwarlabs.sc4j.scr.data.format.image.DdsFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Imports an SC:R game font ({@code font/<name>.fnt} with its distance-field atlas {@code font/<name>.DDS}) as an engine
 * {@link FontFace}:
 * <ul>
 *     <li>the line height is the {@code .fnt} maximum height;</li>
 *     <li>the glyph image is the atlas as {@link org.fogofwarlabs.sc4j.engine.resource.graphics.PixelFormat#ALPHA_8}
 *     coverage: the distance in the red channel through a smoothstep around the edge value {@code 180 / 255};</li>
 *     <li>each glyph record of a character from {@code 32} on is one glyph at its atlas rectangle; it advances
 *     {@code xoff + width}, a space half the maximum width; a tab glyph without pixels advances twice the maximum
 *     width.</li>
 * </ul>
 */
public final class FontFaceUtils {
    private static final byte @NotNull [] SIGNATURE = {'F', 'O', 'N', 'T'};
    private static final int HEADER_SIZE = 12;
    private static final int GLYPH_COUNT_OFFSET = 4;
    private static final int FIRST_CHARACTER_OFFSET = 8;
    private static final int MAXIMUM_WIDTH_OFFSET = 10;
    private static final int MAXIMUM_HEIGHT_OFFSET = 11;
    private static final int GLYPH_RECORD_SIZE = 8;
    private static final int GLYPH_OFFSET_X = 0;
    private static final int GLYPH_OFFSET_Y = 1;
    private static final int GLYPH_WIDTH = 2;
    private static final int GLYPH_HEIGHT = 3;
    private static final int GLYPH_ATLAS_X = 4;
    private static final int GLYPH_ATLAS_Y = 6;
    private static final int BYTE_MASK = 0xFF;

    private static final char TAB = '\t';
    private static final char SPACE = ' ';
    private static final int TAB_ADVANCE_FACTOR = 2;

    /**
     * The RGBA layout of a decoded atlas pixel.
     */
    private static final int RGBA_BYTES = 4;
    private static final float CHANNEL_MAXIMUM = 255f;
    /**
     * The distance-field edge, its smoothing half width and the smoothstep polynomial {@code t * t * (3 - 2 * t)}.
     */
    private static final float DISTANCE_EDGE = 180 / 255f;
    private static final float DISTANCE_SMOOTHING = 1 / 16f;
    private static final int SMOOTHSTEP_CONSTANT = 3;
    private static final int SMOOTHSTEP_FACTOR = 2;

    /**
     * Creates the font face of a game font.
     *
     * @param name  the font name, for the error messages
     * @param data  the {@code .fnt} file
     * @param atlas the distance-field atlas
     * @return the font face
     * @throws IOException if the {@code .fnt} file is not a font or a glyph leaves the atlas
     */
    public static @NotNull FontFace createFont(@NotNull String name, byte @NotNull [] data, @NotNull DdsFile atlas) throws IOException {
        if (data.length < HEADER_SIZE || !Arrays.equals(data, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length)) {
            throw Exceptions.newIOException("Not a FONT file: {0}", name);
        }

        int glyphCount = LittleEndianUtils.readUnsigned16(data, GLYPH_COUNT_OFFSET);
        int firstCharacter = data[FIRST_CHARACTER_OFFSET] & BYTE_MASK;
        int maximumWidth = data[MAXIMUM_WIDTH_OFFSET] & BYTE_MASK;
        int maximumHeight = data[MAXIMUM_HEIGHT_OFFSET] & BYTE_MASK;
        List<FontGlyph> glyphs = new ArrayList<>();

        glyphs.add(new FontGlyph(TAB, TAB_ADVANCE_FACTOR * maximumWidth, 0, 0, 0, 0, 0, 0));

        for (int glyph = Math.max(0, SPACE - firstCharacter); glyph < glyphCount; glyph++) {
            int record = HEADER_SIZE + glyph * GLYPH_RECORD_SIZE;

            if (record + GLYPH_RECORD_SIZE > data.length) {
                break;
            }

            int character = firstCharacter + glyph;
            int offsetX = data[record + GLYPH_OFFSET_X] & BYTE_MASK;
            int width = data[record + GLYPH_WIDTH] & BYTE_MASK;
            int advance = character == SPACE ? maximumWidth >> 1 : offsetX + width;

            glyphs.add(new FontGlyph(character, advance,
                                     offsetX, data[record + GLYPH_OFFSET_Y] & BYTE_MASK, width, data[record + GLYPH_HEIGHT] & BYTE_MASK,
                                     LittleEndianUtils.readUnsigned16(data, record + GLYPH_ATLAS_X),
                                     LittleEndianUtils.readUnsigned16(data, record + GLYPH_ATLAS_Y)));
        }

        byte[] rgbaPixels = atlas.decodeRgba();
        byte[] coverage = new byte[atlas.getWidth() * atlas.getHeight()];
        float low = DISTANCE_EDGE - DISTANCE_SMOOTHING;
        float high = DISTANCE_EDGE + DISTANCE_SMOOTHING;

        for (int pixel = 0; pixel < coverage.length; pixel++) {
            float distance = (rgbaPixels[pixel * RGBA_BYTES] & BYTE_MASK) / CHANNEL_MAXIMUM;
            float smoothingFactor = Math.clamp((distance - low) / (high - low), 0f, 1f);

            coverage[pixel] = (byte) Math.round(smoothingFactor * smoothingFactor * (SMOOTHSTEP_CONSTANT - SMOOTHSTEP_FACTOR * smoothingFactor) *
                                                CHANNEL_MAXIMUM);
        }

        try {
            return new FontFace(maximumHeight, atlas.getWidth(), PixelLayer.ofAlpha(coverage), glyphs);
        } catch (IllegalArgumentException exception) {
            throw Exceptions.newIOException(exception, "Invalid font {0}: {1}", name, exception.getMessage());
        }
    }

    private FontFaceUtils() {
    }
}
