package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Tests the operating system the client runs on.
 */
public final class HostPlatformUtils {
    /**
     * The system property that names the operating system.
     */
    private static final @NotNull String OPERATING_SYSTEM_PROPERTY = "os.name";
    /**
     * The lower-case prefix of every Windows operating system name.
     */
    private static final @NotNull String WINDOWS_PREFIX = "windows";

    /**
     * @return {@code true} if the operating system name is set and starts with {@code windows} in any case, {@code false} otherwise
     */
    public static boolean isWindows() {
        return System.getProperty(OPERATING_SYSTEM_PROPERTY, "").toLowerCase(Locale.ROOT).startsWith(WINDOWS_PREFIX);
    }

    private HostPlatformUtils() {
    }
}
