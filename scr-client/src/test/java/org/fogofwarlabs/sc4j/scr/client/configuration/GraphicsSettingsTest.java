package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies the gamma ramp that {@link GraphicsSettings} builds from the gamma value of the Video options.
 */
final class GraphicsSettingsTest {
    private static final int NEUTRAL_GAMMA = 100;
    private static final int BRIGHTEST_GAMMA = 140;
    private static final int DARKEST_GAMMA = 60;
    private static final float BRIGHTEST_EXPONENT = 0.6f;

    private static final int MIDDLE_RAMP_INDEX = 128;
    private static final int LAST_RAMP_INDEX = 255;
    private static final float CHANNEL_MAXIMUM = 255.0f;
    private static final float RAMP_MAXIMUM = 65535.0f;
    private static final int RAMP_MAXIMUM_VALUE = 65535;

    @Test
    void get_gamma_ramp_value_should_build_gamma_ramp() {
        // Gamma 100: exponent 1, the identity ramp.
        Assertions.assertEquals(0, GraphicsSettings.getGammaRampValue(NEUTRAL_GAMMA, 0));
        Assertions.assertEquals((int) (MIDDLE_RAMP_INDEX / CHANNEL_MAXIMUM * RAMP_MAXIMUM),
                                GraphicsSettings.getGammaRampValue(NEUTRAL_GAMMA, MIDDLE_RAMP_INDEX));
        Assertions.assertEquals(RAMP_MAXIMUM_VALUE, GraphicsSettings.getGammaRampValue(NEUTRAL_GAMMA, LAST_RAMP_INDEX));

        // Gamma 140: exponent (1.4 - 1.4) + 0.6 = 0.6, brighter mid-tones; gamma 60: exponent 1.4, darker.
        Assertions.assertEquals((int) ((float) Math.pow(MIDDLE_RAMP_INDEX / CHANNEL_MAXIMUM, BRIGHTEST_EXPONENT) * RAMP_MAXIMUM),
                                GraphicsSettings.getGammaRampValue(BRIGHTEST_GAMMA, MIDDLE_RAMP_INDEX));
        Assertions.assertTrue(GraphicsSettings.getGammaRampValue(DARKEST_GAMMA, MIDDLE_RAMP_INDEX) <
                              GraphicsSettings.getGammaRampValue(NEUTRAL_GAMMA, MIDDLE_RAMP_INDEX));
    }
}
