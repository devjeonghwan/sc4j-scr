package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Paces replay playback against the host clock and tracks seek requests.
 * <p>
 * Speed only changes how many deterministic game frames run per host second. The recorded game speed supplies the base frame duration; the
 * multipliers extend beyond the original Fastest setting, and the unlimited speed runs as many frames as the per-render budget allows. Seeking
 * replays frames from the current or restarted state because the simulation has no saved snapshots.
 */
public final class ReplayPlayback {
    /**
     * The multiplier of the unlimited speed, which runs frames without host-clock pacing.
     */
    private static final double UNLIMITED_MULTIPLIER = 0;
    private static final double @NotNull [] MULTIPLIERS = {0.25, 0.5, 1, 2, 4, 8, 16, UNLIMITED_MULTIPLIER};
    private static final @NotNull String @NotNull [] LABELS = {"x1/4", "x1/2", "x1", "x2", "x4", "x8", "x16", "MAX"};
    private static final int NORMAL_SPEED_INDEX = 2;

    /**
     * The prefix of a speed label, optional in a parsed value.
     */
    private static final @NotNull String SPEED_PREFIX = "x";

    /**
     * A seek must move more than this many recorded-speed seconds.
     */
    private static final long MINIMUM_SEEK_SECONDS = 1;
    private static final long MILLISECONDS_PER_SECOND = 1000;

    private static final long NO_PENDING_SEEK_TARGET = -1;

    /**
     * @return the normal speed index, one game frame per recorded frame duration
     */
    public static int getNormalSpeedIndex() {
        return NORMAL_SPEED_INDEX;
    }

    /**
     * Parses a speed label such as {@code x2}, {@code 2}, {@code 1/2} or {@code max}.
     *
     * @param value the command-line value
     * @return the speed index
     * @throws IllegalArgumentException if the value names no supported speed
     */
    public static int parseSpeed(@NotNull String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);

        if (normalized.startsWith(SPEED_PREFIX)) {
            normalized = normalized.substring(SPEED_PREFIX.length());
        }

        for (int index = 0; index < LABELS.length; index++) {
            String label = LABELS[index].toLowerCase(Locale.ROOT);

            if (label.equals(normalized) || (label.startsWith(SPEED_PREFIX) && label.substring(SPEED_PREFIX.length()).equals(normalized))) {
                return index;
            }
        }

