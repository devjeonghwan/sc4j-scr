package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrDialog;
import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks victory, draw, disconnected and finished replay outcome boxes on installed dialogs and texts.
 */
@RequiresInstallation
final class OutcomeBoxTest {
    private static final int RESULT_TEXT = -20;
    private static final int DISCONNECTED_TEXT = -21;
    private static final int DRAW_TEXT = -23;

    private static boolean isControlVisible(@NotNull OutcomeBox box, int identifier) {
        ScrDialog dialog = box.getDialog();
        DialogControl control = dialog.getDefinition().findControl(identifier);

        return control != null && dialog.isVisible(control);
    }

    private static @NotNull String getDefaultButtonText(@NotNull OutcomeBox box, @NotNull GluText controls) {
        ScrDialog dialog = box.getDialog();
        DialogControl control = dialog.getDefinition().findControl(OutcomeBox.DEFAULT_BUTTON);

        return control == null ? "" : StatText.stripColors(dialog.getDrawnString(control, controls::get));
    }

    @Test
    void open_should_show_victory_draw_and_disconnected_boxes_per_game_mode() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            GluText controls = GluText.load(storage, "enUS", "RC3.json");
            OutcomeBox single = OutcomeBox.open(storage, true, false, false, false, controls::get);

            Assertions.assertFalse(isControlVisible(single, OutcomeBox.SECOND_BUTTON), "single player has no Continue Playing");
            Assertions.assertTrue(isControlVisible(single, RESULT_TEXT));
            Assertions.assertEquals("Victory", getDefaultButtonText(single, controls));
            Assertions.assertEquals(OutcomeBox.Action.NONE, single.press(OutcomeBox.SECOND_BUTTON));
            Assertions.assertEquals(OutcomeBox.Action.END_GAME, single.press(OutcomeBox.DEFAULT_BUTTON));

            OutcomeBox multi = OutcomeBox.open(storage, true, false, true, false, controls::get);

            Assertions.assertTrue(isControlVisible(multi, OutcomeBox.SECOND_BUTTON), "multiplayer shows Continue Playing");
            Assertions.assertEquals("End Mission", getDefaultButtonText(multi, controls));
            Assertions.assertEquals(OutcomeBox.Action.CONTINUE_PLAYING, multi.press(OutcomeBox.SECOND_BUTTON));

            OutcomeBox draw = OutcomeBox.open(storage, true, true, true, false, controls::get);

            Assertions.assertTrue(isControlVisible(draw, DRAW_TEXT), "the draw text");
            Assertions.assertFalse(isControlVisible(draw, RESULT_TEXT));
            Assertions.assertEquals("OK", getDefaultButtonText(draw, controls));

            OutcomeBox disconnected = OutcomeBox.open(storage, false, false, true, true, controls::get);

            Assertions.assertFalse(isControlVisible(disconnected, OutcomeBox.SECOND_BUTTON), "Become Observer stays hidden");
            Assertions.assertTrue(isControlVisible(disconnected, DISCONNECTED_TEXT));
            Assertions.assertFalse(isControlVisible(disconnected, RESULT_TEXT));
            Assertions.assertEquals(OutcomeBox.Action.NONE, disconnected.press(OutcomeBox.SECOND_BUTTON));
        }
    }

    @Test
    void finished_replay_box_names_end_and_exits_replay() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            GluText glue = GluText.load(storage, "enUS");
            OutcomeBox box = OutcomeBox.openReplayFinished(storage, glue::get);
            ScrDialog dialog = box.getDialog();

            Assertions.assertTrue(box.isVictory());
            Assertions.assertFalse(isControlVisible(box, OutcomeBox.SECOND_BUTTON), "no Continue Playing in a replay");
            Assertions.assertTrue(isControlVisible(box, RESULT_TEXT));
            Assertions.assertEquals("The replay has finished.", dialog.requireControl(RESULT_TEXT).getReplacedText());
            Assertions.assertEquals(glue.get("replayEndGameButton"), dialog.requireControl(OutcomeBox.DEFAULT_BUTTON).getReplacedText());
            Assertions.assertEquals(OutcomeBox.Action.END_GAME, box.press(OutcomeBox.DEFAULT_BUTTON));
            Assertions.assertEquals(OutcomeBox.Action.NONE, box.press(OutcomeBox.SECOND_BUTTON));
        }
    }
}
