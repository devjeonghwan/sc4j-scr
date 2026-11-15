package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.log.LogLevel;
import org.fogofwarlabs.sc4j.scr.data.format.casc.SoundPathResolver;
import org.fogofwarlabs.sc4j.scr.render.hd.HdRenderAssets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Parses the values shared by the settings file ({@link ClientSettings}) and the launch options ({@link ClientOptions}).
 * <p>
 * Both accept the same spellings, case-insensitively, and report an invalid value with the same message: {@code "<name> must be one of <values>."},
 * where the name is the settings key ({@code graphics.renderer}) or the option ({@code --renderer}).
 */
public final class ClientValuesUtils {
    /**
     * The highest frame rate limit; {@code 0} means no limit.
     */
    static final int MAXIMUM_FRAME_RATE_LIMIT = 1000;

    private static final @NotNull String LOCALE_PATTERN = "[A-Za-z]{4}";

    /**
     * The message of a numeric option whose value is not a number: the option, then the value.
     */
    private static final @NotNull String NUMBER_MESSAGE = "{0} must be a number, not \"{1}\".";

    /**
     * Parses the renderer quality from an option or settings value.
     *
     * @param name  the option or settings key reported for invalid input
     * @param value the supplied value
     * @return the HD quality for {@code hd} or {@code hd2}; {@code null} for {@code classic}
     * @throws IllegalArgumentException if the value is not supported
     */
    public static HdRenderAssets.@Nullable Quality parseRenderer(@NotNull String name, @Nullable Object value) {
        return switch (parseChoice(name, value, "classic", "hd", "hd2")) {
            case "hd" -> HdRenderAssets.Quality.HD;
            case "hd2" -> HdRenderAssets.Quality.HD2;
            default -> null;
        };
    }

    /**
     * Parses the vertical synchronization mode from an option or settings value.
     *
     * @param name  the option or settings key reported for invalid input
     * @param value the supplied value
     * @return the synchronization of {@code on}, {@code off} or {@code adaptive}
     * @throws IllegalArgumentException if the value is not supported
     */
    public static GraphicsSettings.@NotNull Vsync parseVsync(@NotNull String name, @Nullable Object value) {
        return switch (parseChoice(name, value, "on", "off", "adaptive")) {
            case "on" -> GraphicsSettings.Vsync.ON;
            case "off" -> GraphicsSettings.Vsync.OFF;
            default -> GraphicsSettings.Vsync.ADAPTIVE;
        };
    }

    /**
     * Parses the HD unit skin from an option or settings value.
     *
     * @param name  the option or settings key reported for invalid input
     * @param value the supplied value
     * @return the skin folder of {@code presale} or {@code carbot}; {@code null} for {@code default}
     * @throws IllegalArgumentException if the value is not supported
     */
    static @Nullable String parseHdSkin(@NotNull String name, @Nullable Object value) {
        return switch (parseChoice(name, value, "default", "presale", "carbot")) {
            case "presale" -> "PreSale";
            case "carbot" -> "Carbot";
            default -> null;
        };
    }

    /**
     * Parses the sound set from an option or settings value.
     *
     * @param name  the option or settings key reported for invalid input
     * @param value the supplied value
     * @return the sound set of {@code remastered} or {@code classic}
     * @throws IllegalArgumentException if the value is not supported
     */
    static SoundPathResolver.@NotNull AudioSet parseAudioSet(@NotNull String name, @Nullable Object value) {
        return parseChoice(name, value, "remastered", "classic").equals("classic")
               ? SoundPathResolver.AudioSet.CLASSIC
               : SoundPathResolver.AudioSet.REMASTERED;
    }

    /**
     * Parses an {@code on}/{@code off} switch from an option or settings value.
     *
     * @param name  the option or settings key reported for invalid input
     * @param value the supplied value
     * @return {@code true} if the value is {@code on}, {@code false} otherwise
     * @throws IllegalArgumentException if the value is not supported
     */
    static boolean parseOnOff(@NotNull String name, @Nullable Object value) {
        return parseChoice(name, value, "on", "off").equals("on");
    }

