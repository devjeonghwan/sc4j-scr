package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Checks the font selection of a dialog control by its text size flags.
 */
final class ControlPainterUtilsTest {
    /**
     * The interface font slots from the smallest to the biggest font.
     */
    private static final int SMALL_FONT = 0;
    private static final int NORMAL_FONT = 1;
    private static final int BIG_FONT = 2;
    private static final int BIGGER_FONT = 3;

    @Test
    void get_font_slot_should_select_font_by_text_size_flags() {
        Assertions.assertEquals(NORMAL_FONT, ControlPainterUtils.getFontSlot(Set.of()));
        Assertions.assertEquals(SMALL_FONT, ControlPainterUtils.getFontSlot(Set.of("RC_SMALLTEXT", "RC_BIGTEXT")));
        Assertions.assertEquals(BIG_FONT, ControlPainterUtils.getFontSlot(Set.of("RC_BIGTEXT")));
        Assertions.assertEquals(BIG_FONT, ControlPainterUtils.getFontSlot(Set.of("RC_BIGTEXT", "RC_BIGGERTEXT")));
        Assertions.assertEquals(BIGGER_FONT, ControlPainterUtils.getFontSlot(Set.of("RC_BIGGERTEXT")));
    }
}
