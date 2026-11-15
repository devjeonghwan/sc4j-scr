package org.fogofwarlabs.sc4j.scr.game.combat;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Checks the image alias cases computed by {@link ProjectileImageAliasCheck}.
 */
final class ProjectileImageAliasTest {
    /**
     * The size of a probed page.
     */
    private static final int PAGE_SIZE = 4096;
    /**
     * The offset of the probed byte, the last byte of a page.
     */
    private static final int PROBE_OFFSET = 4095;
    /**
     * The number of initial values of the probed byte.
     */
    private static final int BYTE_VALUE_COUNT = 256;
    /**
     * The pattern that makes the byte of a separate alias page differ from the image page.
     */
    private static final int SEPARATE_PAGE_PATTERN = 0xA5;
    /**
     * The size of a digested case record: four BYTEs and two QWORDs.
     */
    private static final int CASE_RECORD_SIZE = 20;
    /**
     * The digest algorithm of the case records.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    /**
     * The native digest of the case records of every probe case.
     */
    private static final @NotNull String IMAGE_ALIAS_CASES_DIGEST = "648b89b9579290dc10a006b37183cde8058a2e89f2a5761521c5bc06e2502f3f";

    @Test
    void matches_native_image_alias_probe_cases() throws Exception {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer result = ByteBuffer.allocate(CASE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (boolean aliases : new boolean[]{true, false}) {
            for (int initialValue = 0; initialValue < BYTE_VALUE_COUNT; initialValue++) {
                byte[] imagePage = new byte[PAGE_SIZE];
                byte[] aliasPage = aliases ? imagePage : new byte[PAGE_SIZE];

                imagePage[PROBE_OFFSET] = (byte) initialValue;
                aliasPage[PROBE_OFFSET] = (byte) (initialValue ^ (aliases ? 0 : SEPARATE_PAGE_PATTERN));

                boolean firstViewChanged = ProjectileImageAliasCheck.isImagePageAliased(imagePage, aliasPage);

                Assertions.assertEquals(aliases, firstViewChanged);
                Assertions.assertEquals(initialValue, Byte.toUnsignedInt(imagePage[PROBE_OFFSET]));
                Assertions.assertEquals(initialValue, Byte.toUnsignedInt(aliasPage[PROBE_OFFSET]));

                result.clear();
                result.put((byte) (aliases ? 1 : 0)).put((byte) initialValue)
                      .put(imagePage[PROBE_OFFSET]).put(aliasPage[PROBE_OFFSET]).putLong(firstViewChanged ? 0 : 1).putLong(0);
                digest.update(result.array());
            }
        }

        Assertions.assertEquals(IMAGE_ALIAS_CASES_DIGEST, HexFormat.of().formatHex(digest.digest()));
    }
}
