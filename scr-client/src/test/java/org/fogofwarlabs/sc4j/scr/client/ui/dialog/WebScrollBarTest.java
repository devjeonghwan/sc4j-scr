package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogDefinition;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrControlType;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * Checks the web scroll bar as a control: its synthesized definition, its place in the dialog after the attach, and its bounded offset.
 */
final class WebScrollBarTest {
    private static final int PANEL = 1;
    private static final int SCROLL = 2;
    private static final int PANEL_LEFT = 10;
    private static final int PANEL_TOP = 20;
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 100;
    private static final int DIALOG_SIZE = 300;
    private static final float CONTENT = 400;
    private static final float VIEW = 100;

    private static @NotNull DialogControl createPanel() {
        return ScrDialogControlUtils.createControl(ScrControlType.RECT, PANEL, PANEL_LEFT, PANEL_TOP, PANEL_WIDTH, PANEL_HEIGHT,
                                                   Set.of("WS_VISIBLE"), "", List.of());
    }

    @Test
    void create_definition_should_place_a_hidden_scroll_bar_on_the_panel_right_edge() {
        DialogControl definition = WebScrollBar.createDefinition(SCROLL, createPanel());

        Assertions.assertEquals(SCROLL, definition.getId());
        Assertions.assertEquals(ScrControlType.SCROLLBAR, ScrDialogControlUtils.getType(definition));
        Assertions.assertEquals(PANEL_LEFT + PANEL_WIDTH - (int) WebScrollBar.WIDTH, definition.getX());
        Assertions.assertEquals(PANEL_TOP, definition.getY());
        Assertions.assertEquals((int) WebScrollBar.WIDTH, definition.getWidth());
        Assertions.assertEquals(PANEL_HEIGHT, definition.getHeight());
        Assertions.assertFalse(ScrDialogControlUtils.hasFlag(definition, "WS_VISIBLE"));
    }

    @Test
    void attach_should_make_the_bar_the_dialog_control_of_its_definition_and_keep_it_hidden() {
        DialogControl panel = createPanel();
        DialogControl definition = WebScrollBar.createDefinition(SCROLL, panel);
        ScrDialog dialog = new ScrDialog(new DialogDefinition("test", 0, 0, DIALOG_SIZE, DIALOG_SIZE, List.of(panel, definition)));

        WebScrollBar bar = dialog.attach(new WebScrollBar(definition));

        Assertions.assertSame(bar, dialog.findControl(SCROLL));
        Assertions.assertFalse(bar.isVisible());
        Assertions.assertNotSame(bar, dialog.findControlAt(PANEL_LEFT + PANEL_WIDTH - 1, PANEL_TOP + 1));
    }

    @Test
    void wheel_should_scroll_within_the_content_and_follow_the_end_again_after_scroll_to_end() {
        WebScrollBar bar = new WebScrollBar(WebScrollBar.createDefinition(SCROLL, createPanel()));

        bar.setContent(CONTENT, VIEW);
        Assertions.assertEquals(CONTENT - VIEW, bar.getOffset());

        bar.scrollByWheel(false);
        Assertions.assertTrue(bar.getOffset() < CONTENT - VIEW);

        bar.scrollToEnd();
        bar.setContent(CONTENT + VIEW, VIEW);
        Assertions.assertEquals(CONTENT, bar.getOffset());
    }
}
