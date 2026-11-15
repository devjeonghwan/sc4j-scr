package org.fogofwarlabs.screcorder.format.recording.observation;

/**
 * Describes the stored representation of a sample.
 */
public enum StorageMode {
    /**
     * Canonical sample values are stored.
     */
    VALUES(0),

    /**
     * Only a canonical SHA-256 digest is stored.
     */
    DIGEST(1),

    /**
     * No values or digest are stored.
     */
    NONE(2);

    private final int code;

    StorageMode(int code) {
        this.code = code;
    }

    /**
     * @return the stable binary format code
     */
    public int getCode() {
        return this.code;
    }
}
