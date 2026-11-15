package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.jetbrains.annotations.NotNull;

/**
 * Selects the menu-label shifts for a console race or observer view; the console type also keys the time and APM plate
 * maps of the console classes. Skin-specific console types are not represented.
 */
public enum ConsoleType {
    /**
     * Type {@code 0}: the Zerg console (race letter {@code 'z'}).
     */
    ZERG(MenuTextShift.WIDE),
    /**
     * Type {@code 1}: the Terran console ({@code 't'}).
     */
    TERRAN(MenuTextShift.WIDE),
    /**
     * Type {@code 2}: the Protoss console ({@code 'p'}).
     */
    PROTOSS(MenuTextShift.NARROW),
    /**
     * Type {@code 3}: the neutral console ({@code 'n'}) and every observer or replay view.
     */
    OBSERVER(MenuTextShift.OBSERVER);

    private static final int TERRAN_RACE = 1;
    private static final int PROTOSS_RACE = 2;

    /**
     * Retains the text-rectangle shift and normal and pressed pen offsets.
     */
    public static final class MenuTextShift {
        /**
         * SD, every console: {@code horizontalShift 0, verticalShift 0, pen 3} (pressed {@code 0}).
         */
        public static final @NotNull MenuTextShift SD = new MenuTextShift(0, 0, 3, 0);
        /**
         * HD types {@code 0} and {@code 1}: {@code horizontalShift -4, verticalShift 0, pen 3} (pressed {@code 0}).
         */
        public static final @NotNull MenuTextShift WIDE = new MenuTextShift(-4, 0, 3, 0);
        /**
         * HD type {@code 3}: {@code horizontalShift -3, verticalShift -2, pen 3} (pressed {@code 2}).
         */
        public static final @NotNull MenuTextShift OBSERVER = new MenuTextShift(-3, -2, 3, 2);
        /**
         * HD other types ({@code 2}): {@code horizontalShift -1, verticalShift 0, pen 0} (pressed {@code 0}).
         */
        public static final @NotNull MenuTextShift NARROW = new MenuTextShift(-1, 0, 0, 0);

        private final int horizontalShift;
        private final int verticalShift;
        private final int pen;
        private final int pressedPen;

        private MenuTextShift(int horizontalShift, int verticalShift, int pen, int pressedPen) {
            this.horizontalShift = horizontalShift;
            this.verticalShift   = verticalShift;
            this.pen             = pen;
            this.pressedPen      = pressedPen;
        }

        /**
         * @return the horizontal move of the text rectangle
         */
        public int getHorizontalShift() {
            return this.horizontalShift;
        }

        /**
         * @return the vertical move of the text rectangle
         */
        public int getVerticalShift() {
            return this.verticalShift;
        }

        /**
         * @param pressed whether the button is pressed
         * @return the pen offset from the moved rectangle's top left
         */
        public int getPen(boolean pressed) {
            return pressed ? this.pressedPen : this.pen;
        }
    }

    /**
     * Selects an observer console or the console for the supplied race.
     *
     * @param race     zero for Zerg, one for Terran, or two for Protoss
     * @param observer whether using an observer or replay view
     * @return the console type, with Zerg as the default race
     */
    public static @NotNull ConsoleType of(int race, boolean observer) {
        if (observer) {
            return OBSERVER;
        }

        return switch (race) {
            case TERRAN_RACE -> TERRAN;
            case PROTOSS_RACE -> PROTOSS;
            default -> ZERG;
        };
    }

    private final @NotNull MenuTextShift menuTextShift;

    ConsoleType(@NotNull MenuTextShift menuTextShift) {
        this.menuTextShift = menuTextShift;
    }

    /**
     * @return the menu button text shift of this type in HD
     */
    public @NotNull MenuTextShift getMenuTextShift() {
        return this.menuTextShift;
    }
}
