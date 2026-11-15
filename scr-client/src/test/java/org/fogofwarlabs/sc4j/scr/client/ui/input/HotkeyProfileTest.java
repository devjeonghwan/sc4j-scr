package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatTextScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

/**
 * Checks the button strings and keys of the device, default and disabled hotkey profiles.
 */
final class HotkeyProfileTest {
    private static final int DRAGOON = 601;
    private static final int MOVE = 664;
    private static final int OUTSIDE_TABLE = 10;
    private static final @NotNull String DEFAULT_PROFILE = "$default";
    private static final @NotNull String NO_HOTKEYS_PROFILE = "$NoHotkeys";

    private static @NotNull StatText createStrings() throws IOException {
        return StatTextScenario.createStatText(MOVE, identifier -> switch (identifier) {
            case DRAGOON -> "d\\u0001드라군 소환";
            case MOVE -> "m\\u0007\\u0003M\\u0001ove";
            case OUTSIDE_TABLE -> "x\\u0007Plain";
            default -> "";
        });
    }

    @Test
    void device_profile_strips_prefixes_and_appends_key() throws IOException {
        HotkeyProfile profile = new HotkeyProfile(createStrings(), HotkeyProfile.DEVICE, Map.of());

        Assertions.assertEquals("d\u0001드라군 소환 [\u0003D\u0001]", profile.getString(DRAGOON));
        Assertions.assertEquals("m\u0007Move [\u0003M\u0001]", profile.getString(MOVE));
        Assertions.assertEquals("x\u0007Plain", profile.getString(OUTSIDE_TABLE));
        Assertions.assertEquals('m', profile.getHotkey(MOVE));
    }

    @Test
    void no_hotkeys_profile_keeps_plain_strings() throws IOException {
        HotkeyProfile profile = new HotkeyProfile(createStrings(), HotkeyProfile.NO_HOTKEYS, Map.of(MOVE, (int) 'q'));

        Assertions.assertEquals("m\u0007\u0003M\u0001ove", profile.getString(MOVE));
    }

    @Test
    void overrides_replace_key_in_string_and_hotkey() throws IOException {
        HotkeyProfile profile = new HotkeyProfile(createStrings(), HotkeyProfile.DEVICE, Map.of(MOVE, (int) 'q'));

        Assertions.assertEquals("q\u0007Move [\u0003Q\u0001]", profile.getString(MOVE));
        Assertions.assertEquals('q', profile.getHotkey(MOVE));
    }

    @Test
    void select_for_game_should_use_default_keys_when_custom_hotkeys_are_disabled() throws IOException {
        // The disabled flag selects the default keys without the user's overrides.
        String name = HotkeyProfile.selectForGame(HotkeyProfile.DEVICE, HotkeyProfile.HOTKEYS_DISABLED_FLAG);

        Assertions.assertEquals(DEFAULT_PROFILE, name);

        HotkeyProfile profile = new HotkeyProfile(createStrings(), name, Map.of(MOVE, (int) 'q'));

        Assertions.assertEquals('m', profile.getHotkey(MOVE));
        Assertions.assertEquals(NO_HOTKEYS_PROFILE, HotkeyProfile.selectForGame(HotkeyProfile.NO_HOTKEYS, 0));
    }
}
