package org.fogofwarlabs.sc4j.scr.data.format.terrain;

import org.fogofwarlabs.sc4j.base.binary.BigEndianUtils;
import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

/**
 * Reads a tileset water-mask table mapping megatiles to mask frames.
 */
public final class TerrainMaskTable {
    private static final int MAGIC = 0x4B534D54;
    private static final int HEADER_SIZE = 8;
    private static final int COUNT_OFFSET = 6;
    private static final int RECORD_SIZE = 4;
    private static final int RECORD_FRAME_OFFSET = 2;

    /**
     * Creates a {@link TerrainMaskTable} instance from {@code .tmsk} content.
     *
     * @param data          the file content
     * @param megatileCount the number of VX4 megatiles of the tileset
     * @return the created {@link TerrainMaskTable} instance
     * @throws IOException if the header is invalid or a record exceeds the content or the megatile range
     */
    public static @NotNull TerrainMaskTable parse(byte @NotNull [] data, int megatileCount) throws IOException {
        if (data.length < HEADER_SIZE || BigEndianUtils.readInt32(data, 0) != MAGIC) {
            throw Exceptions.newIOException("Missing TMSK header.");
        }

        int recordCount = LittleEndianUtils.readUnsigned16(data, COUNT_OFFSET);

        if (HEADER_SIZE + recordCount * (long) RECORD_SIZE > data.length) {
            throw Exceptions.newIOException("TMSK records exceed the content.");
        }

        int[] frames = new int[megatileCount];

        Arrays.fill(frames, -1);

        for (int record = 0; record < recordCount; record++) {
            int megatile = LittleEndianUtils.readUnsigned16(data, HEADER_SIZE + record * RECORD_SIZE);

            if (megatile >= megatileCount) {
                throw Exceptions.newIOException("TMSK megatile {0} is outside the tileset.", megatile);
            }

            frames[megatile] = LittleEndianUtils.readUnsigned16(data, HEADER_SIZE + RECORD_FRAME_OFFSET + record * RECORD_SIZE);
        }

        return new TerrainMaskTable(frames, recordCount);
    }


    private final int @NotNull [] frames;
    private final int recordCount;

    private TerrainMaskTable(int @NotNull [] frames, int recordCount) {
        this.frames      = frames;
        this.recordCount = recordCount;
    }

    /**
     * @return the number of records
     */
    public int getRecordCount() {
        return this.recordCount;
    }

    /**
     * @param megatile the VX4 megatile index
     * @return the mask frame, or {@code -1} when the megatile has no water mask
     */
    public int getMaskFrame(int megatile) {
        return megatile >= 0 && megatile < this.frames.length ? this.frames[megatile] : -1;
    }
}
