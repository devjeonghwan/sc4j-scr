package org.fogofwarlabs.sc4j.scr.client.glue.menu;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Checks hotkey filtering, card-local collisions, save errors and default restoration.
 */
final class HotkeyEditorTest {
    private static final int MARINE = 0;
    private static final int SCV = 7;
    private static final int MOVE = 664;
    private static final int STOP = 665;
    private static final int STIM = 334;
    private static final int BUILD_COMMAND_CENTER = 646;
    /**
     * A distinct key for every action: no collision before a rebind.
     */
    private static final int DISTINCT_KEY_BASE = 0x4E00;
    private static final int ACTION_COUNT = 220;
    private static final int LATIN_SMALL_E_ACUTE = 0xE9;
    private static final int DELETE_CODE_POINT = 0x7F;
    /**
     * The key of a refused key press or of an action without a hotkey.
     */
    private static final int NO_KEY = 0;
    /**
     * The key of an action cleared by a binding collision.
     */
    private static final int CLEARED_KEY = -1;

    /**
     * Creates distinct keys for all registered actions.
     *
     * @return the created action bindings
     */
    private static @NotNull Map<Integer, Integer> createDistinctBindings() {
        Map<Integer, Integer> keys = new HashMap<>();

        for (int identifier : HotkeyPanelModel.getActions().keySet()) {
            keys.put(identifier, DISTINCT_KEY_BASE + identifier);
        }

        return keys;
    }

    @Test
    void accept_key_should_take_letters_punctuation_and_high_code_points() {
        Assertions.assertEquals('q', HotkeyEditor.acceptKey('Q'));
        Assertions.assertEquals('q', HotkeyEditor.acceptKey('q'));
        Assertions.assertEquals('`', HotkeyEditor.acceptKey('`'));

        for (char taken : "',.;[\\]".toCharArray()) {
            Assertions.assertEquals(taken, HotkeyEditor.acceptKey(taken));
        }

        Assertions.assertEquals(LATIN_SMALL_E_ACUTE, HotkeyEditor.acceptKey(LATIN_SMALL_E_ACUTE));
        Assertions.assertEquals(DELETE_CODE_POINT, HotkeyEditor.acceptKey(DELETE_CODE_POINT));

        for (char refused : "0123456789 -=/!@#{|}~\r".toCharArray()) {
            Assertions.assertEquals(NO_KEY, HotkeyEditor.acceptKey(refused), "refused " + (int) refused);
        }
    }

    @Test
    void rebind_should_unbind_colliding_action_everywhere_until_bound_again() {
        Map<Integer, Integer> keys = createDistinctBindings();

        keys.put(STOP, (int) 's');

        HotkeyEditor editor = new HotkeyEditor(keys, keys);

        editor.rebind(HotkeyPanelModelCheck.requireUnitByType(MARINE), HotkeyPanelModelCheck.requireAction(MOVE), 's');

        Assertions.assertEquals('s', editor.getKey(MOVE));
        Assertions.assertEquals(CLEARED_KEY, editor.getKey(STOP));
        Assertions.assertTrue(editor.isUnbound(STOP));
        // Every unit whose cards hold Stop now has an unbound command: the races are in error and Save is refused.
        Assertions.assertTrue(editor.hasErrors());
        Assertions.assertTrue(editor.hasError(HotkeyPanelModelCheck.requireUnitByType(MARINE)));
        Assertions.assertTrue(editor.hasError(HotkeyPanelModelCheck.requireUnitByType(SCV)));
        Assertions.assertTrue(editor.hasError(HotkeyPanelModel.Race.TERRAN));
        Assertions.assertTrue(editor.hasError(HotkeyPanelModel.Race.ZERG));
        Assertions.assertEquals(NO_KEY, editor.getBindings().get(STOP));

        // Binding Stop again clears the errors.
        editor.rebind(HotkeyPanelModelCheck.requireUnitByType(MARINE), HotkeyPanelModelCheck.requireAction(STOP), 'x');

        Assertions.assertFalse(editor.hasErrors());
        Assertions.assertFalse(editor.hasError(HotkeyPanelModel.Race.TERRAN));
    }

    @Test
    void rebind_should_allow_same_key_on_another_card() {
        HotkeyEditor editor = new HotkeyEditor(createDistinctBindings(), createDistinctBindings());

        editor.rebind(HotkeyPanelModelCheck.requireUnitByType(MARINE), HotkeyPanelModelCheck.requireAction(MOVE), 'm');
        editor.rebind(HotkeyPanelModelCheck.requireUnitByType(SCV), HotkeyPanelModelCheck.requireAction(BUILD_COMMAND_CENTER), 'm');

        Assertions.assertEquals('m', editor.getKey(MOVE));
        Assertions.assertEquals('m', editor.getKey(BUILD_COMMAND_CENTER));
        Assertions.assertFalse(editor.hasErrors());
    }

    @Test
    void has_unsaved_changes_should_follow_last_unit_checked() {
        HotkeyEditor editor = new HotkeyEditor(createDistinctBindings(), createDistinctBindings());

        // The page keeps the result of the last unit (the Infested Command Center), which has no Stim Packs.
        editor.rebind(HotkeyPanelModelCheck.requireUnitByType(MARINE), HotkeyPanelModelCheck.requireAction(STIM), 'y');

        Assertions.assertFalse(editor.hasUnsavedChanges());

        // Its lifted card holds Move.
        editor.rebind(HotkeyPanelModelCheck.requireUnitByType(MARINE), HotkeyPanelModelCheck.requireAction(MOVE), 'n');

        Assertions.assertTrue(editor.hasUnsavedChanges());
    }

    @Test
    void restore_defaults_should_copy_default_keys_and_clear_errors() {
        Map<Integer, Integer> device = createDistinctBindings();
        Map<Integer, Integer> defaults = createDistinctBindings();

        device.put(MOVE, (int) 'q');
        defaults.put(MOVE, (int) 'm');

        HotkeyEditor editor = new HotkeyEditor(device, defaults);

        Assertions.assertEquals('q', editor.getKey(MOVE));
        Assertions.assertEquals('m', editor.getDefaultKey(MOVE));

        editor.rebind(HotkeyPanelModelCheck.requireUnitByType(MARINE), HotkeyPanelModelCheck.requireAction(STOP), 'q');

        Assertions.assertTrue(editor.hasErrors());

        editor.restoreDefaults();

        Assertions.assertFalse(editor.hasErrors());
        Assertions.assertEquals('m', editor.getKey(MOVE));
        Assertions.assertEquals(defaults.get(STOP), editor.getKey(STOP));
        Assertions.assertEquals(ACTION_COUNT, editor.getBindings().size());
    }
}
