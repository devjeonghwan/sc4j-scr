package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks which games keep the custom lobby colors.
 */
final class LobbyResolutionColorTest {
    @Test
    void keeps_custom_colors_should_follow_the_game_type_and_the_host_flag() {
        Assertions.assertFalse(LobbyResolution.keepsCustomColors(false, GameTemplate.TYPE_MELEE, true), "Single player clears the colours.");
        Assertions.assertTrue(LobbyResolution.keepsCustomColors(true, GameTemplate.TYPE_MELEE, false), "Melee keeps them.");
        Assertions.assertTrue(LobbyResolution.keepsCustomColors(true, GameTemplate.TYPE_TOP_VS_BOTTOM, false), "Top vs Bottom keeps them.");
        Assertions.assertFalse(LobbyResolution.keepsCustomColors(true, LobbyResolution.GAME_TYPE_USE_MAP_SETTINGS, false));
        Assertions.assertTrue(LobbyResolution.keepsCustomColors(true, LobbyResolution.GAME_TYPE_USE_MAP_SETTINGS, true));
    }
}
