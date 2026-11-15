package org.fogofwarlabs.screcorder.capture;

import java.io.IOException;

/**
 * Waits between memory observations. The caller owns the timer and its resources.
 */
@FunctionalInterface
public interface CaptureTimer {
    /**
     * Waits for the requested duration.
     *
     * @param nanoseconds the duration, with {@code 0} requesting no wait
     * @throws IOException if the wait fails
     */
    void await(long nanoseconds) throws IOException;
}
