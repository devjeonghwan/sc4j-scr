package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ClientSessionScenario;
import org.fogofwarlabs.sc4j.scr.client.ui.console.ConsoleModel;
import org.fogofwarlabs.sc4j.scr.client.ui.input.HotkeyProfile;
import org.fogofwarlabs.sc4j.scr.client.ui.input.WorldInterface;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Checks the hotkey release timer, immediate second presses and the cancellation of presses by card refreshes, which in a
 * live game keeps Escape from activating Lift Off when the Cancel slot changes.
 */
final class HotkeyPressTest {
    /**
     * A card whose refreshes canceled no press.
     */
    private static final @NotNull IntUnaryOperator NONE = slot -> 0;
    private static final int NO_ACTIVATION = -1;
    private static final int TIMED_SLOT = 3;
    private static final long PRESS_TIME = 1000;
    private static final int SECOND_PRESS_SLOT = 2;
    private static final long SECOND_PRESS_TIME = 5;
    private static final long LATE_POLL_TIME = 100;
    private static final int FIRST_SLOT = 0;
    private static final int SECOND_SLOT = 1;
    private static final long LATER_PRESS_TIME = 1000;
    /**
     * The Cancel button's slot.
     */
    private static final int CANCEL_SLOT = 8;
    private static final int OTHER_SLOT = 7;
    private static final long FRESH_PRESS_TIME = 30;
    /**
     * The hotkey release delay: a press releases its button twenty milliseconds after it.
     */
    private static final long RELEASE_DELAY = 20;

    private static final int FASTEST_SPEED = 6;
    private static final int GAME_SPEED_SETTING = 4;
    private static final int REPLAY_SPEED = 1;
    private static final int TRAINING_MINERALS = 1000;
    private static final int COMMAND_FRAMES = 3;
    private static final int TRAINING_FRAMES = 2;
    private static final int TRAINING_COMMAND_COUNT = 3;
    private static final int AFTER_ESCAPE_FRAMES = 30;
    private static final int MAXIMUM_LIFT_OFF_FRAMES = 200;
    private static final int SETTLE_FRAMES = 60;
    private static final char ESCAPE = 27;
    private static final int ESCAPE_PRESSES = 12;
    private static final int SCV_TYPE = 7;
    private static final int COMMAND_CENTER_TYPE = 106;

