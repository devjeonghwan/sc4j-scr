package org.fogofwarlabs.sc4j.scr.client.ui.console;

/**
 * The one monotonic millisecond clock of the interface: timers, double clicks, message display, animations.
 * <p>
 * Only differences between two readings are meaningful. Calendar values (random seeds, replay and character file
 * timestamps) use the wall clock instead.
 */
public final class UiClockUtils {
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    /**
     * Reads the monotonic interface clock.
     *
     * @return the current monotonic time in milliseconds
     */
    public static long getHostTimeMilliseconds() {
        return System.nanoTime() / NANOSECONDS_PER_MILLISECOND;
    }

    private UiClockUtils() {
    }
}
