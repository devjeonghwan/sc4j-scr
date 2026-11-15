package org.fogofwarlabs.screcorder.format.recording.summary;

import org.fogofwarlabs.screcorder.exception.Exceptions;

/**
 * Stores an inclusive missing frame range.
 */
public final class FrameRange {
    private final long firstFrame;
    private final long lastFrame;

    /**
     * Creates a {@link FrameRange} instance.
     *
     * @param firstFrame the first missing frame
     * @param lastFrame  the last missing frame
     * @throws IllegalArgumentException if the range is negative, empty or covers every frame from {@code 0} through {@link Long#MAX_VALUE}
     */
    public FrameRange(long firstFrame, long lastFrame) {
        if (firstFrame < 0 || lastFrame < firstFrame || (firstFrame == 0 && lastFrame == Long.MAX_VALUE)) {
            throw Exceptions.newIllegalArgumentException("Invalid frame range: {0}..{1}", firstFrame, lastFrame);
        }

        this.firstFrame = firstFrame;
        this.lastFrame  = lastFrame;
    }

    /**
     * @return the first missing frame
     */
    public long getFirstFrame() {
        return this.firstFrame;
    }

    /**
     * @return the last missing frame
     */
    public long getLastFrame() {
        return this.lastFrame;
    }

    /**
     * @return the number of missing frames
     */
    public long getFrameCount() {
        return this.lastFrame - this.firstFrame + 1;
    }
}
