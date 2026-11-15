package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that teammates command the resolved leader slot.
 */
final class LobbyResolutionTeamTest {
    private static final int TEMPLATE_START_SLOT_COUNT = 4;
    private static final int TEAM_COUNT = 2;
    private static final int HUMAN_TEAM = 1;
    private static final int COMPUTER_TEAM = 2;
    private static final int COMPUTER_SLOT = 4;
    private static final int SEED = 12345;

    @Test
    void resolve_should_make_teammates_command_their_leader_slot() {
        LobbyResolution.Table table = new LobbyResolution.Table();

        for (int slot = 0; slot < TEMPLATE_START_SLOT_COUNT; slot++) {
            table.templateType[slot] = LobbyResolution.TYPE_OPEN;
            table.savedType[slot]    = LobbyResolution.TYPE_OPEN;
        }

        // Team 1: two humans (ids 0 and 1) in slots 0 and 1; team 2: a computer in slot 4.
        table.playerId[0] = 0;
        table.type[0]     = MeleeGameSetup.TYPE_HUMAN;
        table.team[0]     = HUMAN_TEAM;
        table.playerId[1] = 1;
        table.type[1]     = MeleeGameSetup.TYPE_HUMAN;
        table.team[1]     = HUMAN_TEAM;

        table.type[COMPUTER_SLOT] = LobbyResolution.TYPE_COMPUTER;
        table.team[COMPUTER_SLOT] = COMPUTER_TEAM;

        for (int slot : new int[]{2, 3, 5, 6, 7}) {
            table.type[slot] = LobbyResolution.TYPE_OPEN;
        }

        LobbyResolution.Result result = LobbyResolution.resolve(table, new LobbyResolution.Options(true, false, TEAM_COUNT, false, false), SEED);
        int leader = -1;
        int follower = -1;

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            if (result.getRecords()[slot].getPlayerId() == 0) {
                leader = slot;
            }

            if (result.getRecords()[slot].getPlayerId() == 1) {
                follower = slot;
            }
        }

        Assertions.assertTrue(leader >= 0 && follower >= 0, "both humans keep a record");
        Assertions.assertEquals(leader, result.getCommandSlots()[leader], "the leader commands itself");
        Assertions.assertEquals(leader, result.getCommandSlots()[follower], "the teammate commands the leader's slot");
    }
}
