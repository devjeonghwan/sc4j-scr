package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Formats the countdowns, the optional game-time display and the elapsed times of the replay labels, the score screen and the replay information.
 */
public final class TimerTextUtils {
    /**
     * The seconds of one minute.
     */
    private static final int SECONDS_PER_MINUTE = 60;
    /**
     * The minutes of one hour.
     */
    private static final int MINUTES_PER_HOUR = 60;
    /**
     * The seconds of one hour.
     */
    private static final int SECONDS_PER_HOUR = 3600;
    /**
     * The hours of one day.
     */
    private static final int HOURS_PER_DAY = 24;
    /**
     * The seconds of one day.
     */
    private static final int SECONDS_PER_DAY = 86400;
    /**
     * The milliseconds of one second.
     */
    private static final int MILLISECONDS_PER_SECOND = 1000;

    /**
     * The countdown format below one hour (minutes and seconds).
     */
    private static final @NotNull String COUNTDOWN_FORMAT = "%d:%02d";
    /**
     * The countdown format from one hour (hours, minutes and seconds).
     */
    private static final @NotNull String COUNTDOWN_HOURS_FORMAT = "%d:%02d:%02d";
    /**
     * The game time format below one hour (two-digit minutes and seconds).
     */
    private static final @NotNull String GAME_TIME_FORMAT = "%02d:%02d";
    /**
     * The game time format from one hour (two-digit hours, minutes and seconds).
     */
    private static final @NotNull String GAME_TIME_HOURS_FORMAT = "%02d:%02d:%02d";
    /**
     * The game time format from one day (days, then two-digit hours, minutes and seconds).
     */
    private static final @NotNull String GAME_TIME_DAYS_FORMAT = "%d:%02d:%02d:%02d";
    /**
     * The elapsed time format below one hour (two-digit minutes and seconds).
     */
    private static final @NotNull String ELAPSED_TIME_FORMAT = "%02d:%02d";
    /**
     * The elapsed time format from one hour (hours, then two-digit minutes and seconds).
     */
    private static final @NotNull String ELAPSED_TIME_HOURS_FORMAT = "%d:%02d:%02d";

    /**
     * Formats the countdown as minutes and seconds, adding hours from one hour.
     *
     * @param seconds the remaining seconds, {@code 0} through {@code 359999}
     * @return the text
     */
    public static @NotNull String formatCountdown(int seconds) {
        int remainder = seconds % SECONDS_PER_MINUTE;

        if (seconds < SECONDS_PER_HOUR) {
            return String.format(Locale.ROOT, COUNTDOWN_FORMAT, seconds / SECONDS_PER_MINUTE, remainder);
        }

        return String.format(Locale.ROOT,
                             COUNTDOWN_HOURS_FORMAT,
                             seconds / SECONDS_PER_HOUR, seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE, remainder);
    }


    /**
     * Formats the frame counter using the Fastest duration, independent of the current game speed. The millisecond product wraps as an unsigned
     * {@code int}. The result adds hours from one hour and days from one day.
     *
     * @param frames the game frame counter
     * @return the text
     */
    public static @NotNull String formatGameTime(int frames) {
        int seconds = calculateElapsedSeconds(frames, GameSpeed.getBaseDuration(GameSpeed.FASTEST));
        int days = Integer.divideUnsigned(seconds, SECONDS_PER_DAY);
        int hours = Integer.divideUnsigned(seconds, SECONDS_PER_HOUR) % HOURS_PER_DAY;
        int minutes = Integer.divideUnsigned(seconds, SECONDS_PER_MINUTE) % MINUTES_PER_HOUR;
        int remainder = Integer.remainderUnsigned(seconds, SECONDS_PER_MINUTE);

        if (days >= 1) {
            return String.format(Locale.ROOT, GAME_TIME_DAYS_FORMAT, days, hours, minutes, remainder);
        }

        if (hours >= 1) {
            return String.format(Locale.ROOT, GAME_TIME_HOURS_FORMAT, hours, minutes, remainder);
        }

        return String.format(Locale.ROOT, GAME_TIME_FORMAT, minutes, remainder);
    }


    /**
     * Calculates the elapsed seconds of a frame count: the millisecond product wraps as an unsigned {@code int}, and the division into seconds is
     * unsigned.
     *
     * @param frames      the frame count
     * @param framePeriod the frame duration in milliseconds
     * @return the elapsed seconds, an unsigned {@code int}
     */
    public static int calculateElapsedSeconds(int frames, int framePeriod) {
        return Integer.divideUnsigned(frames * framePeriod, MILLISECONDS_PER_SECOND);
    }

    /**
     * Formats an elapsed time as two-digit minutes and seconds, adding the unpadded hours from one hour.
     *
     * @param seconds the elapsed seconds, an unsigned {@code int}
     * @return the text
     */
    public static @NotNull String formatElapsedTime(int seconds) {
        int hours = Integer.divideUnsigned(seconds, SECONDS_PER_HOUR);
        int minutes = Integer.divideUnsigned(seconds, SECONDS_PER_MINUTE) % MINUTES_PER_HOUR;
        int remainder = Integer.remainderUnsigned(seconds, SECONDS_PER_MINUTE);

        if (hours != 0) {
            return String.format(Locale.ROOT, ELAPSED_TIME_HOURS_FORMAT, hours, minutes, remainder);
        }

        return String.format(Locale.ROOT, ELAPSED_TIME_FORMAT, minutes, remainder);
    }


    private TimerTextUtils() {
    }
}
