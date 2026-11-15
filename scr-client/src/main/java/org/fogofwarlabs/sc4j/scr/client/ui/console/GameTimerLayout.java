package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.jetbrains.annotations.NotNull;

/**
 * Places the game time text: the measured text box is centered on a fixed pen, so the text grows to both sides as its format adds hours and days.
 * <p>
 * The race console centers the text on the time pen of its console class ({@link ConsoleKind}) in the {@code 640}-pixel interface, moved right by
 * half the extra interface width, whether or not the time plate is drawn. The observer console centers it on the middle of its timer frame, a fixed
 * distance below the frame top.
 */
final class GameTimerLayout {
    /**
     * The distance of the observer timer text below the top of its frame.
     */
    private static final int OBSERVER_TIMER_TOP_OFFSET = 2;

    /**
     * Creates a {@link GameTimerLayout} instance for the race console of an interface width.
     *
     * @param interfaceWidth the interface width in interface pixels
     * @param kind           the console class of the interface art
     * @return the created {@link GameTimerLayout} instance
     */
    static @NotNull GameTimerLayout ofRaceConsole(int interfaceWidth, @NotNull ConsoleKind kind) {
        return new GameTimerLayout(kind.getTimePenX() + (interfaceWidth - ConsoleLayout.INTERFACE_WIDTH) / 2, kind.getTimePenY());
    }

    /**
     * Creates a {@link GameTimerLayout} instance for the timer frame of the observer console.
     *
     * @param frameWidth the width of the timer frame in interface pixels
     * @param frameTop   the top of the timer frame
     * @return the created {@link GameTimerLayout} instance
     */
    static @NotNull GameTimerLayout ofObserverConsole(int frameWidth, int frameTop) {
        return new GameTimerLayout(frameWidth / 2, frameTop + OBSERVER_TIMER_TOP_OFFSET);
    }


    private final int centerX;
    private final int top;

    /**
     * Creates a {@link GameTimerLayout} instance.
     *
     * @param centerX the pen x the text box is centered on
     * @param top     the text top
     */
    private GameTimerLayout(int centerX, int top) {
        this.centerX = centerX;
        this.top     = top;
    }

    /**
     * @return the pen x the text box is centered on
     */
    int getCenterX() {
        return this.centerX;
    }

    /**
     * @return the text top
     */
    int getTop() {
        return this.top;
    }

    /**
     * Calculates the pen x that centers a measured text box on {@link #getCenterX()}, rounding half the width toward zero.
     *
     * @param boundsWidth the width of the text box measured at {@link #getCenterX()}
     * @return the pen x of the text
     */
    int calculateTextLeft(int boundsWidth) {
        return this.centerX - boundsWidth / 2;
    }
}
