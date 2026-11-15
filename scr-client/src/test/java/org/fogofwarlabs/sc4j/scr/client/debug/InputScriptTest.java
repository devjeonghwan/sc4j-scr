package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.input.InputScript;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Tests that the client's input scripts parse with the engine input script runner.
 */
final class InputScriptTest {
    private static final @NotNull String RESOURCE_FOLDER = "/input-scripts/";

    @Test
    void resource_input_scripts_parse() throws IOException {
        for (String name : List.of("game-menu-options-key.txt", "glue-walk.txt")) {
            try (InputStream stream = InputScriptTest.class.getResourceAsStream(RESOURCE_FOLDER + name)) {
                Assertions.assertNotNull(stream, name);

                List<String> lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();

                Assertions.assertFalse(InputScript.parse(lines).isFinished(), name);
            }
        }
    }
}
