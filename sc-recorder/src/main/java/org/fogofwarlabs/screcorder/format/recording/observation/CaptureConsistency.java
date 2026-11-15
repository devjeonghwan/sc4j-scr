package org.fogofwarlabs.screcorder.format.recording.observation;

/**
 * Describes the evidence supporting one frame observation.
 */
public enum CaptureConsistency {
    /**
     * No synchronization evidence was supplied.
     */
    UNVERIFIED(0),

    /**
     * Frame markers matched before and after sampling.
     */
    MARKER_STABLE(1),

    /**
     * Frame markers changed during sampling.
     */
    TORN(2);

    private final int code;

    CaptureConsistency(int code) {
        this.code = code;
    }

    /**
     * @return the stable binary format code
     */
    public int getCode() {
        return this.code;
    }
}
