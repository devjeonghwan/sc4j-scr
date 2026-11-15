package org.fogofwarlabs.sc4j.scr.game.map;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the team layout of {@link StartingUnitLoader.TeamStart}: the team size and the slot races that each force counts.
 */
final class TeamStartTest {
    /**
     * The team count of the force race test.
     */
    private static final int THREE_TEAMS = 3;
    /**
     * The slot count of a team in a game of {@link #THREE_TEAMS} teams.
     */
    private static final int THREE_TEAM_SIZE = 3;
    /**
     * The first slot of the third team in a game of {@link #THREE_TEAMS} teams, after the slots of the first and the second team.
     */
    private static final int THIRD_TEAM_FIRST_SLOT = THREE_TEAM_SIZE + THREE_TEAM_SIZE;
    /**
     * The third force of the force race test, with forces numbered from {@code 1}.
     */
    private static final int THIRD_FORCE = 3;
    /**
     * The leader slot of the first force.
     */
    private static final int FIRST_FORCE_LEADER = 0;
    /**
     * The leader slot of a force without players.
     */
    private static final int NO_FORCE_OWNER = -1;
    /**
     * The number of Protoss slots that the third force counts: slot {@code 6}, and slot {@code 6} again in place of slot {@code 8}.
     */
    private static final int THIRD_FORCE_PROTOSS_SLOTS = 2;
    /**
     * The number of Zerg slots that the third force counts: slot {@code 7}.
     */
    private static final int THIRD_FORCE_ZERG_SLOTS = 1;

    @Test
    void count_force_race_should_repeat_first_slot_for_third_force_of_three_teams() {
        int zerg = ScrRaceIds.ZERG;
        int protoss = ScrRaceIds.PROTOSS;
        int[] slotRaces = {zerg, zerg, zerg, zerg, zerg, zerg, protoss, zerg};
        int[] forceOwners = {FIRST_FORCE_LEADER, THREE_TEAM_SIZE, THIRD_TEAM_FIRST_SLOT, NO_FORCE_OWNER};
        // The third force counts its first slot again in place of the ninth slot, which does not exist.
        StartingUnitLoader.TeamStart teamStart = new StartingUnitLoader.TeamStart(THREE_TEAMS, slotRaces, forceOwners);

        Assertions.assertEquals(THREE_TEAM_SIZE, teamStart.getTeamSize());
        Assertions.assertEquals(THIRD_FORCE_PROTOSS_SLOTS, teamStart.countForceRace(THIRD_FORCE, protoss), "Slots 6, 7 and (for slot 8) slot 6.");
        Assertions.assertEquals(THIRD_FORCE_ZERG_SLOTS, teamStart.countForceRace(THIRD_FORCE, zerg));
    }
}
