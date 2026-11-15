package org.fogofwarlabs.sc4j.scr.client.ui.observer;

import org.fogofwarlabs.sc4j.engine.client.input.Key;
import org.fogofwarlabs.sc4j.engine.client.input.KeyModifier;
import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Checks replay side-bar geometry, pane toggles, release activation and installed-string tooltips.
 */
final class ReplaySideBarTest {
    /**
     * The page's 1x geometry: eight {@code 18} px icons {@code 26} px apart with {@code 5.5} px end margins, {@code 211} px in all.
     */
    private static final float BAR_HEIGHT = 211;
    private static final float ICON_SIZE = 18;
    private static final float ICON_PITCH = 26;
    private static final float END_MARGIN = 5.5f;
    private static final float ICON_LEFT = 4;
    private static final int LAST_ICON = 7;
    private static final int ICON_COUNT = 8;
    private static final int DIPLOMACY_ICON = 2;
    private static final float ICON_CENTER = 9;
    private static final float ICON_INSIDE_EDGE = 17.9f;
    private static final float INSIDE_RIGHT_EDGE = 21.9f;
    private static final float INSIDE_LEFT_MARGIN = 3.9f;
    private static final float INSIDE_COLUMN = 10;

    /**
     * At {@code 1280x960} the bar is {@code 52} pixels wide and {@code 422} pixels high.
     */
    private static final float SMALL_PAGE_WIDTH = 640;
    private static final float MIDDLE_PAGE_WIDTH = 1280;
    private static final float LARGE_PAGE_WIDTH = 2560;
    private static final float SMALL_PAGE_SCALE = 1;
    private static final float MIDDLE_PAGE_SCALE = 2;
    private static final float LARGE_PAGE_SCALE = 4;
    private static final float MIDDLE_PAGE_BAR_WIDTH = 52;
    private static final float MIDDLE_PAGE_BAR_HEIGHT = 422;

    /**
     * Tooltip layout in units of one per character.
     */
    private static final float WIDE_CONTENT_WIDTH = 20;
    private static final float NARROW_CONTENT_WIDTH = 8;
    private static final float HEADER_GAP = 2;
    private static final float HEADER_WITH_GAP_WIDTH = 11;
    private static final float FIRST_HOTKEY_WIDTH = 7;
    private static final float SECOND_LINE_WIDTH = 5;
    private static final int LINE_COUNT = 2;
    private static final int PRODUCTION_TOOLTIP_PARTS = 2;
    /**
     * The packed {@code 0xRRGGBBAA} color of a tooltip hotkey: opaque white.
     */
    private static final int HOTKEY_COLOR = 0xFFFFFFFF;

    private static final int NO_MODIFIERS = 0;

    /**
     * The page's 1x geometry: eight {@code 18} px icons {@code 26} px apart, {@code 5.5} px end margins, {@code 211} px in all.
     */
    @Test
    void side_bar_places_eight_icons_in_page_column() {
        Assertions.assertEquals(BAR_HEIGHT, ReplaySideBar.HEIGHT);
        Assertions.assertEquals(END_MARGIN, ReplaySideBar.getButtonTop(ReplaySideBar.Button.PRODUCTION));
        Assertions.assertEquals(END_MARGIN + LAST_ICON * ICON_PITCH, ReplaySideBar.getButtonTop(ReplaySideBar.Button.HELP));
        Assertions.assertEquals(ReplaySideBar.Button.PRODUCTION, ReplaySideBar.findButton(ICON_LEFT, END_MARGIN));
        Assertions.assertEquals(ReplaySideBar.Button.DIPLOMACY,
                                ReplaySideBar.findButton(ICON_LEFT + ICON_CENTER, END_MARGIN + DIPLOMACY_ICON * ICON_PITCH + ICON_CENTER));
        Assertions.assertEquals(ReplaySideBar.Button.HELP,
                                ReplaySideBar.findButton(INSIDE_RIGHT_EDGE, END_MARGIN + LAST_ICON * ICON_PITCH + ICON_INSIDE_EDGE));
        Assertions.assertNull(ReplaySideBar.findButton(INSIDE_LEFT_MARGIN, INSIDE_COLUMN), "the left margin");
        Assertions.assertNull(ReplaySideBar.findButton(INSIDE_COLUMN, END_MARGIN + ICON_SIZE + 1), "the gap between two icons");
        Assertions.assertNull(ReplaySideBar.findButton(INSIDE_COLUMN, END_MARGIN + ICON_COUNT * ICON_PITCH), "below the last icon");
    }

