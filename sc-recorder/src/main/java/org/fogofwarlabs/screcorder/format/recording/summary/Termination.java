package org.fogofwarlabs.screcorder.format.recording.summary;

/**
 * Describes why a recording session ended.
 */
public enum Termination {
    /**
     * The session ended normally.
     */
    NORMAL(0),

    /**
     * The caller cancelled the session.
     */
    CANCELLED(1),

    /**
     * The session exceeded its configured time limit.
     */
    TIMEOUT(2),

    /**
     * The session ended because an operation failed.
     */
    FAILED(3);

    private final int code;

    Termination(int code) {
        this.code = code;
    }

    /**
     * @return the stable binary format code
     */
    public int getCode() {
        return this.code;
    }
}
