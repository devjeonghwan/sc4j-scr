package org.fogofwarlabs.screcorder.format.recording.summary;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Stores validated termination and collection quality totals.
 */
public final class RecordSummary {
    private final long frameCount;
    private final long firstFrame;
    private final long lastFrame;

    private final @NotNull Termination termination;
    private final boolean requirementsSatisfied;

    private final long missingFrameCount;
    private final long unavailableSampleCount;
    private final long tornObservationCount;

    private final @NotNull List<FrameRange> missingRanges;

    /**
     * Creates a {@link RecordSummary} instance from validated session totals.
     *
     * @param frameCount             the committed frame count
     * @param firstFrame             the first observed frame, or {@code -1} for an empty session
     * @param lastFrame              the last observed frame, or {@code -1} for an empty session
     * @param termination            the termination reason
     * @param requirementsSatisfied  whether normal termination and configured requirements passed
     * @param missingFrameCount      the missing frame count
     * @param unavailableSampleCount the unavailable observation count
     * @param tornObservationCount   the torn frame observation count
     * @param missingRanges          the ordered, coalesced missing ranges
     */
    public RecordSummary(long frameCount, long firstFrame, long lastFrame,
                         @NotNull Termination termination, boolean requirementsSatisfied,
                         long missingFrameCount, long unavailableSampleCount, long tornObservationCount,
                         @NotNull List<FrameRange> missingRanges) {
        this.frameCount = frameCount;
        this.firstFrame = firstFrame;
        this.lastFrame  = lastFrame;

        this.termination           = termination;
        this.requirementsSatisfied = requirementsSatisfied;

        this.missingFrameCount      = missingFrameCount;
        this.unavailableSampleCount = unavailableSampleCount;
        this.tornObservationCount   = tornObservationCount;

        this.missingRanges = List.copyOf(missingRanges);
    }

    /**
     * @return the committed frame count
     */
    public long getFrameCount() {
        return this.frameCount;
    }

    /**
     * @return the first observed frame, or {@code -1} for an empty session
     */
    public long getFirstFrame() {
        return this.firstFrame;
    }

    /**
     * @return the last observed frame, or {@code -1} for an empty session
     */
    public long getLastFrame() {
        return this.lastFrame;
    }

    /**
     * @return the termination reason
     */
    public @NotNull Termination getTermination() {
        return this.termination;
    }

    /**
     * @return {@code true} if normal termination and configured requirements passed, {@code false} otherwise
     */
    public boolean isRequirementsSatisfied() {
        return this.requirementsSatisfied;
    }

    /**
     * @return the number of missing frames within observed or known expected boundaries
     */
    public long getMissingFrameCount() {
        return this.missingFrameCount;
    }

    /**
     * @return the unavailable sample observation count
     */
    public long getUnavailableSampleCount() {
        return this.unavailableSampleCount;
    }

    /**
     * @return the torn frame observation count
     */
    public long getTornObservationCount() {
        return this.tornObservationCount;
    }

    /**
     * @return the ordered, coalesced missing ranges
     */
    public @NotNull List<FrameRange> getMissingRanges() {
        return this.missingRanges;
    }
}
