package org.fogofwarlabs.sc4j.scr.client.ui.text;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.IntFunction;

/**
 * Builds the {@code stat_txt} string tables that the text and hotkey tests read: one JSON entry per string id, parsed as
 * the installed table is.
 */
public final class StatTextScenario {
    /**
     * Encodes the strings {@code 1} through {@code lastIdentifier} as a {@code stat_txt} JSON table and parses it.
     *
     * @param lastIdentifier the last one-based string id of the table
     * @param values         the JSON string content of each id, for example {@code ""} for an empty string
     * @return the parsed text table
     * @throws IOException if the encoded table cannot be parsed
     */
    public static @NotNull StatText createStatText(int lastIdentifier, @NotNull IntFunction<String> values) throws IOException {
        StringBuilder json = new StringBuilder("[");

        for (int identifier = 1; identifier <= lastIdentifier; identifier++) {
            String value = values.apply(identifier);

            json.append(identifier == 1 ? "" : ",").append("{\"Key\":\"k").append(identifier).append("\",\"Value\":\"").append(value).append("\"}");
        }

        return StatText.parse(json.append(']').toString().getBytes(StandardCharsets.UTF_8));
    }


    /**
     * Creates a {@link StatTextScenario} instance.
     */
    private StatTextScenario() {
    }
}
