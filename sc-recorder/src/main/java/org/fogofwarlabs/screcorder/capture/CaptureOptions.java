package org.fogofwarlabs.screcorder.capture;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

/**
 * Configures how a {@link Recorder} observes frames.
 * <p>
 * The default polls the frame counter in a busy loop (a zero polling interval) and samples a selected frame as soon as
 * the counter changes. A positive polling interval trades missed frames for less CPU time.
 * <p>
 * In strict mode every observed frame is written, a frame whose samples cannot be read consistently is written as
 * torn, and the coverage requirements forbid gaps. In gap-tolerant mode an inconsistent frame is dropped: missed and
 * dropped frames are simply absent from the recording.
 */
public final class CaptureOptions {
    private static final long MAXIMUM_POLLING_INTERVAL_NANOSECONDS = 1_000_000_000;
    private static final int MAXIMUM_WARM_UP_PASSES = 100;
    private static final int DEFAULT_WARM_UP_PASSES = 3;

    /**
     * Creates a {@link CaptureOptions} instance for strict busy-wait capture of every frame with the default warm-up.
     *
     * @return the created {@link CaptureOptions} instance
     */
    public static @NotNull CaptureOptions createStrict() {
        return new CaptureOptions(0, CaptureSchedule.getEveryFrame(), false, DEFAULT_WARM_UP_PASSES);
    }

    /**
     * @return the default number of warm-up passes
     */
    public static int getDefaultWarmUpPasses() {
        return DEFAULT_WARM_UP_PASSES;
    }


    private final long pollingIntervalNanoseconds;
    private final @NotNull CaptureSchedule schedule;
    private final boolean gapTolerant;
    private final int warmUpPasses;

    /**
     * Creates a {@link CaptureOptions} instance.
     *
     * @param pollingIntervalNanoseconds the wait between unchanged counter reads; {@code 0} busy-waits
     * @param schedule                   the frame selection
     * @param gapTolerant                whether inconsistent frames are dropped instead of written as torn
     * @param warmUpPasses               the discarded sampling passes run before the first frame is recorded
     * @throws IllegalArgumentException if the polling interval or the warm-up pass count is negative or exceeds its supported maximum
     */
    public CaptureOptions(long pollingIntervalNanoseconds, @NotNull CaptureSchedule schedule, boolean gapTolerant, int warmUpPasses) {
        if (pollingIntervalNanoseconds < 0 || pollingIntervalNanoseconds > MAXIMUM_POLLING_INTERVAL_NANOSECONDS ||
            warmUpPasses < 0 || warmUpPasses > MAXIMUM_WARM_UP_PASSES) {
            throw Exceptions.newIllegalArgumentException("Invalid capture options: polling {0} ns, warm-up {1}",
                                                         pollingIntervalNanoseconds, warmUpPasses);
        }

        this.pollingIntervalNanoseconds = pollingIntervalNanoseconds;
        this.schedule                   = schedule;
        this.gapTolerant                = gapTolerant;
        this.warmUpPasses               = warmUpPasses;
    }

    /**
     * @return the wait between unchanged counter reads; {@code 0} busy-waits
     */
    public long getPollingIntervalNanoseconds() {
        return this.pollingIntervalNanoseconds;
    }

    /**
     * @return the frame selection
     */
    public @NotNull CaptureSchedule getSchedule() {
        return this.schedule;
    }

    /**
     * @return {@code true} if inconsistent frames are dropped instead of written as torn, {@code false} otherwise
     */
    public boolean isGapTolerant() {
        return this.gapTolerant;
    }

    /**
     * @return the discarded sampling passes run before the first frame is recorded
     */
    public int getWarmUpPasses() {
        return this.warmUpPasses;
    }
}
