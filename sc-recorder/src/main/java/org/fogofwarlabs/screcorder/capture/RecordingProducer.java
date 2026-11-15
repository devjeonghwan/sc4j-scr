package org.fogofwarlabs.screcorder.capture;

import org.fogofwarlabs.screcorder.format.recording.RecordWriter;
import org.fogofwarlabs.screcorder.format.recording.summary.RecordSummary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Produces typed frames through a caller-owned writer and timer.
 */
public interface RecordingProducer {
    /**
     * Records one session after verifying its optional paired replay header.
     * The writer and timer remain owned by the caller.
     *
     * @param writer               the recording destination
     * @param timer                the observation timer
     * @param expectedReplayHeader the paired header, or {@code null} for an attached session
     * @return the completed capture summary
     * @throws IOException if startup, target reads or recording output fail
     */
    @NotNull RecordSummary record(@NotNull RecordWriter writer, @NotNull CaptureTimer timer, byte @Nullable [] expectedReplayHeader) throws IOException;
}
