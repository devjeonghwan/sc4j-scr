package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.jetbrains.annotations.NotNull;

/**
 * Provides the shared input of the MPQ tests: the fixed PKWare DCL stream.
 */
final class MpqTestResources {
    /**
     * The fixed PKWare DCL stream; it decompresses to {@link #IMPLODED_TEXT}.
     */
    static final byte @NotNull [] IMPLODED_STREAM = {0x00, 0x04, (byte) 0x82, 0x24, 0x25, (byte) 0x8F, (byte) 0x80, 0x7F};
    static final @NotNull String IMPLODED_TEXT = "AIAIAIAIAIAIA";


    /**
     * Creates a {@link MpqTestResources} instance.
     */
    private MpqTestResources() {
    }
}
