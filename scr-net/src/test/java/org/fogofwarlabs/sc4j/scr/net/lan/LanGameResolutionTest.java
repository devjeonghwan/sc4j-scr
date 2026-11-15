package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.TestResourceUtils;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeader;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.session.setup.LobbyResolution;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Resolves recorded Top vs Bottom lobbies and compares their players and colors with the replays of the captured games.
 */
final class LanGameResolutionTest {
    private static final int HOST_SLOT = 0;
    private static final int JOINER_SLOT = 1;
    private static final int HOST_TEAM = 1;
    private static final int JOINER_TEAM = 2;
    private static final @NotNull String HOST_NAME = "B";
    private static final @NotNull String JOINER_NAME = "A";
    /**
     * The seeds of the two captured games.
     */
    private static final int FIRST_GAME_SEED = 0x6ABCBEE2;
    private static final int SECOND_GAME_SEED = 0x6ABCBF70;

    private static final int PROTOSS_RACE = 2;

    private static @NotNull ReplayHeader loadReplayHeader(@NotNull String replay) throws IOException {
        return ReplayFile.parse(TestResourceUtils.readAllBytes(replay)).getHeader();
    }

    private static @NotNull LobbyResolution.Result resolveLobby(int hostRace, int joinerRace, int seed) {
        LanLobby.Setup setup = LanTestResources.createCapturedLobbySetup(HOST_NAME);
        MeleeGameSetup.PlayerRecord[] records = new MeleeGameSetup.PlayerRecord[LanLobby.RECORDS];
        byte[] lobbyData = new byte[LanLobby.LOBBY_DATA_SIZE];

        for (int index = 0; index < LanLobby.RECORDS; index++) {
            records[index] = setup.getRecord(index);
        }

        records[HOST_SLOT]   = new MeleeGameSetup.PlayerRecord(HOST_SLOT, PlayerTypes.HUMAN, hostRace, HOST_TEAM, HOST_NAME);
        records[JOINER_SLOT] = new MeleeGameSetup.PlayerRecord(JOINER_SLOT, PlayerTypes.HUMAN, joinerRace, JOINER_TEAM, JOINER_NAME);

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            lobbyData[LanLobby.LOBBY_DATA_COLORS + slot] = (byte) LobbyResolution.COLOR_NONE;
        }

        LobbyResolution.Table table = LanLobby.createResolutionTable(LanLobby.createGameSettings(setup), records, lobbyData);

        return LobbyResolution.resolve(table, LanLobby.createResolutionOptions(LanGameOptions.TOP_VS_BOTTOM_1), seed);
    }

    private static void assertRecords(@NotNull ReplayHeader header, @NotNull LobbyResolution.Result result) {
        List<ReplayHeader.ReplayPlayer> players = header.getPlayers();

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            ReplayHeader.ReplayPlayer expected = players.get(slot);
            MeleeGameSetup.PlayerRecord actual = result.getRecords()[slot];
            String where = "slot " + slot;

            Assertions.assertEquals(expected.getId(), actual.getPlayerId(), where);
            Assertions.assertEquals(expected.getType(), actual.getType(), where);
            Assertions.assertEquals(expected.getRace(), actual.getRace(), where);
            Assertions.assertEquals(expected.getTeam(), actual.getTeam(), where);
        }

        Assertions.assertArrayEquals(header.getColors(), result.getColors());
    }


    @Test
    void first_game_resolves_colors_and_records_like_its_replay() throws IOException {
        LobbyResolution.Result result = resolveLobby(ScrRaceIds.ZERG, ScrRaceIds.TERRAN, FIRST_GAME_SEED);

        Assertions.assertArrayEquals(new int[]{3, 7, 4, 19, 18, 2, 12, 0}, result.getColorIds());
        Assertions.assertArrayEquals(new int[]{7, 3, 1, 5, 6, 2, 4, 0}, result.getColors());
        assertRecords(loadReplayHeader(LanTestResources.REPLAY_TOP_VS_BOTTOM_FIRST), result);
    }

    @Test
    void second_game_resolves_the_random_race_like_its_replay() throws IOException {
        LobbyResolution.Result result = resolveLobby(LobbyResolution.SELECTABLE_RACE, ScrRaceIds.TERRAN, SECOND_GAME_SEED);

        Assertions.assertEquals(PROTOSS_RACE, result.getRecords()[HOST_SLOT].getRace());
        Assertions.assertArrayEquals(new int[]{17, 18, 15, 5, 4, 8, 16, 3}, result.getColorIds());
        Assertions.assertArrayEquals(new int[]{0, 5, 3, 1, 4, 2, 6, 7}, result.getColors());
        assertRecords(loadReplayHeader(LanTestResources.REPLAY_TOP_VS_BOTTOM_RANDOM_RACE), result);
    }
}
