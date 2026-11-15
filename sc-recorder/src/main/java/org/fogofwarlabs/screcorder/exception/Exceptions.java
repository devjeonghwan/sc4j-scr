package org.fogofwarlabs.screcorder.exception;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.MessageFormat;

/**
 * Creates formatted exceptions without a dependency on the consuming application.
 */
public final class Exceptions {
    /**
     * Creates an {@link IllegalArgumentException} instance.
     *
     * @param pattern   the message pattern
     * @param arguments the message arguments
     * @return the created {@link IllegalArgumentException} instance
     */
    public static @NotNull IllegalArgumentException newIllegalArgumentException(@NotNull String pattern, @NotNull Object @NotNull ... arguments) {
        return new IllegalArgumentException(MessageFormat.format(pattern, arguments));
    }

    /**
     * Creates an {@link IllegalArgumentException} instance with its original cause.
     *
     * @param cause     the original failure
     * @param pattern   the message pattern
     * @param arguments the message arguments
     * @return the created {@link IllegalArgumentException} instance
     */
    public static @NotNull IllegalArgumentException newIllegalArgumentException(@NotNull Throwable cause,
                                                                                @NotNull String pattern, @NotNull Object @NotNull ... arguments) {
        return new IllegalArgumentException(MessageFormat.format(pattern, arguments), cause);
    }

    /**
     * Creates an {@link IllegalStateException} instance.
     *
     * @param pattern   the message pattern
     * @param arguments the message arguments
     * @return the created {@link IllegalStateException} instance
     */
    public static @NotNull IllegalStateException newIllegalStateException(@NotNull String pattern, @NotNull Object @NotNull ... arguments) {
        return new IllegalStateException(MessageFormat.format(pattern, arguments));
    }

    /**
     * Creates an {@link IllegalStateException} instance with its original cause.
     *
     * @param cause     the original failure
     * @param pattern   the message pattern
     * @param arguments the message arguments
     * @return the created {@link IllegalStateException} instance
     */
    public static @NotNull IllegalStateException newIllegalStateException(@NotNull Throwable cause,
                                                                          @NotNull String pattern, @NotNull Object @NotNull ... arguments) {
        return new IllegalStateException(MessageFormat.format(pattern, arguments), cause);
    }

    /**
     * Creates an {@link IOException} instance.
     *
     * @param pattern   the message pattern
     * @param arguments the message arguments
     * @return the created {@link IOException} instance
     */
    public static @NotNull IOException newIOException(@NotNull String pattern, @NotNull Object @NotNull ... arguments) {
        return new IOException(MessageFormat.format(pattern, arguments));
    }

    /**
     * Creates an {@link IOException} instance with its original cause.
     *
     * @param cause     the original failure
     * @param pattern   the message pattern
     * @param arguments the message arguments
     * @return the created {@link IOException} instance
     */
    public static @NotNull IOException newIOException(@NotNull Throwable cause, @NotNull String pattern, @NotNull Object @NotNull ... arguments) {
        return new IOException(MessageFormat.format(pattern, arguments), cause);
    }


    private Exceptions() {
    }
}
