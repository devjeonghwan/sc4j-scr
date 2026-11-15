package org.fogofwarlabs.screcorder.capture;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Provides read-only memory and lifecycle observations for one capture target.
 * The caller owns the target and its native resources.
 */
public interface RecordingTarget extends MemoryReader {
    /**
     * @return the total number of bytes read from this target
     */
    long getBytesRead();

    /**
     * @return {@code true} if the target is running, {@code false} otherwise
     * @throws IOException if target termination cannot be queried
     */
    boolean isAlive() throws IOException;

    /**
     * Reads an unsigned little-endian integer without allocating per read.
     *
     * @param address the target address
     * @param size    the integer width in bytes
     * @return the integer bits
     * @throws IOException if the integer cannot be read
     */
    long readInteger(long address, int size) throws IOException;

    /**
     * Lists the thread environment block addresses of the target's threads. An in-memory target has no threads and lists none.
     *
     * @return the immutable addresses in thread creation order, the initial thread first
     * @throws IOException if the threads of the target cannot be listed
     */
    default @NotNull List<Long> readThreadEnvironmentAddresses() throws IOException {
        return List.of();
    }
}
