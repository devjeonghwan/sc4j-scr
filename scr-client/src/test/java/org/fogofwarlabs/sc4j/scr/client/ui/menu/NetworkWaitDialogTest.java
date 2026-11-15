package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogDefinition;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrDialog;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrControlType;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalInterfaceState;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks waiting player lines, countdown visibility, drop eligibility and local waiting stamps.
 */
final class NetworkWaitDialogTest {
    private static final int COUNTDOWN = -30;
    private static final int FIRST_NAME = -10;
    private static final int NAME_LINES = PlayerTable.PLAYER_SLOTS;

    private static final int DIALOG_LEFT = 184;
    private static final int DIALOG_TOP = 32;
    private static final int DIALOG_WIDTH = 264;
    private static final int DIALOG_HEIGHT = 288;
    private static final int BUTTON_LEFT = 20;
    private static final int BUTTON_WIDTH = 224;
    private static final int BUTTON_HEIGHT = 28;
    private static final int DROP_TOP = 252;
    private static final int COUNTDOWN_TOP = 222;
    private static final int NAME_LEFT = 28;
    private static final int FIRST_NAME_TOP = 20;
    private static final int NAME_PITCH = 15;
    private static final int NAME_WIDTH = 220;
    private static final int NAME_HEIGHT = 14;

    private static final int NOT_WAITING = -1;
    private static final int WAIT_SECONDS = 45;
    private static final int OPEN_CLOCK = 1000;
    private static final int WAIT_CLOCK = 5000;
    private static final int DROP_CLOCK = 50000;
    private static final int CLOSE_CLOCK = 51000;

    /**
     * Creates the definition of {@code timeout.ui.json}: Drop ({@code 2}), the countdown ({@code -30}) and the twelve name lines.
     *
     * @return the created {@link DialogDefinition} instance
     */
    private static @NotNull DialogDefinition createTimeoutDialogDefinition() {
        List<DialogControl> controls = new ArrayList<>();

        controls.add(ScrDialogControlUtils.createControl(ScrControlType.PUSHBUTTON, NetworkWaitDialog.DROP,
                                                         BUTTON_LEFT, DROP_TOP, BUTTON_WIDTH, BUTTON_HEIGHT,
                                                         Set.of("WS_VISIBLE"), "", List.of()));
        controls.add(ScrDialogControlUtils.createControl(ScrControlType.CTEXT, COUNTDOWN,
                                                         BUTTON_LEFT, COUNTDOWN_TOP, BUTTON_WIDTH, BUTTON_HEIGHT,
                                                         Set.of("WS_VISIBLE"), "", List.of()));

        for (int line = 0; line < NAME_LINES; line++) {
            controls.add(ScrDialogControlUtils.createControl(ScrControlType.LTEXT, FIRST_NAME - line,
                                                             NAME_LEFT, FIRST_NAME_TOP + NAME_PITCH * line, NAME_WIDTH, NAME_HEIGHT,
                                                             Set.of("WS_VISIBLE"), "", List.of()));
        }

        return new DialogDefinition(NetworkWaitDialog.FILE, DIALOG_LEFT, DIALOG_TOP, DIALOG_WIDTH, DIALOG_HEIGHT, controls);
    }

    @Test
    void update_should_show_waited_players_and_countdown_until_drop_is_allowed() {
        DialogDefinition definition = createTimeoutDialogDefinition();
        NetworkWaitDialog wait = new NetworkWaitDialog(definition);
        LocalInterfaceState local = new LocalInterfaceState();

        wait.update(NOT_WAITING, List.of(), local, OPEN_CLOCK);

        Assertions.assertNull(wait.getOpenDialog());

        wait.update(WAIT_SECONDS, List.of("Player B"), local, WAIT_CLOCK);

        ScrDialog dialog = wait.getOpenDialog();

        Assertions.assertNotNull(dialog);
        Assertions.assertEquals("0:45", dialog.requireControl(COUNTDOWN).getReplacedText());
        Assertions.assertEquals("Player B", dialog.requireControl(FIRST_NAME).getReplacedText());
        Assertions.assertEquals("", dialog.requireControl(FIRST_NAME - 1).getReplacedText());

        DialogControl countdownControl = definition.findControl(COUNTDOWN);

        Assertions.assertNotNull(countdownControl);
        Assertions.assertTrue(dialog.isVisible(countdownControl));

        DialogControl dropControl = definition.findControl(NetworkWaitDialog.DROP);

        Assertions.assertNotNull(dropControl);
        Assertions.assertFalse(dialog.isEnabled(dropControl));
        Assertions.assertEquals(WAIT_SECONDS, local.getStallCountdown());

        wait.update(0, List.of("Player B"), local, DROP_CLOCK);

        Assertions.assertFalse(dialog.isVisible(countdownControl));
        Assertions.assertTrue(dialog.isEnabled(dropControl));

        // Closing stores the host clock in the waiting stamp.
        wait.update(NOT_WAITING, List.of(), local, CLOSE_CLOCK);

        Assertions.assertNull(wait.getOpenDialog());
        Assertions.assertEquals(CLOSE_CLOCK, local.getStallStamp());
    }
}
