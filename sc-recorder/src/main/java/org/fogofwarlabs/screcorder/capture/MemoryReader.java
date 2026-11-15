package org.fogofwarlabs.screcorder.capture;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Reads bytes from a capture target into caller-owned storage.
 */
public interface MemoryReader {
    /**
     * Reads the entire requested range.
     *
     * @param address     the target address
     * @param destination the destination storage
     * @param offset      the destination offset
     * @param length      the byte count
     * @throws IOException if the entire range cannot be read
     */
    void read(long address, byte @NotNull [] destination, int offset, int length) throws IOException;
}
