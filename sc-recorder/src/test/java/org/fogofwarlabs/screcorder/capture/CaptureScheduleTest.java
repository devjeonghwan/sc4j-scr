package org.fogofwarlabs.screcorder.capture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks which observed frames the every-frame, thinned and windowed capture schedules select.
 */
final class CaptureScheduleTest {
    private static final int UNKNOWN_FINAL_FRAME = -1;
    private static final int NO_WRITTEN_FRAME = -1;
    private static final int DENSE_PREFIX = 10;
    private static final int STRIDE = 24;
    private static final int WINDOW_FIRST_FRAME = 100;
    private static final int WINDOW_LAST_FRAME = 200;

    @Test
    void get_every_frame_should_select_each_new_frame() {
        CaptureSchedule schedule = CaptureSchedule.getEveryFrame();

        Assertions.assertTrue(schedule.isSelected(0, UNKNOWN_FINAL_FRAME, NO_WRITTEN_FRAME));
        Assertions.assertTrue(schedule.isSelected(5, UNKNOWN_FINAL_FRAME, 4));
        Assertions.assertFalse(schedule.isSelected(4, UNKNOWN_FINAL_FRAME, 4));
    }

    @Test
    void is_selected_should_select_the_dense_prefix_then_the_first_observed_frame_of_each_stride_period() {
        CaptureSchedule schedule = new CaptureSchedule(0, Long.MAX_VALUE, DENSE_PREFIX, STRIDE);

        Assertions.assertTrue(schedule.isSelected(9, UNKNOWN_FINAL_FRAME, 8));
        Assertions.assertFalse(schedule.isSelected(10, UNKNOWN_FINAL_FRAME, 9));
        Assertions.assertTrue(schedule.isSelected(24, UNKNOWN_FINAL_FRAME, 9));
        Assertions.assertFalse(schedule.isSelected(25, UNKNOWN_FINAL_FRAME, 24));
        // A missed multiple of the stride is replaced by the next observed frame of its period.
        Assertions.assertTrue(schedule.isSelected(50, UNKNOWN_FINAL_FRAME, 24));
        Assertions.assertFalse(schedule.isSelected(51, UNKNOWN_FINAL_FRAME, 50));
    }

    @Test
    void is_selected_should_select_every_frame_from_the_final_expected_frame() {
        CaptureSchedule schedule = new CaptureSchedule(0, Long.MAX_VALUE, 0, STRIDE);

        Assertions.assertTrue(schedule.isSelected(99, 99, 96));
        Assertions.assertTrue(schedule.isSelected(100, 99, 99));
    }

    @Test
    void is_selected_should_select_only_frames_inside_the_window() {
        CaptureSchedule schedule = new CaptureSchedule(WINDOW_FIRST_FRAME, WINDOW_LAST_FRAME, 0, 1);

        Assertions.assertFalse(schedule.isSelected(99, UNKNOWN_FINAL_FRAME, NO_WRITTEN_FRAME));
        Assertions.assertTrue(schedule.isSelected(100, UNKNOWN_FINAL_FRAME, NO_WRITTEN_FRAME));
        Assertions.assertTrue(schedule.isSelected(200, UNKNOWN_FINAL_FRAME, 199));
        Assertions.assertTrue(schedule.isPastWindow(201));
        Assertions.assertFalse(schedule.isSelected(201, UNKNOWN_FINAL_FRAME, 200));
    }

    @Test
    void constructor_should_reject_invalid_schedules() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CaptureSchedule(-1, 10, 0, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CaptureSchedule(10, 9, 0, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CaptureSchedule(0, 10, -1, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new CaptureSchedule(0, 10, 0, 0));
    }
}