    /**
     * At {@code 1280x960} the bar is {@code 52} pixels wide and {@code 422} pixels high, with icons {@code 52} pixels apart.
     */
    @Test
    void get_page_scale_should_be_one_two_and_four_at_640_1280_and_2560() {
        float scale = ObserverConsoleUtils.getPageScale(MIDDLE_PAGE_WIDTH);

        Assertions.assertEquals(MIDDLE_PAGE_SCALE, scale);
        Assertions.assertEquals(MIDDLE_PAGE_BAR_WIDTH, ReplaySideBar.WIDTH * scale);
        Assertions.assertEquals(MIDDLE_PAGE_BAR_HEIGHT, ReplaySideBar.HEIGHT * scale);
        Assertions.assertEquals(SMALL_PAGE_SCALE, ObserverConsoleUtils.getPageScale(SMALL_PAGE_WIDTH));
        Assertions.assertEquals(LARGE_PAGE_SCALE, ObserverConsoleUtils.getPageScale(LARGE_PAGE_WIDTH));
    }

    /**
     * A pane icon shows its pane with its color image and toggles it on a click released over the same icon.
     */
    @Test
    void release_over_pressed_icon_toggles_pane_or_opens_its_dialog() {
        ReplaySideBar bar = new ReplaySideBar();

        for (ReplaySideBar.Pane pane : ReplaySideBar.Pane.values()) {
            Assertions.assertTrue(bar.isShown(pane), "a new replay session shows every pane");
        }

        Assertions.assertEquals("minimap64.png", ReplaySideBar.Button.MAP.getImage(true));
        Assertions.assertEquals("minimap64_grey.png", ReplaySideBar.Button.MAP.getImage(false));

        bar.press(ReplaySideBar.Button.MAP);

        Assertions.assertEquals(ReplaySideBar.Action.NONE, bar.release(ReplaySideBar.Button.UNIT), "released over another icon");
        Assertions.assertTrue(bar.isShown(ReplaySideBar.Pane.MINIMAP));

        bar.press(ReplaySideBar.Button.MAP);

        Assertions.assertEquals(ReplaySideBar.Action.TOGGLED, bar.release(ReplaySideBar.Button.MAP));
        Assertions.assertFalse(bar.isShown(ReplaySideBar.Pane.MINIMAP));
        Assertions.assertNull(bar.getPressed());

        bar.press(ReplaySideBar.Button.DIPLOMACY);

        Assertions.assertEquals(ReplaySideBar.Action.OPEN_DIPLOMACY, bar.release(ReplaySideBar.Button.DIPLOMACY));

        bar.press(ReplaySideBar.Button.MENU);

        Assertions.assertEquals(ReplaySideBar.Action.OPEN_MENU, bar.release(ReplaySideBar.Button.MENU));

        bar.press(ReplaySideBar.Button.HELP);

        Assertions.assertEquals(ReplaySideBar.Action.NONE, bar.release(ReplaySideBar.Button.HELP), "help has only its tooltip");
    }

    /**
     * Checks hotkey precedence.
     */
    @Test
    void press_key_should_toggle_panes_by_page_hotkeys() {
        ReplaySideBar bar = new ReplaySideBar();

        Assertions.assertEquals(ReplaySideBar.Action.TOGGLED, bar.pressKey(Key.E, NO_MODIFIERS));
        Assertions.assertFalse(bar.isShown(ReplaySideBar.Pane.STATISTICS));

        bar.pressKey(Key.E, KeyModifier.ALT.getMask());

        Assertions.assertTrue(bar.isShown(ReplaySideBar.Pane.STATISTICS));
        Assertions.assertEquals(ReplaySideBar.Action.NONE, bar.pressKey(Key.F, NO_MODIFIERS), "production needs Alt or Ctrl");

        bar.pressKey(Key.F, KeyModifier.ALT.getMask());

        Assertions.assertFalse(bar.isShown(ReplaySideBar.Pane.PRODUCTION));

        bar.pressKey(Key.F, KeyModifier.CONTROL.getMask());

        Assertions.assertTrue(bar.isShown(ReplaySideBar.Pane.PRODUCTION));

        bar.pressKey(Key.Q, NO_MODIFIERS);

        Assertions.assertFalse(bar.isShown(ReplaySideBar.Pane.MINIMAP));

        bar.pressKey(Key.I, KeyModifier.ALT.getMask());

        Assertions.assertTrue(bar.isShown(ReplaySideBar.Pane.MINIMAP));

        bar.pressKey(Key.W, NO_MODIFIERS);

        Assertions.assertFalse(bar.isShown(ReplaySideBar.Pane.UNIT));

        bar.pressKey(Key.U, KeyModifier.ALT.getMask());

        Assertions.assertTrue(bar.isShown(ReplaySideBar.Pane.UNIT));

        // Alt+W is the bar itself, not the unit pane; R too.
        bar.pressKey(Key.W, KeyModifier.ALT.getMask());

        Assertions.assertFalse(bar.isShown(ReplaySideBar.Pane.NAVIGATION));
        Assertions.assertTrue(bar.isShown(ReplaySideBar.Pane.UNIT));

        bar.pressKey(Key.R, NO_MODIFIERS);

        Assertions.assertTrue(bar.isShown(ReplaySideBar.Pane.NAVIGATION));
        Assertions.assertEquals(ReplaySideBar.Action.OPEN_DIPLOMACY, bar.pressKey(Key.Z, KeyModifier.ALT.getMask()));
        Assertions.assertEquals(ReplaySideBar.Action.NONE, bar.pressKey(Key.Z, NO_MODIFIERS));

        bar.pressKey(Key.Y, KeyModifier.CONTROL.getMask());

        Assertions.assertFalse(bar.isShown(ReplaySideBar.Pane.REPLAY));
        Assertions.assertEquals(ReplaySideBar.Action.NONE, bar.pressKey(Key.M, KeyModifier.ALT.getMask()),
                                "Alt+M is the accelerator table's game menu");
    }

