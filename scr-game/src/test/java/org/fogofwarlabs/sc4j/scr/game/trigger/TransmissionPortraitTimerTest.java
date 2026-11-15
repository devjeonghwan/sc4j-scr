package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks strict signed portrait-timer expiry, rearming and cancellation.
 */
final class TransmissionPortraitTimerTest {
    @Test
    void expire_should_fire_only_after_duration_has_fully_passed() {
        TransmissionPortraitTimer timer = new TransmissionPortraitTimer();

        timer.arm(1000, 30000);

        Assertions.assertFalse(timer.expire(31000));
        Assertions.assertTrue(timer.expire(31001));
        Assertions.assertFalse(timer.isArmed());
        Assertions.assertFalse(timer.expire(40000));
    }

    @Test
    void arm_should_replace_running_timer() {
        TransmissionPortraitTimer timer = new TransmissionPortraitTimer();

        timer.arm(0, 3000);
        timer.arm(2000, 3000);

        Assertions.assertFalse(timer.expire(3001));
        Assertions.assertTrue(timer.expire(5001));
    }

    @Test
    void expire_should_compare_as_signed_dword_across_clock_wrap() {
        TransmissionPortraitTimer timer = new TransmissionPortraitTimer();

        timer.arm(Integer.MAX_VALUE - 10, 20);

        Assertions.assertFalse(timer.expire(Integer.MAX_VALUE));
        Assertions.assertTrue(timer.expire(Integer.MIN_VALUE + 10));
    }

    @Test
    void canceled_timer_does_not_fire() {
        TransmissionPortraitTimer timer = new TransmissionPortraitTimer();

        timer.arm(0, 10);
        timer.cancel();

        Assertions.assertFalse(timer.expire(100));
    }
}
