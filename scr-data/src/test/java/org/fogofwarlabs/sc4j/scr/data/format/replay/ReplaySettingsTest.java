package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.scr.core.GameSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the shared setup boundary while retaining replay-only raw fields.
 */
final class ReplaySettingsTest {
    /**
     * The player slots of the melee starting unit flags.
     */
    private static final int MELEE_PLAYER_COUNT = 8;

    @Test
    void replay_settings_keep_common_values_melee_array_aliasing_and_raw_gas_value() {
        boolean[] meleePlayers = new boolean[MELEE_PLAYER_COUNT];
        ReplaySettings replaySettings = new ReplaySettings(0,
                                                           1,
                                                           1,
                                                           7,
                                                           8,
                                                           2,
                                                           9,
                                                           10,
                                                           11, 12,
                                                           13,
                                                           0,
                                                           0xFFFFFFFFL,
                                                           0x80000000L, 0xFEDCBA98L,
                                                           meleePlayers);
        GameSettings settings = replaySettings;

        Assertions.assertEquals(1, settings.getVictoryCondition());
        Assertions.assertEquals(1, settings.getResourceType());
        Assertions.assertEquals(2, settings.getCreateInitialUnits());
        Assertions.assertEquals(0, settings.getTournamentMode());
        Assertions.assertEquals(0x80000000L, settings.getStartingMinerals());
        Assertions.assertEquals(0xFEDCBA98L, replaySettings.getStartingGas());
        Assertions.assertFalse(settings.createsMeleeUnitsForPlayer(3));
        meleePlayers[3] = true;

        Assertions.assertTrue(settings.createsMeleeUnitsForPlayer(3));
        Assertions.assertFalse(settings.createsMeleeUnitsForPlayer(-1));
        Assertions.assertFalse(settings.createsMeleeUnitsForPlayer(MELEE_PLAYER_COUNT));
    }

    @Test
    void uses_map_settings_should_require_all_zero_inputs_without_narrowing_them() {
        for (int victoryCondition : new int[]{0, 1, 256}) {
            for (int tournamentMode : new int[]{0, 1, 256}) {
                for (int initialUnits : new int[]{0, 1, 256}) {
                    GameSettings settings = new ReplaySettings(0,
                                                               victoryCondition,
                                                               0,
                                                               0,
                                                               0,
                                                               initialUnits,
                                                               0,
                                                               0,
                                                               0, 0,
                                                               0,
                                                               tournamentMode,
                                                               0,
                                                               0, 0,
                                                               new boolean[MELEE_PLAYER_COUNT]);

                    Assertions.assertEquals(victoryCondition == 0 && tournamentMode == 0 && initialUnits == 0, settings.usesMapSettings());
                }
            }
        }
    }
}
