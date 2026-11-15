package org.fogofwarlabs.sc4j.scr.client.ui.observer;

import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the elapsed time of the replay command-card labels.
 */
@RequiresInstallation
final class ReplayPanelTest {
    private static final @NotNull String LOCALE = "enUS";
    private static final int TIME_CONTROL = -46;
    /**
     * Frames of {@code 42} ms whose millisecond product {@code 4294967334} wraps to {@code 38} in {@code 32} bits.
     */
    private static final long WRAPPING_FRAMES = 102261127L;
    /**
     * The elapsed time of the wrapped product.
     */
    private static final @NotNull String WRAPPED_TIME = "00:00";
    private static final int NO_MULTIPLIER = 1;
    private static final long NO_SEEK_TARGET = -1;

    @Test
    void update_should_wrap_the_elapsed_milliseconds_as_an_unsigned_32_bit_value() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            ReplayPanel panel = new ReplayPanel(StatText.load(storage, LOCALE), GluText.load(storage, LOCALE));

            panel.update(WRAPPING_FRAMES, WRAPPING_FRAMES, GameSpeed.FASTEST, GameSpeed.FASTEST, NO_MULTIPLIER, false, false, NO_SEEK_TARGET);

            String time = panel.getTexts().get(TIME_CONTROL);

            Assertions.assertNotNull(time);
            Assertions.assertTrue(time.endsWith(WRAPPED_TIME), time);
        }
    }
}
