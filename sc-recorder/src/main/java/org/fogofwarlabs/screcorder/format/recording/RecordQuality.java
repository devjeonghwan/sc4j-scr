package org.fogofwarlabs.screcorder.format.recording;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.format.recording.metadata.RecordMetadata;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.summary.FrameRange;
import org.fogofwarlabs.screcorder.format.recording.summary.RecordSummary;
import org.fogofwarlabs.screcorder.format.recording.summary.Termination;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class RecordQuality {
    private static final int MAXIMUM_MISSING_RANGES = (RecordFormatUtils.MAXIMUM_CHUNK_BYTES - RecordFormatUtils.SUMMARY_PREFIX_BYTES) /
                                                      RecordFormatUtils.MISSING_RANGE_BYTES;

    private final @NotNull RecordMetadata metadata;
    private final @NotNull List<FrameRange> gaps;

    private long frameCount;
    private long firstFrame;
    private long lastFrame;
    private long lastElapsedNanoseconds;
    private long missingFrameCount;
    private long unavailableSampleCount;
    private long tornObservationCount;
    private long unstableFrameCount;

    RecordQuality(@NotNull RecordMetadata metadata) {
        this.metadata = metadata;
        this.gaps     = new ArrayList<>();

        this.frameCount             = 0;
        this.firstFrame             = -1;
        this.lastFrame              = -1;
        this.lastElapsedNanoseconds = -1;
        this.missingFrameCount      = 0;
        this.unavailableSampleCount = 0;
        this.tornObservationCount   = 0;
        this.unstableFrameCount     = 0;
    }

    void validateFrame(long frameNumber, long elapsedNanoseconds) throws IOException {
        if (frameNumber < 0 || elapsedNanoseconds < 0 || frameNumber <= this.lastFrame || elapsedNanoseconds < this.lastElapsedNanoseconds) {
            throw Exceptions.newIOException("Observed frames must increase and elapsed time must not decrease");
        }

        long gapStart = this.findGapStart();

        if (gapStart >= 0 && frameNumber > gapStart && this.gaps.size() >= MAXIMUM_MISSING_RANGES) {
            throw Exceptions.newIOException("Missing frame range summary exceeds format bounds");
        }
    }

    void commit(long frameNumber, long elapsedNanoseconds, @NotNull CaptureConsistency consistency, long unavailableSamples) throws IOException {
        this.validateFrame(frameNumber, elapsedNanoseconds);
        this.validateObservationCounts(unavailableSamples);

        long gapStart = this.findGapStart();

        if (gapStart >= 0 && frameNumber > gapStart) {
            FrameRange range = new FrameRange(gapStart, frameNumber - 1);

            this.gaps.add(range);
            this.missingFrameCount += range.getFrameCount();
        }

        if (this.frameCount == 0) {
            this.firstFrame = frameNumber;
        }

        this.frameCount++;
        this.lastFrame              = frameNumber;
        this.lastElapsedNanoseconds = elapsedNanoseconds;
        this.unavailableSampleCount += unavailableSamples;

        if (consistency == CaptureConsistency.TORN) {
            this.tornObservationCount++;
        }

        if (consistency != CaptureConsistency.MARKER_STABLE) {
            this.unstableFrameCount++;
        }
    }

    void validateObservationCounts(long unavailableSamples) throws IOException {
        if (this.frameCount == Long.MAX_VALUE || unavailableSamples < 0 || this.unavailableSampleCount > Long.MAX_VALUE - unavailableSamples) {
            throw Exceptions.newIOException("Frame or unavailable sample count exceeds supported integer bounds");
        }
    }

    @NotNull RecordSummary summarize(@NotNull Termination termination) throws IOException {
        List<FrameRange> ranges = new ArrayList<>(this.gaps);
        long missingFrames = this.missingFrameCount;
        long expectedFirst = this.metadata.getExpectedFirstFrame();
        long expectedLast = this.metadata.getExpectedLastFrame();
        long trailingStart = expectedFirst;

        if (this.frameCount > 0) {
            trailingStart = this.lastFrame == Long.MAX_VALUE ? -1 : this.lastFrame + 1;
        }

        if (trailingStart >= 0 && trailingStart <= expectedLast) {
            if (trailingStart == 0 && expectedLast == Long.MAX_VALUE) {
                throw Exceptions.newIOException("Missing frame count exceeds supported integer bounds");
            }

            FrameRange trailing = new FrameRange(trailingStart, expectedLast);

            ranges.add(trailing);
            missingFrames = Math.addExact(missingFrames, trailing.getFrameCount());
        }

        if (ranges.size() > MAXIMUM_MISSING_RANGES) {
            throw Exceptions.newIOException("Missing frame range summary exceeds format bounds");
        }

        int requirements = this.metadata.getCoverageRequirements();
        boolean satisfied = termination == Termination.NORMAL &&
                            ((requirements & RecordMetadata.REQUIRE_NO_GAPS) == 0 || missingFrames == 0) &&
                            ((requirements & RecordMetadata.REQUIRE_AVAILABLE_SAMPLES) == 0 || this.unavailableSampleCount == 0) &&
                            ((requirements & RecordMetadata.REQUIRE_MARKER_STABLE) == 0 || this.unstableFrameCount == 0) &&
                            ((requirements & RecordMetadata.REQUIRE_EXPECTED_BOUNDARIES) == 0 ||
                             (expectedFirst >= 0 && expectedLast >= 0 && this.firstFrame >= 0 &&
                              this.firstFrame <= expectedFirst && this.lastFrame >= expectedLast));

        return new RecordSummary(this.frameCount, this.firstFrame, this.lastFrame,
                                 termination, satisfied,
                                 missingFrames, this.unavailableSampleCount, this.tornObservationCount,
                                 ranges);
    }

    private long findGapStart() {
        return this.frameCount == 0 ? this.metadata.getExpectedFirstFrame() : this.lastFrame + 1;
    }
}
