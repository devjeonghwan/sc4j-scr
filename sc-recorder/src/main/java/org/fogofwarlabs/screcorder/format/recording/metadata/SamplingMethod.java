package org.fogofwarlabs.screcorder.format.recording.metadata;

/**
 * Describes how frame values were obtained.
 */
public enum SamplingMethod {
    /**
     * Values were supplied directly by the caller.
     */
    CALLER_PROVIDED(0),

    /**
     * Values were collected by external read-only memory polling.
     */
    EXTERNAL_MARKER_POLLING(1);

    private final int code;

    SamplingMethod(int code) {
        this.code = code;
    }

    /**
     * @return the stable binary format code
     */
    public int getCode() {
        return this.code;
    }
}
