package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verifies the interface settings the options and the accelerators change (scroll speeds, {@code ShowFPS}, {@code ShowTurnRate}), their settings file
 * keys, and the frame rate limit default of the Video tab.
 */
final class InterfaceSettingsTest {
    private static final @NotNull String SETTINGS = """
                                                    {
                                                      "input": {"keyScroll": 6, "mouseScroll": 0},
                                                      "game": {"showFps": true, "showTurnRate": true}
                                                    }
                                                    """;

    private static final int DEFAULT_SCROLL_SPEED = 3;
    private static final int TOO_FAST_SCROLL_SPEED = 7;
    private static final int NATIVE_DEFAULT_FRAME_RATE_LIMIT = 300;

    @Test
    void new_interface_settings_start_with_defaults_and_bound_key_scroll() {
        InterfaceSettings settings = new InterfaceSettings();

        Assertions.assertEquals(DEFAULT_SCROLL_SPEED, settings.getKeyScroll());
        Assertions.assertEquals(DEFAULT_SCROLL_SPEED, settings.getMouseScroll());
        Assertions.assertFalse(settings.isFrameRateShown());
        Assertions.assertFalse(settings.isTurnRateShown());
        Assertions.assertThrows(IllegalArgumentException.class, () -> settings.setKeyScroll(TOO_FAST_SCROLL_SPEED));
    }

    @Test
    void settings_file_fills_interface_settings() throws IOException {
        Path file = Files.createTempFile("sc4j-interface", ".json");

        try {
            Files.writeString(file, SETTINGS, StandardCharsets.UTF_8);

            InterfaceSettings settings = new InterfaceSettings();

            ClientSettings.load(file).applyTo(settings);

            Assertions.assertEquals(6, settings.getKeyScroll());
            Assertions.assertEquals(0, settings.getMouseScroll());
            Assertions.assertTrue(settings.isFrameRateShown());
            Assertions.assertTrue(settings.isTurnRateShown());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void get_effective_frame_rate_limit_should_default_to_300_when_enabled() {
        GraphicsSettings graphics = new GraphicsSettings();

        Assertions.assertEquals(NATIVE_DEFAULT_FRAME_RATE_LIMIT, graphics.getFrameRateLimit());
        Assertions.assertEquals(0, graphics.getEffectiveFrameRateLimit(), "disabled: no limit");

        graphics.setFrameRateLimitEnabled(true);

        Assertions.assertEquals(NATIVE_DEFAULT_FRAME_RATE_LIMIT, graphics.getEffectiveFrameRateLimit());

        graphics.setFrameRateLimit(0);

        Assertions.assertEquals(NATIVE_DEFAULT_FRAME_RATE_LIMIT, graphics.getEffectiveFrameRateLimit(), "an enabled 0 is the native default");

        graphics.setFrameRateLimit(144);

        Assertions.assertEquals(144, graphics.getEffectiveFrameRateLimit());
    }
}
