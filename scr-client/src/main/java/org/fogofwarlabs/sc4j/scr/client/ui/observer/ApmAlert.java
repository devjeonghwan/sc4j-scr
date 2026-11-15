package org.fogofwarlabs.sc4j.scr.client.ui.observer;

import org.jetbrains.annotations.NotNull;

/**
 * Selects the APM display color and emits delayed low-APM warnings for the supplied local clock.
 * The caller chooses when live-game alerts are enabled; color lookup remains available independently.
 */
public final class ApmAlert {
    /**
     * The color code of the APM number ({@code 0x0714}: code {@code 7}).
     */
    public static final int NORMAL_CODE = 7;
    /**
     * The color code below the threshold.
     */
    public static final int LOW_CODE = 6;
    /**
     * The color code below {@code 110} % of the threshold.
     */
    public static final int NEAR_CODE = 3;
    /**
     * The sound of the alert.
     */
    public static final @NotNull String SOUND = "SND_TERRAN_RESCUE";
    /**
     * The threshold's default ({@code apm_AlertValue}, {@code 0x3c}).
     */
    public static final int DEFAULT_THRESHOLD = 60;

    /**
     * The game time the alert arms at by default (reset value).
     */
    private static final long DEFAULT_ARM_TIME = 90000;
    /**
     * The delay after the first time the APM reached {@code 110} % of the threshold.
     */
    private static final long EARLY_ARM_DELAY = 20000;
    /**
     * The least game time between two alerts.
     */
    private static final long REPEAT_MILLISECONDS = 2000;
    /**
     * The near band: {@code 110} % of the threshold.
     */
    private static final int NEAR_PERCENT = 110;
    private static final int PERCENT = 100;

    /**
     * Returns the color code of the shown APM.
     *
     * @param apm       the shown APM
     * @param alertUser whether "Alert when APM falls below" is on ({@code apm_AlertUser})
     * @param useColor  whether "Color Text" is on ({@code apm_AlertUseColor})
     * @param threshold the threshold ({@code apm_AlertValue})
     * @return {@link #NORMAL_CODE}, {@link #LOW_CODE} or {@link #NEAR_CODE}
     */
    public static int getColorCode(int apm, boolean alertUser, boolean useColor, int threshold) {
        if (!alertUser || !useColor) {
            return NORMAL_CODE;
        }

        if (Integer.compareUnsigned(apm, threshold) < 0) {
            return LOW_CODE;
        }

        if (Integer.compareUnsigned(apm, threshold * NEAR_PERCENT / PERCENT) < 0) {
            return NEAR_CODE;
        }

        return NORMAL_CODE;
    }

    private long armAt;
    private long lastAlert;

    /**
     * Creates an {@link ApmAlert} instance in the state of a game start.
     */
    public ApmAlert() {
        this.armAt     = DEFAULT_ARM_TIME;
        this.lastAlert = 0;
    }


    /**
     * Runs the alert for one game frame.
     *
     * @param clock     the game time of the APM history head in milliseconds
     * @param shownApm  the APM the console last showed
     * @param alertUser whether "Alert when APM falls below" is on
     * @param useColor  whether "Color Text" is on
     * @param useSound  whether "Play Sound" is on
     * @param threshold the threshold
     * @return {@code true} if the alert sound plays now, {@code false} otherwise
     */
    public boolean tick(long clock, int shownApm, boolean alertUser, boolean useColor, boolean useSound, int threshold) {
        if (!alertUser) {
            return false;
        }

        if (clock < this.armAt) {
            if (this.armAt != DEFAULT_ARM_TIME || clock + EARLY_ARM_DELAY >= DEFAULT_ARM_TIME) {
                return false;
            }

            if ((useColor || useSound) && Integer.compareUnsigned(shownApm, threshold * NEAR_PERCENT / PERCENT) >= 0) {
                this.armAt = clock + EARLY_ARM_DELAY;
            }

            return false;
        }

        if (clock < this.lastAlert + REPEAT_MILLISECONDS || !useSound || Integer.compareUnsigned(shownApm, threshold) >= 0) {
            return false;
        }

        this.lastAlert = clock;

        return true;
    }
}
