package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Reads DDS frame container metadata and parses embedded textures on demand while borrowing the source array.
 */
public final class DdsGrpFile {
    private static final int HEADER_SIZE = 8;
    private static final int FRAME_COUNT_OFFSET = 4;
    private static final int VERSION_OFFSET = 6;

    private static final int FRAME_HEADER_SIZE = 12;
    private static final int FRAME_WIDTH_OFFSET = 4;
    private static final int FRAME_HEIGHT_OFFSET = 6;
    private static final int FRAME_SIZE_OFFSET = 8;

    /**
     * Creates a {@link DdsGrpFile} instance from a DDS frame container.
     *
     * @param data the container content
     * @return the created {@link DdsGrpFile} instance
     * @throws IOException if the header or a frame record exceeds the content
     */
    public static @NotNull DdsGrpFile parse(byte @NotNull [] data) throws IOException {
        if (data.length < HEADER_SIZE) {
            throw Exceptions.newIOException("DDS container is shorter than its header.");
        }

        int declaredSize = LittleEndianUtils.readInt32(data, 0);
        int frameCount = LittleEndianUtils.readUnsigned16(data, FRAME_COUNT_OFFSET);
        int version = LittleEndianUtils.readUnsigned16(data, VERSION_OFFSET);

        if (declaredSize != data.length) {
            throw Exceptions.newIOException("DDS container size {0} differs from the content length {1}", declaredSize, data.length);
        }

        int[] offsets = new int[frameCount];
        int[] sizes = new int[frameCount];
        int[] widths = new int[frameCount];
        int[] heights = new int[frameCount];
        long position = HEADER_SIZE;

        for (int frame = 0; frame < frameCount; frame++) {
            if (position + FRAME_HEADER_SIZE > data.length) {
                throw Exceptions.newIOException("DDS container frame {0} header exceeds the content.", frame);
            }

            int start = (int) position;

            widths[frame]  = LittleEndianUtils.readUnsigned16(data, start + FRAME_WIDTH_OFFSET);
            heights[frame] = LittleEndianUtils.readUnsigned16(data, start + FRAME_HEIGHT_OFFSET);
            sizes[frame]   = LittleEndianUtils.readInt32(data, start + FRAME_SIZE_OFFSET);
            offsets[frame] = start + FRAME_HEADER_SIZE;
            position       = (long) offsets[frame] + Integer.toUnsignedLong(sizes[frame]);

            if (sizes[frame] < 0 || position > data.length) {
                throw Exceptions.newIOException("DDS container frame {0} exceeds the content.", frame);
            }
        }

        return new DdsGrpFile(data, version, offsets, sizes, widths, heights);
    }


    private final byte @NotNull [] data;
    private final int version;

    private final int @NotNull [] offsets;
    private final int @NotNull [] sizes;

    private final int @NotNull [] widths;
    private final int @NotNull [] heights;

    private DdsGrpFile(byte @NotNull [] data, int version,
                       int @NotNull [] offsets, int @NotNull [] sizes,
                       int @NotNull [] widths, int @NotNull [] heights) {
        this.data    = data;
        this.version = version;

        this.offsets = offsets;
        this.sizes   = sizes;

        this.widths  = widths;
        this.heights = heights;
    }

    /**
     * @return the container version word
     */
    public int getVersion() {
        return this.version;
    }

    /**
     * @return the number of frames
     */
    public int getFrameCount() {
        return this.offsets.length;
    }

    /**
     * @param frameIndex the frame index
     * @return the frame width recorded in the container
     */
    public int getFrameWidth(int frameIndex) {
        return this.widths[frameIndex];
    }

    /**
     * @param frameIndex the frame index
     * @return the frame height recorded in the container
     */
    public int getFrameHeight(int frameIndex) {
        return this.heights[frameIndex];
    }

    /**
     * Parses one frame's DDS texture.
     *
     * @param frameIndex the frame index
     * @return the parsed {@link DdsFile} instance borrowing this container's content
     * @throws IOException               if the embedded DDS file is invalid
     * @throws IndexOutOfBoundsException if the frame index is outside the container
     */
    public @NotNull DdsFile getFrame(int frameIndex) throws IOException {
        if (frameIndex < 0 || frameIndex >= this.offsets.length) {
            throw Exceptions.newIndexOutOfBoundsException("Invalid DDS container frame: {0}", frameIndex);
        }

        return DdsFile.parse(this.data, this.offsets[frameIndex], this.sizes[frameIndex]);
    }
}
