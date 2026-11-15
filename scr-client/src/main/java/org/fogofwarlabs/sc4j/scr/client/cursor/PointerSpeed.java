package org.fogofwarlabs.sc4j.scr.client.cursor;

import org.fogofwarlabs.sc4j.engine.client.host.SystemPointerSpeed;
import org.jetbrains.annotations.NotNull;

/**
 * Tracks the original system pointer speed and applies the configured sensitivity while the window is active. Inactive windows and restoration use
 * the original speed; each distinct written speed is sent to the platform's pointer speed ({@link SystemPointerSpeed}) without saving it.
 */
public final class PointerSpeed {
    /**
     * The fallback speed when the system speed cannot be read, also the system default.
     */
    public static final int DEFAULT_SPEED = SystemPointerSpeed.DEFAULT_SPEED;
    /**
     * The default sensitivity at the system default speed.
     */
    public static final int DEFAULT_SENSITIVITY = 50;
    /**
     * The largest sensitivity of the options page.
     */
    public static final int MAXIMUM_SENSITIVITY = 100;
    private static final float MINIMUM_FACTOR = 0.1f;
    private static final float MAXIMUM_FACTOR = 2.0f;
    /**
     * The speed per unit of the factor ({@code (int) (f * 10.0)}).
     */
    private static final float SPEED_PER_FACTOR = 10.0f;
    /**
     * The factor of a sensitivity: {@code 2 * S / 100}.
     */
    private static final float FACTOR_PER_SENSITIVITY = 2.0f / 100.0f;

    private final @NotNull SystemPointerSpeed backend;
    private final int originalSpeed;

    private int appliedSpeed;
    private int writtenSpeed;

    /**
     * Creates a {@link PointerSpeed} instance and reads the original speed.
     *
     * @param backend the platform's pointer speed ({@link SystemPointerSpeed#getDefault}; one that reads no speed changes nothing)
     */
    public PointerSpeed(@NotNull SystemPointerSpeed backend) {
        int speed = backend.readSpeed();

        this.backend       = backend;
        this.originalSpeed = speed <= 0 ? DEFAULT_SPEED : speed;

        this.appliedSpeed = this.originalSpeed;
        this.writtenSpeed = this.originalSpeed;
    }

    /**
     * @return the default Mouse Sensitivity for the original speed ({@code (int) (originalSpeed / 10 * 50)})
     */
    public int getDefaultSensitivity() {
        return (int) (this.originalSpeed / (float) DEFAULT_SPEED * DEFAULT_SENSITIVITY);
    }


    /**
     * Follows the options and the window activation: the applied speed of the sensitivity while the window is active, the original speed otherwise;
     * the system speed is written only when it changes.
     *
     * @param active         whether the window is active
     * @param useSensitivity whether Mouse Sensitivity is on
     * @param sensitivity    the sensitivity from {@code 0} to {@code 100}
     */
    public void update(boolean active, boolean useSensitivity, int sensitivity) {
        if (useSensitivity) {
            float factor = Math.clamp(Math.max(MINIMUM_FACTOR, sensitivity * FACTOR_PER_SENSITIVITY), MINIMUM_FACTOR, MAXIMUM_FACTOR);

            this.appliedSpeed = (int) (factor * SPEED_PER_FACTOR);
        } else {
            this.appliedSpeed = this.originalSpeed;
        }

        this.writeSpeedIfChanged(active ? this.appliedSpeed : this.originalSpeed);
    }

    /**
     * Gives the original speed back (the window went away or the program ends).
     */
    public void restore() {
        this.writeSpeedIfChanged(this.originalSpeed);
    }

    /**
     * Records and writes a changed speed once, preserving the recorded value if the backend fails.
     *
     * @param speed the requested system pointer speed
     */
    private void writeSpeedIfChanged(int speed) {
        if (speed == this.writtenSpeed) {
            return;
        }

        // A refused speed is not tried again until the wanted speed changes.
        this.writtenSpeed = speed;
        this.backend.writeSpeed(speed);
    }
}
