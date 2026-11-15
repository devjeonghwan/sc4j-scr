package org.fogofwarlabs.screcorder.sample;

import org.fogofwarlabs.screcorder.capture.MemoryReader;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serves capture reads from byte ranges allocated at fixed addresses, so that samplers read a prepared game memory image.
 */
public final class RangeMemoryScenario implements MemoryReader {
    private final @NotNull TreeMap<Long, byte[]> ranges;

    /**
     * Creates a {@link RangeMemoryScenario} instance without allocated ranges.
     */
    public RangeMemoryScenario() {
        this.ranges = new TreeMap<>();
    }


    /**
     * Reads the entire requested range.
     *
     * @param address     the target address
     * @param destination the destination storage
     * @param offset      the destination offset
     * @param length      the byte count
     * @throws IOException if the entire range cannot be read
     */
    @Override
    public void read(long address, byte @NotNull [] destination, int offset, int length) throws IOException {
        Map.Entry<Long, byte[]> range = this.ranges.floorEntry(address);

        if (range == null || address - range.getKey() > range.getValue().length - length) {
            throw Exceptions.newIOException("Unmapped fixture memory: {0}", address);
        }

        System.arraycopy(range.getValue(), (int) (address - range.getKey()), destination, offset, length);
    }

    /**
     * Allocates a zero-filled range that later reads at its addresses observe.
     *
     * @param address the first address of the range
     * @param bytes   the range length
     * @return the range storage, which the caller fills
     */
    public byte @NotNull [] allocateRange(long address, int bytes) {
        byte[] storage = new byte[bytes];

        this.ranges.put(address, storage);

        return storage;
    }
}