    /**
     * Steps the client session for the specified number of silent frames.
     *
     * @param session the client session
     * @param frames  the number of frames
     */
    private static void stepFrames(@NotNull ClientSession session, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            session.stepFrame(false);
        }
    }

    @Test
    void hotkey_press_releases_after_twenty_milliseconds() {
        HotkeyPress press = new HotkeyPress();

        Assertions.assertEquals(NO_ACTIVATION, press.press(NONE, TIMED_SLOT, false, PRESS_TIME));
        Assertions.assertTrue(press.isPressed(TIMED_SLOT));
        Assertions.assertEquals(NO_ACTIVATION, press.poll(NONE, PRESS_TIME + RELEASE_DELAY));
        Assertions.assertEquals(TIMED_SLOT, press.poll(NONE, PRESS_TIME + RELEASE_DELAY + 1));
        Assertions.assertFalse(press.isPressed(TIMED_SLOT));
    }

    @Test
    void hotkey_second_press_activates_at_once() {
        HotkeyPress press = new HotkeyPress();

        press.press(NONE, SECOND_PRESS_SLOT, false, 0);

        Assertions.assertEquals(SECOND_PRESS_SLOT, press.press(NONE, SECOND_PRESS_SLOT, true, SECOND_PRESS_TIME));
        Assertions.assertTrue(press.isReleasedQueued());
        Assertions.assertEquals(NO_ACTIVATION, press.poll(NONE, LATE_POLL_TIME));
    }

    @Test
    void hotkey_press_keeps_second_button_pressed_while_first_release_is_timed() {
        HotkeyPress press = new HotkeyPress();

        press.press(NONE, FIRST_SLOT, false, 0);
        press.press(NONE, SECOND_SLOT, false, SECOND_PRESS_TIME);

        Assertions.assertEquals(FIRST_SLOT, press.poll(NONE, RELEASE_DELAY + 1));
        Assertions.assertTrue(press.isPressed(SECOND_SLOT));
        Assertions.assertEquals(NO_ACTIVATION, press.poll(NONE, LATER_PRESS_TIME));
        Assertions.assertEquals(SECOND_SLOT, press.press(NONE, SECOND_SLOT, false, LATER_PRESS_TIME + 1));
    }

    @Test
    void mouse_press_is_kept_until_refresh_changes_button() {
        HotkeyPress press = new HotkeyPress();
        int[] cancellations = new int[CommandCard.SLOT_COUNT];

        press.pressMouse(slot -> cancellations[slot], CANCEL_SLOT);

        Assertions.assertTrue(press.isMousePressKept(slot -> cancellations[slot], CANCEL_SLOT));
        Assertions.assertFalse(press.isMousePressKept(slot -> cancellations[slot], OTHER_SLOT), "another button is not pressed");

        cancellations[CANCEL_SLOT]++;

        Assertions.assertFalse(press.isMousePressKept(slot -> cancellations[slot], CANCEL_SLOT), "the refresh cancelled the press");

        press.releaseMouse();
        press.pressMouse(slot -> cancellations[slot], CANCEL_SLOT);

        Assertions.assertTrue(press.isMousePressKept(slot -> cancellations[slot], CANCEL_SLOT));

        press.releaseMouse();

        Assertions.assertFalse(press.isMousePressKept(slot -> cancellations[slot], CANCEL_SLOT));
    }

    @Test
    void refresh_that_replaces_button_cancels_press() {
        // Esc pressed on Cancel (slot 8); the refresh then shows Lift Off there with another icon.
        HotkeyPress press = new HotkeyPress();
        int[] cancellations = new int[CommandCard.SLOT_COUNT];

        Assertions.assertEquals(NO_ACTIVATION, press.press(slot -> cancellations[slot], CANCEL_SLOT, false, 0));

        cancellations[CANCEL_SLOT]++;

        Assertions.assertEquals(NO_ACTIVATION, press.poll(slot -> cancellations[slot], RELEASE_DELAY + 1), "the release activates nothing");
        Assertions.assertFalse(press.isPressed(CANCEL_SLOT));

        // The next key press is a fresh press, not an immediate activation of the new button.
        Assertions.assertEquals(NO_ACTIVATION, press.press(slot -> cancellations[slot], CANCEL_SLOT, false, FRESH_PRESS_TIME));
    }

    @RequiresInstallation
    @Test
    void escape_cancels_queue_but_never_lifts_off() throws IOException {
        try (ClientSession session = ClientSession.startCampaign(ExternalInput.INSTALLATION.getPath(),
                                                                 "bw-terran-1", "Tester", FASTEST_SPEED, ClientSessionScenario.GAME_SEED,
                                                                 "enUS")) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();
            stepFrames(session, SETTLE_FRAMES);

            Game game = session.getGame();
            int player = session.getLocalPlayer();

            Player localPlayer = game.getPlayerTable().getPlayer(player);

            Assertions.assertNotNull(localPlayer);

            localPlayer.setMinerals(TRAINING_MINERALS);

            ConsoleModel console = new ConsoleModel(new ScrGameView(game), new ScrPresentation(game));
            WorldInterface world = new WorldInterface(console, (owner, opcode, payload) -> session.submitLocalCommand(owner, opcode, payload), true);
            CommandInput input = world.getCommandInput();
            HotkeyProfile profile = new HotkeyProfile(StatText.load(session.getStorage(), "enUS"), HotkeyProfile.DEVICE, Map.of());

            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            int requiredType = ScrUnitIds.TERRAN_COMMAND_CENTER;
            UnitPool unitPool = game.getUnitSystem().getUnitPool();
            Unit commandCenter = null;

            Assertions.assertNotNull(unitPool);

            for (int index = 1; index <= unitPool.getCapacity(); index++) {
                if (unitPool.isAllocated(index) && unitPool.getUnit(index).getOwnerIndex() == player &&
                    unitPool.getUnit(index).getUnitId() == requiredType) {
                    commandCenter = unitPool.getUnit(index);

                    break;
                }
            }

            Assertions.assertNotNull(commandCenter);
            input.select(commandCenter);
            stepFrames(session, COMMAND_FRAMES);
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            for (int count = 0; count < TRAINING_COMMAND_COUNT; count++) {
                Assertions.assertEquals(CommandInput.Result.COMMAND, input.pressHotkey(profile, 's', false));
                stepFrames(session, TRAINING_FRAMES);
                console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);
            }

            Assertions.assertTrue(commandCenter.getBuildQueueUnitType(commandCenter.getBuildQueueIndex()) == SCV_TYPE, "an SCV trains");

            // Esc spam through the client's press / release path: each press matches the card of its time; a release
            // runs 20 ms later, after the frame that may have refreshed the card.
            HotkeyPress press = new HotkeyPress();
            CommandCard card = console.getCommandCard();
            long now = 0;

            for (int count = 0; count < ESCAPE_PRESSES; count++) {
                int slot = input.findHotkeySlot(profile, ESCAPE);

                if (slot >= 0) {
                    int activated = press.press(card::getPressCancellations, slot, false, now);

                    if (activated >= 0) {
                        input.pressSlot(activated, false);
                    }
                }

                stepFrames(session, 1);
                console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);
                now += HotkeyPress.RELEASE_DELAY_MILLISECONDS + 1;

                int released = press.poll(card::getPressCancellations, now);

                if (released >= 0) {
                    input.pressSlot(released, false);
                }

                stepFrames(session, 1);
                console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);
            }

            stepFrames(session, AFTER_ESCAPE_FRAMES);

            Assertions.assertEquals(COMMAND_CENTER_TYPE, commandCenter.getUnitId());
            Assertions.assertTrue((commandCenter.getStatus() & UnitStatusFlags.FLYING) == 0, "Esc never lifts the Command Center off");
            Assertions.assertEquals(-1, input.findHotkeySlot(profile, ESCAPE), "no button of the empty card has the Esc hotkey");

            // Lift Off answers its own hotkey, L.
            console.update(player, CommandCard.Perspective.PLAYER, true, GAME_SPEED_SETTING, REPLAY_SPEED);

            Assertions.assertEquals(CommandInput.Result.COMMAND, input.pressHotkey(profile, 'l', false));

            boolean lifted = false;

            for (int frame = 0; frame < MAXIMUM_LIFT_OFF_FRAMES && !lifted; frame++) {
                session.stepFrame(false);
                lifted = (commandCenter.getStatus() & UnitStatusFlags.FLYING) != 0;
            }

            Assertions.assertTrue(lifted, "L lifts the Command Center off");
        }
    }
}
