package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerModifiers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that the kills and deaths of the unit type after the last unit type read the counters after their tables, which the tests address by DWORD
 * index because the EUD-visible game-context layout, the death table after the kill table and the classic technology availability bytes after the
 * death table, is the contract.
 */
final class PlayerScoresTest {
    /**
     * The player whose deaths are set.
     */
    private static final int PLAYER = 3;
    /**
     * A player whose deaths stay unset.
     */
    private static final int OTHER_PLAYER = 2;
    /**
     * The player whose classic technologies are the first availability bytes after the death table.
     */
    private static final int FIRST_PLAYER = 0;
    /**
     * The player whose death count of the unit type after the last unit type is the availability DWORD of {@link #FIRST_PLAYER}, technologies
     * {@code 4..7}.
     */
    private static final int AVAILABILITY_PLAYER = 1;
    /**
     * The player of the Deaths read of a table without technology storage.
     */
    private static final int UNLINKED_DEATHS_PLAYER = 0;
    /**
     * The death count that the test sets.
     */
    private static final int DEATH_COUNT = 41;
    /**
     * The kill count of a player whose deaths stay unset.
     */
    private static final int UNSET_COUNT = 0;

    /**
     * The availability bytes {@code 1, 1, 1, 1} as a little-endian DWORD: every technology of the DWORD is available.
     */
    private static final int FOUR_AVAILABLE = 0x01010101;
    /**
     * The availability bytes {@code 0, 1, 0, 0} as a little-endian DWORD: only the second technology of the DWORD is available.
     */
    private static final int SECOND_OF_FOUR_AVAILABLE = 0x00000100;
    /**
     * The Scanner Sweep technology, the first technology of the availability DWORD, which the written DWORD makes unavailable.
     */
    private static final int SCANNER_SWEEP = 4;
    /**
     * The Tank Siege Mode technology, the second technology of the availability DWORD, which the written DWORD makes available.
     */
    private static final int TANK_SIEGE_MODE = 5;

    @Test
    void get_kills_should_read_first_death_row_for_unit_type_none() {
        PlayerScores scores = PlayerProgressTestUtils.createPlayerScores();

        scores.modifyDeaths(PLAYER, ScrUnitIds.TERRAN_MARINE, TriggerModifiers.SET_TO, DEATH_COUNT);

        Assertions.assertEquals(DEATH_COUNT, scores.getKills(PLAYER, TriggerUnitGroups.NONE));
        Assertions.assertEquals(UNSET_COUNT, scores.getKills(OTHER_PLAYER, TriggerUnitGroups.NONE));
    }

    @Test
    void get_deaths_should_read_the_classic_technology_availability_for_unit_type_none() {
        PlayerTable players = new PlayerTable(null);
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        PlayerTechnology technology = PlayerProgressTestUtils.createPlayerTechnology(true);

        players.setUnitAccounting(accounting);
        players.setPlayerTechnology(technology);

        PlayerScores scores = accounting.getScores();

        Assertions.assertEquals(FOUR_AVAILABLE, scores.getDeaths(PLAYER, TriggerUnitGroups.NONE));

        scores.getDeathTable().setByNativeIndex(PlayerUnitCounts.NATIVE_COUNT + AVAILABILITY_PLAYER, SECOND_OF_FOUR_AVAILABLE);

        Assertions.assertFalse(technology.isAvailable(FIRST_PLAYER, SCANNER_SWEEP));
        Assertions.assertTrue(technology.isAvailable(FIRST_PLAYER, TANK_SIEGE_MODE));
        Assertions.assertEquals(SECOND_OF_FOUR_AVAILABLE, scores.getDeaths(AVAILABILITY_PLAYER, TriggerUnitGroups.NONE));
    }

    @Test
    void get_deaths_should_throw_for_unit_type_none_without_the_technology_storage() {
        PlayerScores scores = PlayerProgressTestUtils.createPlayerScores();

        Assertions.assertThrows(UnsupportedOperationException.class, () -> scores.getDeaths(UNLINKED_DEATHS_PLAYER, TriggerUnitGroups.NONE));
    }
}
