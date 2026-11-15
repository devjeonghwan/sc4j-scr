package org.fogofwarlabs.screcorder.capture.windows;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;

/**
 * Verifies waits through an actual Windows high-resolution timer.
 */
@EnabledOnOs(OS.WINDOWS)
final class WindowsWaitableTimerTest {
    private static final long WAIT_NANOSECONDS = 1_000_000L;
    private static final @NotNull String CLOSED_MESSAGE = "Capture timer is closed";

    @Test
    void await_should_reject_every_duration_after_close() throws IOException {
        WindowsWaitableTimer timer = WindowsWaitableTimer.open();

        timer.await(WAIT_NANOSECONDS);
        timer.await(0);
        timer.close();

        IOException spin = Assertions.assertThrows(IOException.class, () -> timer.await(0));
        IOException wait = Assertions.assertThrows(IOException.class, () -> timer.await(WAIT_NANOSECONDS));

        Assertions.assertEquals(CLOSED_MESSAGE, spin.getMessage());
        Assertions.assertEquals(CLOSED_MESSAGE, wait.getMessage());
    }
}
