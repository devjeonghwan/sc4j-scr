package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.engine.client.input.InputEvent;
import org.fogofwarlabs.sc4j.engine.client.input.Key;
import org.fogofwarlabs.sc4j.engine.client.input.KeyModifier;
import org.fogofwarlabs.sc4j.scr.client.configuration.ClientOptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests that the client's own features have no key or mouse bindings: they are chat-line commands only.
 */
final class ClientKeyBindingsTest {
    private static final int NO_MODIFIERS = 0;

    /**
     * The keys and modifiers the client used to bind: F7, F12, Ctrl+F12 and the replay viewer letters and signs.
     */
    private static final @NotNull Object @NotNull [] @NotNull [] REMOVED_KEYS = {
            {Key.F7, NO_MODIFIERS}, {Key.F12, NO_MODIFIERS}, {Key.F12, KeyModifier.CONTROL.getMask()},
            {Key.O, NO_MODIFIERS}, {Key.N, NO_MODIFIERS}, {Key.R, NO_MODIFIERS}, {Key.G, NO_MODIFIERS},
            {Key.V, NO_MODIFIERS}, {Key.L, NO_MODIFIERS}, {Key.PERIOD, NO_MODIFIERS},
            {Key.COMMA, NO_MODIFIERS}, {Key.HOME, NO_MODIFIERS}, {Key.F, NO_MODIFIERS},
            {Key.LEFT_BRACKET, NO_MODIFIERS}, {Key.RIGHT_BRACKET, NO_MODIFIERS}, {Key.M, NO_MODIFIERS},
            {Key.I, NO_MODIFIERS}, {Key.J, NO_MODIFIERS}, {Key.K, NO_MODIFIERS}, {Key.T, NO_MODIFIERS},
            {Key.BACKSPACE, NO_MODIFIERS}, {Key.W, NO_MODIFIERS}, {Key.A, NO_MODIFIERS},
            {Key.S, NO_MODIFIERS}, {Key.D, NO_MODIFIERS}
    };
    /**
     * The index of the key in a {@link #REMOVED_KEYS} entry.
     */
    private static final int KEY_INDEX = 0;
    /**
     * The index of the modifier mask in a {@link #REMOVED_KEYS} entry.
     */
    private static final int MODIFIERS_INDEX = 1;

    /**
     * The launch options of a hidden replay client that never finds its replay.
     */
    private static final @NotNull String @NotNull [] HIDDEN_CLIENT_ARGUMENTS = {"--replay", "absent.rep", "--hidden", "--frames", "1"};

    /**
     * The console player that {@code /console player 3} selects.
     */
    private static final int CONSOLE_PLAYER = 3;

    /**
     * Creates a {@link Main} instance of a hidden client.
     *
     * @return the created {@link Main} instance
     */
    private static @NotNull Main createClient() {
        return new Main(ClientOptions.parse(HIDDEN_CLIENT_ARGUMENTS), null);
    }

    @Test
    void removed_client_keys_change_nothing() {
        Main client = createClient();
        GameClientState state = client.gameClientState;
        int consolePlayer = client.consolePlayer;
        boolean replayControls = client.consoleReplayControls;
        int animation = state.getInspection().getRequestedAnimation();

        for (Object[] entry : REMOVED_KEYS) {
            Key key = (Key) entry[KEY_INDEX];
            int modifiers = (Integer) entry[MODIFIERS_INDEX];

            state.onKeyEvent(InputEvent.createKey(InputEvent.Type.KEY_PRESSED, key, modifiers));
            state.onKeyEvent(InputEvent.createKey(InputEvent.Type.KEY_RELEASED, key, modifiers));
        }

        Assertions.assertFalse(client.debugView, "F12 no longer shows the debug view");
        Assertions.assertEquals("Debug panel page: performance.", client.runPromptCommand("/debug page"), "Ctrl+F12 no longer pages the panel");
        Assertions.assertTrue(client.runPromptCommand("/debug lighting").startsWith("Lighting view: lights"),
                              "F7 no longer cycles the lighting views");
        Assertions.assertEquals(consolePlayer, client.consolePlayer, "G no longer cycles the console player");
        Assertions.assertEquals(replayControls, client.consoleReplayControls, "V no longer toggles the replay controls");
        Assertions.assertEquals(animation, state.getInspection().getRequestedAnimation(), "J and K no longer choose an animation");
        Assertions.assertFalse(state.getChatPrompt().isOpen());
    }

    /**
     * Checks that the commands reach what the keys did.
     */
    @Test
    void prompt_commands_reach_former_key_features() {
        Main client = createClient();

        Assertions.assertEquals("Debug view shown.", client.runPromptCommand("/debug"));
        Assertions.assertTrue(client.debugView);
        Assertions.assertEquals("Debug panel page: performance.", client.runPromptCommand("/debug page"));
        Assertions.assertTrue(client.runPromptCommand("/debug lighting").startsWith("Lighting view: lights"));
        Assertions.assertEquals("Console player 3.", client.runPromptCommand("/console player 3"));
        Assertions.assertEquals(CONSOLE_PLAYER, client.consolePlayer);
        Assertions.assertEquals("No file dialog in a hidden run.", client.runPromptCommand("/replay open"));
    }

    /**
     * Checks that the wheel zoom and the drag panning are off until {@code /zoom on} and {@code /pan on}.
     */
    @Test
    void wheel_zoom_and_drag_pan_start_off_until_enabled_by_command() {
        Main client = createClient();
        GameClientState state = client.gameClientState;

        Assertions.assertFalse(state.isWheelZoom());
        Assertions.assertFalse(state.isDragPan());
        Assertions.assertEquals("Wheel zoom on.", client.runPromptCommand("/zoom on"));
        Assertions.assertEquals("Drag panning on.", client.runPromptCommand("/pan on"));
        Assertions.assertTrue(state.isWheelZoom());
        Assertions.assertTrue(state.isDragPan());
        Assertions.assertEquals("Wheel zoom off.", client.runPromptCommand("/zoom off"), "without a game view there is no zoom to reset");
        Assertions.assertFalse(state.isWheelZoom());
    }
}
