package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.client.host.HostTickClock;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Locale;

/**
 * Reads absolute Windows host ticks (the engine host clock, {@link HostTickClock}) and the separate unit-phase timing diagnostic from the Windows
 * shared user data.
 */
public final class WindowsHostTiming {
    private static final @NotNull String OPERATING_SYSTEM_PROPERTY = "os.name";
    private static final @NotNull String ARCHITECTURE_PROPERTY = "os.arch";
    private static final @NotNull String WINDOWS_PREFIX = "Windows";
    private static final @NotNull String AMD64_ARCHITECTURE = "amd64";
    private static final @NotNull String X86_64_ARCHITECTURE = "x86_64";

    private static final long SHARED_USER_DATA_ADDRESS = 0x7FFE0000L;
    private static final int SHARED_USER_DATA_BYTE_SIZE = 0x328;

    private static final int TICK_MULTIPLIER_OFFSET = 4;
    private static final int MAJOR_VERSION_OFFSET = 0x26C;
    private static final int MINOR_VERSION_OFFSET = 0x270;
    private static final int LONG_TICKS_OFFSET = 0x320;
    private static final int LEGACY_TICKS_OFFSET = 0;

    private static final int LONG_TICKS_MAJOR_VERSION = 6;
    private static final int TRANSITION_MAJOR_VERSION = 5;
    private static final int TRANSITION_MINOR_VERSION = 2;
    private static final int TICK_MULTIPLIER_SHIFT = 24;


    private final @NotNull MemorySegment sharedUserData;
    private final @NotNull HostTickClock clock;

    /**
     * Creates a {@link WindowsHostTiming} instance bound to the Windows x64 uptime clock.
     * Native access must be enabled for this module to bind the shared memory.
     *
     * @throws UnsupportedOperationException if the current host is not Windows x64
     */
    @SuppressWarnings("restricted")
    public WindowsHostTiming() {
        String architecture = System.getProperty(ARCHITECTURE_PROPERTY).toLowerCase(Locale.ROOT);

        if (!System.getProperty(OPERATING_SYSTEM_PROPERTY).startsWith(WINDOWS_PREFIX) ||
            !(architecture.equals(AMD64_ARCHITECTURE) || architecture.equals(X86_64_ARCHITECTURE))) {
            throw Exceptions.newUnsupportedOperationException("The native shared-data timing layout requires Windows x64.");
        }

        this.sharedUserData = MemorySegment.ofAddress(SHARED_USER_DATA_ADDRESS).reinterpret(SHARED_USER_DATA_BYTE_SIZE).asReadOnly();
        this.clock          = HostTickClock.getDefault();
    }

    /**
     * Converts shared ticks and subtracts them from the host uptime clock.
     *
     * @return the wrapped 32-bit difference used by unsigned diagnostic comparisons
     */
    public int readElapsedTicks() {
        int multiplier = this.sharedUserData.get(ValueLayout.JAVA_INT, TICK_MULTIPLIER_OFFSET);
        int majorVersion = this.sharedUserData.get(ValueLayout.JAVA_INT, MAJOR_VERSION_OFFSET);
        int minorVersion = this.sharedUserData.get(ValueLayout.JAVA_INT, MINOR_VERSION_OFFSET);
        long sharedTicks = Integer.compareUnsigned(majorVersion, LONG_TICKS_MAJOR_VERSION) >= 0 ||
                           majorVersion == TRANSITION_MAJOR_VERSION && Integer.compareUnsigned(minorVersion, TRANSITION_MINOR_VERSION) >= 0
                           ? this.sharedUserData.get(ValueLayout.JAVA_LONG, LONG_TICKS_OFFSET)
                           : Integer.toUnsignedLong(this.sharedUserData.get(ValueLayout.JAVA_INT, LEGACY_TICKS_OFFSET));
        int convertedTicks = (int) ((sharedTicks * Integer.toUnsignedLong(multiplier)) >>> TICK_MULTIPLIER_SHIFT);

        return this.readHostTicks() - convertedTicks;
    }

    /**
     * Reads the system uptime clock used by host display scheduling.
     *
     * @return the current {@code GetTickCount} DWORD, wrapping every {@code 49.7} days
     * @throws IllegalStateException if the {@code GetTickCount} call fails
     */
    public int readHostTicks() {
        return this.clock.readTicks();
    }
}