    /**
     * Parses the log threshold from an option or settings value.
     *
     * @param name  the option or settings key reported for invalid input
     * @param value the supplied value
     * @return the log threshold of {@code error}, {@code warn}, {@code info}, {@code debug}, {@code trace} or {@code off}
     * @throws IllegalArgumentException if the value is not supported
     */
    static @NotNull LogLevel parseLogLevel(@NotNull String name, @Nullable Object value) {
        LogLevel level = value instanceof String text ? LogLevel.parse(text) : null;

        if (level == null) {
            throw Exceptions.newIllegalArgumentException("{0} must be one of error, warn, info, debug, trace, off.", name);
        }

        return level;
    }

    /**
     * Parses the game locale from an option or settings value.
     *
     * @param name  the option or settings key reported for invalid input
     * @param value the supplied value
     * @return the four-letter locale code, such as {@code koKR}
     * @throws IllegalArgumentException if the value is not supported
     */
    static @NotNull String parseLocale(@NotNull String name, @Nullable Object value) {
        if (!(value instanceof String text) || !text.matches(LOCALE_PATTERN)) {
            throw Exceptions.newIllegalArgumentException("{0} must be a locale such as \"koKR\".", name);
        }

        return text;
    }

    /**
     * Parses the frame rate limit from an option or settings value.
     *
     * @param name  the option or settings key reported for invalid input
     * @param value the supplied value
     * @return the frame rate limit, {@code 0} through {@link #MAXIMUM_FRAME_RATE_LIMIT}
     * @throws IllegalArgumentException if the value is not supported
     */
    static int parseFrameRateLimit(@NotNull String name, @Nullable Object value) {
        long limit;

        try {
            limit = value instanceof Long number ? number : value instanceof String text ? Long.parseLong(text.trim()) : -1;
        } catch (NumberFormatException exception) {
            limit = -1;
        }

        if (limit < 0 || limit > MAXIMUM_FRAME_RATE_LIMIT) {
            throw Exceptions.newIllegalArgumentException("{0} must be a number from 0 to {1}.", name, MAXIMUM_FRAME_RATE_LIMIT);
        }

        return (int) limit;
    }

    /**
     * Parses a decimal integer of a launch option.
     *
     * @param name  the option reported for invalid input
     * @param value the supplied value
     * @return the integer
     * @throws IllegalArgumentException if the value is not a decimal integer
     */
    static int parseInteger(@NotNull String name, @NotNull String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw Exceptions.newIllegalArgumentException(exception, NUMBER_MESSAGE, name, value);
        }
    }

    /**
     * Parses a decimal long integer of a launch option.
     *
     * @param name  the option reported for invalid input
     * @param value the supplied value
     * @return the long integer
     * @throws IllegalArgumentException if the value is not a decimal integer
     */
    static long parseLong(@NotNull String name, @NotNull String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw Exceptions.newIllegalArgumentException(exception, NUMBER_MESSAGE, name, value);
        }
    }

    /**
     * Parses a decimal floating-point number of a launch option.
     *
     * @param name  the option reported for invalid input
     * @param value the supplied value
     * @return the number
     * @throws IllegalArgumentException if the value is not a number
     */
    static float parseFloat(@NotNull String name, @NotNull String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            throw Exceptions.newIllegalArgumentException(exception, NUMBER_MESSAGE, name, value);
        }
    }

    /**
     * Parses one of the allowed values from an option or settings value and returns its canonical spelling, using locale-independent case
     * conversion.
     *
     * @param name    the option or settings key reported for invalid input
     * @param value   the supplied value
     * @param allowed the supported canonical spellings
     * @return the matching canonical spelling
     * @throws IllegalArgumentException if the value does not match an allowed spelling
     */
    static @NotNull String parseChoice(@NotNull String name, @Nullable Object value, @NotNull String @NotNull ... allowed) {
        if (value instanceof String text) {
            String normalized = text.toLowerCase(Locale.ROOT);

            for (String candidate : allowed) {
                if (candidate.equals(normalized)) {
                    return candidate;
                }
            }
        }

        throw Exceptions.newIllegalArgumentException("{0} must be one of {1}.", name, String.join(", ", allowed));
    }

    /**
     * Creates a {@link ClientValuesUtils} instance.
     */
    private ClientValuesUtils() {
    }
}
