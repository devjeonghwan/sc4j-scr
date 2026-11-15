package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that the unit availability section applies the player values and, for the players using the default, the global values.
 */
final class PlayerUnitAvailabilityTest {
    /**
     * The first player slot.
     */
    private static final int FIRST_PLAYER = 0;
    /**
     * The fourth player slot.
     */
    private static final int FOURTH_PLAYER = 3;
    /**
     * The sixth player slot.
     */
    private static final int SIXTH_PLAYER = 5;
    /**
     * The rows of a unit availability section: a row for every player slot, the default row and a use-default row for every player slot.
     */
    private static final int UNIT_SECTION_ROWS = 25;
    /**
     * The global row of a unit availability section, after the player rows.
     */
    private static final int GLOBAL_ROW = 12;
    /**
     * The first use-default row of a unit availability section.
     */
    private static final int USE_DEFAULT_FIRST_ROW = 13;
    /**
     * The section BYTE of a set allowed, available, researched or use-default value.
     */
    private static final byte SECTION_SET = 1;
    /**
     * The section BYTE of a cleared allowed value.
     */
    private static final byte SECTION_CLEAR = 0;

    @Test
    void apply_section_should_apply_player_and_default_values() {
        byte[] units = new byte[UnitsDat.UNIT_COUNT * UNIT_SECTION_ROWS];

        units[FOURTH_PLAYER * UnitsDat.UNIT_COUNT + ScrUnitIds.TERRAN_SCV] = SECTION_SET;
        units[GLOBAL_ROW * UnitsDat.UNIT_COUNT + ScrUnitIds.TERRAN_SCV]    = SECTION_CLEAR;
        units[GLOBAL_ROW * UnitsDat.UNIT_COUNT + ScrUnitIds.TERRAN_WRAITH] = SECTION_SET;

        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            units[USE_DEFAULT_FIRST_ROW * UnitsDat.UNIT_COUNT + player * UnitsDat.UNIT_COUNT + ScrUnitIds.TERRAN_WRAITH] = SECTION_SET;
        }

        PlayerUnitAvailability availability = PlayerProgressTestUtils.createPlayerUnitAvailability();

        availability.applySection(units);

        Assertions.assertTrue(availability.isAllowed(FOURTH_PLAYER, ScrUnitIds.TERRAN_SCV));
        Assertions.assertFalse(availability.isAllowed(FIRST_PLAYER, ScrUnitIds.TERRAN_SCV));
        Assertions.assertTrue(availability.isAllowed(SIXTH_PLAYER, ScrUnitIds.TERRAN_WRAITH),
                              "The global value replaces players using the default.");
    }
}
