package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that the game time text is centered on the console's pen for every console class, interface width and text length.
 */
final class GameTimerLayoutTest {
    private static final int CLASSIC_CENTER_X = 358;
    private static final int CLASSIC_TOP = 365;
    private static final int REMASTERED_CENTER_X = 351;
    private static final int REMASTERED_TOP = 363;
    /**
     * The width of the 4:3 interface; a wider interface adds half of its extra width on each side of the console.
     */
    private static final int INTERFACE_WIDTH = 640;
    private static final int CENTER_DIVISOR = 2;
    /**
     * The interface width of a 1920x1032 screen, {@code 126} pixels wider on each side than the 640-pixel interface.
     */
    private static final int WIDE_INTERFACE_WIDTH = 893;
    private static final int WIDE_REMASTERED_CENTER_X = 477;
    /**
     * Interface widths of 4:3, 16:10 and 16:9 screens at the interface height of 480.
     */
    private static final int @NotNull [] INTERFACE_WIDTHS = {640, 768, 854};
    /**
     * The game time formats from minutes and seconds to days.
     */
    private static final @NotNull String @NotNull [] TEXTS = {"00:00", "59:59", "01:00:00", "1:00:00:00"};
    private static final int CHARACTER_ADVANCE = 5;
    /**
     * The measured text box reaches two pixels past each side of the text.
     */
    private static final int BOX_MARGINS = 4;
    /**
     * An odd text box width and its half, truncated.
     */
    private static final int ODD_BOUNDS_WIDTH = 29;
    private static final int HALF_ODD_BOUNDS_WIDTH = 14;
    /**
     * The width and top of an observer timer frame, and the text top two pixels below the frame top.
     */
    private static final int OBSERVER_FRAME_WIDTH = 151;
    private static final int OBSERVER_FRAME_TOP = 300;
    private static final int OBSERVER_CENTER_X = 75;
    private static final int OBSERVER_TOP = 302;

    @Test
    void of_race_console_should_center_the_text_box_on_the_console_pen_for_every_width_and_format() {
        for (ConsoleKind kind : ConsoleKind.values()) {
            int penX = kind == ConsoleKind.CLASSIC ? CLASSIC_CENTER_X : REMASTERED_CENTER_X;
            int top = kind == ConsoleKind.CLASSIC ? CLASSIC_TOP : REMASTERED_TOP;

            for (int interfaceWidth : INTERFACE_WIDTHS) {
                GameTimerLayout layout = GameTimerLayout.ofRaceConsole(interfaceWidth, kind);
                int centerX = penX + (interfaceWidth - INTERFACE_WIDTH) / CENTER_DIVISOR;

                Assertions.assertEquals(centerX, layout.getCenterX(), kind.name());
                Assertions.assertEquals(top, layout.getTop(), kind.name());

                for (String text : TEXTS) {
                    int boundsWidth = text.length() * CHARACTER_ADVANCE + BOX_MARGINS;

                    Assertions.assertEquals(centerX - boundsWidth / CENTER_DIVISOR, layout.calculateTextLeft(boundsWidth), text);
                }
            }
        }
    }

    @Test
    void of_race_console_should_use_the_remastered_pen_for_the_hd_art() {
        GameTimerLayout layout = GameTimerLayout.ofRaceConsole(ConsoleLayout.INTERFACE_WIDTH, ConsoleKind.REMASTERED);
        GameTimerLayout wide = GameTimerLayout.ofRaceConsole(WIDE_INTERFACE_WIDTH, ConsoleKind.REMASTERED);

        Assertions.assertEquals(REMASTERED_CENTER_X, layout.getCenterX());
        Assertions.assertEquals(REMASTERED_TOP, layout.getTop());
        Assertions.assertEquals(WIDE_REMASTERED_CENTER_X, wide.getCenterX());
    }

    @Test
    void of_observer_console_should_center_the_text_on_the_frame_middle_below_its_top() {
        GameTimerLayout layout = GameTimerLayout.ofObserverConsole(OBSERVER_FRAME_WIDTH, OBSERVER_FRAME_TOP);

        Assertions.assertEquals(OBSERVER_CENTER_X, layout.getCenterX());
        Assertions.assertEquals(OBSERVER_TOP, layout.getTop());
    }

    @Test
    void calculate_text_left_should_round_half_an_odd_width_toward_the_pen() {
        GameTimerLayout layout = GameTimerLayout.ofRaceConsole(ConsoleLayout.INTERFACE_WIDTH, ConsoleKind.CLASSIC);

        Assertions.assertEquals(CLASSIC_CENTER_X - HALF_ODD_BOUNDS_WIDTH, layout.calculateTextLeft(ODD_BOUNDS_WIDTH));
    }
}
