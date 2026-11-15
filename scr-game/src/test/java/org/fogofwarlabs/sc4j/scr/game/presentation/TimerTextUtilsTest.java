package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the countdown, game-time and elapsed-time text.
 */
final class TimerTextUtilsTest {
    /**
     * Frames of {@code 42} ms that make one minute and one second ({@code 61026} ms).
     */
    private static final int ONE_MINUTE_ONE_SECOND_FRAMES = 1453;
    /**
     * Frames of {@code 42} ms past one hour ({@code 3600030} ms).
     */
    private static final int ONE_HOUR_FRAMES = 85715;
    /**
     * Frames of {@code 42} ms past one day and one hour ({@code 90000006} ms).
     */
    private static final int DAY_AND_HOUR_FRAMES = 2142858;
    /**
     * Frames of {@code 42} ms whose millisecond product {@code 4294967334} wraps to {@code 38} in {@code 32} bits.
     */
    private static final int WRAPPING_FRAMES = 102261127;
    /**
     * The seconds of that wrapped millisecond product.
     */
    private static final int WRAPPED_SECONDS = 0;
    /**
     * Frames of {@code 42} ms whose millisecond product {@code 3000000024} has the sign bit set.
     */
    private static final int SIGN_BIT_FRAMES = 71428572;
    /**
     * The seconds of that sign-bit millisecond product.
     */
    private static final int SIGN_BIT_SECONDS = 3000000;
    /**
     * The largest unsigned {@code 32}-bit second count: {@code 1193046} hours, {@code 28} minutes and {@code 15} seconds.
     */
    private static final int MAXIMUM_SECONDS = -1;
    /**
     * The last second of the first minute.
     */
    private static final int LAST_SECOND_OF_MINUTE = 59;
    /**
     * One minute and one second.
     */
    private static final int ONE_MINUTE_ONE_SECOND = 61;
    /**
     * The last second of the first hour.
     */
    private static final int LAST_SECOND_OF_HOUR = 3599;
    /**
     * One hour, in seconds.
     */
    private static final int ONE_HOUR_SECONDS = 3600;
    /**
     * One day, one hour, one minute and one second, in seconds.
     */
    private static final int DAY_HOUR_MINUTE_SECOND = 90061;
    /**
     * The last second of the first {@code 100} hours.
     */
    private static final int LAST_SECOND_OF_HUNDRED_HOURS = 359999;

    @Test
    void format_countdown_should_show_minutes_or_hours() {
        Assertions.assertEquals("0:00", TimerTextUtils.formatCountdown(0));
        Assertions.assertEquals("0:59", TimerTextUtils.formatCountdown(LAST_SECOND_OF_MINUTE));
        Assertions.assertEquals("59:59", TimerTextUtils.formatCountdown(LAST_SECOND_OF_HOUR));
        Assertions.assertEquals("1:00:00", TimerTextUtils.formatCountdown(ONE_HOUR_SECONDS));
        Assertions.assertEquals("99:59:59", TimerTextUtils.formatCountdown(LAST_SECOND_OF_HUNDRED_HOURS));
    }

    @Test
    void format_game_time_should_use_fastest_frames_and_two_digit_fields() {
        Assertions.assertEquals("00:00", TimerTextUtils.formatGameTime(0));
        Assertions.assertEquals("01:01", TimerTextUtils.formatGameTime(ONE_MINUTE_ONE_SECOND_FRAMES));
        Assertions.assertEquals("01:00:00", TimerTextUtils.formatGameTime(ONE_HOUR_FRAMES));
        Assertions.assertEquals("1:01:00:00", TimerTextUtils.formatGameTime(DAY_AND_HOUR_FRAMES));
    }

    @Test
    void format_elapsed_time_should_show_two_digit_minutes_or_unpadded_hours() {
        Assertions.assertEquals("00:00", TimerTextUtils.formatElapsedTime(0));
        Assertions.assertEquals("01:01", TimerTextUtils.formatElapsedTime(ONE_MINUTE_ONE_SECOND));
        Assertions.assertEquals("59:59", TimerTextUtils.formatElapsedTime(LAST_SECOND_OF_HOUR));
        Assertions.assertEquals("1:00:00", TimerTextUtils.formatElapsedTime(ONE_HOUR_SECONDS));
        Assertions.assertEquals("25:01:01", TimerTextUtils.formatElapsedTime(DAY_HOUR_MINUTE_SECOND));
    }

    @Test
    void format_elapsed_time_should_read_the_seconds_as_unsigned() {
        Assertions.assertEquals("1193046:28:15", TimerTextUtils.formatElapsedTime(MAXIMUM_SECONDS));
    }

    @Test
    void calculate_elapsed_seconds_should_wrap_the_millisecond_product_and_divide_unsigned() {
        Assertions.assertEquals(ONE_MINUTE_ONE_SECOND,
                                TimerTextUtils.calculateElapsedSeconds(ONE_MINUTE_ONE_SECOND_FRAMES, GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(WRAPPED_SECONDS, TimerTextUtils.calculateElapsedSeconds(WRAPPING_FRAMES, GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(SIGN_BIT_SECONDS, TimerTextUtils.calculateElapsedSeconds(SIGN_BIT_FRAMES, GameSpeed.FASTEST_DURATION));
    }
}
