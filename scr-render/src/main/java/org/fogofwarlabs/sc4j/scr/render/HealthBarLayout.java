package org.fogofwarlabs.sc4j.scr.render;

import org.jetbrains.annotations.NotNull;

/**
 * Computes reusable health, shield and energy bar geometry.
 * <p>
 * The bar is {@code width - (width - 1) % 3} pixels wide (at least {@code 19}), five rows high plus two rows for shields and six rows for energy.
 * Filled widths round to multiples of three with a minimum of three. Every third column separates the health and energy rows with color {@code 18}.
 * The reusable instance holds no references.
 */
public final class HealthBarLayout {
    private static final int @NotNull [] NO_SHIELD_66 = {18, 0, 1, 2, 18};
    private static final int @NotNull [] NO_SHIELD_33 = {18, 3, 4, 5, 18};
    private static final int @NotNull [] NO_SHIELD_0 = {18, 6, 7, 8, 18};
    private static final int @NotNull [] NO_SHIELD_BACKGROUND = {18, 15, 16, 17, 18};

    private static final int @NotNull [] SHIELD_66 = {18, 0, 0, 1, 1, 2, 18};
    private static final int @NotNull [] SHIELD_33 = {18, 3, 3, 4, 4, 5, 18};
    private static final int @NotNull [] SHIELD_0 = {18, 6, 6, 7, 7, 8, 18};
    private static final int @NotNull [] SHIELD_BACKGROUND = {18, 15, 15, 16, 16, 17, 18};

    private static final int @NotNull [] SHIELD_ROWS = {18, 10, 11, 18};

    private static final int @NotNull [] SHIELD_ROWS_BACKGROUND = {18, 16, 17, 18};

    private static final int @NotNull [] ENERGY_ROWS = {18, 12, 13, 14, 18};

    /**
     * The divisor of a percentage.
     */
    private static final int PERCENT = 100;
    /**
     * The width of a bar segment: filled widths are multiples of it, and each segment starts with a separator column.
     */
    private static final int SEGMENT_WIDTH = 3;
    /**
     * The smallest bar width.
     */
    private static final int MINIMUM_WIDTH = 19;
    /**
     * The health rows without shields.
     */
    private static final int HEALTH_ROWS = 5;
    /**
     * The rows that shields add to the health rows.
     */
    private static final int SHIELD_EXTRA_ROWS = 2;
    /**
     * The empty row between the health rows and the energy rows.
     */
    private static final int ENERGY_GAP = 1;
    /**
     * The hit point percentage from which the bar shows the high health colors.
     */
    private static final int HIGH_HEALTH_PERCENT = 66;
    /**
     * The hit point percentage from which the bar shows the medium health colors.
     */
    private static final int MEDIUM_HEALTH_PERCENT = 33;
    /**
     * The fill color index of the middle health row at high health.
     */
    private static final int HIGH_HEALTH_COLOR = 1;
    /**
     * The fill color index of the middle health row at medium health.
     */
    private static final int MEDIUM_HEALTH_COLOR = 4;
    /**
     * The fill color index of the middle health row at low health.
     */
    private static final int LOW_HEALTH_COLOR = 7;
    /**
     * The color index of the separator columns.
     */
    private static final int SEPARATOR_COLOR = 18;

    /**
     * Receives one solid bar rectangle in bar-local pixels.
     */
    public interface RectangleSink {
        /**
         * Receives a rectangle.
         *
         * @param left       the left bar pixel
         * @param top        the top bar pixel
         * @param width      the width in pixels
         * @param height     the height in pixels
         * @param colorIndex the bar palette color index
         */
        void fill(int left, int top, int width, int height, int colorIndex);
    }

    /**
     * Returns {@code percent * max(spriteBarWidth - 1, 0) / 100}, rounded to the nearest multiple of three, at least three.
     *
     * @param percent        the percentage
     * @param spriteBarWidth the {@code sprites.dat} health bar width
     * @return the filled width in pixels
     */
    static int getFilledWidth(int percent, int spriteBarWidth) {
        int result = percent * Math.max(spriteBarWidth - 1, 0) / PERCENT;

        if (result < SEGMENT_WIDTH) {
            result = SEGMENT_WIDTH;
        } else if (result % SEGMENT_WIDTH != 0) {
            if (result % SEGMENT_WIDTH > 1) {
                result += SEGMENT_WIDTH - result % SEGMENT_WIDTH;
            } else {
                result -= result % SEGMENT_WIDTH;
            }
        }

        return result;
    }

    private int width;
    private int height;

    private boolean shield;
    private boolean energy;

    private int hitPointsPercent;
    private int healthFill;
    private int shieldFill;
    private int energyFill;

