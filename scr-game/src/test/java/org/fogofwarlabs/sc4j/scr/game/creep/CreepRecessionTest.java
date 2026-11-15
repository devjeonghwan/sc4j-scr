package org.fogofwarlabs.sc4j.scr.game.creep;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Checks the recession countdown of an update without receding tiles.
 */
final class CreepRecessionTest {
    /**
     * The sampled countdowns: zero, one, the longest recession period, minus one and the smallest signed DWORD.
     */
    private static final int @NotNull [] COUNTDOWN_SAMPLES = {0, 1, 9, -1, Integer.MIN_VALUE};
    private static final @NotNull String EMPTY_BUCKET_DIGEST = "29e7e1772a3f5689dd9f57ce2a2c1921766df0ff15b27247bf93bfecddd15a53";
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";

    @Test
    void matches_native_creep_recession_empty_bucket_cases() throws Exception {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer output = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int freeCount = 0; freeCount <= CreepRecession.RECORD_COUNT; freeCount++) {
            for (int countdown : COUNTDOWN_SAMPLES) {
                CreepRecession recession = new CreepRecession(countdown, freeCount, new int[CreepRecession.LIST_COUNT]);

                recession.update();
                output.clear();
                digest.update(output.putInt(recession.getCountdown()).array());
            }
        }

        Assertions.assertEquals(EMPTY_BUCKET_DIGEST, HexFormat.of().formatHex(digest.digest()));
    }
}
