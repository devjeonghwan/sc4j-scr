package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Checks that lobby players can reach every configured start slot.
 */
final class LobbyResolutionStartSlotTest {
    private static final int SEED_CASE_COUNT = 64;
    private static final int START_SLOT_COUNT = 4;
    private static final int HUMAN_PLAYER_COUNT = 2;
    private static final int CLOSED_START_SLOT = 3;
    private static final int MELEE_TEAM = 1;
    /**
     * The player type of a human.
     */
    private static final int HUMAN_TYPE = 2;
    /**
     * The races of the two players and of the template records.
     */
    private static final int ZERG_RACE = 0;
    private static final int PROTOSS_RACE = 2;
    private static final int RANDOM_RACE = 6;

    @Test
    void resolve_should_let_two_players_reach_every_start_slot_of_a_four_player_map() {
        Set<Integer> reached = new HashSet<>();

        for (int seed = 1; seed <= SEED_CASE_COUNT; seed++) {
            LobbyResolution.Table table = new LobbyResolution.Table();

            for (int slot = 0; slot < START_SLOT_COUNT; slot++) {
                table.templateType[slot] = LobbyResolution.TYPE_OPEN;
                table.savedType[slot]    = LobbyResolution.TYPE_OPEN;
                table.templateRace[slot] = ScrRaceIds.RANDOM;
                table.templateTeam[slot] = MELEE_TEAM;
                table.type[slot]         = LobbyResolution.TYPE_OPEN;
                table.race[slot]         = ScrRaceIds.RANDOM;
            }

            // The two players hold the lobby slots 0 and 1; slot 2 is open and slot 3 closed.
            table.type[CLOSED_START_SLOT] = LobbyResolution.TYPE_CLOSED;

            for (int player = 0; player < HUMAN_PLAYER_COUNT; player++) {
                table.playerId[player] = player;
                table.type[player]     = MeleeGameSetup.TYPE_HUMAN;
                table.race[player]     = player == 0 ? ScrRaceIds.ZERG : ScrRaceIds.PROTOSS;
                table.team[player]     = MELEE_TEAM;
            }

            LobbyResolution.Result result = LobbyResolution.resolve(table, new LobbyResolution.Options(true, false, 0, false, false), seed);
            int players = 0;

            for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
                MeleeGameSetup.PlayerRecord record = result.getRecords()[slot];

                if (record.getPlayerId() >= 0) {
                    players++;
                    reached.add(slot);

                    Assertions.assertEquals(HUMAN_TYPE, record.getType());
                    Assertions.assertEquals(record.getPlayerId() == 0 ? ZERG_RACE : PROTOSS_RACE, record.getRace(), "a player keeps its race");
                } else {
                    Assertions.assertEquals(0, record.getType(), "seed " + seed + " slot " + slot + " holds no player");

                    if (slot < START_SLOT_COUNT) {
                        Assertions.assertEquals(RANDOM_RACE, record.getRace(), "seed " + seed + " slot " + slot + " is the template record");
                    }
                }
            }

            Assertions.assertEquals(HUMAN_PLAYER_COUNT, players, "seed " + seed);
        }

        Assertions.assertEquals(Set.of(0, 1, 2, 3), reached, "every start slot, the open and the closed one too");
        Assertions.assertTrue(reached.stream().allMatch(slot -> slot < START_SLOT_COUNT), "no slot without a start location");
    }
}