    /**
     * Creates a {@link HealthBarLayout} instance without a configured bar.
     */
    public HealthBarLayout() {
        this.width  = 0;
        this.height = 0;

        this.shield = false;
        this.energy = false;

        this.hitPointsPercent = 0;
        this.healthFill       = 0;
        this.shieldFill       = 0;
        this.energyFill       = 0;
    }

    /**
     * @return the bar width in pixels
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * @return the bar height in pixels
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * @return the first row of the energy rows
     */
    int getEnergyTop() {
        return this.getHealthRows() + ENERGY_GAP;
    }

    /**
     * @return the health fill color index of the middle health row: {@code 1}, {@code 4} or {@code 7}
     */
    int getHealthColor() {
        return this.hitPointsPercent >= HIGH_HEALTH_PERCENT ? HIGH_HEALTH_COLOR
                                                            : this.hitPointsPercent >= MEDIUM_HEALTH_PERCENT ? MEDIUM_HEALTH_COLOR
                                                                                                             : LOW_HEALTH_COLOR;
    }

    /**
     * @return the number of health rows, including the shield rows
     */
    private int getHealthRows() {
        return this.shield ? HEALTH_ROWS + SHIELD_EXTRA_ROWS : HEALTH_ROWS;
    }


    /**
     * Sets up a bar.
     *
     * @param spriteBarWidth   the {@code sprites.dat} health bar width
     * @param hitPointsPercent the hit point percentage
     * @param shieldPercent    the shield percentage, or a negative value without shields
     * @param energyPercent    the energy or timer percentage, or a negative value without an energy row
     */
    public void configure(int spriteBarWidth, int hitPointsPercent, int shieldPercent, int energyPercent) {
        int barWidth = spriteBarWidth - (spriteBarWidth - 1) % SEGMENT_WIDTH;

        this.width = Math.max(barWidth, MINIMUM_WIDTH);

        this.shield = shieldPercent >= 0;
        this.energy = energyPercent >= 0;
        this.height = this.getHealthRows() + (this.energy ? ENERGY_GAP + ENERGY_ROWS.length : 0);

        this.hitPointsPercent = hitPointsPercent;
        this.healthFill       = Math.min(getFilledWidth(hitPointsPercent, spriteBarWidth), this.width);
        this.shieldFill       = this.shield ? Math.min(getFilledWidth(shieldPercent, spriteBarWidth), this.width) : 0;
        this.energyFill       = this.energy ? Math.min(getFilledWidth(energyPercent, spriteBarWidth), this.width) : 0;
    }

    /**
     * Emits the health, shield and energy rectangles, followed by their separator columns.
     *
     * @param sink the rectangle receiver
     */
    public void emit(@NotNull RectangleSink sink) {
        int[] colors66 = this.shield ? SHIELD_66 : NO_SHIELD_66;
        int[] colors33 = this.shield ? SHIELD_33 : NO_SHIELD_33;
        int[] colors0 = this.shield ? SHIELD_0 : NO_SHIELD_0;
        int[] background = this.shield ? SHIELD_BACKGROUND : NO_SHIELD_BACKGROUND;
        int[] fill = this.hitPointsPercent >= HIGH_HEALTH_PERCENT ? colors66 : this.hitPointsPercent >= MEDIUM_HEALTH_PERCENT ? colors33 : colors0;
        int healthRows = this.getHealthRows();

        for (int row = 0; row < healthRows; row++) {
            sink.fill(0, row, this.healthFill, 1, fill[row]);

            if (this.width > this.healthFill) {
                sink.fill(this.healthFill, row, this.width - this.healthFill, 1, background[row]);
            }
        }

        if (this.shield) {
            for (int row = 0; row < SHIELD_ROWS.length; row++) {
                sink.fill(0, row, this.shieldFill, 1, SHIELD_ROWS[row]);

                if (this.width > this.shieldFill) {
                    sink.fill(this.shieldFill, row, this.width - this.shieldFill, 1, SHIELD_ROWS_BACKGROUND[row]);
                }
            }
        }

        int energyTop = this.getEnergyTop();

        if (this.energy) {
            for (int row = 0; row < ENERGY_ROWS.length; row++) {
                sink.fill(0, energyTop + row, this.energyFill, 1, ENERGY_ROWS[row]);

                if (this.width > this.energyFill) {
                    sink.fill(this.energyFill, energyTop + row, this.width - this.energyFill, 1, NO_SHIELD_BACKGROUND[row]);
                }
            }
        }

        for (int column = 0; column < this.width; column += SEGMENT_WIDTH) {
            sink.fill(column, 0, 1, healthRows, SEPARATOR_COLOR);

            if (this.energy) {
                sink.fill(column, energyTop, 1, ENERGY_ROWS.length, SEPARATOR_COLOR);
            }
        }
    }
}
