package org.fogofwarlabs.screcorder.format.recording.observation;

/**
 * Describes whether a selected sample was collected.
 */
public enum SampleStatus {
    /**
     * Sample values were collected.
     */
    AVAILABLE(0),

    /**
     * Required source memory could not be read.
     */
    READ_FAILED(1),

    /**
     * The sample changed during collection.
     */
    TORN(2),

    /**
     * Sampling was not attempted.
     */
    NOT_ATTEMPTED(3);

    private final int code;

    SampleStatus(int code) {
        this.code = code;
    }

    /**
     * @return the stable binary format code
     */
    public int getCode() {
        return this.code;
    }
}
