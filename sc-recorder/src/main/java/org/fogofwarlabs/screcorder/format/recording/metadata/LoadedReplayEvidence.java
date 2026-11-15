package org.fogofwarlabs.screcorder.format.recording.metadata;

/**
 * Describes loaded replay header evidence without claiming complete byte verification.
 */
public enum LoadedReplayEvidence {
    /**
     * Loaded replay header evidence was unavailable.
     */
    UNAVAILABLE(0),

    /**
     * The loaded replay header matched the requested replay header.
     */
    HEADER_MATCHED(1),

    /**
     * The loaded replay header differed from the requested replay header.
     */
    MISMATCHED(2);

    private final int code;

    LoadedReplayEvidence(int code) {
        this.code = code;
    }

    /**
     * @return the stable binary format code
     */
    public int getCode() {
        return this.code;
    }
}