        throw Exceptions.newIllegalArgumentException("Unsupported playback speed: {0} (use x1/4, x1/2, x1, x2, x4, x8, x16 or max).", value);
    }

    private int speedIndex;
    private double accumulatedSeconds;

    private long seekTarget;
    private boolean seekRestartPending;

    /**
     * Creates a {@link ReplayPlayback} instance without a pending seek.
     *
     * @param speedIndex the initial speed index from {@link #parseSpeed(String)}
     */
    ReplayPlayback(int speedIndex) {
        this.speedIndex         = Math.clamp(speedIndex, 0, MULTIPLIERS.length - 1);
        this.accumulatedSeconds = 0;

        this.seekTarget         = NO_PENDING_SEEK_TARGET;
        this.seekRestartPending = false;
    }

    /**
     * @return the display label of the current speed
     */
    @NotNull String getSpeedLabel() {
        return LABELS[this.speedIndex];
    }

    /**
     * @return {@code true} if frames run without host-clock pacing, {@code false} otherwise
     */
    boolean isUnlimited() {
        return MULTIPLIERS[this.speedIndex] == UNLIMITED_MULTIPLIER;
    }

    /**
     * @return the pending seek target frame, or {@code -1} if no seek is pending
     */
    long getSeekTarget() {
        return this.seekTarget;
    }


    /**
     * Selects the next faster speed, keeping the fastest speed at the end of the range.
     */
    void increaseSpeed() {
        this.speedIndex         = Math.min(this.speedIndex + 1, MULTIPLIERS.length - 1);
        this.accumulatedSeconds = 0;
    }

    /**
     * Selects the next slower speed, keeping the slowest speed at the start of the range.
     */
    void decreaseSpeed() {
        this.speedIndex         = Math.max(this.speedIndex - 1, 0);
        this.accumulatedSeconds = 0;
    }

    /**
     * Converts elapsed host time into the number of game frames due at the current speed.
     * The caller runs as many due frames as its render budget allows and drops the rest, so a slow host lags behind the requested speed
     * instead of building an unbounded backlog.
     *
     * @param elapsedSeconds            the host time since the previous call
     * @param frameDurationMilliseconds the recorded game-speed frame duration
     * @return the number of frames to run, or {@link Integer#MAX_VALUE} at unlimited speed
     */
    int takeDueFrames(double elapsedSeconds, int frameDurationMilliseconds) {
        if (this.isUnlimited()) {
            return Integer.MAX_VALUE;
        }

        double frameSeconds = frameDurationMilliseconds / (double) MILLISECONDS_PER_SECOND / MULTIPLIERS[this.speedIndex];

        this.accumulatedSeconds += elapsedSeconds;

        int dueFrames = (int) Math.floor(this.accumulatedSeconds / frameSeconds);

        this.accumulatedSeconds -= dueFrames * frameSeconds;

        return dueFrames;
    }

    /**
     * Runs at most one live frame per pass, carrying owed time until the lag exceeds the lag limit; then the schedule restarts with one frame due.
     *
     * @param elapsedSeconds            the wall time since the previous pass
     * @param frameDurationMilliseconds the game frame duration
     * @param lagLimitMilliseconds      the lag above which the schedule restarts
     * @return {@code 1} if a frame is due, {@code 0} otherwise
     */
    int takeLiveDueFrame(double elapsedSeconds, int frameDurationMilliseconds, int lagLimitMilliseconds) {
        double frameSeconds = frameDurationMilliseconds / (double) MILLISECONDS_PER_SECOND;

        this.accumulatedSeconds += elapsedSeconds;

        if (this.accumulatedSeconds - frameSeconds > lagLimitMilliseconds / (double) MILLISECONDS_PER_SECOND) {
            this.accumulatedSeconds = frameSeconds;
        }

        if (this.accumulatedSeconds < frameSeconds) {
            return 0;
        }

        this.accumulatedSeconds -= frameSeconds;

        return 1;
    }

    /**
     * Discards partially elapsed frame time, used when playback pauses or restarts.
     */
    void resetClock() {
        this.accumulatedSeconds = 0;
    }

    /**
     * Requests playback to reach a completed frame. Earlier frames restart the replay first.
     *
     * @param targetFrame    the completed frame to reach, clamped at {@code 0}
     * @param completedFrame the currently completed frame
     */
    void requestSeek(long targetFrame, long completedFrame) {
        this.seekTarget         = Math.max(0, targetFrame);
        this.seekRestartPending = this.seekTarget < completedFrame;
    }

    /**
     * Tests whether the seek bar sends its Replay Seek command: no seek is pending and the clamped replay target is more
     * than one recorded-speed second away. The command's run requests the seek ({@link #requestSeek}).
     *
     * @param targetFrame    the requested frame
     * @param completedFrame the currently completed frame
     * @param totalFrames    the replay frame count
     * @param recordedSpeed  the recorded game speed
     * @return {@code true} if the seek bar sends the command, {@code false} otherwise
     */
    boolean canSendReplaySeek(long targetFrame, long completedFrame, long totalFrames, int recordedSpeed) {
        if (this.seekTarget >= 0) {
            return false;
        }

        long target = Math.clamp(targetFrame, 0, totalFrames);
        long milliseconds = Math.abs(target - completedFrame) * GameSpeed.getBaseDuration(recordedSpeed);

        return milliseconds / MILLISECONDS_PER_SECOND > MINIMUM_SEEK_SECONDS;
    }

    /**
     * Consumes the pending restart request of a backward seek.
     *
     * @return {@code true} if the replay must restart before stepping toward the target, {@code false} otherwise
     */
    boolean takeSeekRestart() {
        boolean restart = this.seekRestartPending;

        this.seekRestartPending = false;

        return restart;
    }

    /**
     * Ends the pending seek.
     */
    void finishSeek() {
        this.seekTarget         = NO_PENDING_SEEK_TARGET;
        this.seekRestartPending = false;
        this.accumulatedSeconds = 0;
    }
}
