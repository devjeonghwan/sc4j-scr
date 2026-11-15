package org.fogofwarlabs.sc4j.scr.net.lan;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies the size check of the LAN game data that {@link LanGameOptions} parses.
 */
final class LanGameOptionsTest {
    private static final int SHORT_DATA_SIZE = 10;
    private static final @NotNull String SHORT_DATA_MESSAGE = "The game data holds 10 bytes, not 32.";

    @Test
    void parse_should_report_the_received_and_required_sizes_of_short_data() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                     () -> LanGameOptions.parse(new byte[SHORT_DATA_SIZE]));

        Assertions.assertEquals(SHORT_DATA_MESSAGE, exception.getMessage());
    }
}
