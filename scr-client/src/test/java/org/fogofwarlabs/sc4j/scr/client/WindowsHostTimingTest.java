package org.fogofwarlabs.sc4j.scr.client;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Locale;

/**
 * Checks the actual Windows shared-data and imported-clock boundary.
 */
final class WindowsHostTimingTest {
    private static final @NotNull String OPERATING_SYSTEM_PROPERTY = "os.name";
    private static final @NotNull String ARCHITECTURE_PROPERTY = "os.arch";
    private static final @NotNull String WINDOWS_PREFIX = "Windows";
    private static final @NotNull String AMD64_ARCHITECTURE = "amd64";
    private static final @NotNull String X86_64_ARCHITECTURE = "x86_64";

    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;
    /**
     * The tolerance of the host clock against the measured interval, in milliseconds.
     */
    private static final int CLOCK_TOLERANCE_MILLISECONDS = 1000;
    /**
     * The largest elapsed tick difference of the diagnostic's short branch.
     */
    private static final int DIAGNOSTIC_ELAPSED_LIMIT = 7500;
    /**
     * The smallest elapsed tick difference of the diagnostic's wrapped branch.
     */
    private static final int DIAGNOSTIC_WRAPPED_MINIMUM = 0x0FFFFFFF;

    @Test
    void host_timing_reads_windows_clocks_and_is_unsupported_elsewhere() {
        String architecture = System.getProperty(ARCHITECTURE_PROPERTY).toLowerCase(Locale.ROOT);

        if (!System.getProperty(OPERATING_SYSTEM_PROPERTY).startsWith(WINDOWS_PREFIX) ||
            !(architecture.equals(AMD64_ARCHITECTURE) || architecture.equals(X86_64_ARCHITECTURE))) {
            Assertions.assertThrows(UnsupportedOperationException.class, WindowsHostTiming::new);

            return;
        }

        WindowsHostTiming timing = new WindowsHostTiming();
        int initialHostTick = timing.readHostTicks();
        long initialMeasurement = System.nanoTime();
        int elapsedTicks = timing.readElapsedTicks();
        int finalHostTick = timing.readHostTicks();
        long measurementMilliseconds = (System.nanoTime() - initialMeasurement) / NANOSECONDS_PER_MILLISECOND;

        Assertions.assertTrue(Integer.toUnsignedLong(finalHostTick - initialHostTick) <= measurementMilliseconds + CLOCK_TOLERANCE_MILLISECONDS,
                              "The absolute DWORD clock preserves short elapsed intervals across wraparound.");
        Assertions.assertTrue(Integer.compareUnsigned(elapsedTicks, DIAGNOSTIC_ELAPSED_LIMIT) <= 0 ||
                              Integer.compareUnsigned(elapsedTicks, DIAGNOSTIC_WRAPPED_MINIMUM) >= 0,
                              "The actual host clocks reached the original timing-diagnostic branch.");
    }
}
