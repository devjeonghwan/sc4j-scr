package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a CASC build configuration file.
 * <p>
 * The file is a plain text list of {@code key = value} lines. Some values hold two space-separated hashes, for example the {@code encoding}
 * entry holds the content key and the encoding key of the encoding file.
 */
public final class BuildConfig {
    /**
     * The length of one directory level of the configuration file path: one hexadecimal byte of the key.
     */
    private static final int KEY_DIRECTORY_LENGTH = 2;

    /**
     * Creates a {@link BuildConfig} instance from the build configuration file that the given configuration key names.
     *
     * @param configurationDirectory the CASC {@code config} directory
     * @param configurationKey       the hexadecimal configuration key
     * @return the created {@link BuildConfig} instance
     * @throws IOException if the file cannot be read
     */
    public static @NotNull BuildConfig readFrom(@NotNull Path configurationDirectory, @NotNull String configurationKey) throws IOException {
        Path configurationFile = configurationDirectory
                .resolve(configurationKey.substring(0, KEY_DIRECTORY_LENGTH))
                .resolve(configurationKey.substring(KEY_DIRECTORY_LENGTH, 2 * KEY_DIRECTORY_LENGTH))
                .resolve(configurationKey);

        return readFrom(configurationFile);
    }

    /**
     * Creates a {@link BuildConfig} instance from the given build configuration file.
     *
     * @param configurationFile the path to the build configuration file
     * @return the created {@link BuildConfig} instance
     * @throws IOException if the file cannot be read
     */
    public static @NotNull BuildConfig readFrom(@NotNull Path configurationFile) throws IOException {
        List<String> lines = Files.readAllLines(configurationFile, StandardCharsets.UTF_8);
        Map<String, String> values = new LinkedHashMap<>();

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }

            int separatorIndex = trimmedLine.indexOf('=');

            if (separatorIndex < 0) {
                continue;
            }

            String key = trimmedLine.substring(0, separatorIndex).trim();
            String value = trimmedLine.substring(separatorIndex + 1).trim();

            values.put(key, value);
        }

        return new BuildConfig(values);
    }


    private final @NotNull Map<String, String> values;

    /**
     * Creates a {@link BuildConfig} instance.
     *
     * @param values the key-value pairs
     */
    private BuildConfig(@NotNull Map<String, String> values) {
        this.values = values;
    }

    /**
     * @param key the key
     * @return the value, or {@code null} if the key does not exist
     */
    public @Nullable String get(@NotNull String key) {
        return this.values.get(key);
    }

    /**
     * Returns the value of the given key.
     *
     * @param key the key
     * @return the value
     * @throws IllegalStateException if the key does not exist
     */
    public @NotNull String getRequired(@NotNull String key) {
        String value = this.values.get(key);

        if (value == null) {
            throw Exceptions.newIllegalStateException("The build configuration misses the required key: {0}", key);
        }

        return value;
    }

    /**
     * Returns the first hash of a two-hash value.
     *
     * @param key the key
     * @return the first hash
     */
    public @NotNull String getFirstHash(@NotNull String key) {
        return this.getRequired(key).split("\\s+")[0];
    }

    /**
     * Returns the second hash of a two-hash value.
     *
     * @param key the key
     * @return the second hash, or the first hash if the value holds a single hash
     */
    public @NotNull String getSecondHash(@NotNull String key) {
        String[] hashes = this.getRequired(key).split("\\s+");

        return hashes.length > 1 ? hashes[1] : hashes[0];
    }
}
