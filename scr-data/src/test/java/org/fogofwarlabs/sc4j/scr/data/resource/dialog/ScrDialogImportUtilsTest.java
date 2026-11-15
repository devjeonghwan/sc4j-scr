package org.fogofwarlabs.sc4j.scr.data.resource.dialog;

import org.fogofwarlabs.sc4j.engine.resource.dialog.ControlKind;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogDefinition;
import org.fogofwarlabs.sc4j.engine.resource.dialog.TextAlignment;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Tests the import of an SC:R interface file: dialog and control rectangles, the engine kinds and the native type, flags
 * and content kept in the attributes.
 */
final class ScrDialogImportUtilsTest {
    private static final byte @NotNull [] DIALOG = """
                                                   {"name": "gluTest", "rect": [10, 20, 300, 200], "children": [
                                                     {"type": "CTEXT", "id": 3, "rect": [5, 6, 40, 12], "flags": ["WS_VISIBLE", "RC_SMALLTEXT", 7], "content": "title"},
                                                     {"type": "PushButton", "id": 4, "rect": [1, 2, 30, 20], "children": [
                                                       {"type": "HOTBOX", "id": 3, "rect": [4, 5, 14, 25]},
                                                       {"type": "TEXTBOX", "rect": [0, 0, 8, 8], "content": "label"}
                                                     ]},
                                                     {"type": "frobnicator", "id": 9, "rect": [0, 0, 1, 1]}
                                                   ]}
                                                   """.getBytes(StandardCharsets.UTF_8);

    @Test
    void parse_should_keep_rectangles_and_derive_kinds_from_native_types() throws IOException {
        DialogDefinition definition = ScrDialogImportUtils.parse(DIALOG, "fallback");
        DialogControl title = definition.getControls().getFirst();
        DialogControl button = definition.getControls().get(1);
        DialogControl hotBox = button.getChildren().getFirst();
        DialogControl label = ScrDialogControlUtils.findChild(button, ScrControlType.TEXTBOX);

        Assertions.assertEquals("gluTest", definition.getName());
        Assertions.assertEquals(10, definition.getX());
        Assertions.assertEquals(300, definition.getWidth());
        Assertions.assertEquals(ControlKind.LABEL, title.getKind());
        Assertions.assertEquals(TextAlignment.CENTER, title.getTextAlignment());
        Assertions.assertEquals(40, title.getWidth());
        Assertions.assertEquals("title", title.getText());
        Assertions.assertEquals(ControlKind.BUTTON, button.getKind());
        Assertions.assertEquals(ScrControlType.PUSHBUTTON, ScrDialogControlUtils.getType(button));
        Assertions.assertEquals(ControlKind.CUSTOM, hotBox.getKind());
        Assertions.assertEquals(10, hotBox.getWidth());
        Assertions.assertEquals(20, hotBox.getHeight());
        Assertions.assertEquals(ScrControlType.OTHER, ScrDialogControlUtils.getType(definition.getControls().get(2)));
        Assertions.assertEquals("", ScrDialogControlUtils.getContent(button));
        Assertions.assertNotNull(label);
        Assertions.assertEquals("label", ScrDialogControlUtils.getContent(label));
    }

    @Test
    void controls_should_answer_flags_bits_and_top_level_ids() throws IOException {
        DialogDefinition definition = ScrDialogImportUtils.parse(DIALOG, "fallback");
        DialogControl title = definition.getControls().getFirst();

        Assertions.assertTrue(ScrDialogControlUtils.hasFlag(title, "WS_VISIBLE"));
        Assertions.assertFalse(ScrDialogControlUtils.hasFlag(title, "WS_TABSTOP"));
        Assertions.assertEquals(0x82, ScrDialogControlUtils.getFlagBits(title));
        Assertions.assertSame(title, ScrDialogControlUtils.findControl(definition, 3));
        Assertions.assertNull(ScrDialogControlUtils.findControl(definition, 0));
        Assertions.assertThrows(IOException.class, () -> ScrDialogImportUtils.parse("[]".getBytes(StandardCharsets.UTF_8), "list"));
    }
}
