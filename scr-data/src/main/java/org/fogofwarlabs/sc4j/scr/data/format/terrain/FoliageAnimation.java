package org.fogofwarlabs.sc4j.scr.data.format.terrain;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The frame extents of the SD foliage animation ({@code tileset/foliage.anim}), the only part foliage population reads.
 * <p>
 * The header is {@code ANIM}, version {@code 0x104}, one layer named {@code diffuse} and the animation count WORD;
 * the entry offsets start at {@code 0x14C}. An entry holds its frame count WORD, a {@code -1} reference WORD and the
 * offset of its 16-byte frames, whose width and height WORDs at offsets 8 and 10 are in quarter pixels.
 */
public final class FoliageAnimation {
    private static final int MAGIC_ANIM = 0x4D494E41;
    private static final int VERSION = 0x104;
    private static final int SINGLE_LAYER_COUNT = 1;
    private static final int LAYER_NAME_DIFF = 0x66666964;
    private static final int LAYER_NAME_USE = 0x00657375;

    private static final int VERSION_OFFSET = 4;
    private static final int LAYER_COUNT_OFFSET = 8;
    private static final int ANIMATION_COUNT_OFFSET = 10;
    private static final int LAYER_NAME_OFFSET = 12;
    private static final int LAYER_NAME_SUFFIX_OFFSET = 16;
    private static final int OFFSET_TABLE = 0x14C;
    private static final int OFFSET_SIZE = Integer.BYTES;
    private static final int MINIMUM_SIZE = 0x10E8;
    private static final int MAXIMUM_ANIMATIONS = 999;

    private static final int ENTRY_SIZE = 24;
    private static final int ENTRY_REFERENCE_OFFSET = 2;
    private static final int ENTRY_FRAMES_OFFSET = 8;
    private static final int FRAME_SIZE = 16;
    private static final int FRAME_WIDTH_OFFSET = 8;
    private static final int FRAME_HEIGHT_OFFSET = 10;
    /**
     * Frame extents are stored in quarter pixels.
     */
    private static final int EXTENT_SCALE = 4;

    /**
     * Creates a {@link FoliageAnimation} instance after validating the animation header.
     *
     * @param data the file contents
     * @return the created {@link FoliageAnimation} instance
     * @throws IllegalArgumentException      if the offset table is truncated or the count exceeds it
     * @throws UnsupportedOperationException if the file is not the single-diffuse SD layout
     */
    public static @NotNull FoliageAnimation parse(byte @NotNull [] data) {
        if (data.length < MINIMUM_SIZE) {
            throw Exceptions.newIllegalArgumentException("The foliage animation lacks its offset table.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        if (buffer.getInt(0) != MAGIC_ANIM || buffer.getInt(VERSION_OFFSET) != VERSION || buffer.getShort(LAYER_COUNT_OFFSET) != SINGLE_LAYER_COUNT ||
            buffer.getInt(LAYER_NAME_OFFSET) != LAYER_NAME_DIFF || buffer.getInt(LAYER_NAME_SUFFIX_OFFSET) != LAYER_NAME_USE) {
            throw Exceptions.newUnsupportedOperationException("Only the established single-diffuse SD foliage animation is represented.");
        }

        int animationCount = Short.toUnsignedInt(buffer.getShort(ANIMATION_COUNT_OFFSET));

        if (animationCount > MAXIMUM_ANIMATIONS) {
            throw Exceptions.newIllegalArgumentException("The animation count exceeds its offset table.");
        }

        return new FoliageAnimation(buffer, data.length, animationCount);
    }


    private final @NotNull ByteBuffer buffer;
    private final int length;
    private final int animationCount;

    private FoliageAnimation(@NotNull ByteBuffer buffer, int length, int animationCount) {
        this.buffer         = buffer;
        this.length         = length;
        this.animationCount = animationCount;
    }

    /**
     * @return the number of animations
     */
    public int getAnimationCount() {
        return this.animationCount;
    }


    /**
     * Reads the width and height of one frame, in pixels.
     *
     * @param animationIndex the animation, below {@link #getAnimationCount()}
     * @param frameIndex     the frame within the animation
     * @return {@code {width, height}}
     * @throws IllegalArgumentException      if the entry or its frames are truncated or the frame is absent
     * @throws UnsupportedOperationException if the entry shares another entry's frames
     */
    public int @NotNull [] readFrameExtent(int animationIndex, int frameIndex) {
        long entryOffset = Integer.toUnsignedLong(this.buffer.getInt(OFFSET_TABLE + animationIndex * OFFSET_SIZE));

        if (entryOffset > this.length - (long) ENTRY_SIZE) {
            throw Exceptions.newIllegalArgumentException("The foliage animation entry is truncated.");
        }

        int frameCount = this.buffer.getShort((int) entryOffset);

        if (this.buffer.getShort((int) entryOffset + ENTRY_REFERENCE_OFFSET) != -1) {
            throw Exceptions.newUnsupportedOperationException("Shared foliage animation entries are not represented.");
        }

        long frameOffset = Integer.toUnsignedLong(this.buffer.getInt((int) entryOffset + ENTRY_FRAMES_OFFSET));

        if (frameIndex < 0 || frameIndex >= frameCount || frameOffset + (long) frameCount * FRAME_SIZE > this.length) {
            throw Exceptions.newIllegalArgumentException("The foliage animation frame vector is truncated.");
        }

        frameOffset += (long) frameIndex * FRAME_SIZE;

        return new int[]{Integer.divideUnsigned(this.buffer.getShort((int) frameOffset + FRAME_WIDTH_OFFSET), EXTENT_SCALE),
                         Integer.divideUnsigned(this.buffer.getShort((int) frameOffset + FRAME_HEIGHT_OFFSET), EXTENT_SCALE)};
    }
}
