package org.fogofwarlabs.sc4j.scr.core.test.golden;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the values that a check records about one run, such as the end state of a replay, as ordered
 * {@code key = value} pairs.
 * <p>
 * Values are exact strings. Tests read them to assert facts and write them to result files for comparison between runs.
 */
public final class GoldenValues {
    private final @NotNull Map<String, String> values;

    /**
     * Creates an empty {@link GoldenValues} instance.
     */
    public GoldenValues() {
        this.values = new LinkedHashMap<>();
    }

    /**
     * @param key the value key
     * @return the value, or {@code null} if the key is absent
     */
    public @Nullable String get(@NotNull String key) {
        return this.values.get(key);
    }

    /**
     * @param key      the value key
     * @param fallback the value returned when the key is absent
     * @return the value, or {@code fallback} if the key is absent
     */
    public @NotNull String get(@NotNull String key, @NotNull String fallback) {
        return this.values.getOrDefault(key, fallback);
    }


    /**
     * Sets a value; a new key is appended after the existing keys.
     *
     * @param key   the value key
     * @param value the value; line breaks are replaced by spaces
     * @return this instance for chaining
     */
    public @NotNull GoldenValues put(@NotNull String key, @NotNull Object value) {
        this.values.put(key, String.valueOf(value).replace('\r', ' ').replace('\n', ' ').strip());

        return this;
    }

    /**
     * Copies every value of another instance whose key starts with a prefix.
     *
     * @param source the values to copy from
     * @param prefix the key prefix
     * @return this instance for chaining
     */
    public @NotNull GoldenValues putAll(@NotNull GoldenValues source, @NotNull String prefix) {
        for (Map.Entry<String, String> entry : source.values.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                this.values.put(entry.getKey(), entry.getValue());
            }
        }

        return this;
    }
}
