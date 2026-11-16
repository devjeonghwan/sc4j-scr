package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks which games keep the custom lobby colors and which custom colors the game start gives the slots.
 */
final class LobbyResolutionColorTest {
    /**
     * The packed {@code 0xRRGGBB} map color of the slot whose {@code CRGB} section fixes its own color.
     */
    private static final int MAP_ORANGE = 0xFF8000;
    /**
     * The network color id of pink.
     */
    private static final int PINK_COLOR_ID = 15;
    /**
     * The game seed of the resolution.
     */
    private static final int SEED = 1234;

    @Test
    void keeps_custom_colors_should_follow_the_game_type_and_the_host_flag() {
        Assertions.assertFalse(LobbyResolution.keepsCustomColors(false, GameTemplate.TYPE_MELEE, true), "Single player clears the colours.");
        Assertions.assertTrue(LobbyResolution.keepsCustomColors(true, GameTemplate.TYPE_MELEE, false), "Melee keeps them.");
        Assertions.assertTrue(LobbyResolution.keepsCustomColors(true, GameTemplate.TYPE_TOP_VS_BOTTOM, false), "Top vs Bottom keeps them.");
        Assertions.assertFalse(LobbyResolution.keepsCustomColors(true, LobbyResolution.GAME_TYPE_USE_MAP_SETTINGS, false));
        Assertions.assertTrue(LobbyResolution.keepsCustomColors(true, LobbyResolution.GAME_TYPE_USE_MAP_SETTINGS, true));
    }

    @Test
    void custom_colors_take_the_map_color_of_a_kept_slot_and_the_network_color_of_the_others() {
        LobbyResolution.Table table = new LobbyResolution.Table();

        for (int slot = 0; slot < 2; slot++) {
            table.playerId[slot]     = slot;
            table.type[slot]         = MeleeGameSetup.TYPE_HUMAN;
            table.race[slot]         = ScrRaceIds.ZERG;
            table.templateType[slot] = LobbyResolution.TYPE_OPEN;
            table.templateRace[slot] = ScrRaceIds.ZERG;
            table.savedType[slot]    = LobbyResolution.TYPE_OPEN;
            table.colorLock[slot]    = true;
        }

        // Slot 0's CRGB mode fixes the map's own color (MapSpecified); slot 1's names a network color.
        table.lobbyColor[0] = LobbyResolution.COLOR_NONE + 1;
        table.colorKept[0]  = true;
        table.mapColor[0]   = MAP_ORANGE;
        table.lobbyColor[1] = PINK_COLOR_ID;

        LobbyResolution.Result result = LobbyResolution.resolve(table, new LobbyResolution.Options(true, true, 0, true, false), SEED);
        int[] colors = result.getCustomColors();

        Assertions.assertEquals(-1, result.getColorIds()[0], "a kept slot stays out of the color pass");
        Assertions.assertEquals(MAP_ORANGE, colors[0]);
        Assertions.assertEquals(PINK_COLOR_ID, result.getColorIds()[1]);
        Assertions.assertEquals(LobbyResolution.getColorRgb(PINK_COLOR_ID), colors[1]);

        for (int slot = 2; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            Assertions.assertEquals(LobbyResolution.getColorRgb(result.getColorIds()[slot]), colors[slot], "slot " + slot + " has its drawn color");
        }
    }
}
