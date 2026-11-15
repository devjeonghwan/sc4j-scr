package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.jetbrains.annotations.NotNull;

/**
 * Represents the container format variant of a {@code .rep} file: how its sections are compressed and where its extension lies.
 */
public enum ReplayContainerFormat {
    /**
     * The PKWARE-compressed format of classic replays.
     */
    LEGACY,
    /**
     * The zlib-compressed format of Remastered replays.
     */
    MODERN,
    /**
     * The zlib-compressed format with the {@code seRS} identifier and extension offset (1.21 and later).
     */
    MODERN_121;

    /**
     * The shortest content whose format can be detected.
     */
    private static final int MINIMUM_DETECTION_SIZE = 30;
    /**
     * The offset of the replay ID section data.
     */
    private static final int REPLAY_ID_OFFSET = 12;
    /**
     * The first byte of the {@code seRS} replay ID.
     */
    private static final byte EXTENDED_REPLAY_ID_FIRST_BYTE = 's';
    /**
     * The offset of the first compressed chunk of the header section.
     */
    private static final int HEADER_CHUNK_OFFSET = 28;
    private static final byte ZLIB_MAGIC = 0x78;

    /**
     * Detects the replay format from the first bytes of the file.
     * <p>
     * The replay ID section data is at offset 12: {@code "seRS"} marks 1.21+, and the first compressed chunk of the header section starts at
     * offset 28 with a zlib magic byte for modern replays.
     *
     * @param data the replay file content; content shorter than 30 bytes selects the legacy format
     * @return the detected format
     */
    public static @NotNull ReplayContainerFormat detect(byte @NotNull [] data) {
        if (data.length < MINIMUM_DETECTION_SIZE) {
            return LEGACY;
        }

        if (data[REPLAY_ID_OFFSET] == EXTENDED_REPLAY_ID_FIRST_BYTE) {
            return MODERN_121;
        }

        if (data[HEADER_CHUNK_OFFSET] == ZLIB_MAGIC) {
            return MODERN;
        }

        return LEGACY;
    }
}