    /**
     * A hides every pane when all are shown, and otherwise shows them all.
     */
    @Test
    void toggle_all_should_hide_all_when_all_shown_else_show_all() {
        ReplaySideBar bar = new ReplaySideBar();

        bar.pressKey(Key.A, NO_MODIFIERS);

        for (ReplaySideBar.Pane pane : ReplaySideBar.Pane.values()) {
            Assertions.assertFalse(bar.isShown(pane));
        }

        bar.pressKey(Key.A, NO_MODIFIERS);

        for (ReplaySideBar.Pane pane : ReplaySideBar.Pane.values()) {
            Assertions.assertTrue(bar.isShown(pane));
        }

        bar.setShown(ReplaySideBar.Pane.UNIT, false);
        bar.toggleAll();

        Assertions.assertTrue(bar.isShown(ReplaySideBar.Pane.UNIT), "not all shown: all are shown");
    }

    /**
     * Inline blocks flow in the {@code 150} px box; a header wider than the box wraps into lines of its own.
     */
    @Test
    void layout_tooltip_should_flow_inline_blocks_and_wrap_wide_header() {
        List<ReplaySideBar.TooltipPart> parts = List.of(new ReplaySideBar.TooltipPart("aaaa bbbb", ReplaySideBar.TOOLTIP_TEXT, true),
                                                        new ReplaySideBar.TooltipPart("Alt + I", ReplaySideBar.TOOLTIP_HOTKEY, false),
                                                        new ReplaySideBar.TooltipPart(" or ", ReplaySideBar.TOOLTIP_TEXT, false),
                                                        new ReplaySideBar.TooltipPart("Q", ReplaySideBar.TOOLTIP_HOTKEY, false));
        // One unit per character.
        List<ReplaySideBar.TooltipLine> lines = ReplaySideBar.layoutTooltip(parts, String::length, WIDE_CONTENT_WIDTH, HEADER_GAP);

        Assertions.assertEquals(LINE_COUNT, lines.size());
        Assertions.assertEquals(HEADER_WITH_GAP_WIDTH + FIRST_HOTKEY_WIDTH, lines.get(0).getWidth(), "the header with its gap and the first hotkey");
        Assertions.assertEquals(SECOND_LINE_WIDTH, lines.get(1).getWidth());

        List<ReplaySideBar.TooltipLine> wrapped = ReplaySideBar.layoutTooltip(parts, String::length, NARROW_CONTENT_WIDTH, HEADER_GAP);

        Assertions.assertEquals("aaaa", wrapped.get(0).getParts().getFirst().getText());
        Assertions.assertEquals("bbbb", wrapped.get(1).getParts().getFirst().getText());
        Assertions.assertEquals("Alt + I", wrapped.get(2).getParts().getFirst().getText(), "the hotkeys start below a wrapped header");
    }

    /**
     * Checks tooltips built from the installed Korean strings.
     */
    @RequiresInstallation
    @Test
    void build_tooltip_should_use_installed_strings_and_hotkeys() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            GluText text = GluText.load(storage, "koKR");
            List<ReplaySideBar.TooltipPart> map = ReplaySideBar.buildTooltip(ReplaySideBar.Button.MAP, text);

            Assertions.assertEquals("미니맵을 표시하거나 감춥니다.", map.get(0).getText());
            Assertions.assertEquals("Alt + I", map.get(1).getText());
            Assertions.assertEquals(" 또는 ", map.get(2).getText());
            Assertions.assertEquals("Q", map.get(3).getText());
            Assertions.assertEquals(HOTKEY_COLOR, map.get(3).getColor());

            List<ReplaySideBar.TooltipPart> production = ReplaySideBar.buildTooltip(ReplaySideBar.Button.PRODUCTION, text);

            Assertions.assertEquals("연구 창을 표시하거나 감춥니다.", production.get(0).getText());
            Assertions.assertEquals(PRODUCTION_TOOLTIP_PARTS, production.size());
        }
    }
}
