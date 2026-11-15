package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks timed indicator expiry across signed tick wraparound.
 */
final class TimedIndicatorsTest {
    /**
     * The SHA-256 digest of the timed indicator update capture.
     */
    private static final @NotNull String INDICATOR_CAPTURE_HASH = "0036c0912d7ef9f71cdde5b929743f90e16407476ad48d88ca9534d041e636ed";
    /**
     * The cases of the timed indicator update capture.
     */
    private static final int CAPTURE_CASE_COUNT = 1024;

    @Test
    void matches_native_timed_indicator_update_cases() throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_TIMED_INDICATORS, INDICATOR_CAPTURE_HASH);
        ByteBuffer records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        for (int caseIndex = 0; caseIndex < CAPTURE_CASE_COUNT; caseIndex++) {
            int hostTicks = records.getInt();
            byte[] values = new byte[TimedIndicators.INDICATOR_COUNT];
            int[] deadlines = new int[TimedIndicators.INDICATOR_COUNT];

            for (int index = 0; index < values.length; index++) {
                values[index] = (byte) records.getInt();
            }

            for (int index = 0; index < deadlines.length; index++) {
                deadlines[index] = records.getInt();
            }

            TimedIndicators indicators = new TimedIndicators(values, deadlines);

            indicators.update(hostTicks);

            for (int index = 0; index < values.length; index++) {
                Assertions.assertEquals(records.getInt(), indicators.getValue(index), "case " + caseIndex + ", indicator " + index);
            }
        }

        Assertions.assertFalse(records.hasRemaining());
    }
}
